"""Ponte Android (Chaquopy) → módulos validados no protótipo Python."""
from __future__ import annotations

import json

import fc_camera
import urina


def medir_fc_video(caminho: str) -> str:
    return json.dumps(fc_camera.analisar_video(caminho), ensure_ascii=False)


def estimar_noite(amostras_json: str, fs: float = 100.0) -> str:
    """DSP do pad (bcg_noturno.py) sobre o eixo Z já em g."""
    import numpy as np
    import bcg_noturno

    sinal = np.asarray(json.loads(amostras_json), dtype=float)
    return json.dumps(bcg_noturno.estimar_noturno(sinal, fs=float(fs)), ensure_ascii=False)


def classificar_urina_foto(caminho: str) -> str:
    import cv2

    img = cv2.imread(caminho)
    if img is None:
        return json.dumps({"ok": False, "motivo": "imagem não lida"}, ensure_ascii=False)
    return json.dumps(urina.classificar(img), ensure_ascii=False)
