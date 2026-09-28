"""
Testes de regressão do pipeline.

Cada teste gera um olho sintético com um parâmetro conhecido variado e confere
se a métrica correspondente se move na direção esperada (correlação monotônica).

Isso valida a MECANICA do pipeline. Não valida precisão clínica — não existe
dado clínico aqui.
"""
from __future__ import annotations

import sys
import tempfile
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from analisador_olho import analisar, indice_superficie  # noqa: E402
from olho_sintetico import gerar_olho  # noqa: E402

TMP = Path(tempfile.mkdtemp(prefix="olho_test_"))


def _analisar_sintetico(**kw):
    img = gerar_olho(**kw)
    p = TMP / f"{abs(hash(tuple(sorted(kw.items())))) % 10**8}.png"
    cv2.imwrite(str(p), img)
    return analisar(p), p


def _correlacao(x, y):
    return float(np.corrcoef(np.asarray(x, float), np.asarray(y, float))[0, 1])


# --------------------------------------------------------------------------- #
def test_vermelhidao_aumenta_com_vermelhidao():
    vals, rrs = [], []
    for v in (0.0, 0.25, 0.5, 0.75, 1.0):
        r, _ = _analisar_sintetico(vermelhidao=v, semente=11)
        assert r.ok, r.motivo_rejeicao
        vals.append(v)
        rrs.append(r.vermelhidao["relative_redness"])
    c = _correlacao(vals, rrs)
    # r alto mas não 1.0: a relação é monotônica porém levemente não linear
    # (a razão (R-G)/(R+G+B) comprime o topo da escala). O que se exige é a
    # ordenação correta em todos os 5 pontos.
    assert c > 0.95, f"relative_redness não acompanhou vermelhidao (r={c:.3f}): {rrs}"
    assert rrs == sorted(rrs), f"relative_redness não monotônica: {rrs}"
    print(f"  relative_redness: {rrs[0]:+.4f} -> {rrs[-1]:+.4f} (r={c:.3f})")


def test_densidade_vascular_aumenta_com_numero_de_vasos():
    vals, dv = [], []
    for n in (10, 30, 55, 80):
        r, _ = _analisar_sintetico(vasos=n, semente=3)
        assert r.ok, r.motivo_rejeicao
        vals.append(n)
        dv.append(r.vasculatura["densidade_vascular_frac"])
    c = _correlacao(vals, dv)
    assert c > 0.85, f"densidade vascular não acompanhou nº de vasos (r={c:.3f}): {dv}"
    assert dv[-1] > dv[0] * 1.1, f"densidade vascular não cresceu o bastante: {dv}"
    print(f"  densidade_vascular: {dv[0]:.4f} -> {dv[-1]:.4f} (r={c:.3f})")


def test_manchas_secas_aumentam_com_filme_rompido():
    vals, mn = [], []
    for m in (0.0, 0.25, 0.5, 0.75, 1.0):
        r, _ = _analisar_sintetico(mancha_seca=m, semente=5)
        assert r.ok, r.motivo_rejeicao
        vals.append(m)
        mn.append(r.filme_lacrimal["frac_manchas_opacas"])
    c = _correlacao(vals, mn)
    assert c > 0.90, f"manchas opacas não acompanharam mancha_seca (r={c:.3f}): {mn}"
    assert mn[-1] > mn[0] * 1.3, f"manchas opacas não cresceram o bastante: {mn}"
    print(f"  frac_manchas_opacas: {mn[0]:.4f} -> {mn[-1]:.4f} (r={c:.3f})")


def test_indice_sobe_com_piora():
    bom = indice_superficie(vasos=0.03, manchas=0.01, vermelhidao_rr=0.02)
    ruim = indice_superficie(vasos=0.22, manchas=0.12, vermelhidao_rr=0.09)
    assert bom < ruim, f"índice não discriminou: bom={bom} ruim={ruim}"
    assert 0.0 <= bom <= 100 and 0.0 <= ruim <= 100
    print(f"  índice bom={bom} / ruim={ruim}")


def test_foto_desfocada_e_rejeitada():
    r, _ = _analisar_sintetico(desfoque_px=6.0, semente=2)
    assert not r.ok, "imagem borrada foi aceita"
    assert "desfocada" in (r.motivo_rejeicao or "")
    print(f"  rejeitada: {r.motivo_rejeicao}")


def test_foto_estourada_e_rejeitada():
    img = gerar_olho(semente=4)
    img = np.clip(img.astype(np.int16) * 2.2, 0, 255).astype(np.uint8)
    p = TMP / "estourada.png"
    cv2.imwrite(str(p), img)
    r = analisar(p)
    assert not r.ok, "imagem estourada foi aceita"
    print(f"  rejeitada: {r.motivo_rejeicao}")


def test_imagem_sem_olho_e_rejeitada():
    rng = np.random.default_rng(0)
    p = TMP / "ruido.png"
    cv2.imwrite(str(p), rng.integers(0, 255, (300, 400, 3), dtype=np.uint8))
    r = analisar(p)
    assert not r.ok, "ruído puro foi aceito como olho"
    print(f"  rejeitada: {r.motivo_rejeicao}")


def test_delta_contra_baseline():
    r, _ = _analisar_sintetico(vasos=150, mancha_seca=0.7, vermelhidao=0.8, semente=6)
    assert r.ok
    base = {"indice_superficie_ocular": 10.0}
    img = gerar_olho(vasos=150, mancha_seca=0.7, vermelhidao=0.8, semente=6)
    p = TMP / "com_baseline.png"
    cv2.imwrite(str(p), img)
    r2 = analisar(p, base)
    assert r2.delta_vs_baseline is not None
    assert abs(r2.delta_vs_baseline - (r2.indice_superficie_ocular - 10.0)) < 0.05
    print(f"  delta vs baseline = {r2.delta_vs_baseline:+.1f}")


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
