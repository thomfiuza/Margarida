"""
Fluxo guiado por voz para o idoso — integra A (hidratação por urina) + B (teste
ortostático por rPPG) num protocolo único, com histórico, tendências contra a
linha de base PESSOAL e relatório para o cuidador.

Qualidades replicadas de outros apps (e o que eles não têm — ver README.md):
  - OrthoStat: protocolo ortostático guiado passo a passo + registro +
    interpretação -> aqui a MEDIÇÃO é pela câmera, não digitada.
  - Welltory/fones: FC por PPG -> aqui sem contato (rPPG) ou dedo no flash.
  - Apps de hidratação: escala + lembretes -> aqui a leitura é pela câmera com
    cartão branco, e entra na mesma tendência.
  - Relógios de queda: alerta a contatos -> aqui alerta de tendência ao
    cuidador (não diagnóstico).

Áudio das etapas: pasta audio/ (gerado por TTS pt-BR; nomes em PROMPT_AUDIO).
"""
from __future__ import annotations

import json
from datetime import date
from pathlib import Path

import numpy as np

import fc_camera
import urina

PROMPT_AUDIO = {
    "boas_vindas": "audio/01_boas_vindas.mp3",
    "sente": "audio/02_sente.mp3",
    "grava_repouso": "audio/03_grava_repouso.mp3",
    "levante": "audio/04_levante.mp3",
    "grava_pe": "audio/05_grava_pe.mp3",
    "urina": "audio/06_urina.mp3",
    "agua": "audio/07_agua.mp3",
    "feito": "audio/08_feito.mp3",
}


def _avaliar_ortostatica(fc_repouso, fc_pe, tontura: bool) -> list[str]:
    avisos = []
    delta = fc_pe - fc_repouso
    if tontura and delta < 10:
        avisos.append(
            "Tontura ao levantar SEM a subida esperada da frequência cardíaca: "
            "padrão compatível com hipotensão ortostática — levar ao médico."
        )
    if delta > 30:
        avisos.append(
            "Subida exagerada da frequência ao ficar em pé (>30 bpm): possível "
            "depleção de volume/desidratação — reforçar líquidos e reavaliar."
        )
    if fc_repouso > 100:
        avisos.append("FC de repouso acima de 100 bpm: registrar e comentar com o médico.")
    return avisos


def _nivel_alerta(avisos: list[str], nivel_urina: int | None) -> str:
    urgente = any("hipotensão ortostática" in a or "médico" in a for a in avisos) or (
        nivel_urina is not None and nivel_urina >= 7)
    atencao = bool(avisos) or (nivel_urina is not None and nivel_urina >= 5)
    return "urgente" if urgente else ("atencao" if atencao else "nenhum")


def rodar_fluxo(video_repouso: str | Path, video_pe: str | Path,
                foto_urina: str | Path | None = None, tontura: bool = False,
                historico: str | Path = "historico.jsonl") -> dict:
    historico = Path(historico)
    etapas = ["boas_vindas", "sente", "grava_repouso", "levante", "grava_pe"]

    r_rep = fc_camera.analisar_video(video_repouso)
    r_pe = fc_camera.analisar_video(video_pe)
    if not (r_rep["ok"] and r_pe["ok"]):
        qual = "repouso" if not r_rep["ok"] else "em pé"
        return {
            "status": "repetir_medicao",
            "etapa_com_falha": qual,
            "motivo": (r_rep if not r_rep["ok"] else r_pe)["motivo"],
            "tocar": PROMPT_AUDIO["sente"],
            "avisos": [f"Medição {qual} sem qualidade (luz/movimento). Repetir em ambiente iluminado, sentado e parado."],
        }

    fc_r, fc_p = r_rep["fc_bpm"], r_pe["fc_bpm"]
    avisos = _avaliar_ortostatica(fc_r, fc_p, tontura)

    ur = None
    if foto_urina is not None:
        import cv2
        img = cv2.imread(str(foto_urina))
        if img is not None:
            cu = urina.classificar(img)
            if cu["ok"]:
                ur = cu["nivel_armstrong"]
                etapas.append("urina")
                if ur >= 7:
                    avisos.append(f"Urina nível {ur} (escala 1-8): cor muito escura — reidratar e procurar atendimento.")
                elif ur >= 5:
                    avisos.append(f"Urina nível {ur}: desidratação moderada — reforçar líquidos hoje.")
            else:
                avisos.append(f"Foto de urina não utilizável: {cu['motivo']}")

    # ---- histórico e tendência contra a linha de base pessoal ----
    entradas = []
    if historico.exists():
        entradas = [json.loads(l) for l in historico.read_text().splitlines() if l.strip()]
    tendencia = []
    if entradas:
        base_fc = [e["fc_repouso"] for e in entradas[-7:] if e.get("fc_repouso")]
        if base_fc and fc_r - np.mean(base_fc) >= 10:
            tendencia.append(f"FC de repouso {fc_r - np.mean(base_fc):.0f} bpm acima da sua média recente.")
        if ur is not None:
            prev = [e["urina"] for e in entradas[-3:] if e.get("urina")]
            if prev and ur - np.mean(prev) >= 2:
                tendencia.append("Urina bem mais escura que nos últimos registros.")
    with historico.open("a") as f:
        f.write(json.dumps({"data": str(date.today()), "fc_repouso": fc_r, "fc_pe": fc_p,
                            "delta_fc": round(fc_p - fc_r, 1), "urina": ur,
                            "tontura": bool(tontura)}, ensure_ascii=False) + "\n")

    nivel = _nivel_alerta(avisos + tendencia, ur)
    etapas.append("agua" if (ur or 0) >= 4 else "feito")

    return {
        "status": "ok",
        "fc_repouso_bpm": fc_r,
        "fc_em_pe_bpm": fc_p,
        "delta_fc_bpm": round(fc_p - fc_r, 1),
        "snr_repouso": r_rep["snr"],
        "snr_pe": r_pe["snr"],
        "urina_nivel": ur,
        "tontura_relatada": bool(tontura),
        "avisos": avisos,
        "tendencias": tendencia,
        "nivel_alerta": nivel,
        "etapas_tocadas": [PROMPT_AUDIO[e] for e in etapas],
        "relatorio_cuidador": _relatorio(fc_r, fc_p, ur, avisos, tendencia, nivel),
    }


def _relatorio(fc_r, fc_p, ur, avisos, tendencia, nivel) -> str:
    linhas = [f"FC repouso {fc_r:.0f} bpm | em pé {fc_p:.0f} bpm (Δ {fc_p - fc_r:+.0f})"]
    if ur is not None:
        linhas.append(f"Hidratação (urina): nível {ur}/8")
    linhas += [f"• {a}" for a in avisos + tendencia]
    linhas.append(f"Nível de alerta para o cuidador: {nivel.upper()}")
    return "\n".join(linhas)


if __name__ == "__main__":
    import argparse

    p = argparse.ArgumentParser(description="Fluxo guiado idoso: ortostática + hidratação")
    p.add_argument("--repouso", required=True)
    p.add_argument("--pe", required=True)
    p.add_argument("--urina")
    p.add_argument("--tontura", action="store_true")
    p.add_argument("--historico", default="historico.jsonl")
    a = p.parse_args()
    print(json.dumps(rodar_fluxo(a.repouso, a.pe, a.urina, a.tontura, a.historico),
                     indent=2, ensure_ascii=False))
