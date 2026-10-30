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

// ============================================================
// Gramática reduzida para o motor Vosk (MEDICAO_MOTORES.md)
//
// O Vosk em ditado livre decodifica o vocabulário inteiro do modelo a cada
// quadro de áudio. Numa escuta 24/7 isso é o que mais come CPU e bateria, e é
// também o que mais gera falso positivo com televisão ligada.
//
// Com gramática, o reconhecedor só pode emitir as palavras listadas. Decodifica
// menos, gasta menos e erra menos. O preço: o que não está na lista vira
// "[unk]" e some. Por isso a lista precisa cobrir tudo que o idoso pode
// precisar dizer, e é por isso que ela mora aqui no núcleo e é testada na JVM,
// e não dentro do código Android.
//
// Os três níveis são cumulativos: o 24/7 fica só com o nível 1. Quando a wake
// word acorda o app, o detector passa para o nível 3, que é o que a
// confirmação precisa para classificar a intenção.
// ============================================================

/** Palavra que o Vosk usa para "ouvi algo que não está na lista". */
const val VOSK_DESCONHECIDO = "[unk]"

/** Nível 1: o mínimo absoluto para a escuta 24/7. */
val VOCAB_WAKE_SOCORRO = listOf("socorro", "socorre", "socoro", "me socorre")

/** Nível 2: o que a confirmação de 10 s precisa ouvir para cancelar ou manter. */
val VOCAB_CONFIRMACAO = listOf(
    "sim", "confirmo", "confirma",
    "nao", "cancela", "cancelar", "engano", "para", "pare"
)

/**
 * Nível 3: emergências que disparam MESMO sem a palavra "socorro".
 *
 * Espelham Intencoes.EMERGENCIA_FRASES e CLINICA_FRASES. Se uma frase estiver
 * lá e não estiver aqui, a gramática engole ela e o idoso que diz "eu caí"
 * fica sem resposta. Essa é a falha mais cara possível deste arquivo, e é por
 * isso que existe teste cruzando as duas listas.
 *
 * Formas verbais: o modelo acústico emite a forma FALADA. "cair" não casa com
 * "caí", então a forma conjugada entra na lista.
 */
val VOCAB_EMERGENCIA = listOf(
    "me ajuda", "me ajude", "ajuda eu", "preciso de ajuda", "chama alguem",
    "eu cai", "eu cai no banheiro", "nao consigo levantar",
    "nao consigo me levantar", "me machuquei", "estou machucada",
    "passando mal", "estou mal", "dor forte",
    "dor no peito", "aperto no peito", "coracao apertado"
)

/** Vocabulário completo usado depois que a wake word acorda o app. */
val VOCAB_POS_WAKE = VOCAB_WAKE_SOCORRO + VOCAB_CONFIRMACAO + VOCAB_EMERGENCIA

/**
 * Monta o JSON de gramática que o Vosk espera.
 *
 * Formato: array JSON de strings, minúsculas e sem acento, sempre terminando
 * em "[unk]". Sem o "[unk]" o reconhecedor é forçado a encaixar tudo que ouve
 * numa palavra da lista, e aí qualquer ruído vira "socorro".
 */
fun gramaticaVosk(vocabulario: List<String>): String {
    val limpos = vocabulario
        .map { normalizarFala(it) }
        .filter { it.isNotEmpty() }
        .distinct()
    return (limpos + VOSK_DESCONHECIDO)
        .joinToString(prefix = "[\"", postfix = "\"]", separator = "\", \"")
}

/** Gramática da escuta 24/7: só a wake word. */
fun gramaticaVoskWake(): String = gramaticaVosk(VOCAB_WAKE_SOCORRO)

/** Gramática depois que o app acordou: wake word + confirmação + emergência. */
fun gramaticaVoskPosWake(): String = gramaticaVosk(VOCAB_POS_WAKE)

/**
 * O texto reconhecido contém alguma palavra do vocabulário?
 *
 * Com gramática o Vosk só emite palavras da lista, então esta função e
 * [ehWakeWordSocorro] têm que concordar sempre. Se discordarem, existe palavra
 * no vocabulário que o filtro de wake word não pega, e o chamado se perde em
 * silêncio. O teste que cruza as duas é o que impede isso de chegar no APK.
 */
fun temPalavraDoVocabulario(texto: String, vocabulario: List<String>): Boolean {
    val t = normalizarFala(texto)
    if (t.isEmpty()) return false
    val procuradas = vocabulario
        .map { normalizarFala(it) }
        .filter { it.isNotEmpty() && it != VOSK_DESCONHECIDO }
        .distinct()
    return procuradas.any { Regex("(^| )${Regex.escape(it)}( |$)").containsMatchIn(t) }
}
