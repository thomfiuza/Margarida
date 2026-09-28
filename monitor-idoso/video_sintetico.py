"""
Vídeo sintético de "rosto" com pulso conhecido, para validar a MECÂNICA do rPPG.

O brilho da região de pele é modulado por (1 + amplitude * sen(2π fc t/60)), com
deriva lenta de iluminação, ruído de sensor e, opcionalmente, movimento rígido.
Nada disso valida precisão clínica em pessoas reais — para isso existem datasets
públicos (UBFC-RPPG, MMSE-HR, PURE).
"""
from __future__ import annotations

import numpy as np


def gerar_video(
    fc_bpm: float = 72.0,
    segundos: float = 30.0,
    fps: float = 30.0,
    amplitude: float = 0.012,      # ~1%: ordem de grandeza do rPPG real
    ruido: float = 2.0,
    deriva: float = 0.06,          # variação lenta de iluminação
    movimento_px: float = 0.0,     # translação rígida (artefato de movimento)
    largura: int = 320,
    altura: int = 240,
    semente: int = 1,
) -> list[np.ndarray]:
    rng = np.random.default_rng(semente)
    n = int(segundos * fps)
    pele_base = np.array([120, 160, 215], np.float32)   # BGR pele

    yy, xx = np.mgrid[0:altura, 0:largura].astype(np.float32)
    # elipse de rosto + dois blobs de bochecha para a ROI de pele ter textura
    rosto = (((xx - largura / 2) / (largura * 0.30)) ** 2 +
             ((yy - altura / 2) / (altura * 0.42)) ** 2) <= 1.0

    textura = np.sin(xx / 23.0) * 2 + np.cos(yy / 17.0) * 2

    frames = []
    for k in range(n):
        t = k / fps
        pulso = 1.0 + amplitude * np.sin(2 * np.pi * fc_bpm * t / 60.0)
        luz = 1.0 + deriva * np.sin(2 * np.pi * 0.08 * t)
        dx = int(movimento_px * np.sin(2 * np.pi * 0.5 * t))
        dy = int(movimento_px * np.cos(2 * np.pi * 0.37 * t))

        img = np.zeros((altura, largura, 3), np.float32)
        img[:] = (70, 70, 75)                            # fundo
        for c in range(3):
            camada = pele_base[c] * pulso * luz + textura
            img[:, :, c][rosto] = camada[rosto]
        # olhos/boca escuros para parecer rosto (não usado na medição)
        img[altura // 2 - 20:altura // 2 - 12, largura // 2 - 40:largura // 2 - 16] = (40, 40, 45)
        img[altura // 2 - 20:altura // 2 - 12, largura // 2 + 16:largura // 2 + 40] = (40, 40, 45)
        img[altura // 2 + 30:altura // 2 + 40, largura // 2 - 25:largura // 2 + 25] = (90, 90, 110)

        img += rng.normal(0, ruido, img.shape)
        if dx or dy:
            M = np.float32([[1, 0, dx], [0, 1, dy]])
            img = cv2_warp(img, M, (largura, altura))
        frames.append(np.clip(img, 0, 255).astype(np.uint8))
    return frames


def cv2_warp(img, M, size):
    import cv2
    return cv2.warpAffine(img, M, size)
