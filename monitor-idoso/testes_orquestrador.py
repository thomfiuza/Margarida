"""
Testes de integração do orquestrador: rotina + noite + emergência passando
PELOS MÓDULOS REAIS (fluxo_idoso com vídeos em disco, bcg_noturno com sinal
sintético, panico com gateways falsos), tudo convergindo no diário único.
"""
from __future__ import annotations

import sys
import tempfile
from pathlib import Path

import cv2

sys.path.insert(0, str(Path(__file__).parent))
from bcg_noturno import gerar_sinal_bcg  # noqa: E402
from orquestrador import MonitorIdoso  # noqa: E402
from panico import Contato, Localizacao  # noqa: E402
from testes_urina import cena, ILUMINANTES  # noqa: E402
from video_sintetico import gerar_video  # noqa: E402

TMP = Path(tempfile.mkdtemp(prefix="orq_test_"))
CONTATOS = [Contato("Filha Ana", "+5534999990001"), Contato("SAMU", "192")]
LOC = Localizacao(lat=-19.5937, lon=-46.9409, precisao_m=8.0)


def _video(fc: float, nome: str, semente: int) -> Path:
    p = TMP / nome
    frames = gerar_video(fc_bpm=fc, segundos=30, semente=semente)
    w = cv2.VideoWriter(str(p), cv2.VideoWriter_fourcc(*"mp4v"), 30.0,
                        (frames[0].shape[1], frames[0].shape[0]))
    for fr in frames:
        w.write(fr)
    w.release()
    return p


def _urina(nivel: int, nome: str) -> Path:
    p = TMP / nome
    cv2.imwrite(str(p), cena(nivel, ILUMINANTES["quente"]))
    return p


class ChamadaFalsa:
    def __init__(self, atendem=()):
        self.n = 0
        self.atendem = set(atendem)

    def ligar(self, telefone, audio_path):
        self.n += 1
        return telefone in self.atendem


class SMSFalso:
    def __init__(self):
        self.mensagens = {}

    def enviar(self, telefone, texto):
        self.mensagens[telefone] = texto
        return True


def _novo_monitor(nome_diario: str) -> MonitorIdoso:
    return MonitorIdoso("Dona Maria", CONTATOS,
                        diario=TMP / nome_diario,
                        historico_rotina=TMP / f"hist_{nome_diario}")


def test_jornada_completa_rotina_noite_sos():
    m = _novo_monitor("jornada.jsonl")
    # dia: rotina normal
    ev = m.rotina_diaria(_video(70, "rep1.avi", 1), _video(75, "pe1.avi", 2))
    assert ev.tipo == "rotina" and ev.nivel == "nenhum", (ev.tipo, ev.nivel)
    # noite estável
    ev_n = m.noite(gerar_sinal_bcg(fc_bpm=64, fr_irpm=14, segundos=300, semente=1))
    assert ev_n.nivel == "nenhum" and ev_n.dados["ok"]
    # SOS com contexto (sem alertas recentes -> contexto diz isso)
    sm = SMSFalso()
    ev_s = m.emergencia(LOC, ChamadaFalsa(atendem={"192"}), sm)
    assert ev_s.nivel == "urgente" and "SAMU" in ev_s.mensagem
    assert "sem alertas recentes" in sm.mensagens["192"]
    r = m.resumo_do_dia()
    assert r["eventos"] == 3 and r["pior_nivel"] == "urgente"
    print(f"  3 eventos no diário; pior nível: {r['pior_nivel']}")


def test_sos_viaja_com_contexto_de_saude():
    m = _novo_monitor("contexto.jsonl")
    # rotina com urina urgente deixa alerta no diário
    ev = m.rotina_diaria(_video(70, "rep2.avi", 3), _video(75, "pe2.avi", 4),
                         foto_urina=_urina(8, "urina_escura.png"))
    assert ev.nivel == "urgente"
    # noites com tendência de FC subindo
    for s in range(5):
        m.noite(gerar_sinal_bcg(fc_bpm=64, segundos=300, semente=10 + s))
    ev_sub = m.noite(gerar_sinal_bcg(fc_bpm=80, segundos=300, semente=99))
    assert ev_sub.nivel == "urgente", ev_sub.nivel   # +16 bpm vs média -> urgente
    assert "acima da media" in ev_sub.mensagem
    # agora o SOS deve carregar os dois contextos
    sm = SMSFalso()
    m.emergencia(LOC, ChamadaFalsa(), sm)
    sms = sm.mensagens["192"]
    assert "Contexto de saude" in sms
    assert "noites com sinal de alerta" in sms
    print(f"  SMS do SOS: ...{sms[-120:]}")


def test_noite_sem_sinal_nao_gera_falso_alerta():
    m = _novo_monitor("noite_ruim.jsonl")
    import numpy as np
    ev = m.noite(np.random.default_rng(0).normal(0, 0.1, 30000))
    assert ev.nivel == "nenhum" and "sem sinal" in ev.mensagem
    print("  pad fora do lugar -> evento 'sem sinal', nenhum alerta")


def test_diario_acumula_entre_modulos():
    m = _novo_monitor("acumula.jsonl")
    m.rotina_diaria(_video(70, "rep3.avi", 5), _video(76, "pe3.avi", 6))
    m.noite(gerar_sinal_bcg(segundos=300, semente=7))
    tipos = [e["tipo"] for e in m.diario.todos()]
    assert tipos == ["rotina", "noite"], tipos
    print(f"  diário JSONL: {tipos}")


def test_sensor_queda_dispara_sos_com_contexto():
    m = _novo_monitor("sensor_sos.jsonl")
    # deixa um alerta de rotina antes, para o SOS sair com contexto
    m.rotina_diaria(_video(70, "rep4.avi", 7), _video(75, "pe4.avi", 8),
                    foto_urina=_urina(8, "urina8.png"))
    sm = SMSFalso()
    ev = m.registrar_evento_sensor("queda", {"imovel_s": 30}, hora=14,
                                   chamar=ChamadaFalsa(), sms=sm)
    assert ev.nivel == "urgente" and "QUEDA CONFIRMADA" in ev.mensagem
    assert "SOS disparado" in ev.mensagem
    assert "Contexto de saude" in sm.mensagens["192"]
    print("  radar: queda confirmada -> SOS sequencial com contexto ✔")


def test_inatividade_da_manh():
    from datetime import datetime, timedelta
    m = _novo_monitor("inativo.jsonl")
    agora = datetime.now().replace(hour=10, minute=30, second=0, microsecond=0)
    ev = m.checar_inatividade(agora)
    assert ev is not None and ev.nivel == "atencao", ev
    # segunda checagem no mesmo dia não repete o alerta
    assert m.checar_inatividade(agora + timedelta(hours=1)) is None
    # antes do limite, nada
    m2 = _novo_monitor("inativo2.jsonl")
    cedo = datetime.now().replace(hour=8, minute=0, second=0, microsecond=0)
    assert m2.checar_inatividade(cedo) is None
    print("  rotina não feita até 10h -> 1 alerta/dia ao cuidador ✔")


def test_saida_leito_pelo_pad():
    import numpy as np
    from bcg_noturno import gerar_sinal_bcg
    m = _novo_monitor("leito.jsonl")
    fs = 100.0
    com = gerar_sinal_bcg(fc_bpm=64, fr_irpm=14, segundos=120, semente=11)
    vazio = np.random.default_rng(12).normal(0, 0.001, int(15 * 60 * fs))  # 15 min fora
    sinal = np.concatenate([com, vazio])
    ev = m.registrar_saida_leito(sinal, fs=fs, hora=3)
    assert ev is not None and ev.nivel == "atencao", ev
    assert "Fora do leito" in ev.mensagem
    print("  pad: 15 min de cama vazia à noite -> atenção (risco no banheiro) ✔")


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
