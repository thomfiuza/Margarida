"""
Testes de integração do fluxo guiado: vídeos reais em disco (sintéticos, com FC
conhecida) + fotos de urina sintéticas, passando pelo MESMO caminho de I/O que o
app usaria (VideoWriter -> analisar_video -> classificar).
"""
from __future__ import annotations

import sys
import tempfile
from pathlib import Path

import cv2

sys.path.insert(0, str(Path(__file__).parent))
from fluxo_idoso import rodar_fluxo  # noqa: E402
from testes_urina import cena, ILUMINANTES  # noqa: E402
from video_sintetico import gerar_video  # noqa: E402

TMP = Path(tempfile.mkdtemp(prefix="fluxo_test_"))


def _escrever_video(fc: float, nome: str, semente: int) -> Path:
    p = TMP / nome
    frames = gerar_video(fc_bpm=fc, segundos=30, semente=semente)
    w = cv2.VideoWriter(str(p), cv2.VideoWriter_fourcc(*"mp4v"), 30.0,
                        (frames[0].shape[1], frames[0].shape[0]))
    for fr in frames:
        w.write(fr)
    w.release()
    return p


def _escrever_urina(nivel: int, nome: str) -> Path:
    p = TMP / nome
    cv2.imwrite(str(p), cena(nivel, ILUMINANTES["quente"]))
    return p


def test_fluxo_normal_sem_alerta():
    r = rodar_fluxo(_escrever_video(72, "rep1.avi", 21), _escrever_video(95, "pe1.avi", 22),
                    _escrever_urina(2, "u1.png"), False, TMP / "h1.jsonl")
    assert r["status"] == "ok", r
    assert abs(r["fc_repouso_bpm"] - 72) <= 3 and abs(r["fc_em_pe_bpm"] - 95) <= 3
    assert r["urina_nivel"] in (1, 2, 3)
    assert r["nivel_alerta"] == "nenhum", r["avisos"]
    print(f"  normal: repouso {r['fc_repouso_bpm']}, pé {r['fc_em_pe_bpm']}, urina {r['urina_nivel']} -> nenhum alerta")


def test_tontura_com_resposta_apagada_alerta():
    r = rodar_fluxo(_escrever_video(74, "rep2.avi", 23), _escrever_video(76, "pe2.avi", 24),
                    None, True, TMP / "h2.jsonl")
    assert r["nivel_alerta"] in ("atencao", "urgente")
    assert any("hipotensão ortostática" in a for a in r["avisos"])
    print(f"  tontura + Δ{r['delta_fc_bpm']:.0f} bpm -> alerta ortostático disparado")


def test_urina_escura_eh_urgente():
    r = rodar_fluxo(_escrever_video(70, "rep3.avi", 25), _escrever_video(88, "pe3.avi", 26),
                    _escrever_urina(8, "u3.png"), False, TMP / "h3.jsonl")
    assert r["nivel_alerta"] == "urgente"
    print(f"  urina nível {r['urina_nivel']} -> nível de alerta urgente")


def test_tendencia_contra_baseline_pessoal():
    h = TMP / "h4.jsonl"
    rodar_fluxo(_escrever_video(70, "rep4a.avi", 27), _escrever_video(85, "pe4a.avi", 28),
                _escrever_urina(2, "u4a.png"), False, h)
    r = rodar_fluxo(_escrever_video(95, "rep4b.avi", 29), _escrever_video(110, "pe4b.avi", 30),
                    _escrever_urina(2, "u4b.png"), False, h)
    assert any("acima da sua média" in t for t in r["tendencias"]), r["tendencias"]
    print(f"  2º dia com FC de repouso +25 bpm -> tendência sinalizada: {r['tendencias'][0][:46]}...")


def test_video_ruim_pede_repeticao():
    r = rodar_fluxo(_escrever_video(72, "rep5.avi", 31), _escrever_video(72, "pe5.avi", 31),
                    None, False, TMP / "h5.jsonl")
    # mesmo fc nos dois vídeos ainda é ok; o caso ruim é vídeo curto:
    p = TMP / "curto.avi"
    frames = gerar_video(fc_bpm=72, segundos=4, semente=33)
    w = cv2.VideoWriter(str(p), cv2.VideoWriter_fourcc(*"mp4v"), 30.0, (320, 240))
    for fr in frames:
        w.write(fr)
    w.release()
    r2 = rodar_fluxo(p, _escrever_video(80, "pe5b.avi", 34), None, False, TMP / "h5b.jsonl")
    assert r["status"] == "ok" and r2["status"] == "repetir_medicao"
    print(f"  vídeo de 4 s -> status repetir_medicao ({r2['motivo'][:38]}...)")


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
