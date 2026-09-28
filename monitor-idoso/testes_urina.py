"""
Testes do classificador de urina: cenas sintéticas com cartão branco, sob
iluminantes diferentes (quente/frio/escuro). A correção de branco deve tornar a
classificação invariante à luz — é exatamente isso que se testa aqui.
Valida a mecânica do pipeline; calibração clínica exige amostras reais.
"""
from __future__ import annotations

import sys
from pathlib import Path

import cv2
import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from urina import RAMPA_RGB, classificar  # noqa: E402

ILUMINANTES = {
    "neutro": (1.0, 1.0, 1.0),
    "quente": (1.0, 0.85, 0.70),   # RGB: lâmpada incandescente
    "frio": (0.75, 0.90, 1.10),    # sombra / LED frio
    "escuro": (0.55, 0.55, 0.55),  # banheiro pouco iluminado
}


def cena(nivel: int, iluminante=(1.0, 1.0, 1.0), cartao: bool = True, ruido=1.5, semente=1):
    rng = np.random.default_rng(semente)
    img = np.zeros((280, 280, 3), np.float64)
    img[:] = (95, 95, 100)                                  # fundo bancada
    if cartao:
        img[20:100, 20:100] = (235, 235, 235)              # cartão branco
    r, g, b = RAMPA_RGB[nivel]
    img[120:250, 90:230] = (b, g, r)                        # BGR
    img *= np.array(iluminante)[::-1]                       # iluminante em BGR
    img += rng.normal(0, ruido, img.shape)
    return np.clip(img, 0, 255).astype(np.uint8)


def test_classifica_sob_luzes_diferentes():
    erros = []
    for nivel in RAMPA_RGB:
        for nome, il in ILUMINANTES.items():
            r = classificar(cena(nivel, il))
            assert r["ok"], f"nível {nivel}/{nome}: {r.get('motivo')}"
            d = abs(r["nivel_armstrong"] - nivel)
            erros.append(d)
            # a carta impressa tem níveis vizinhos quase idênticos em Lab
            # (4 vs 6); a própria escala declara confiabilidade boa só p/ 1-5
            assert d <= 1, f"nível {nivel} sob luz {nome} -> {r['nivel_armstrong']}"
    print(f"  32 combinações (8 níveis x 4 luzes), erro médio {sum(erros)/len(erros):.2f} nível")


def test_sem_cartao_rejeita():
    r = classificar(cena(4, cartao=False))
    assert not r["ok"] and "cartão" in r["motivo"]
    print(f"  sem cartão -> ok=False ({r['motivo'][:46]}...)")


def test_correcao_de_branco_e_necessaria():
    sem = classificar(cena(2, ILUMINANTES["quente"]), corrigir=False)
    com = classificar(cena(2, ILUMINANTES["quente"]), corrigir=True)
    assert abs(com["nivel_armstrong"] - 2) <= 1
    assert abs(sem["nivel_armstrong"] - 2) > abs(com["nivel_armstrong"] - 2), (
        f"correção não ajudou: sem={sem['nivel_armstrong']} com={com['nivel_armstrong']}")
    print(f"  luz quente, nível 2: sem correção -> {sem['nivel_armstrong']}, com correção -> {com['nivel_armstrong']}")


def test_niveis_distantes_nao_confundem():
    for a, b in ((1, 5), (2, 6), (3, 8)):
        ra, rb = classificar(cena(a, ILUMINANTES["frio"])), classificar(cena(b, ILUMINANTES["quente"]))
        assert rb["nivel_armstrong"] > ra["nivel_armstrong"]
    print("  ordenação entre níveis distantes preservada mesmo cruzando luzes")


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
