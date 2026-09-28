"""Testes do orquestrador do botão de pânico (panico.py) com gateways falsos."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from panico import Contato, Localizacao, acionar_panico  # noqa: E402

CONTATOS = [
    Contato("Filha Ana", "+5534999990001"),
    Contato("Vizinho Beto", "+5534999990002"),
    Contato("SAMU", "192"),
]
LOC = Localizacao(lat=-19.5937, lon=-46.9409, precisao_m=8.0, origem="gps")


class ChamadaFalsa:
    """Registra a ordem das ligações; 'atendem' controla quem atende."""

    def __init__(self, atendem=()):
        self.registro: list[tuple[str, str | None]] = []
        self.atendem = set(atendem)

    def ligar(self, telefone, audio_path):
        self.registro.append((telefone, audio_path))
        return telefone in self.atendem


class SMSFalso:
    def __init__(self, falham=()):
        self.mensagens: dict[str, str] = {}
        self.falham = set(falham)

    def enviar(self, telefone, texto):
        if telefone in self.falham:
            return False
        self.mensagens[telefone] = texto
        return True


def test_ordem_sequencial_e_segue_mesmo_sem_atender():
    ch = ChamadaFalsa(atendem={"+5534999990002"})  # só o 2º atende
    rel = acionar_panico("Dona Maria", CONTATOS, ch, SMSFalso(),
                         audio_path="audio/09_mensagem_panico.mp3",
                         localizacao=LOC)
    ordem = [t for t, _ in ch.registro]
    assert ordem == [c.telefone for c in CONTATOS], ordem          # 1 por 1, na ordem
    assert [t.atendeu for t in rel.tentativas] == [False, True, False]
    assert len(rel.tentativas) == 3                                 # ligou p/ todos
    assert rel.alguem_atendeu
    assert ch.registro[0][1] == "audio/09_mensagem_panico.mp3"      # áudio enviado
    print(f"  ordem {[t.nome for t in rel.tentativas]}, atendeu: Ana não/Beto sim/SAMU não")


def test_sms_para_todos_com_localizacao():
    rel = acionar_panico("Dona Maria", CONTATOS, ChamadaFalsa(), SMSFalso(),
                         localizacao=LOC)
    assert len(rel.sms_enviados) == 3
    texto = rel.mensagem_sms
    assert "maps.google.com" in texto and "-19.59" in texto
    assert "EMERGENCIA" in texto and "Dona Maria" in texto
    assert "8 m" in texto                                           # precisão incluída
    print(f"  SMS p/ 3 contatos: {texto[:60]}...")


def test_cancelamento_evita_falso_positivo():
    ch, sm = ChamadaFalsa(), SMSFalso()
    rel = acionar_panico("Dona Maria", CONTATOS, ch, sm, confirmar=lambda: False)
    assert rel.status == "cancelado"
    assert ch.registro == [] and sm.mensagens == {}
    print("  'não' na confirmação -> zero ligações, zero SMS")


def test_sem_localizacao_ainda_avisa():
    rel = acionar_panico("Dona Maria", CONTATOS, ChamadaFalsa(), SMSFalso(),
                         localizacao=None)
    assert len(rel.sms_enviados) == 3
    assert "indisponivel" in rel.mensagem_sms
    print("  GPS indisponível -> SMS mesmo assim, com aviso")


def test_lista_vazia_e_falha_de_sms():
    rel = acionar_panico("Dona Maria", [], ChamadaFalsa(), SMSFalso())
    assert rel.status == "sem_contatos"
    rel2 = acionar_panico("Dona Maria", CONTATOS, ChamadaFalsa(),
                          SMSFalso(falham={"+5534999990002"}), localizacao=LOC)
    assert rel2.sms_falhas == ["+5534999990002"] and len(rel2.sms_enviados) == 2
    print("  lista vazia -> sem_contatos | SMS que falha -> registrado na falha")


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
