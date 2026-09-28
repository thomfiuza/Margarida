"""
Frequência cardíaca pela câmera (rPPG) — OpenCV/numpy puro.

Física do método: a cada batimento o volume de sangue na pele varia ~1%, o que
modula sutilmente a luz verde refletida. Filmando o rosto a 30 fps e extraindo a
média do canal verde numa ROI de pele por frame, o pulso vira uma senoide cuja
frequência é a FC. É o único sinal cardiovascular que a câmera RGB captura de
forma reproduzível; pressão arterial por rPPG continua ±8-15 mmHg e dependente de
calibração com manguito (ver mapa-ideias.md).

Pipeline: ROI de pele -> sinal verde -> detrend -> Hann -> FFT -> pico parabólico
em 0.7-3.5 Hz -> FC + SNR (portão de qualidade).
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

FC_MIN_HZ, FC_MAX_HZ = 0.7, 3.5          # 42-210 bpm
SNR_MINIMO = 3.0                          # calibrado nos testes sintéticos
SEGUNDOS_MINIMOS = 10


# --------------------------------------------------------------------------- #
def detectar_roi_pele(frame: np.ndarray) -> tuple[int, int, int, int] | None:
    """ROI de pele: maior blob com H/S de pele, restrito ao terço superior do
    quadro (rosto costuma estar lá em selfie/vídeo guiado)."""
    hsv = cv2.cvtColor(frame, cv2.COLOR_BGR2HSV)
    h, s, v = hsv[:, :, 0], hsv[:, :, 1], hsv[:, :, 2]
    pele = (((h < 25) | (h > 165)) & (s > 25) & (s < 170) & (v > 60)).astype(np.uint8) * 255
    pele = cv2.morphologyEx(pele, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    n, _, st, _ = cv2.connectedComponentsWithStats(pele, 8)
    if n <= 1:
        return None
    i = 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))
    area = st[i, cv2.CC_STAT_AREA]
    if area < 0.02 * frame.shape[0] * frame.shape[1]:
        return None
    x, y, w, hh = (int(v_) for v_ in st[i, :4])
    # testa/bochechas: miolo do blob, longe das bordas (cabelo/fundo)
    mx, my = int(x + w * 0.25), int(y + hh * 0.20)
    mw, mh = int(w * 0.50), int(hh * 0.45)
    return mx, my, mw, mh


def extrair_sinal(frames, roi=None) -> np.ndarray:
    """Média do canal verde por frame dentro da ROI (fixa ou detectada no 1º)."""
    vals = []
    caixa = roi
    for fr in frames:
        if caixa is None:
            caixa = detectar_roi_pele(fr)
        if caixa is None:
            vals.append(np.nan)
            continue
        x, y, w, h = caixa
        verde = fr[y:y + h, x:x + w, 1].astype(np.float64)
        vals.append(float(verde.mean()))
    return np.asarray(vals, np.float64)


def _interpolar_nans(s: np.ndarray) -> np.ndarray:
    if not np.isnan(s).any():
        return s
    idx = np.arange(len(s))
    ok = ~np.isnan(s)
    if ok.sum() < 2:
        return np.zeros_like(s)
    return np.interp(idx, idx[ok], s[ok])


def estimar_fc(sinal: np.ndarray, fps: float) -> dict:
    sinal = _interpolar_nans(np.asarray(sinal, np.float64))
    n = len(sinal)
    if fps <= 0 or n / fps < SEGUNDOS_MINIMOS:
        return {"ok": False, "motivo": f"vídeo curto demais (<{SEGUNDOS_MINIMOS} s) para resolver FC"}

    # detrend: remove deriva de iluminação/lento movimento
    t = np.arange(n) / fps
    sinal = sinal - np.convolve(sinal, np.ones(int(fps * 1.5)), "same")
    sinal -= np.polyval(np.polyfit(t, sinal, 1), t)

    janela = np.hanning(n)
    espectro = np.abs(np.fft.rfft(sinal * janela)) ** 2
    freqs = np.fft.rfftfreq(n, 1 / fps)
    banda = (freqs >= FC_MIN_HZ) & (freqs <= FC_MAX_HZ)
    if banda.sum() < 8:
        return {"ok": False, "motivo": "resolução espectral insuficiente"}
    fb, pb = freqs[banda], espectro[banda]

    i = int(np.argmax(pb))
    # pico parabólico (sub-bin)
    if 0 < i < len(pb) - 1:
        y0, y1, y2 = np.log(pb[i - 1] + 1e-12), np.log(pb[i] + 1e-12), np.log(pb[i + 1] + 1e-12)
        den = (y0 - 2 * y1 + y2)
        delta = 0.5 * (y0 - y2) / den if abs(den) > 1e-12 else 0.0
        f_pico = fb[i] + delta * (fb[1] - fb[0])
    else:
        f_pico = fb[i]

    # SNR: potência perto do pico vs. resto da banda
    perto = np.abs(fb - f_pico) <= 0.12
    ruido = pb[~perto].sum() + 1e-12
    snr = float(pb[perto].sum() / ruido)

    fc = float(f_pico * 60)
    if not (FC_MIN_HZ <= f_pico <= FC_MAX_HZ):
        return {"ok": False, "motivo": "pico fora da faixa fisiológica", "snr": round(snr, 2)}
    return {
        "ok": snr >= SNR_MINIMO,
        "fc_bpm": round(fc, 1),
        "snr": round(snr, 2),
        "resolucao_hz": round(fb[1] - fb[0], 3),
        "motivo": None if snr >= SNR_MINIMO else f"SNR {snr:.2f} < {SNR_MINIMO} (sem pulso confiável)",
    }


def analisar_video(caminho: str | Path, roi=None, max_frames: int | None = None) -> dict:
    cap = cv2.VideoCapture(str(caminho))
    if not cap.isOpened():
        return {"ok": False, "motivo": "não abriu o vídeo"}
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    frames = []
    while True:
        ok, fr = cap.read()
        if not ok:
            break
        frames.append(fr)
        if max_frames and len(frames) >= max_frames:
            break
    cap.release()
    if not frames:
        return {"ok": False, "motivo": "vídeo sem frames"}
    sinal = extrair_sinal(frames, roi)
    r = estimar_fc(sinal, fps)
    r["n_frames"] = len(frames)
    r["fps"] = round(fps, 2)
    return r


if __name__ == "__main__":
    import argparse

    p = argparse.ArgumentParser(description="FC por rPPG a partir de vídeo")
    p.add_argument("video")
    p.add_argument("--json")
    a = p.parse_args()
    r = analisar_video(a.video)
    print(json.dumps(r, indent=2, ensure_ascii=False))
    if a.json:
        Path(a.json).write_text(json.dumps(r, indent=2, ensure_ascii=False))
