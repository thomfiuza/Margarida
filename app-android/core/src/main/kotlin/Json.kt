package br.com.monitoridoso.core

/**
 * JSON mínimo (parse + escrita) sem dependências — o app Android e o protótipo
 * Python usam o MESMO formato de Evento no diário.
 */
object Json {
    fun stringify(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> v.toString()
        is Int, is Long -> v.toString()
        is Float, is Double -> {
            val d = (v as Number).toDouble()
            if (d == Math.floor(d) && !d.isInfinite() && kotlin.math.abs(d) < 1e15)
                d.toLong().toString() else d.toString()
        }
        is Number -> v.toString()
        is String -> "\"" + esc(v) + "\""
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") {
            "\"${esc(it.key.toString())}\":${stringify(it.value)}"
        }
        is List<*> -> v.joinToString(",", "[", "]") { stringify(it) }
        else -> "\"" + esc(v.toString()) + "\""
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            return when {
                s[i] == '{' -> obj()
                s[i] == '[' -> arr()
                s[i] == '"' -> str()
                s.startsWith("true", i) -> { i += 4; true }
                s.startsWith("false", i) -> { i += 5; false }
                s.startsWith("null", i) -> { i += 4; null }
                else -> num()
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); check(s[i] == ':') { "':' esperado em $i" }; i++
                m[k] = value(); ws()
                if (s[i] == ',') i++ else break
            }
            check(s[i] == '}') { "'}' esperado em $i" }; i++
            return m
        }
        fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(value()); ws()
                if (s[i] == ',') i++ else break
            }
            check(s[i] == ']') { "']' esperado em $i" }; i++
            return l
        }
        fun str(): String {
            check(s[i] == '"'); i++
            val sb = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (s[i]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                    }
                } else sb.append(s[i])
                i++
            }
            i++
            return sb.toString()
        }
        fun num(): Any {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
            val txt = s.substring(st, i)
            // inteiro volta Int/Long (não Double) — senão round-trip quebra igualdade
            if (txt.none { it in ".eE" }) {
                val l = txt.toLong()
                return if (l >= Int.MIN_VALUE && l <= Int.MAX_VALUE) l.toInt() else l
            }
            return txt.toDouble()
        }
    }
}
