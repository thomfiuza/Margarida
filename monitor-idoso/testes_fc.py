"""
Testes de regressão do rPPG: o pipeline recupera a FC conhecida do vídeo
sintético e rejeita os casos em que não há pulso confiável.
Valida a mecânica — NÃO a precisão clínica (para isso: UBFC-RPPG/PURE/MMSE-HR).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from fc_camera import estimar_fc, extrair_sinal, detectar_roi_pele  # noqa: E402
from video_sintetico import gerar_video  # noqa: E402


def test_recupera_fc_em_repouso():
    for fc in (55, 72, 100):
        frames = gerar_video(fc_bpm=fc, segundos=30, semente=10 + int(fc))
        roi = detectar_roi_pele(frames[0])
        assert roi is not None, "ROI de pele não detectada no vídeo sintético"
        r = estimar_fc(extrair_sinal(frames, roi), 30.0)
        assert r["ok"], f"fc={fc}: rejeitado ({r['motivo']})"
        assert abs(r["fc_bpm"] - fc) <= 3, f"fc={fc}: medido {r['fc_bpm']}"
        print(f"  fc real {fc:>3} -> medido {r['fc_bpm']:>6.1f} (SNR {r['snr']})")


def test_rejeita_sem_pulso():
    frames = gerar_video(amplitude=0.0, semente=3)
    roi = detectar_roi_pele(frames[0])
    r = estimar_fc(extrair_sinal(frames, roi), 30.0)
    assert not r["ok"], "vídeo sem pulso foi aceito"
    print(f"  sem pulso -> ok=False ({r['motivo']})")


def test_rejeita_video_curto():
    frames = gerar_video(segundos=5, semente=4)
    roi = detectar_roi_pele(frames[0])
    r = estimar_fc(extrair_sinal(frames, roi), 30.0)
    assert not r["ok"], "vídeo de 5 s aceito"
    print(f"  5 s -> ok=False ({r['motivo']})")


def test_movimento_degrada_snr():
    quiet = gerar_video(movimento_px=0.0, semente=5)
    mov = gerar_video(movimento_px=7.0, semente=5)
    r1 = estimar_fc(extrair_sinal(quiet, detectar_roi_pele(quiet[0])), 30.0)
    r2 = estimar_fc(extrair_sinal(mov, detectar_roi_pele(mov[0])), 30.0)
    assert r2["snr"] < r1["snr"], f"movimento não degradou SNR ({r2['snr']} vs {r1['snr']})"
    print(f"  SNR parado {r1['snr']} -> com movimento {r2['snr']}")


if __name__ == "__main__":
    testes = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    falhas = 0
    for t in testes:
        try:
            t()
            print(f"PASS  {t.__name__}")
        except AssertionError as e:
            falhas += 1
            print(f"FAIL  {t.__name__}: {e}")
    print(f"\n{len(testes) - falhas}/{len(testes)} testes passaram")
    sys.exit(1 if falhas else 0)
