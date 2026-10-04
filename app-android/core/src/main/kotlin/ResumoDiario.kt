package br.com.monitoridoso.core

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Resumo semanal para o cuidador, gerado 100% NO APARELHO, sem nuvem:
 * é o "relatório de IA" que vimos no benchmark (Nonno), feito do jeito
 * Margarida: regras transparentes, nenhum dado saindo do celular.
 * O texto é quente e curto, pra família ler no café, não um dashboard frio.
 */
object ResumoSemana {
    fun gerar(diario: Diario, dias: Int = 7): String {
        val corte = LocalDateTime.now().minus(dias.toLong(), ChronoUnit.DAYS)
        val evs = diario.todos().filter { dentroDaJanela(it.quando, corte) }
        val sos = evs.count { it.tipo == "emergencia" && it.nivel == Nivel.URGENTE }
        val rotOk = evs.count { it.tipo == "rotina" && it.nivel == Nivel.NENHUM }
        val rotAt = evs.count { it.tipo == "rotina" && it.nivel != Nivel.NENHUM }
        val noites = evs.filter { it.tipo == "noite" }
        val noitesOk = noites.count { it.nivel == Nivel.NENHUM }

        val sb = StringBuilder()
        sb.append("Resumo dos ultimos $dias dias: ")
        sb.append(if (sos == 0) "nenhum SOS disparado." else "$sos SOS disparado(s).")
        sb.append(" Rotinas: $rotOk tranquilas, $rotAt com ponto de atencao.")
        sb.append(" Noites: $noitesOk tranquilas de ${noites.size}.")
        val atencoes = evs.filter { it.nivel != Nivel.NENHUM }.takeLast(3)
        if (atencoes.isNotEmpty()) {
            sb.append(" Ultimos pontos de atencao: ")
            sb.append(atencoes.joinToString("; ") { it.mensagem })
        }
        return sb.toString()
    }

    private fun dentroDaJanela(quando: String, corte: LocalDateTime): Boolean =
        runCatching {
            LocalDateTime.parse(quando.take(19)).isAfter(corte)
        }.getOrDefault(false)
}
