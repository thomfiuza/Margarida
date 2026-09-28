"""
Pipeline de análise de imagem de olho (esclera/conjuntiva) com OpenCV puro.

O que este código faz de verdade:
  1. Portão de qualidade  -> rejeita foto ruim (desfoque, estouro, pouco olho na cena)
  2. Segmentação da esclera
  3. Métricas de vermelhidão (Relative Redness, R-G, Hue do vermelho, a* do CIELAB)
  4. Densidade vascular (vasos visíveis na esclera)
  5. Sinais de filme lacrimal (manchas opacas/foscas + menisco na margem inferior)
  6. Índice de superfície ocular + score relativo à linha de base pessoal

O que este código NAO faz: medir hidratação corporal. Ver README.md.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field, asdict
from pathlib import Path

import cv2
import numpy as np


# --------------------------------------------------------------------------- #
# Estruturas
# --------------------------------------------------------------------------- #
@dataclass
class Resultado:
    arquivo: str
    ok: bool
    motivo_rejeicao: str | None = None
    qualidade: dict = field(default_factory=dict)
    segmentacao: dict = field(default_factory=dict)
    vermelhidao: dict = field(default_factory=dict)
    vasculatura: dict = field(default_factory=dict)
    filme_lacrimal: dict = field(default_factory=dict)
    indice_superficie_ocular: float | None = None
    delta_vs_baseline: float | None = None
    avisos: list = field(default_factory=list)

    def to_json(self, **kw) -> str:
        return json.dumps(asdict(self), indent=2, ensure_ascii=False, **kw)


# --------------------------------------------------------------------------- #
# 1. Qualidade
# --------------------------------------------------------------------------- #
def _nitidez_normalizada(cinza: np.ndarray) -> float:
    """var(Laplaciano) / var(imagem).

    Invariante a exposição (uma foto apenas mais clara/mais escura não muda a
    razão) e sensível a desfoque. Duas alternativas testadas e descartadas:
      - var(Laplaciano) pura: sobe com ruído e cai com subexposição;
      - gradiente médio / desvio-padrão: o desfoque reduz numerador E
        denominador, então a razão quase não se move (1.198 vs 1.183 numa
        imagem borrada com sigma=6 — o bug que este cálculo substitui)."""
    g = cinza.astype(np.float64)
    var_img = float(g.var())
    if var_img < 1e-6:
        return 0.0
    return float(cv2.Laplacian(cinza, cv2.CV_64F).var() / var_img)


def avaliar_qualidade(img: np.ndarray) -> tuple[dict, bool, str | None]:
    cinza = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    nitidez = _nitidez_normalizada(cinza)

    pixels = cinza.size
    frac_estourada = float((cinza >= 250).sum() / pixels)
    frac_escura = float((cinza <= 10).sum() / pixels)

    m = {
        "nitidez_normalizada": round(nitidez, 4),
        "frac_pixels_estourados": round(frac_estourada, 4),
        "frac_pixels_subexpostos": round(frac_escura, 4),
        "resolucao": [int(img.shape[1]), int(img.shape[0])],
    }

    if min(img.shape[:2]) < 160:
        return m, False, "resolução muito baixa (<160 px no lado menor)"
    if nitidez < 0.15:
        return m, False, f"imagem desfocada (nitidez {nitidez:.3f} < 0.15)"
    if frac_estourada > 0.12:
        return m, False, f"estouro de luz em {frac_estourada:.0%} dos pixels"
    # Não rejeitamos por "cena escura": num close-up de olho é normal ter cabelo,
    # sobrancelha e fundo escuros ocupando metade do quadro. A segmentação da
    # esclera já é o portão que decide se há olho utilizável.
    return m, True, None


# --------------------------------------------------------------------------- #
# 2. Segmentação da esclera
# --------------------------------------------------------------------------- #
def _localizar_iris(img: np.ndarray) -> tuple[int, int, int] | None:
    """Acha a íris: o único componente ESCURO e aproximadamente CIRCULAR da cena.

    Ancora-se na íris (e não no "maior blob claro") porque a íris divide a
    esclera em dois lobos — nasal e temporal. Sem essa âncora, a elipse do olho
    é ajustada a um lobo só e cobre metade do olho (bug real que esta função
    substitui: bbox y=42..176 num olho que ocupava y=64..336)."""
    v = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)[:, :, 2]
    escuro = (v < 80).astype(np.uint8) * 255
    escuro = cv2.morphologyEx(escuro, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    escuro = cv2.morphologyEx(escuro, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))

    n, _, stats, cent = cv2.connectedComponentsWithStats(escuro, 8)
    lado_min = min(img.shape[:2])
    r_min, r_max = lado_min * 0.03, lado_min * 0.22

    melhor, melhor_ponto = None, 0.0
    for i in range(1, n):
        area = int(stats[i, cv2.CC_STAT_AREA])
        bw, bh = int(stats[i, cv2.CC_STAT_WIDTH]), int(stats[i, cv2.CC_STAT_HEIGHT])
        if bw < 6 or bh < 6:
            continue
        r_equiv = float(np.sqrt(area / np.pi))
        if not (r_min <= r_equiv <= r_max):
            continue
        # compactez: 1.0 = disco perfeito; filamentos e manchas irregulares caem
        compactez = area / (np.pi * (max(bw, bh) / 2.0) ** 2 + 1e-6)
        aspecto = min(bw, bh) / max(bw, bh)
        ponto = compactez * aspecto * np.sqrt(area)
        if ponto > melhor_ponto:
            melhor_ponto = ponto
            melhor = (int(cent[i][0]), int(cent[i][1]), int(round(r_equiv)))
    return melhor


def segmentar_esclera(img: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray, str | None]:
    """Retorna (mascara_esclera, mascara_olho, regiao_medicao, motivo_falha).

    Estratégia: âncora na íris -> elipse do olho -> dentro dela, esclera é o que
    é claro e pouco saturado RELATIVO AO QUADRO, menos a íris e o reflexo.
    As restrições relativas importam porque pele bem iluminada também passa em
    qualquer limiar absoluto de V/S (numa pele clara de estúdio: H=15, S=68,
    V=205 — mais clara que a própria esclera)."""
    h, w = img.shape[:2]
    vazio = np.zeros((h, w), np.uint8)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    s, v = hsv[:, :, 1].astype(np.float32), hsv[:, :, 2].astype(np.float32)

    iris = _localizar_iris(img)
    if iris is None:
        return vazio, vazio, vazio, "íris não localizada (nenhum blob escuro e circular)"
    icx, icy, r_iris = iris

    # r_iris é o raio do disco ESCURO (íris inteira). A fenda palpebral humana tem
    # ~30 mm e a córnea ~11,8 mm de diâmetro -> semi-eixo horizontal ~3,8 x r_iris.
    rx_olho, ry_olho = int(r_iris * 3.8), int(r_iris * 1.55)
    olho = np.zeros((h, w), np.uint8)
    cv2.ellipse(olho, (icx, icy), (rx_olho, ry_olho), 0, 0, 360, 255, -1)

    # Reflexo especular = cauda SUPERIOR do histograma de brilho do olho, em blob
    # pequeno e compacto. Limiares absolutos falham dos dois lados: V>235 marca
    # pele iluminada e sombra esmaecida (chegou a cobrir 30% do quadro), e
    # V>232 & S<40 marca a própria esclera (V~240, S~20) e apagava 6.187 dos
    # 5.642 px de esclera. O percentil se adapta ao brilho real da cena.
    sel_olho = olho > 0
    if sel_olho.sum() < 100:
        return vazio, vazio, vazio, "região do olho pequena demais"
    corte_brilho = max(240.0, float(np.percentile(v[sel_olho], 99.3)))
    cand = ((v > corte_brilho) & (s < 30) & sel_olho).astype(np.uint8) * 255
    n_c, lab_c, st_c, _ = cv2.connectedComponentsWithStats(cand, 8)
    reflexo = np.zeros((h, w), np.uint8)
    area_max = 0.18 * (olho > 0).sum()
    for i in range(1, n_c):
        area, bw, bh = int(st_c[i, 4]), int(st_c[i, 2]), int(st_c[i, 3])
        if area > area_max or area / (bw * bh + 1e-6) < 0.4:
            continue
        reflexo[lab_c == i] = 255
    reflexo = cv2.dilate(reflexo, np.ones((7, 7), np.uint8))
    olho = cv2.bitwise_and(olho, cv2.bitwise_not(reflexo))

    mascara_iris = np.zeros((h, w), np.uint8)
    cv2.circle(mascara_iris, (icx, icy), int(r_iris * 1.15), 255, -1)
    mascara_iris = cv2.dilate(mascara_iris, np.ones((5, 5), np.uint8))

    # limiar absoluto: um limite RELATIVO (s < s_med*0.75) é frágil — num
    # close-up em que a pele ocupa quase todo o quadro, s_med fica alto e o
    # corte come a esclera.
    clara = ((v > 190) & (s < 90)).astype(np.uint8) * 255
    clara = cv2.morphologyEx(clara, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    clara = cv2.morphologyEx(clara, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))

    # A elipse do olho é maior que a fenda palpebral, então sozinha ela inclui
    # pele acima e abaixo — e a pele domina as métricas de cor (num teste isso
    # deu a* = 17 e R-G = 46 numa esclera que era só levemente rosada).
    # Restringe ao casco convexo dos blobs claros VIZINHOS da íris.
    n_b, lab_b, st_b, cent_b = cv2.connectedComponentsWithStats(clara, 8)
    pts = []
    for i in range(1, n_b):
        if int(st_b[i, cv2.CC_STAT_AREA]) < 60:
            continue
        bx, by = float(cent_b[i][0]), float(cent_b[i][1])
        if ((bx - icx) / (rx_olho + 1e-6)) ** 2 + ((by - icy) / (ry_olho + 1e-6)) ** 2 > 1.6:
            continue
        pts.append(np.where(lab_b == i))
    if not pts:
        return vazio, vazio, vazio, "nenhum blob claro vizinho à íris"
    ys = np.concatenate([p[0] for p in pts])
    xs = np.concatenate([p[1] for p in pts])
    casco = cv2.convexHull(np.stack([xs, ys], axis=1).astype(np.int32))
    regiao = np.zeros((h, w), np.uint8)
    cv2.fillConvexPoly(regiao, casco, 255)
    regiao = cv2.dilate(regiao, np.ones((5, 5), np.uint8))

    esclera = cv2.bitwise_and(clara, cv2.bitwise_and(olho, regiao))
    esclera = cv2.bitwise_and(esclera, cv2.bitwise_not(mascara_iris))
    esclera = cv2.erode(esclera, np.ones((3, 3), np.uint8))

    n_px = int((esclera > 0).sum())
    if n_px < int(0.002 * h * w):
        return vazio, vazio, vazio, f"esclera muito pequena após recorte ({n_px} px)"
    # região de medição com denominador estável: olho menos íris
    regiao = cv2.bitwise_and(olho, cv2.bitwise_not(mascara_iris))
    return esclera, olho, regiao, None


def medir_vermelhidao(img: np.ndarray, mask: np.ndarray, regiao: np.ndarray | None = None) -> dict:
    b, g, r = cv2.split(img.astype(np.float32))
    eps = 1e-6
    # Medir sobre a REGIÃO fixa (olho menos íris), não sobre a máscara de
    # esclera: a máscara é definida por V>190 & S<90, e uma esclera que
    # avermelha de verdade perde saturação... ou ganha, dependendo do tom, e a
    # área medida muda junto com o que se quer medir (n_pixels caiu de 6.602
    # para 2.576 entre vermelhidao 0 e 1, o que achatou a curva).
    idx = (regiao > 0) if regiao is not None else (mask > 0)

    B, G, R = b[idx], g[idx], r[idx]
    rr = float(((R - G) / (R + G + B + eps)).mean())          # Relative Redness (Papas)
    rg = float((R - G).mean())                                # diferença R-G
    rb = float((R - B).mean())                                # diferença R-B

    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    hue = float(hsv[:, :, 0][idx].mean())                     # 0..179 em OpenCV

    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB).astype(np.float32)
    a_star = float(lab[:, :, 1][idx].mean() - 128.0)          # eixo verde-vermelho
    return {
        "relative_redness": round(rr, 4),
        "diff_R_G": round(rg, 2),
        "diff_R_B": round(rb, 2),
        "hue_medio_opencv": round(hue, 2),
        "a_star_cielab": round(a_star, 2),
        "n_pixels_esclera": int(idx.sum()),
    }


# --------------------------------------------------------------------------- #
# 4. Densidade vascular
# --------------------------------------------------------------------------- #
def medir_vasos(img: np.ndarray, mask: np.ndarray, regiao: np.ndarray | None = None) -> dict:
    """Realce tophat em escala de cinza + limiar ADAPTATIVO -> fração de pixels
    de vaso. Otsu global falha aqui: numa esclera quase homogênea ele corta no
    meio do ruído e devolve 0 quando há poucos vasos."""
    cinza = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    k = max(7, int(round(min(img.shape[:2]) * 0.03)) | 1)
    tophat = cv2.morphologyEx(cinza, cv2.MORPH_TOPHAT,
                              cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k)))
    tophat = cv2.GaussianBlur(tophat, (3, 3), 0)

    # O denominador é a REGIÃO do olho (menos a íris), não a máscara de esclera:
    # vasos deixam de ser "claros", então quanto mais vasos, menor a máscara de
    # esclera — e a razão ficava instável em vez de crescer (r=0.26 num teste
    # com 10->200 vasos antes desta correção).
    idx = (regiao > 0) if regiao is not None else (mask > 0)
    if idx.sum() < 200:
        return {"densidade_vascular_frac": 0.0, "n_pixels_vaso": 0, "kernel_tophat_px": int(k),
                "limiar_vaso": None}
    vals = tophat[idx]
    # Limiar = fundo local + 3 desvios, onde "fundo" são os 80% de pixels de
    # menor resposta tophat. Alternativas medidas e descartadas:
    #   fixo em 10.0  -> 0.6005 numa foto real (60% de "vasos": absurdo)
    #   percentil 92  -> 0.0708 na foto real, e por construção devolve sempre
    #                    os mesmos ~8% de pixels, saturando a métrica
    #   fundo + 3σ    -> 0.1180 na foto real (faixa plausível) e monotônico
    #                    de 0.146 a 0.174 na varredura sintética
    base = vals[vals < np.percentile(vals, 80)]
    limiar = float(base.mean() + 3.0 * base.std())

    bin_ = (tophat > limiar).astype(np.uint8) * 255
    bin_ = cv2.bitwise_and(bin_, (regiao if regiao is not None else mask))
    # vasos são alongados: remove blobs de 1-2 px (ruído)
    bin_ = cv2.morphologyEx(bin_, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))

    total = int(idx.sum())
    vasos = int((bin_ > 0).sum())
    return {
        "densidade_vascular_frac": round(vasos / total, 4),
        "n_pixels_vaso": vasos,
        "kernel_tophat_px": int(k),
        "limiar_vaso": round(float(limiar), 2),
    }


# --------------------------------------------------------------------------- #
# 5. Filme lacrimal
# --------------------------------------------------------------------------- #
def medir_filme_lacrimal(img: np.ndarray, mask_esclera: np.ndarray, mask_olho: np.ndarray,
                         regiao: np.ndarray | None = None) -> dict:
    """Manchas foscas/opacas dentro da esclera = filme rompido.
    Menisco = faixa clara e estreita colada à margem inferior do olho."""
    cinza = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.float32)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)

    idx = (regiao > 0) if regiao is not None else (mask_esclera > 0)
    if idx.sum() < 200:
        return {"erro": "região do olho pequena demais"}

    # referência LOCAL de brilho: sem isso os vasos (que cruzam a esclera)
    # puxam a mediana global para baixo e o detector fica cego para manchas.
    kbg = max(11, int(round(min(img.shape[:2]) * 0.035)) | 1)
    fundo = cv2.GaussianBlur(cinza, (kbg, kbg), 0)
    residuo = fundo - cinza                      # > 0 = mais escuro que o entorno
    r_idx = residuo[idx]
    desv = float(np.percentile(r_idx, 84) - np.median(r_idx)) + 1e-6
    limiar = max(1.5 * desv, 4.0)

    escuro = ((residuo > limiar) & idx).astype(np.uint8) * 255
    # manchas de filme rompido são BLOBAS; vasos são filamentos -> filtro de forma
    n, _, stats, _ = cv2.connectedComponentsWithStats(escuro, 8)
    manchas, area_man = [], 0
    for i in range(1, n):
        area = int(stats[i, cv2.CC_STAT_AREA])
        bw, bh = int(stats[i, cv2.CC_STAT_WIDTH]), int(stats[i, cv2.CC_STAT_HEIGHT])
        if area < 15:
            continue
        compact = area / (bw * bh + 1e-6)
        if compact >= 0.35 and max(bw, bh) / (min(bw, bh) + 1e-6) < 4.0:
            manchas.append(area)
            area_man += area
    frac_man = area_man / int(idx.sum())

    ys, xs = np.where(mask_olho > 0)
    if len(ys) == 0:
        return {"frac_manchas_opacas": round(frac_man, 4), "n_manchas": len(manchas)}
    y_max, y_min = int(ys.max()), int(ys.min())
    banda = mask_olho.copy()
    banda[: y_max - int(0.22 * (y_max - y_min)), :] = 0
    idx_b = banda > 0
    brilho_banda = float(hsv[:, :, 2][idx_b].mean()) if idx_b.any() else 0.0
    brilho_geral = float(hsv[:, :, 2][mask_olho > 0].mean())
    return {
        "frac_manchas_opacas": round(frac_man, 4),
        "n_manchas_opacas": len(manchas),
        "contraste_menisco": round(brilho_banda - brilho_geral, 2),
        "limiar_mancha": round(float(limiar), 2),
    }


# --------------------------------------------------------------------------- #
# 6. Score
# --------------------------------------------------------------------------- #
def indice_superficie(vasos: float, manchas: float, vermelhidao_rr: float) -> float:
    """Índice 0..100 de "superfície ocular irritada/seca". Heurística, não validada.
    Não é hidratação corporal."""
    escore = 0.0
    escore += np.clip(vasos / 0.25, 0, 1) * 45
    escore += np.clip(manchas / 0.15, 0, 1) * 35
    escore += np.clip((vermelhidao_rr - 0.02) / 0.10, 0, 1) * 20
    return round(float(escore), 1)


# --------------------------------------------------------------------------- #
# Pipeline
# --------------------------------------------------------------------------- #
def analisar(caminho: str | Path, baseline: dict | None = None) -> Resultado:
    caminho = Path(caminho)
    img = cv2.imread(str(caminho), cv2.IMREAD_COLOR)
    if img is None:
        return Resultado(arquivo=caminho.name, ok=False, motivo_rejeicao="arquivo não lido como imagem")

    q, ok, motivo = avaliar_qualidade(img)
    res = Resultado(arquivo=caminho.name, ok=False, qualidade=q)
    if not ok:
        res.motivo_rejeicao = motivo
        return res

    esclera, olho, regiao, falha_seg = segmentar_esclera(img)
    if falha_seg:
        res.motivo_rejeicao = f"segmentação de esclera falhou: {falha_seg}"
        return res
    frac_esclera = float((esclera > 0).sum() / (olho > 0).sum()) if (olho > 0).sum() else 0.0
    res.segmentacao = {
        "frac_area_olho_na_cena": round(float((olho > 0).mean()), 4),
        "frac_esclera_dentro_do_olho": round(frac_esclera, 4),
    }
    if frac_esclera < 0.06:
        res.motivo_rejeicao = (f"esclera segmentada em apenas {frac_esclera:.1%} da área do olho "
                               "(olho parcialmente fechado, sombra ou reflexo)")
        return res

    res.vermelhidao = medir_vermelhidao(img, esclera, regiao)
    res.vasculatura = medir_vasos(img, esclera, regiao)
    res.filme_lacrimal = medir_filme_lacrimal(img, esclera, olho, regiao)
    res.indice_superficie_ocular = indice_superficie(
        res.vasculatura["densidade_vascular_frac"],
        res.filme_lacrimal.get("frac_manchas_opacas", 0.0),
        res.vermelhidao["relative_redness"],
    )

    if baseline and "indice_superficie_ocular" in baseline:
        res.delta_vs_baseline = round(res.indice_superficie_ocular - baseline["indice_superficie_ocular"], 1)

    avisos = [
        "Índice é heurística não validada clinicamente; não é dispositivo médico.",
        "Mede superfície ocular, NÃO água corporal total.",
        "Comparável apenas dentro do mesmo aparelho/iluminação, ou contra linha de base pessoal.",
    ]
    if q["frac_pixels_estourados"] > 0.03:
        avisos.append("Estouro de luz parcial presente — métricas de cor podem estar enviesadas.")
    if res.vermelhidao["n_pixels_esclera"] < 4000:
        avisos.append("Poucos pixels de esclera — alta incerteza.")

    # Íris clara (azul/verde/âmbar): só a pupila é escura, então a âncora acha a
    # pupila e a exclusão subestima a íris — textura de íris clara entra na
    # máscara de esclera (visível no painel real-3 como arco verde sobre a íris)
    # e infla a densidade vascular. Detectamos e avisamos em vez de fingir.
    ir = _localizar_iris(img)
    if ir is not None and (esclera > 0).sum() > 0:
        icx, icy, r_p = ir
        ys, xs = np.where(esclera > 0)
        dentro = ((xs - icx) ** 2 + (ys - icy) ** 2) < (2.2 * r_p) ** 2
        if dentro.mean() > 0.25:
            avisos.append(
                "Possível íris clara incluída na máscara de esclera: a densidade "
                "vascular e a vermelhidão podem estar infladas nesta foto."
            )
    res.avisos = avisos
    res.ok = True
    return res


def desenhar_debug(caminho: str | Path, destino: str | Path) -> None:
    """Salva painel com máscaras e vasos para inspeção visual."""
    caminho, destino = Path(caminho), Path(destino)
    img = cv2.imread(str(caminho))
    esclera, olho, _, _ = segmentar_esclera(img)
    cinza = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    k = max(7, int(round(min(img.shape[:2]) * 0.03)) | 1)
    th = cv2.morphologyEx(cinza, cv2.MORPH_TOPHAT, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k)))
    _, vasos = cv2.threshold(th, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    vasos = cv2.bitwise_and(vasos, esclera)

    def colorir(m, cor):
        out = np.zeros_like(img)
        out[m > 0] = cor
        return out

    def sobrepor(m, cor):
        out = img.copy()
        out[m > 0] = (out[m > 0].astype(np.int16) // 2 + np.array(cor, np.int16) // 2).astype(np.uint8)
        return out

    painel = np.hstack([img, sobrepor(olho, (255, 0, 0)), sobrepor(esclera, (0, 255, 0)),
                        sobrepor(vasos, (0, 0, 255))])
    destino.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(destino), painel)


if __name__ == "__main__":
    import argparse

    p = argparse.ArgumentParser(description="Análise de superfície ocular a partir de foto")
    p.add_argument("imagem")
    p.add_argument("--json", help="salva relatório JSON neste caminho")
    p.add_argument("--debug", help="salva painel de máscaras neste caminho")
    p.add_argument("--baseline", help="JSON com índice de linha de base pessoal")
    a = p.parse_args()

    base = json.loads(Path(a.baseline).read_text()) if a.baseline else None
    r = analisar(a.imagem, base)
    print(r.to_json())
    if a.json:
        Path(a.json).parent.mkdir(parents=True, exist_ok=True)
        Path(a.json).write_text(r.to_json())
    if a.debug:
        desenhar_debug(a.imagem, a.debug)
