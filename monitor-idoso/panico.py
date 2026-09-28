"""
Botão de pânico por comando de voz — orquestrador do fluxo de emergência.

Fluxo (como especificado pelo usuário):
  1. Idoso fala a palavra-chave ("socorro", estilo Google/Alexa).
  2. Confirmação rápida (evita falso positivo do wake word).
  3. Liga para a lista de contatos UM POR UM, EM SEQUÊNCIA, tocando uma
     mensagem de voz ("estou passando mal...") — independentemente de atender
     ou não, segue para o próximo.
  4. Ao final, envia SMS para TODOS com a localização (link do mapa + precisão).

ARQUITETURA (importante — restrição real do Android):
  O Android/iOS NÃO permite injetar áudio numa ligação nativa do celular
  (restrição de segurança no nível do SO). Duas rotas possíveis:
  A) NUVEM (recomendada): o app avisa um serviço (ex.: Twilio/Totalk) que
     LIGA para cada contato e TOCA a mensagem gravada. Exige internet no
     momento do acionamento.
  B) LOCAL (fallback sem internet): o app faz as ligações nativas em sequência
     (sem a mensagem de voz para quem atende) + SMS com localização.
  Este módulo é independente da rota: os gateways de chamada/SMS são
  injetáveis, e o fluxo/relatório são os mesmos.

O que vive AQUI: a máquina de estados (ordem, "atendeu ou não segue", SMS
final, relatório). O que vive no app Android: wake word (ex.: Porcupine
pt-BR), GPS, intenções de chamada/SMS.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Callable, Protocol


@dataclass
class Contato:
    nome: str
    telefone: str


@dataclass
class Localizacao:
    lat: float
    lon: float
    precisao_m: float
    origem: str = "gps"          # gps | rede | ultima_conhecida
    quando: datetime = field(default_factory=datetime.now)

    def link(self) -> str:
        return f"https://maps.google.com/?q={self.lat:.6f},{self.lon:.6f}"

    def resumo(self) -> str:
        txt = (f"Localizacao (±{int(self.precisao_m)} m, via {self.origem}): "
               f"{self.link()}")
        return txt


class GatewayChamada(Protocol):
    """Rota A: liga e toca a mensagem na nuvem. Rota B: ligação nativa."""

    def ligar(self, telefone: str, audio_path: str | None) -> bool:
        """Retorna True se o contato ATENDEU (False = não atendeu/recusou)."""
        ...


class GatewaySMS(Protocol):
    def enviar(self, telefone: str, texto: str) -> bool: ...


@dataclass
class Tentativa:
    nome: str
    telefone: str
    atendeu: bool


@dataclass
class RelatorioPanico:
    status: str                       # acionado | cancelado | sem_contatos
    tentativas: list[Tentativa] = field(default_factory=list)
    sms_enviados: list[str] = field(default_factory=list)
    sms_falhas: list[str] = field(default_factory=list)
    localizacao: Localizacao | None = None
    mensagem_sms: str = ""

    @property
    def alguem_atendeu(self) -> bool:
        return any(t.atendeu for t in self.tentativas)


def montar_mensagem_sms(nome_idoso: str, loc: Localizacao | None,
                        contexto: str | None = None) -> str:
    quando = datetime.now().strftime("%d/%m %H:%M")
    cab = f"ALERTA DE EMERGENCIA de {nome_idoso} em {quando}: " \
          f"acionou o botao de socorro e esta passando mal."
    corpo = cab if loc is None else f"{cab} {loc.resumo()}"
    if loc is None:
        corpo += " Localizacao indisponivel no momento."
    if contexto:
        # diferencial do produto: o SOS chega com o contexto de saúde recente
        corpo += f" Contexto de saude: {contexto}"
    return corpo


def acionar_panico(
    nome_idoso: str,
    contatos: list[Contato],
    chamar: GatewayChamada,
    sms: GatewaySMS,
    audio_path: str | None = None,
    localizacao: Localizacao | None = None,
    confirmar: Callable[[], bool] | None = None,
    contexto: str | None = None,
) -> RelatorioPanico:
    """Roda o fluxo completo. Gateways são injetáveis (nuvem ou nativo)."""
    # 1) confirmação rápida contra falso positivo do wake word
    if confirmar is not None and not confirmar():
        return RelatorioPanico(status="cancelado")

    if not contatos:
        return RelatorioPanico(status="sem_contatos")

    rel = RelatorioPanico(status="acionado", localizacao=localizacao)

    # 2) ligações EM SEQUÊNCIA, uma por uma; atendeu ou não, segue para a próxima
    for c in contatos:
        atendeu = bool(chamar.ligar(c.telefone, audio_path))
        rel.tentativas.append(Tentativa(c.nome, c.telefone, atendeu))

    # 3) SMS com localização (e contexto de saúde) para TODOS
    texto = montar_mensagem_sms(nome_idoso, localizacao, contexto)
    rel.mensagem_sms = texto
    for c in contatos:
        (rel.sms_enviados if sms.enviar(c.telefone, texto)
         else rel.sms_falhas).append(c.telefone)

    return rel
