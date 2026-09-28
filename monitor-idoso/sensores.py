"""
Camada de sensores — o cérebro da Margarida para eventos de sensores.

O hardware (radar Matter FALLR1-like, contato de porta, gás, armário de
remédio, pad de cama) fala protocolos variados; o app recebe EVENTOS e decide
o que cada um VALE. Regras derivadas do mapa mundial
(margarida_mapa_mundial.md):

  queda     -> estilo FALLR1: confirma se imóvel >= min (5-90 s; padrão 15 s).
               Confirmada = URGENTE e dispara o SOS sequencial.
  gas       -> URGENTE sempre (risco de explosão/asfixia) + SOS.
  porta     -> aberta na janela de sono (22h-05h) = ATENCAO (deambulação,
               comum em demência); de dia = registro normal.
  remedio   -> armário fechado além do horário = ATENCAO (esquecimento).
  saida_leito -> via pad BCG: silêncio do sinal = pessoa levantou. Normal;
               demorada à noite = ATENCAO (banheiro prolongado = risco de queda).

Nada aqui depende de hardware: os eventos chegam como dicts e saem como
decisões (nivel, acao, mensagem). Testável 100%.
"""
from __future__ import annotations

import numpy as np

# ---- tipos de evento (contrato com a ponte Matter/Tuya no app Android) ----
QUEDA = "queda"
GAS = "gas"
PORTA = "porta"
REMEDIO = "remedio"
SAIDA_LEITO = "saida_leito"

JANELA_SONO = (22, 5)      # hora inicial (inclusiva), final (exclusiva)
IMOBILIDADE_MIN_S = 15     # FALLR1 aceita 5-90 s; 15 s equilibra erro/omissão
SAIDA_LEITO_LONGA_MIN = 10


def _na_janela_sono(hora: int) -> bool:
    ini, fim = JANELA_SONO
    return hora >= ini or hora < fim


def decidir_evento(tipo: str, payload: dict, hora: int) -> tuple[str, str, str]:
    """(nivel, acao, mensagem). acao: 'sos' | 'notificar' | 'registrar'."""
    if tipo == QUEDA:
        imovel = float(payload.get("imovel_s", 0))
        if imovel >= IMOBILIDADE_MIN_S:
            return ("urgente", "sos",
                    f"QUEDA CONFIRMADA pelo radar (imóvel há {int(imovel)} s).")
        return ("atencao", "notificar",
                f"Radar registrou queda não confirmada (imóvel {int(imovel)} s "
                f"< {IMOBILIDADE_MIN_S} s) — verificar.")

    if tipo == GAS:
        if payload.get("detectado"):
            return ("urgente", "sos",
                    "Sensor de GÁS disparado — risco imediato. Se possível, "
                    "abrir janelas e sair do ambiente.")
        return ("nenhum", "registrar", "Sensor de gás normalizado.")

    if tipo == PORTA:
        if payload.get("aberta") and _na_janela_sono(hora):
            return ("atencao", "notificar",
                    f"Porta aberta às {hora:02d}h (janela de sono) — possível "
                    f"deambulação; verificar.")
        return ("nenhum", "registrar", "Porta aberta (horário normal).")

    if tipo == REMEDIO:
        if not payload.get("aberto_hoje"):
            return ("atencao", "notificar",
                    f"Armário de remédio ainda fechado às {hora:02d}h — "
                    f"possível esquecimento da medicação.")
        return ("nenhum", "registrar", "Medicação acessada hoje.")

    if tipo == SAIDA_LEITO:
        minutos = float(payload.get("minutos", 0))
        if minutos >= SAIDA_LEITO_LONGA_MIN and _na_janela_sono(hora):
            return ("atencao", "notificar",
                    f"Fora do leito há {int(minutos)} min durante a noite — "
                    f"risco de queda no trajeto do banheiro.")
        return ("nenhum", "registrar", f"Saída do leito ({int(minutos)} min).")

    return ("nenhum", "registrar", f"Evento desconhecido: {tipo}.")


def detectar_saida_leito(sinal: np.ndarray, fs: float = 100.0,
                         janela_s: float = 20.0) -> dict:
    """Acha os trechos de SILÊNCIO (sem batimento nem respiração) num sinal de
    pad: é assim que o colchão 'vê' a pessoa levantar.

    Retorna {'silencios': [(t_ini_s, t_fim_s), ...], 'maior_silencio_s': float}.
    Silêncio = energia da janela < 5% da energia mediana do sinal inteiro.
    """
    n = int(janela_s * fs)
    if len(sinal) < n * 2:
        return {"silencios": [], "maior_silencio_s": 0.0}
    quadros = len(sinal) // n
    energia = np.array([np.sqrt(np.mean(sinal[i * n:(i + 1) * n] ** 2))
                        for i in range(quadros)])
    # referência = MÁXIMO, não mediana: se a maior parte da noite foi de cama
    # vazia, a mediana cai para o nível do silêncio e nada seria detectado.
    # O batimento presente sempre define o teto de energia.
    limiar = 0.05 * float(np.max(energia))
    silencioso = energia < limiar
    silencios, ini = [], None
    for i, s in enumerate(silencioso):
        if s and ini is None:
            ini = i
        elif not s and ini is not None:
            silencios.append((round(ini * janela_s, 1), round(i * janela_s, 1)))
            ini = None
    if ini is not None:
        silencios.append((round(ini * janela_s, 1), round(quadros * janela_s, 1)))
    maior = max((f - i for i, f in silencios), default=0.0)
    return {"silencios": silencios, "maior_silencio_s": float(maior)}
