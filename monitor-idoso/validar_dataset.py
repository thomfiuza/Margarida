"""
Validação do fc_camera.py contra datasets públicos com ground truth de pulso.

- UBFC-RPPG: https://sites.google.com/view/ybenezeth/ubfcrppg
  Acesso por FORMULÁRIO/contato com os autores (não há mirror direto confiável;
  os links de outros mirrors são zips de 4-7 GB instáveis). Estrutura esperada:
  <raiz>/DATASET2/subject<N>/vid.avi + groundtruth.txt (onda de pulso por frame).
- LGI-PPGI (CC-BY 4.0): https://github.com/partofthestars/LGI-PPGI-DB
  Hospedagem oficial instável (zips de 4-7 GB); contactar autores/CanControls.
  Estrutura esperada: <raiz>/<sessao>/*.avi + arquivo de onda de pulso/HR.

Como rodar (fora deste sandbox, após obter os dados):
    python3 validar_dataset.py --raiz /caminho/UBFC --tipo ubfc
    python3 validar_dataset.py --raiz /caminho/LGI   --tipo lgi

Métrica por janela de 20 s: FC de referência = frequência dominante da onda de
pulso (FFT); FC medida = pipeline fc_camera no vídeo da mesma janela. Relatório:
MAE, viés, limites de concordância (Bland-Altman) e taxa de aceitação pelo SNR.

Este script NÃO consegue baixar os datasets (são gateados/GB demais); o
--selftest verifica a matemática do relatório com pares sintéticos.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import numpy as np

from fc_camera import estimar_fc, extrair_sinal, detectar_roi_pele

F_MIN, F_MAX = 0.7, 3.5


def fc_referencia_da_onda(onda: np.ndarray, fps: float) -> float | None:
    n = len(onda)
    if n / fps < 8:
        return None
    onda = onda - np.convolve(onda, np.ones(int(fps)), "same")
    esp = np.abs(np.fft.rfft(onda * np.hanning(n))) ** 2
    f = np.fft.rfftfreq(n, 1 / fps)
    b = (f >= F_MIN) & (f <= F_MAX)
    if b.sum() < 4 or esp[b].max() <= 0:
        return None
    return float(f[b][int(np.argmax(esp[b]))]) * 60


def janelas(n_total: int, fps: float, seg=20.0, passo=10.0):
    w, p = int(seg * fps), int(passo * fps)
    for i0 in range(0, n_total - w + 1, p):
        yield i0, i0 + w


def avaliar_pares(pares: list[tuple[float, float]]) -> dict:
    """pares = [(referencia_bpm, medido_bpm)]. MAE, viés e limites 95%."""
    if not pares:
        return {"n": 0}
    errs = np.array([m - r for r, m in pares])
    mae = float(np.abs(errs).mean())
    vies = float(errs.mean())
    lim = 1.96 * float(errs.std() + 1e-9)
    return {"n": len(pares), "mae_bpm": round(mae, 2), "vies_bpm": round(vies, 2),
            "limites_95": [round(vies - lim, 2), round(vies + lim, 2)]}


def iterar_ubfc(raiz: Path):
    for vid in sorted(raiz.glob("**/vid.avi")):
        gt = vid.parent / "groundtruth.txt"
        if not gt.exists():
            continue
        onda = np.loadtxt(gt)
        yield str(vid), onda


def iterar_lgi(raiz: Path):
    for vid in sorted(raiz.glob("**/*.avi")):
        cand = list(vid.parent.glob("*pulse*.txt")) + list(vid.parent.glob("*pulse*.csv"))
        if not cand:
            continue
        onda = np.loadtxt(cand[0], delimiter=",")
        if onda.ndim > 1:
            onda = onda[:, 0]
        yield str(vid), onda


def validar(raiz: Path, tipo: str, fps_padrao: float) -> dict:
    pares, aceitas, rejeitadas = [], 0, 0
    it = iterar_ubfc(raiz) if tipo == "ubfc" else iterar_lgi(raiz)
    fps_onda = 60.0 if tipo == "lgi" else 30.0
    for caminho, onda in it:
        cap = cv2.VideoCapture(caminho)
        fps = cap.get(cv2.CAP_PROP_FPS) or fps_padrao
        frames = []
        while True:
            ok, fr = cap.read()
            if not ok:
                break
            frames.append(fr)
        cap.release()
        roi = detectar_roi_pele(frames[0]) if frames else None
        sinal = extrair_sinal(frames, roi) if frames else np.array([])
        for i0, i1 in janelas(len(frames), fps):
            ref = fc_referencia_da_onda(onda[int(i0 * fps_onda / fps):int(i1 * fps_onda / fps)], fps_onda)
            med = estimar_fc(sinal[i0:i1], fps)
            if ref is None:
                continue
            if med["ok"]:
                aceitas += 1
                pares.append((ref, med["fc_bpm"]))
            else:
                rejeitadas += 1
    res = avaliar_pares(pares)
    res["janelas_aceitas"] = aceitas
    res["janelas_rejeitadas_pelo_snr"] = rejeitadas
    return res


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--raiz", type=Path)
    p.add_argument("--tipo", choices=["ubfc", "lgi"], default="ubfc")
    p.add_argument("--fps", type=float, default=30.0)
    p.add_argument("--selftest", action="store_true")
    a = p.parse_args()

    if a.selftest:
        rng = np.random.default_rng(0)
        refs = rng.uniform(55, 110, 40)
        meds = refs + rng.normal(0, 3, 40)
        r = avaliar_pares(list(zip(refs, meds)))
        assert abs(r["mae_bpm"] - np.abs(meds - refs).mean()) < 0.01
        assert r["n"] == 40
        print("selftest OK:", json.dumps(r))
    else:
        print(json.dumps(validar(a.raiz, a.tipo, a.fps), indent=2))
