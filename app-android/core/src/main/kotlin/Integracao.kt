package br.com.monitoridoso.core

/**
 * Camada de INTEROPERABILIDADE (parceria Mariana Aguiar, 01/10: o app dela
 * guarda o histórico de saúde e pode embutir o SOS do Margarida).
 * Dois contratos abertos e documentados, sempre SEM NUVEM:
 *
 *  1) ENTRADA: o app parceiro aciona o SOS por intent Android; o núcleo só
 *     aceita se o app estiver registrado como confiável (fonte PARCEIRO,
 *     disparo direto, sem pergunta).
 *  2) SAÍDA: o diário do Margarida alimenta o histórico do parceiro em JSON,
 *     gerado e entregue localmente, de aparelho para aparelho.
 */
data class PedidoSosParceiro(
    val appOrigem: String,
    val confiavel: Boolean,
    val observacao: String = ""
)

object Integracao {
    /** Só vira fonte PARCEIRO se o app de origem for confiável e identificado. */
    fun validar(p: PedidoSosParceiro): FonteSos? =
        if (p.confiavel && p.appOrigem.isNotBlank()) FonteSos.PARCEIRO else null

    /** Exporta o diário como array JSON para o histórico do parceiro. 100% local. */
    fun historicoJson(diario: Diario): String =
        "[" + diario.todos().joinToString(",") { it.json() } + "]"
}
