package br.com.monitoridoso.core

/**
 * Ponte Matter/Tuya → evento que [decidirEvento] já sabe julgar.
 *
 * Três formatos, o mesmo resultado:
 *  1. Canônico (o contrato interno): {"tipo":"queda","hora":15,"imovel_s":30}
 *  2. Tuya / Smart Life: {"protocolo":"tuya","papel":"radar|gas|porta|remedio|leito",
 *     "status":[{"code":"...","value":...}]}
 *  3. Matter (atributos já decodificados pelo controller da casa):
 *     {"protocolo":"matter","papel":"...","attributes":{...}}
 *
 * O campo "token" não entra no payload do diário. Quem dispara o SOS é o app,
 * depois de conferir o token com [tokenConfere].
 */
data class EventoNormalizado(
    val tipo: String,
    val payload: Map<String, Any?>,
    val hora: Int
)

fun tokenConfere(raiz: Map<String, Any?>, esperado: String): Boolean {
    if (esperado.isBlank()) return false
    return raiz["token"]?.toString() == esperado
}

fun normalizarEventoSensor(json: String, horaPadrao: Int): EventoNormalizado? {
    val raiz = Json.parse(json) as? Map<String, Any?> ?: return null
    return normalizarEventoSensor(raiz, horaPadrao)
}

fun normalizarEventoSensor(raiz: Map<String, Any?>, horaPadrao: Int): EventoNormalizado? {
    val hora = (raiz["hora"] as? Number)?.toInt() ?: horaPadrao
    val protocolo = (raiz["protocolo"] as? String)?.lowercase()
    if (protocolo == null) {
        val tipo = raiz["tipo"] as? String ?: return null
        val payload = raiz.filterKeys { it !in setOf("tipo", "hora", "token", "protocolo") }
        return EventoNormalizado(tipo, payload, hora)
    }
    val papel = (raiz["papel"] as? String)?.lowercase() ?: return null
    return when (protocolo) {
        "tuya" -> doTuya(papel, statusDe(raiz), hora)
        "matter" -> doMatter(papel, mapaDe(raiz["attributes"]), hora)
        else -> null
    }
}

@Suppress("UNCHECKED_CAST")
private fun statusDe(raiz: Map<String, Any?>): List<Map<String, Any?>> {
    val s = raiz["status"] as? List<*> ?: return emptyList()
    return s.mapNotNull { it as? Map<String, Any?> }
}

@Suppress("UNCHECKED_CAST")
private fun mapaDe(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

private fun codigo(status: List<Map<String, Any?>>, nome: String): Any? =
    status.firstOrNull { (it["code"] as? String)?.equals(nome, true) == true }?.get("value")

private fun attr(attrs: Map<String, Any?>, nome: String): Any? =
    attrs.entries.firstOrNull { it.key.equals(nome, true) }?.value

private fun asDouble(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

/** true/false, 1/0, alarm/normal. Não trata "fall" como booleano. */
private fun asBoolEstrito(v: Any?): Boolean? = when (v) {
    is Boolean -> v
    is Number -> when (v.toInt()) {
        1 -> true
        0 -> false
        else -> null
    }
    is String -> when (v.lowercase()) {
        "true", "1", "alarm", "open", "opened" -> true
        "false", "0", "normal", "close", "closed" -> false
        else -> null
    }
    else -> null
}

private fun doTuya(papel: String, status: List<Map<String, Any?>>, hora: Int): EventoNormalizado? {
    return when (papel) {
        "radar" -> {
            val fall = codigo(status, "fall_state")?.toString()?.lowercase()
            val tempo = asDouble(
                codigo(status, "motionless_time")
                    ?: codigo(status, "stay_time")
                    ?: codigo(status, "imovel_s")
            )
            // "normal" é o radar ocioso — não vira evento, senão o diário enche.
            if (fall == null && tempo == null) return null
            if (fall in setOf("normal", "none", "0", "false")) return null
            val confirmado = fall == "fall" || fall == "1" || fall == "true"
            val imovel = if (confirmado) tempo ?: 15.0 else tempo ?: 0.0
            EventoNormalizado("queda", mapOf("imovel_s" to imovel), hora)
        }
        "gas" -> {
            val det = asBoolEstrito(codigo(status, "gas_sensor_state") ?: codigo(status, "gas_sensor_value"))
                ?: return null
            EventoNormalizado("gas", mapOf("detectado" to det), hora)
        }
        "porta" -> {
            val aberto = asBoolEstrito(
                codigo(status, "doorcontact_state")
                    ?: codigo(status, "door_opened")
                    ?: codigo(status, "switch")
            ) ?: return null
            EventoNormalizado("porta", mapOf("aberta" to aberto), hora)
        }
        "remedio" -> {
            val aberto = asBoolEstrito(
                codigo(status, "doorcontact_state")
                    ?: codigo(status, "door_opened")
                    ?: codigo(status, "switch")
            ) ?: return null
            EventoNormalizado("remedio", mapOf("aberto_hoje" to aberto), hora)
        }
        "leito" -> {
            val min = asDouble(codigo(status, "bed_exit_min") ?: codigo(status, "minutos")) ?: return null
            EventoNormalizado("saida_leito", mapOf("minutos" to min), hora)
        }
        else -> null
    }
}

private fun doMatter(papel: String, attrs: Map<String, Any?>?, hora: Int): EventoNormalizado? {
    if (attrs == null) return null
    return when (papel) {
        "radar" -> {
            val fall = asBoolEstrito(attr(attrs, "FallDetected") ?: attr(attrs, "fall_state"))
            val tempo = asDouble(attr(attrs, "MotionlessSeconds") ?: attr(attrs, "motionless_time"))
            if (fall == null && tempo == null) return null
            if (fall == false && (tempo == null || tempo <= 0.0)) return null
            val imovel = if (fall == true) tempo ?: 15.0 else tempo ?: 0.0
            EventoNormalizado("queda", mapOf("imovel_s" to imovel), hora)
        }
        "gas" -> {
            val det = asBoolEstrito(attr(attrs, "StateValue") ?: attr(attrs, "GasAlarm")) ?: return null
            EventoNormalizado("gas", mapOf("detectado" to det), hora)
        }
        "porta" -> {
            val aberto = asBoolEstrito(attr(attrs, "StateValue")) ?: return null
            EventoNormalizado("porta", mapOf("aberta" to aberto), hora)
        }
        "remedio" -> {
            val aberto = asBoolEstrito(attr(attrs, "StateValue")) ?: return null
            EventoNormalizado("remedio", mapOf("aberto_hoje" to aberto), hora)
        }
        "leito" -> {
            val min = asDouble(attr(attrs, "BedExitMinutes") ?: attr(attrs, "minutos")) ?: return null
            EventoNormalizado("saida_leito", mapOf("minutos" to min), hora)
        }
        else -> null
    }
}
