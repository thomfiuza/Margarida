"""
Ponte Matter/Tuya → decidir_evento (espelho de PonteSensores.kt).

Três formatos:
  canônico  {"tipo","hora", ...payload}
  tuya      {"protocolo":"tuya","papel":"radar|gas|porta|remedio|leito","status":[...]}
  matter    {"protocolo":"matter","papel":"...","attributes":{...}}

"token" não entra no payload. O app confere o token antes de chamar isto.
"""
from __future__ import annotations


def token_confere(raiz: dict, esperado: str) -> bool:
    if not esperado:
        return False
    return str(raiz.get("token", "")) == esperado


def normalizar_evento(raiz: dict, hora_padrao: int) -> tuple[str, dict, int] | None:
    hora = int(raiz["hora"]) if "hora" in raiz and raiz["hora"] is not None else hora_padrao
    protocolo = raiz.get("protocolo")
    if not protocolo:
        tipo = raiz.get("tipo")
        if not isinstance(tipo, str):
            return None
        payload = {k: v for k, v in raiz.items() if k not in ("tipo", "hora", "token", "protocolo")}
        return tipo, payload, hora
    papel = str(raiz.get("papel", "")).lower()
    if protocolo == "tuya":
        return _tuya(papel, raiz.get("status") or [], hora)
    if protocolo == "matter":
        return _matter(papel, raiz.get("attributes") or {}, hora)
    return None


def _codigo(status: list, nome: str):
    for item in status:
        if str(item.get("code", "")).lower() == nome.lower():
            return item.get("value")
    return None


def _attr(attrs: dict, nome: str):
    for k, v in attrs.items():
        if str(k).lower() == nome.lower():
            return v
    return None


def _as_float(v):
    if isinstance(v, bool) or v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, str):
        try:
            return float(v)
        except ValueError:
            return None
    return None


def _as_bool(v):
    if isinstance(v, bool):
        return v
    if isinstance(v, (int, float)) and not isinstance(v, bool):
        if int(v) == 1:
            return True
        if int(v) == 0:
            return False
        return None
    if isinstance(v, str):
        s = v.lower()
        if s in ("true", "1", "alarm", "open", "opened"):
            return True
        if s in ("false", "0", "normal", "close", "closed"):
            return False
    return None


def _primeiro_float(status: list, nomes: tuple[str, ...]):
    for nome in nomes:
        v = _as_float(_codigo(status, nome))
        if v is not None:
            return v
    return None


def _tuya(papel: str, status: list, hora: int):
    if papel == "radar":
        fall = _codigo(status, "fall_state")
        fall_s = str(fall).lower() if fall is not None else None
        tempo = _primeiro_float(status, ("motionless_time", "stay_time", "imovel_s"))
        if fall_s is None and tempo is None:
            return None
        if fall_s in ("normal", "none", "0", "false"):
            return None
        confirmado = fall_s in ("fall", "1", "true")
        imovel = (tempo if tempo is not None else 15.0) if confirmado else (tempo or 0.0)
        return "queda", {"imovel_s": imovel}, hora
    if papel == "gas":
        det = _as_bool(_codigo(status, "gas_sensor_state"))
        if det is None:
            det = _as_bool(_codigo(status, "gas_sensor_value"))
        if det is None:
            return None
        return "gas", {"detectado": det}, hora
    if papel in ("porta", "remedio"):
        aberto = None
        for nome in ("doorcontact_state", "door_opened", "switch"):
            aberto = _as_bool(_codigo(status, nome))
            if aberto is not None:
                break
        if aberto is None:
            return None
        if papel == "porta":
            return "porta", {"aberta": aberto}, hora
        return "remedio", {"aberto_hoje": aberto}, hora
    if papel == "leito":
        minutos = _as_float(_codigo(status, "bed_exit_min"))
        if minutos is None:
            minutos = _as_float(_codigo(status, "minutos"))
        if minutos is None:
            return None
        return "saida_leito", {"minutos": minutos}, hora
    return None


def _matter(papel: str, attrs: dict, hora: int):
    if not isinstance(attrs, dict):
        return None
    if papel == "radar":
        fall = _as_bool(_attr(attrs, "FallDetected"))
        if fall is None:
            fall = _as_bool(_attr(attrs, "fall_state"))
        tempo = _as_float(_attr(attrs, "MotionlessSeconds"))
        if tempo is None:
            tempo = _as_float(_attr(attrs, "motionless_time"))
        if fall is None and tempo is None:
            return None
        if fall is False and (tempo is None or tempo <= 0):
            return None
        imovel = (tempo if tempo is not None else 15.0) if fall is True else (tempo or 0.0)
        return "queda", {"imovel_s": imovel}, hora
    if papel == "gas":
        det = _as_bool(_attr(attrs, "StateValue"))
        if det is None:
            det = _as_bool(_attr(attrs, "GasAlarm"))
        if det is None:
            return None
        return "gas", {"detectado": det}, hora
    if papel == "porta":
        aberto = _as_bool(_attr(attrs, "StateValue"))
        if aberto is None:
            return None
        return "porta", {"aberta": aberto}, hora
    if papel == "remedio":
        aberto = _as_bool(_attr(attrs, "StateValue"))
        if aberto is None:
            return None
        return "remedio", {"aberto_hoje": aberto}, hora
    if papel == "leito":
        minutos = _as_float(_attr(attrs, "BedExitMinutes"))
        if minutos is None:
            minutos = _as_float(_attr(attrs, "minutos"))
        if minutos is None:
            return None
        return "saida_leito", {"minutos": minutos}, hora
    return None
