"""Testes da camada de sensores (sensores.py)."""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from bcg_noturno import gerar_sinal_bcg  # noqa: E402
from sensores import (GAS, PORTA, QUEDA, REMEDIO, SAIDA_LEITO,  # noqa: E402
                      decidir_evento, detectar_saida_leito)


def test_queda_confirmada_dispara_sos():
    nivel, acao, msg = decidir_evento(QUEDA, {"imovel_s": 30}, hora=15)
    assert nivel == "urgente" and acao == "sos" and "CONFIRMADA" in msg
    nivel2, acao2, _ = decidir_evento(QUEDA, {"imovel_s": 8}, hora=15)
    assert nivel2 == "atencao" and acao2 == "notificar"
    print(f"  queda 30 s imóvel -> SOS | 8 s -> só notifica")


def test_gas_sempre_urgente():
    nivel, acao, _ = decidir_evento(GAS, {"detectado": True}, hora=14)
    assert nivel == "urgente" and acao == "sos"
    nivel2, _, _ = decidir_evento(GAS, {"detectado": False}, hora=14)
    assert nivel2 == "nenhum"
    print("  gás detectado -> SOS | normalizado -> registro")


def test_porta_janela_de_sono():
    nivel, _, _ = decidir_evento(PORTA, {"aberta": True}, hora=2)
    assert nivel == "atencao", "porta aberta 02h deveria ser atenção"
    nivel2, _, _ = decidir_evento(PORTA, {"aberta": True}, hora=10)
    assert nivel2 == "nenhum"
    nivel3, _, _ = decidir_evento(PORTA, {"aberta": True}, hora=23)
    assert nivel3 == "atencao"
    print("  porta 02h/23h -> deambulação | 10h -> normal")


def test_remedio_e_saida_leito():
    nivel, _, _ = decidir_evento(REMEDIO, {"aberto_hoje": False}, hora=11)
    assert nivel == "atencao"
    nivel2, _, _ = decidir_evento(SAIDA_LEITO, {"minutos": 14}, hora=3)
    assert nivel2 == "atencao"      # 14 min fora do leito às 3h = risco
    nivel3, _, _ = decidir_evento(SAIDA_LEITO, {"minutos": 3}, hora=3)
    assert nivel3 == "nenhum"       # ida rápida ao banheiro = normal
    print("  remédio fechado -> atenção | 14 min fora do leito -> atenção | 3 min -> normal")


def test_detecta_silencio_no_pad():
    # 5 min de BCG, mas o 2º-3º minuto sem pessoa (silêncio)
    fs = 100.0
    com1 = gerar_sinal_bcg(fc_bpm=64, fr_irpm=14, segundos=120, semente=1)
    sem = np.random.default_rng(2).normal(0, 0.001, int(90 * fs))   # cama vazia
    com2 = gerar_sinal_bcg(fc_bpm=64, fr_irpm=14, segundos=90, semente=3)
    sinal = np.concatenate([com1, sem, com2])
    r = detectar_saida_leito(sinal, fs=fs, janela_s=20)
    assert r["maior_silencio_s"] >= 60, r
    ini, fim = r["silencios"][0]
    assert 100 <= ini <= 140 and 180 <= fim <= 240, r["silencios"]
    print(f"  cama vazia detectada: silêncio de {ini} s a {fim} s (esperado ~120-210)")


def test_sem_falso_silencio():
    sinal = gerar_sinal_bcg(fc_bpm=70, fr_irpm=15, segundos=300, semente=5)
    r = detectar_saida_leito(sinal)
    assert r["silencios"] == [], r
    print("  pessoa na cama a noite toda -> zero silêncios")


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
