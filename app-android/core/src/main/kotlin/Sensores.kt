package br.com.monitoridoso.core

/**
 * Camada de sensores — porte fiel de sensores.py (regras idênticas).
 * Eventos vindos da ponte Matter/Tuya no app Android caem aqui.
 */
const val IMOBILIDADE_MIN_S = 15.0      // FALLR1 aceita 5-90 s
const val SAIDA_LEITO_LONGA_MIN = 10.0

data class DecisaoSensor(val nivel: Nivel, val acao: String, val mensagem: String)

fun naJanelaSono(hora: Int): Boolean = hora >= 22 || hora < 5

fun decidirEvento(tipo: String, payload: Map<String, Any?>, hora: Int): DecisaoSensor {
    return when (tipo) {
        "queda" -> {
            val imovel = (payload["imovel_s"] as? Number)?.toDouble() ?: 0.0
            if (imovel >= IMOBILIDADE_MIN_S)
                DecisaoSensor(Nivel.URGENTE, "sos",
                    "QUEDA CONFIRMADA pelo radar (imóvel há ${imovel.toInt()} s).")
            else
                DecisaoSensor(Nivel.ATENCAO, "notificar",
                    "Radar registrou queda não confirmada (imóvel ${imovel.toInt()} s " +
                        "< ${IMOBILIDADE_MIN_S.toInt()} s) — verificar.")
        }
        "gas" ->
            if (payload["detectado"] == true)
                DecisaoSensor(Nivel.URGENTE, "sos",
                    "Sensor de GÁS disparado — risco imediato. Se possível, " +
                        "abrir janelas e sair do ambiente.")
            else DecisaoSensor(Nivel.NENHUM, "registrar", "Sensor de gás normalizado.")
        "porta" ->
            if (payload["aberta"] == true && naJanelaSono(hora))
                DecisaoSensor(Nivel.ATENCAO, "notificar",
                    "Porta aberta às ${String.format("%02d", hora)}h (janela de sono) — " +
                        "possível deambulação; verificar.")
            else DecisaoSensor(Nivel.NENHUM, "registrar", "Porta aberta (horário normal).")
        "remedio" ->
            if (payload["aberto_hoje"] != true)
                DecisaoSensor(Nivel.ATENCAO, "notificar",
                    "Armário de remédio ainda fechado às ${String.format("%02d", hora)}h — " +
                        "possível esquecimento da medicação.")
            else DecisaoSensor(Nivel.NENHUM, "registrar", "Medicação acessada hoje.")
        "saida_leito" -> {
            val minutos = (payload["minutos"] as? Number)?.toDouble() ?: 0.0
            if (minutos >= SAIDA_LEITO_LONGA_MIN && naJanelaSono(hora))
                DecisaoSensor(Nivel.ATENCAO, "notificar",
                    "Fora do leito há ${minutos.toInt()} min durante a noite — " +
                        "risco de queda no trajeto do banheiro.")
            else DecisaoSensor(Nivel.NENHUM, "registrar",
                "Saída do leito (${minutos.toInt()} min).")
        }
        else -> DecisaoSensor(Nivel.NENHUM, "registrar", "Evento desconhecido: $tipo.")
    }
}
