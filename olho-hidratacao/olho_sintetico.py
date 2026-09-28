"""
Gerador de olho sintético com parâmetros CONHECIDOS.

Objetivo: validar a mecânica do pipeline (as métricas respondem ao estímulo?)
e o portão de qualidade (imagens ruins são rejeitadas?).

NAO serve para validar precisão clínica. O olho aqui é um desenho paramétrico;
nada disso tem relação com fisiologia humana real.
"""
from __future__ import annotations

import numpy as np
import cv2


def gerar_olho(
    largura: int = 640,
    altura: int = 400,
    vermelhidao: float = 0.35,
    vasos: int = 70,
    mancha_seca: float = 0.0,
    menisco_lagrimal: float = 0.5,
    brilho_corneal: float = 0.9,
    desfoque_px: float = 0.0,
    ruido: float = 4.0,
    tom_pele: tuple[int, int, int] = (126, 150, 214),  # BGR -> HSV ~ (15, 105, 214)
    fundo_olho: tuple[int, int, int] = (236, 232, 238),  # BGR (esclera "limpa")
    semente: int = 7,
) -> np.ndarray:
    """Desenha um olho frontal. Retorna imagem BGR uint8.

    vermelhidao: 0..1 -> quanto de sangue tinge a esclera
    vasos:       número de vasos radiais visíveis
    mancha_seca: 0..1 -> área de filme lacrimal rompido (regiões opacas/foscas)
    menisco_lagrimal: 0..1 -> espessura da faixa lacrimal na pálpebra inferior
    brilho_corneal: 0..1 -> intensidade do reflexo especular
    """
    rng = np.random.default_rng(semente)
    img = np.zeros((altura, largura, 3), np.uint8)
    img[:] = tom_pele

    cx, cy = largura // 2, altura // 2
    rx, ry = int(largura * 0.40), int(altura * 0.34)

    # ---- esclera (formato amendoado) ----
    mascara = np.zeros((altura, largura), np.uint8)
    cv2.ellipse(mascara, (cx, cy), (rx, ry), 0, 0, 360, 255, -1)
    # Fissura = elipse cheia. (A versão anterior "afinava os cantos" com duas
    # elipses de semi-eixo 0.55rx x 0.60ry centradas em cx+-rx, o que apagava
    # ~92% da esclera e deixava só dois resquícios perto das comissuras.)

    esclera = np.array(fundo_olho, np.float32)
    # tinge de vermelho proporcional à "vermelhidao"
    tingida = esclera.copy()
    tingida[2] = min(255.0, esclera[2] + 14.0)          # +R
    tingida[1] = max(0.0, esclera[1] - 26.0)            # -G
    tingida[0] = max(0.0, esclera[0] - 30.0)            # -B
    img[mascara > 0] = (esclera * (1 - vermelhidao) + tingida * vermelhidao).astype(np.uint8)

    # textura de baixa frequência na esclera: tecido real não é um flat de cor,
    # e sem isso os vasos ficam invisíveis para qualquer realce de borda
    yy, xx = np.mgrid[0:altura, 0:largura].astype(np.float32)
    textura = (np.sin(xx / 37.0) * 3.0 + np.cos(yy / 29.0) * 2.5
               + rng.normal(0, 2.0, (altura, largura)))
    for c in range(3):
        img[:, :, c][mascara > 0] = np.clip(
            img[:, :, c][mascara > 0].astype(np.float32) + textura[mascara > 0], 0, 255
        ).astype(np.uint8)

    # ---- vasos radiais ----
    rp = int(rx * 0.44)  # raio da íris
    # Determinístico por construção: os primeiros N vasos são SEMPRE os mesmos.
    # Se cada vaso consumir números aleatórios, mudar `vasos` muda também o
    # ruído de toda a imagem e as medições deixam de ser comparáveis (foi o que
    # fazia a densidade vascular oscilar em vez de crescer).
    raio_vaso = [1, 2, 3, 2, 1, 2, 3, 3, 1, 2]
    cores_vaso = [(120, 130, 200), (105, 118, 215), (128, 138, 205), (112, 124, 210)]
    for k in range(vasos):
        ang = k * 2.39996323 + 0.37          # ângulo áureo: distribuição uniforme
        r0 = rp + 3.0 + (k % 5) * 1.7
        r1 = r0 + rx * (0.18 + 0.05 * (k % 7))
        p0 = (int(cx + r0 * np.cos(ang)), int(cy + r0 * np.sin(ang)))
        meio = (int(cx + ((r0 + r1) / 2) * np.cos(ang + 0.10 * ((k % 3) - 1))),
                int(cy + ((r0 + r1) / 2) * np.sin(ang + 0.10 * ((k % 3) - 1))))
        p1 = (int(cx + r1 * np.cos(ang + 0.16 * ((k % 5) - 2))),
              int(cy + r1 * np.sin(ang + 0.16 * ((k % 5) - 2))))
        cor = cores_vaso[k % len(cores_vaso)]
        esp = raio_vaso[k % len(raio_vaso)]
        cv2.polylines(img, [np.array([p0, meio, p1])], False, cor, esp, cv2.LINE_AA)

    # ---- íris ----
    cv2.circle(img, (cx, cy), rp, (95, 120, 70), -1, cv2.LINE_AA)
    for _ in range(260):  # estrias da íris
        a = rng.uniform(0, 2 * np.pi)
        r_a, r_b = rng.uniform(rp * 0.28, rp * 0.95), rng.uniform(rp * 0.3, rp)
        cv2.line(
            img,
            (int(cx + r_a * np.cos(a)), int(cy + r_a * np.sin(a))),
            (int(cx + r_b * np.cos(a + 0.05)), int(cy + r_b * np.sin(a + 0.05))),
            (int(rng.integers(60, 150)), int(rng.integers(90, 170)), int(rng.integers(40, 100))),
            1, cv2.LINE_AA,
        )
    cv2.circle(img, (cx, cy), rp, (40, 55, 30), 2, cv2.LINE_AA)  # colarete/limbo
    # ---- pupila ----
    cv2.circle(img, (cx, cy), int(rp * 0.38), (8, 8, 10), -1, cv2.LINE_AA)

    # ---- reflexo especular da córnea ----
    if brilho_corneal > 0:
        hx, hy = cx - int(rp * 0.35), cy - int(rp * 0.45)
        for r, alfa in ((int(rp * 0.30), 0.08), (int(rp * 0.18), 0.18), (int(rp * 0.09), 0.32)):
            overlay = img.astype(np.float32)
            cv2.circle(overlay, (hx, hy), max(2, r), (255, 255, 255), -1, cv2.LINE_AA)
            img = cv2.addWeighted(overlay.astype(np.uint8), alfa * brilho_corneal,
                                  img, 1 - alfa * brilho_corneal, 0)
        # núcleo quase branco
        cv2.circle(img, (hx, hy), max(1, int(rp * 0.05)), (248, 248, 250), -1)

    # ---- filme lacrimal rompido (manchas secas na esclera) ----
    # Idem: posições determinísticas, `mancha_seca` só decide quantas aparecem.
    if mancha_seca > 0:
        n = int(mancha_seca * 26)
        colocadas = 0
        for k in range(400):
            if colocadas >= n:
                break
            t_ = 0.45 + 0.50 * ((k * 7) % 20) / 19.0
            fx, fy = rx * t_, ry * t_
            a = k * 2.39996323 + 1.1
            px, py = int(cx + fx * np.cos(a)), int(cy + fy * np.sin(a))
            if not (0 <= px < largura and 0 <= py < altura):
                continue
            if (px - cx) ** 2 + (py - cy) ** 2 < (rp * 1.3) ** 2:
                continue
            if mascara[py, px] == 0:
                continue
            colocadas += 1
            raio = 4 + (k % 10)
            # mancha seca: claramente mais escura que a esclera ao redor
            cv2.circle(img, (px, py), raio, (166, 176, 196), -1, cv2.LINE_AA)
            cv2.circle(img, (px, py), raio + 2, (182, 190, 206), 2, cv2.LINE_AA)

    # ---- menisco lacrimal (faixa brilhante na margem inferior) ----
    if menisco_lagrimal > 0:
        esp = max(1, int(menisco_lagrimal * 9))
        alfa = 0.25 + 0.5 * menisco_lagrimal
        overlay = img.astype(np.float32)
        cv2.ellipse(overlay, (cx, cy + ry - 2), (int(rx * 0.92), ry), 0, 8, 172,
                    (250, 252, 255), esp, cv2.LINE_AA)
        img = cv2.addWeighted(overlay.astype(np.uint8), alfa, img, 1 - alfa, 0)

    # ---- margem palpebral ----
    # O fundo já é pele; a fissura é o que a máscara define. Aqui só se desenha a
    # linha da margem, CLIPADA para fora do olho. (A versão anterior desenhava
    # duas elipses de pele CHEIAS por cima, o que tampava a fissura inteira —
    # nenhuma imagem gerada tinha olho visível.)
    fora = cv2.bitwise_not(mascara)
    sombra = (int(tom_pele[0] * 0.72), int(tom_pele[1] * 0.72), int(tom_pele[2] * 0.72))

    def pintar_fora(camada: np.ndarray) -> None:
        """Copia só os pixels NÃO-preto da camada, e só para fora da fissura.
        (Atribuir img[fora>0] = camada[fora>0] pintaria o rosto inteiro de
        preto, porque a camada é inicializada zerada.)"""
        m = cv2.bitwise_and((camada.max(axis=2) > 0).astype(np.uint8) * 255, fora)
        cv2.copyTo(camada, img, m)

    for esp, cor in ((5, sombra), (2, (60, 70, 95))):
        linha = np.zeros_like(img)
        cv2.ellipse(linha, (cx, cy), (rx, ry), 0, 0, 360, cor, esp, cv2.LINE_AA)
        pintar_fora(linha)

    sombra_sup = np.zeros_like(img)
    cv2.ellipse(sombra_sup, (cx, cy - int(ry * 0.16)), (int(rx * 1.02), int(ry * 1.02)), 0, 190, 350,
                sombra, int(ry * 0.22), cv2.LINE_AA)
    sombra_sup = cv2.GaussianBlur(sombra_sup, (0, 0), ry * 0.12)
    pintar_fora(sombra_sup)

    # óptica e sensor vêm DEPOIS de tudo, como numa câmera real
    if desfoque_px > 0:
        img = cv2.GaussianBlur(img, (0, 0), desfoque_px)
    if ruido > 0:
        rng_ruido = np.random.default_rng(semente + 10_000)
        img = np.clip(img.astype(np.float32) + rng_ruido.normal(0, ruido, img.shape), 0, 255).astype(np.uint8)
    return img
