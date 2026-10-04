package br.com.monitoridoso.core

/**
 * CHECK-IN ATIVO estilo MIMAMORI (Japão): o app LIGA/PERGUNTA ao idoso uma
 * vez por dia ("você está bem?"). Respondeu "sim" → registra ok.
 * Não respondeu → ATENCAO; 2 dias seguidos sem resposta → URGENTE.
 * Complementa a inatividade da manhã com um contato DIRETO e humano.
 */
object CheckIn {

    fun avaliar(
        respondeu: Boolean,
        semRespostaAnteriores: Int = 0,
        nome: String = "Check-in diário"
    ): DecisaoSensor {
        if (respondeu) return DecisaoSensor(Nivel.NENHUM, "registrar", "$nome: respondeu que está bem.")
        val seguidas = semRespostaAnteriores + 1
        return if (seguidas >= 2)
            DecisaoSensor(Nivel.URGENTE, "sos",
                "$nome sem resposta há $seguidas dias — acionar contatos.")
        else
            DecisaoSensor(Nivel.ATENCAO, "notificar",
                "$nome sem resposta hoje — tentar de novo em 2 h.")
    }
}
