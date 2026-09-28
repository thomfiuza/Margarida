"""
Orquestrador — amarra TODOS os módulos do app numa jornada única.

Este é o equivalente em Python do "núcleo de negócio" que o app Android vai
carregar (ver especificacao_android.md). Ele liga:

  - rotina diurna   -> fluxo_idoso.rodar_fluxo   (voz + FC + ortostática + urina)
  - noite           -> bcg_noturno.estimar_noturno (pad sob o colchão, v2)
  - emergência      -> panico.acionar_panico      (SOS com contexto de saúde)

Tudo converge para um DIÁRIO único (JSONL) de Eventos, que alimenta:
  - o resumo do dia para o cuidador;
  - o CONTEXTO DE SAÚDE que viaja junto com o SOS (o diferencial: quem recebe
    o socorro sabe o que estava acontecendo com a pessoa nos últimos dias).

Contrato de Evento (o app Android usa o MESMO JSON):
  {"tipo": "rotina"|"noite"|"emergencia", "quando": iso, "nivel": nivel,
   "mensagem": str, "dados": {...}}
"""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from datetime import date, datetime
from pathlib import Path

from bcg_noturno import estimar_noturno
from fluxo_idoso import rodar_fluxo
from panico import GatewayChamada, GatewaySMS, Localizacao, acionar_panico
from sensores import decidir_evento, detectar_saida_leito

NIVEIS = ("nenhum", "atencao", "urgente")

AUDIO_SOS = "audio/09_mensagem_panico.mp3"


@dataclass
class Evento:
    tipo: str                       # rotina | noite | emergencia
    nivel: str                      # nenhum | atencao | urgente
    mensagem: str
    quando: str = field(default_factory=lambda: datetime.now().isoformat(timespec="seconds"))
    dados: dict = field(default_factory=dict)

    def json(self) -> str:
        return json.dumps(asdict(self), ensure_ascii=False)


class Diario:
    """Registro cronológico único do idoso (JSONL) — 1 linha = 1 evento."""

    def __init__(self, path: str | Path):
        self.path = Path(path)

    def registrar(self, ev: Evento) -> None:
        with self.path.open("a") as f:
            f.write(ev.json() + "\n")

    def todos(self) -> list[dict]:
        if not self.path.exists():
            return []
        return [json.loads(l) for l in self.path.read_text().splitlines() if l.strip()]

    def do_tipo(self, tipo: str, n: int | None = None) -> list[dict]:
        evs = [e for e in self.todos() if e["tipo"] == tipo]
        return evs[-n:] if n else evs


def _pior(niveis: list[str]) -> str:
    return max(niveis, key=NIVEIS.index) if niveis else "nenhum"


class MonitorIdoso:
    def __init__(self, nome_idoso: str, contatos: list, diario: str | Path,
                 historico_rotina: str | Path = "historico.jsonl"):
        self.nome = nome_idoso
        self.contatos = contatos
        self.diario = Diario(diario)
        self.historico_rotina = str(historico_rotina)

    # ---------- ROTINA DIURNA (modo cuidador, guiada por voz) ----------
    def rotina_diaria(self, video_repouso, video_pe, foto_urina=None,
                      tontura: bool = False) -> Evento:
        r = rodar_fluxo(video_repouso, video_pe, foto_urina, tontura,
                        historico=self.historico_rotina)
        if r["status"] != "ok":
            ev = Evento("rotina", "nenhum",
                        f"Medicao incompleta ({r.get('etapa_com_falha', '?')}); pedir repeticao.",
                        dados=r)
        else:
            ev = Evento("rotina", r["nivel_alerta"], r["relatorio_cuidador"], dados=r)
        self.diario.registrar(ev)
        return ev

    # ---------- NOITE (pad BCG, passivo — adesão zero) ----------
    def noite(self, sinal_bcg) -> Evento:
        r = estimar_noturno(sinal_bcg)
        if not r["ok"]:
            ev = Evento("noite", "nenhum", "Noite sem sinal utilizavel do pad.", dados=r)
            self.diario.registrar(ev)
            return ev

        base_fc = [e["dados"]["fc_bpm"] for e in self.diario.do_tipo("noite", 7)
                   if e["dados"].get("ok")]
        base_fr = [e["dados"]["fr_irpm"] for e in self.diario.do_tipo("noite", 7)
                   if e["dados"].get("ok")]
        nivel, avisos = "nenhum", []
        if base_fc:
            import numpy as np
            d_fc = r["fc_bpm"] - float(np.mean(base_fc))
            d_fr = r["fr_irpm"] - float(np.mean(base_fr)) if base_fr else 0.0
            if d_fc >= 15 or d_fr >= 6:
                nivel = "urgente"
            elif d_fc >= 8 or d_fr >= 3:
                nivel = "atencao"
            if d_fc >= 8:
                avisos.append(f"FC noturna {d_fc:.0f} bpm acima da media dos ultimos dias.")
            if d_fr >= 3:
                avisos.append(f"Respiracao noturna {d_fr:.0f} irpm acima da media (possivel taquipneia).")
        msg = ("; ".join(avisos) if avisos else
               f"Noite estavel: FC {r['fc_bpm']} bpm, FR {r['fr_irpm']} irpm.")
        ev = Evento("noite", nivel, msg, dados=r)
        self.diario.registrar(ev)
        return ev

    # ---------- EMERGÊNCIA (modo idoso, wake word) ----------
    def emergencia(self, localizacao: Localizacao | None, chamar: GatewayChamada,
                   sms: GatewaySMS, confirmar=None,
                   audio_path: str = AUDIO_SOS) -> Evento:
        rel = acionar_panico(self.nome, self.contatos, chamar, sms,
                             audio_path=audio_path, localizacao=localizacao,
                             confirmar=confirmar, contexto=self.contexto_saude())
        if rel.status != "acionado":
            return Evento("emergencia", "nenhum", f"SOS nao disparado ({rel.status}).",
                          dados={"status": rel.status})
        atendidos = [t.nome for t in rel.tentativas if t.atendeu]
        msg = (f"SOS disparado: {len(rel.tentativas)} ligacoes, "
               f"atendeu: {', '.join(atendidos) or 'ninguem'}; "
               f"SMS para {len(rel.sms_enviados)} contatos.")
        ev = Evento("emergencia", "urgente", msg,
                    dados={"tentativas": [asdict(t) for t in rel.tentativas],
                           "sms": rel.mensagem_sms})
        self.diario.registrar(ev)
        return ev

    # ---------- SENSORES (radar/queda, gás, porta, remédio — mapa mundial) ----------
    def registrar_evento_sensor(self, tipo: str, payload: dict, hora: int,
                                chamar: GatewayChamada | None = None,
                                sms: GatewaySMS | None = None,
                                localizacao: Localizacao | None = None,
                                confirmar=None) -> Evento:
        """Recebe um evento de sensor, decide o nível e — se acao='sos' —
        dispara o MESMO SOS sequencial com contexto de saúde."""
        nivel, acao, msg = decidir_evento(tipo, payload, hora)
        dados = {"sensor": tipo, "payload": payload, "acao": acao}
        if acao == "sos" and chamar is not None and sms is not None:
            sos = self.emergencia(localizacao, chamar, sms, confirmar=confirmar)
            dados["sos"] = sos.mensagem
            ev = Evento("sensor", "urgente", f"{msg} {sos.mensagem}", dados=dados)
        else:
            ev = Evento("sensor", nivel, msg, dados=dados)
        self.diario.registrar(ev)
        return ev

    # ---------- NOITE passiva: saída do leito vista pelo pad ----------
    def registrar_saida_leito(self, sinal_bcg, fs: float = 100.0,
                              hora: int = 3, chamar: GatewayChamada | None = None,
                              sms: GatewaySMS | None = None) -> Evento | None:
        r = detectar_saida_leito(sinal_bcg, fs=fs)
        if r["maior_silencio_s"] < 60:
            return None
        minutos = r["maior_silencio_s"] / 60.0
        return self.registrar_evento_sensor(
            "saida_leito", {"minutos": minutos}, hora=hora,
            chamar=chamar, sms=sms)

    # ---------- INATIVIDADE (replicável sem hardware — só software) ----------
    def checar_inatividade(self, agora: datetime, hora_limite: tuple[int, int] = (10, 0)
                           ) -> Evento | None:
        """Se a rotina da manhã não aconteceu até o limite (e ainda não
        alertamos hoje), gera um evento de atenção para o cuidador."""
        hoje = str(agora.date())
        limite = agora.replace(hour=hora_limite[0], minute=hora_limite[1],
                               second=0, microsecond=0)
        if agora < limite:
            return None
        rotinas_hoje = [e for e in self.diario.do_tipo("rotina")
                        if e["quando"].startswith(hoje)]
        alertas_hoje = [e for e in self.diario.do_tipo("inatividade")
                        if e["quando"].startswith(hoje)]
        if rotinas_hoje or alertas_hoje:
            return None
        ev = Evento("inatividade", "atencao",
                    f"{self.nome} ainda não fez a rotina da manhã "
                    f"(limite {hora_limite[0]:02d}h{hora_limite[1]:02d}) — "
                    f"ligar para verificar.",
                    quando=agora.isoformat(timespec="seconds"))
        self.diario.registrar(ev)
        return ev

    # ---------- O QUE AMARRA TUDO ----------
    def contexto_saude(self, dias: int = 7) -> str:
        """Resumo curto dos últimos dias — viaja junto com o SOS."""
        partes = []
        ult_rot = self.diario.do_tipo("rotina")[-1:]
        if ult_rot and ult_rot[0]["nivel"] != "nenhum":
            partes.append(f"ultimo alerta da rotina: {ult_rot[0]['mensagem'][:90]}")
        noites = [e for e in self.diario.do_tipo("noite", dias) if e["nivel"] != "nenhum"]
        if noites:
            partes.append(f"{len(noites)} noites com sinal de alerta, "
                          f"a mais recente: {noites[-1]['mensagem'][:90]}")
        return "; ".join(partes) if partes else "sem alertas recentes registrados."

    def resumo_do_dia(self) -> dict:
        hoje = str(date.today())
        evs = [e for e in self.diario.todos() if e["quando"].startswith(hoje)]
        pior = _pior([e["nivel"] for e in evs])
        linhas = [f"[{e['nivel'].upper()}] {e['tipo']}: {e['mensagem']}" for e in evs]
        return {
            "data": hoje,
            "eventos": len(evs),
            "pior_nivel": pior,
            "texto": (f"Resumo de {hoje} — {self.nome} ({pior}):\n" +
                      "\n".join(linhas)) if evs else f"Nenhum evento em {hoje}.",
        }
