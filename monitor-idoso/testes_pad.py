"""Testes do quadro ADXL345 e do silêncio do leito (pad_bcg.py)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from pad_bcg import codificar_quadro, decodificar_quadro, detectar_saida_leito  # noqa: E402


def test_quadro_roundtrip():
    bruto = codificar_quadro(7, 100, [1.0, -0.5, 0.0])
    q = decodificar_quadro(bruto)
    assert q is not None
    assert q["seq"] == 7 and q["fs_hz"] == 100
    assert abs(q["amostras_g"][0] - 1.0) < 1e-9
    assert abs(q["amostras_g"][1] + 0.5) < 1e-9
    assert decodificar_quadro(b"\x00\x01\x02") is None
    assert decodificar_quadro(bruto[:-1]) is None
    print("  quadro ADXL: 1 g e -0,5 g sobrevivem ao byte; lixo é rejeitado")


def test_silencio_no_eixo():
    sinal = [1.0] * 400 + [0.0] * 200 + [1.0] * 400
    r = detectar_saida_leito(sinal, fs=10.0, janela_s=20.0)
    assert r["maior_silencio_s"] == 20.0, r
    assert r["silencios"] == [(40.0, 60.0)], r
    cheio = [0.8] * 800
    assert detectar_saida_leito(cheio, fs=10.0, janela_s=20.0)["silencios"] == []
    print("  20 s de cama vazia no meio do sinal; noite cheia -> zero silêncios")


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
    print(f"\n{len(testes) - falhas}/{len(testes)}")
    sys.exit(1 if falhas else 0)
