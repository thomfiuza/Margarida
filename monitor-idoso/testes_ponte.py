"""Testes da ponte Tuya/Matter (ponte_sensores.py) — mesmos casos do núcleo Kotlin."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from ponte_sensores import normalizar_evento, token_confere  # noqa: E402
from sensores import decidir_evento  # noqa: E402


def _decidir(raiz: dict, hora_padrao: int = 12):
    norm = normalizar_evento(raiz, hora_padrao)
    assert norm is not None, raiz
    tipo, payload, hora = norm
    return decidir_evento(tipo, payload, hora), payload


def test_tuya_queda_e_gas():
    nivel, acao, msg = _decidir({
        "protocolo": "tuya", "papel": "radar", "hora": 15, "token": "abc",
        "status": [
            {"code": "fall_state", "value": "fall"},
            {"code": "motionless_time", "value": 30},
        ],
    })[0]
    assert nivel == "urgente" and acao == "sos" and "CONFIRMADA" in msg
    nivel2, acao2, _ = _decidir({
        "protocolo": "tuya", "papel": "radar", "hora": 15,
        "status": [
            {"code": "fall_state", "value": "maybe"},
            {"code": "motionless_time", "value": 8},
        ],
    })[0]
    assert nivel2 == "atencao" and acao2 == "notificar"
    nivel3, acao3, _ = _decidir({
        "protocolo": "tuya", "papel": "gas", "hora": 14,
        "status": [{"code": "gas_sensor_state", "value": "alarm"}],
    })[0]
    assert nivel3 == "urgente" and acao3 == "sos"
    print("  Tuya: queda 30 s -> SOS | maybe 8 s -> notifica | gás alarm -> SOS")


def test_matter_porta_remedio_leito_e_canonico():
    nivel, _, _ = _decidir({
        "protocolo": "matter", "papel": "porta", "hora": 2,
        "attributes": {"StateValue": True},
    })[0]
    assert nivel == "atencao"
    nivel2, _, _ = _decidir({
        "protocolo": "tuya", "papel": "remedio", "hora": 11,
        "status": [{"code": "doorcontact_state", "value": False}],
    })[0]
    assert nivel2 == "atencao"
    nivel3, _, _ = _decidir({
        "protocolo": "matter", "papel": "leito", "hora": 3,
        "attributes": {"BedExitMinutes": 14},
    })[0]
    assert nivel3 == "atencao"
    (nivel4, _, _), payload = _decidir({
        "tipo": "queda", "hora": 14, "imovel_s": 30, "token": "abc",
    })
    assert nivel4 == "urgente" and "token" not in payload
    assert normalizar_evento({"protocolo": "zigbee", "papel": "radar"}, 12) is None
    assert token_confere({"token": "abc"}, "abc")
    assert not token_confere({"token": "outro"}, "abc")
    assert not token_confere({}, "")
    print("  Matter porta/leito + Tuya remédio + canônico sem token no payload")


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
