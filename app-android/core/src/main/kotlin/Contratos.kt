package br.com.monitoridoso.core

import java.time.LocalDateTime
import java.util.Locale

/** Níveis de alerta — mesma hierarquia do protótipo Python. */
enum class Nivel(val ord: Int) {
    NENHUM(0), ATENCAO(1), URGENTE(2);
    companion object {
        fun de(s: String): Nivel = valueOf(s.uppercase(Locale.ROOT))
    }
}

fun piorNivel(ns: List<Nivel>): Nivel = ns.maxByOrNull { it.ord } ?: Nivel.NENHUM

data class Contato(val nome: String, val telefone: String)

data class Localizacao(
    val lat: Double, val lon: Double, val precisaoM: Double, val origem: String = "gps"
) {
    fun link(): String = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon)
    fun resumo(): String = "Localizacao (±${precisaoM.toInt()} m, via $origem): ${link()}"
}

/** Evento do diário — JSON idêntico ao de orquestrador.py. */
data class Evento(
    val tipo: String,                 // rotina | noite | emergencia
    val nivel: Nivel,
    val mensagem: String,
    val quando: String = agoraIso(),
    val dados: Map<String, Any?> = emptyMap()
) {
    fun json(): String = Json.stringify(
        mapOf(
            "tipo" to tipo, "quando" to quando, "nivel" to nivel.name.lowercase(Locale.ROOT),
            "mensagem" to mensagem, "dados" to dados
        )
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun doJson(line: String): Evento {
            val m = Json.parse(line) as Map<String, Any?>
            val d = (m["dados"] as? Map<String, Any?>) ?: emptyMap()
            return Evento(
                tipo = m["tipo"] as String,
                nivel = Nivel.de(m["nivel"] as String),
                mensagem = m["mensagem"] as String,
                quando = m["quando"] as String,
                dados = d
            )
        }
    }
}

fun agoraIso(): String = LocalDateTime.now().withNano(0).toString()
