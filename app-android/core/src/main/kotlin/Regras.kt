package br.com.monitoridoso.core

/**
 * Regras clínicas de bem-estar — portes fieis de fluxo_idoso.py e
 * orquestrador.py (mesmos textos, mesmos limiares).
 */
fun avaliarOrtostatica(fcRepouso: Double, fcPe: Double, tontura: Boolean): List<String> {
    val avisos = mutableListOf<String>()
    val delta = fcPe - fcRepouso
    if (tontura && delta < 10) {
        avisos.add(
            "Tontura ao levantar SEM a subida esperada da frequência cardíaca: " +
                "padrão compatível com hipotensão ortostática — levar ao médico."
        )
    }
    if (delta > 30) {
        avisos.add(
            "Subida exagerada da frequência ao ficar em pé (>30 bpm): possível " +
                "depleção de volume/desidratação — reforçar líquidos e reavaliar."
        )
    }
    if (fcRepouso > 100) avisos.add("FC de repouso acima de 100 bpm: registrar e comentar com o médico.")
    return avisos
}

/**
 * Tendências contra a linha de base pessoal — espelho de fluxo_idoso.py (últimos 7 FC,
 * últimos 3 níveis de urina), calculado ANTES de registrar o dia atual.
 */
fun tendenciasRotina(rotinasAnteriores: List<Evento>, fcRepouso: Double, urina: Int?): List<String> {
    val tendencia = mutableListOf<String>()
    val baseFc = rotinasAnteriores.takeLast(7).mapNotNull { ev ->
        (ev.dados["fc_repouso"] as? Number)?.toDouble()
    }
    if (baseFc.isNotEmpty() && fcRepouso - baseFc.average() >= 10) {
        val delta = (fcRepouso - baseFc.average()).toInt()
        tendencia.add("FC de repouso $delta bpm acima da sua média recente.")
    }
    if (urina != null) {
        val prev = rotinasAnteriores.takeLast(3).mapNotNull { ev ->
            (ev.dados["urina"] as? Number)?.toInt()
        }
        if (prev.isNotEmpty() && urina - prev.average() >= 2) {
            tendencia.add("Urina bem mais escura que nos últimos registros.")
        }
    }
    return tendencia
}

fun nivelAlerta(avisos: List<String>, nivelUrina: Int?): Nivel {
    val urgente = avisos.any { it.contains("hipotensão ortostática") || it.contains("médico") } ||
        (nivelUrina != null && nivelUrina >= 7)
    val atencao = avisos.isNotEmpty() || (nivelUrina != null && nivelUrina >= 5)
    return when {
        urgente -> Nivel.URGENTE
        atencao -> Nivel.ATENCAO
        else -> Nivel.NENHUM
    }
}

/** Tendências noturnas do BCG — limiares idênticos aos de orquestrador.py. */
fun avaliarNoite(
    fc: Double, fr: Double, baseFc: List<Double>, baseFr: List<Double>
): Pair<Nivel, List<String>> {
    if (baseFc.isEmpty()) return Nivel.NENHUM to emptyList()
    val dFc = fc - baseFc.average()
    val dFr = if (baseFr.isNotEmpty()) fr - baseFr.average() else 0.0
    val nivel = when {
        dFc >= 15 || dFr >= 6 -> Nivel.URGENTE
        dFc >= 8 || dFr >= 3 -> Nivel.ATENCAO
        else -> Nivel.NENHUM
    }
    val avisos = mutableListOf<String>()
    if (dFc >= 8) avisos.add("FC noturna ${dFc.toInt()} bpm acima da media dos ultimos dias.")
    if (dFr >= 3) avisos.add("Respiracao noturna ${dFr.toInt()} irpm acima da media (possivel taquipneia).")
    return nivel to avisos
}
