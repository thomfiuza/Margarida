package br.com.monitoridoso.core

import java.text.Normalizer
import java.util.Locale

/**
 * Wake word "socorro" e a confirmação de 10 s (especificacao_android.md, seção 3).
 * Sem "não" dentro da janela, o SOS dispara mesmo assim: omissão custa mais
 * que um falso positivo. O motor (Porcupine ou o reconhecedor do sistema) só
 * entrega texto; a decisão mora aqui para ser testada sem Android.
 */
enum class DecisaoWake { AGUARDAR, DISPARAR, CANCELAR }

const val JANELA_CONFIRMACAO_MS = 10_000L

const val FALA_CONFIRMACAO =
    "Você chamou ajuda? Diga não para cancelar. Se não responder, eu ligo."

private val CANCELA = listOf("nao", "cancela", "cancelar", "engano", "errado")
private val CONFIRMA = listOf("sim", "socorro", "socorre", "socoro", "confirmo", "confirma")

fun normalizarFala(texto: String): String {
    val semAcento = Normalizer.normalize(texto.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return semAcento.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}

fun ehWakeWordSocorro(texto: String): Boolean {
    val n = normalizarFala(texto)
    if (n.isEmpty()) return false
    // "socoro" é o erro comum do reconhecedor para "socorro".
    return Regex("(^| )(socorro|socorre|socoro|me socorre)( |$)").containsMatchIn(n)
}

fun decidirConfirmacaoSos(
    falas: List<String>,
    decorridoMs: Long,
    janelaMs: Long = JANELA_CONFIRMACAO_MS
): DecisaoWake {
    val textos = falas.map { normalizarFala(it) }.filter { it.isNotEmpty() }
    if (textos.any { temPalavra(it, CANCELA) }) return DecisaoWake.CANCELAR
    if (textos.any { temPalavra(it, CONFIRMA) || ehWakeWordSocorro(it) }) return DecisaoWake.DISPARAR
    if (decorridoMs >= janelaMs) return DecisaoWake.DISPARAR
    return DecisaoWake.AGUARDAR
}

private fun temPalavra(texto: String, palavras: List<String>): Boolean =
    palavras.any { Regex("(^| )$it( |$)").containsMatchIn(texto) }
