"""
Hidratação por cor de urina (escala de Armstrong 1-8) com correção de branco.

Por que este método é o caminho certo para idosos (e não a câmera no olho):
a cor da urina acompanha osmolalidade/densidade urinária e foi validada como
marcador de hidratação inclusive em residentes de casas de repouso; a câmera do
celular com cartão de calibração de cor torna a leitura confiável entre
aparelhos e iluminações (ver mapa-ideias.md pelas referências).

Protocolo de uso real: foto do coletor TRANSPARENTE encostado num cartão/papel
BRANCO, sob luz ambiente (sem flash direto). O cartão ancora o balanço de
branco; sem ele, a correção automática do celular falseia a cor.
"""
from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

# rampa CALIBRADA amostrando a carta oficial Urine Color Chart (Armstrong),
# imagem pública HPRC-online (ver rampa_calibrada.json com as coordenadas).
# Níveis 4 e 6 são próximos na carta impressa — a própria escala declara
# confiabilidade visual boa só para 1-5; o classificador portanto tolera ±1.
# CALIBRAR CONTRA AMOSTRAS REAIS ANTES DE QUALQUER USO SÉRIO.
RAMPA_RGB = {
    1: (242, 242, 215),
    2: (244, 243, 198),
    3: (235, 235, 175),
    4: (233, 231, 85),
    5: (239, 236, 134),
    6: (231, 228, 92),
    7: (236, 216, 92),
    8: (179, 179, 117),
}

STATUS = {
    1: "bem hidratado",
    2: "bem hidratado",
    3: "adequado — vale um copo d'água",
    4: "desidratação leve — beber 1-2 copos e reavaliar",
    5: "desidratação moderada — reidratar agora",
    6: "desidratação significativa — reidratar e avisar cuidador",
    7: "grave — procurar atendimento",
    8: "crítico — procurar atendimento (pode não ser só desidratação)",
}

AVISOS_FIXOS = [
    "Vitamina B (riboflavina) deixa a urina amarelo-vivo independente da hidratação.",
    "A primeira urina da manhã é sempre concentrada; avalie a 2ª/3ª do dia.",
    "Idosos podem estar desidratados SEM sentir sede — tendência importa mais que um ponto isolado.",
]


def _maior_blob(mascara: np.ndarray, area_min: int, longe_da_borda: bool = False) -> np.ndarray | None:
    n, lab, st, _ = cv2.connectedComponentsWithStats(mascara, 8)
    h, w = mascara.shape
    candidatos = []
    for i in range(1, n):
        if st[i, cv2.CC_STAT_AREA] < area_min:
            continue
        x, y, bw, bh = st[i, 0], st[i, 1], st[i, 2], st[i, 3]
        if longe_da_borda and (x <= 1 or y <= 1 or x + bw >= w - 1 or y + bh >= h - 1):
            continue  # fundo da cena encosta na borda; a amostra, não
        candidatos.append(i)
    if not candidatos:
        return None
    i = max(candidatos, key=lambda j: st[j, cv2.CC_STAT_AREA])
    return (lab == i).astype(np.uint8) * 255


def classificar(img: np.ndarray, corrigir: bool = True) -> dict:
    h, w = img.shape[:2]
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    s, v = hsv[:, :, 1], hsv[:, :, 2]

    # 1) cartão branco de referência.
    # Sob luz quente o papel branco fica "creme" (S até ~80), então S<45 falha.
    # O branco é o objeto que reflete a luz mais fielmente: entre os blobs
    # claros, é o de MAIOR razão azul/vermelho (tudo mais no cenário absorve
    # azul — a urina, principalmente).
    claros = ((v > 115) & (s < 110)).astype(np.uint8) * 255
    n_b, lab_b, st_b, _ = cv2.connectedComponentsWithStats(claros, 8)
    ref, melhor_razao = None, 0.5
    for i in range(1, n_b):
        if st_b[i, cv2.CC_STAT_AREA] < int(0.01 * h * w):
            continue
        blob = lab_b == i
        b_med = float(img[:, :, 0][blob].mean())
        r_med = float(img[:, :, 2][blob].mean())
        razao = b_med / (r_med + 1e-6)
        if razao > melhor_razao:
            melhor_razao, ref = razao, blob.astype(np.uint8) * 255
    if ref is None:
        return {"ok": False,
                "motivo": "cartão branco de referência não encontrado na foto "
                          "(coloque o coletor encostado num papel branco)"}

    ganho = np.ones(3)
    if corrigir:
        # o cartão define crominância E luminância: papel branco é ~235. Só
        # neutralizar a crominância não basta — sob luz fraca a urina clara
        # fica escura e é confundida com os níveis marrons (nível 1 -> 7 num
        # teste com luz a 55%).
        medias = np.array([img[:, :, c][ref > 0].mean() for c in range(3)])
        ganho = np.clip(235.0 / (medias + 1e-6), 0.2, 5.0)
    corr = np.clip(img.astype(np.float64) * ganho, 0, 255).astype(np.uint8)

    # 2) região de urina — detectada na imagem CORRIGIDA, não na original.
    # Sob luz fria a urina clara fica esverdeada e sai da banda de hue amarelo;
    # depois do balanço de branco a cor volta ao amarelo/marrom da escala.
    hsv2 = cv2.cvtColor(corr, cv2.COLOR_BGR2HSV)
    hue2, s2, v2 = hsv2[:, :, 0], hsv2[:, :, 1], hsv2[:, :, 2]
    # fundo da cena ancorado pela borda (ele sempre toca a borda; a amostra, não)
    borda = np.concatenate([hsv2[0, :], hsv2[-1, :], hsv2[:, 0], hsv2[:, -1]])
    f_v, f_s = float(np.median(borda[:, 2])), float(np.median(borda[:, 1]))
    amarelo = ((hue2 >= 10) & (hue2 <= 45)) & (s2 > 22) & (v2 > 40)
    destaque = (v2 > f_v + 25) | (s2 > f_s + 25)
    urina_m = (amarelo & destaque).astype(np.uint8) * 255
    urina_m = cv2.bitwise_and(urina_m, cv2.bitwise_not(ref))
    urina = _maior_blob(urina_m, int(0.01 * h * w), longe_da_borda=True)
    if urina is None:
        return {"ok": False, "motivo": "região de urina não encontrada"}

    rgb = np.array([corr[:, :, c][urina > 0].mean() for c in range(2, -1, -1)])  # RGB

    # 3) vizinho mais próximo em Lab
    lab_med = cv2.cvtColor(np.uint8([[rgb[::-1]]]), cv2.COLOR_BGR2LAB)[0, 0].astype(float)
    melhor, d_melhor = None, 1e9
    for nivel, cor in RAMPA_RGB.items():
        l = cv2.cvtColor(np.uint8([[cor[::-1]]]), cv2.COLOR_BGR2LAB)[0, 0].astype(float)
        d = float(np.linalg.norm(lab_med - l))
        if d < d_melhor:
            d_melhor, melhor = d, nivel

    return {
        "ok": True,
        "nivel_armstrong": int(melhor),
        "status": STATUS[melhor],
        "rgb_corrigido": [round(float(x), 1) for x in rgb],
        "distancia_lab": round(d_melhor, 1),
        "ganhos_balanco_branco": [round(float(g), 2) for g in ganho],
        "avisos": AVISOS_FIXOS,
    }


if __name__ == "__main__":
    import argparse

    p = argparse.ArgumentParser(description="Classifica hidratação pela cor da urina")
    p.add_argument("foto")
    a = p.parse_args()
    img = cv2.imread(a.foto)
    print(json.dumps(classificar(img), indent=2, ensure_ascii=False) if img is not None
          else json.dumps({"ok": False, "motivo": "imagem não lida"}))
