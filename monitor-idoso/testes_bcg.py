"""
Testes do processador de BCG noturno: FC e FR conhecidas são recuperadas do
sinal sintético de colchão e janelas sem sinal são rejeitadas.
Valida a mecânica do DSP; a calibração mecânica real exige o pad físico.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from bcg_noturno import estimar_noturno, gerar_sinal_bcg  # noqa: E402


def test_recupera_fc_e_fr():
    for fc, fr in ((58, 12), (70, 15), (82, 18)):
        s = gerar_sinal_bcg(fc_bpm=fc, fr_irpm=fr, segundos=300, semente=fc)
        r = estimar_noturno(s)
        assert r["ok"], f"fc={fc}, fr={fr}: rejeitado ({r['motivo']})"
        assert abs(r["fc_bpm"] - fc) <= 3, f"fc={fc}: medido {r['fc_bpm']}"
        assert abs(r["fr_irpm"] - fr) <= 1.5, f"fr={fr}: medido {r['fr_irpm']}"
        print(f"  fc {fc} -> {r['fc_bpm']} | fr {fr} -> {r['fr_irpm']}")


def test_rejeita_sem_sinal():
    import numpy as np
    rng = np.random.default_rng(0)
    r = estimar_noturno(rng.normal(0, 0.15, 30000))
    assert not r["ok"]
    print(f"  ruído puro -> ok=False ({r['motivo']})")


def test_rejeita_janela_curta():
    r = estimar_noturno(gerar_sinal_bcg(segundos=60))
    assert not r["ok"]
    print(f"  60 s -> ok=False ({r['motivo']})")


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
