package br.com.monitoridoso.core

import java.io.File
import java.time.LocalDate

/** Diário único (JSONL) — 1 linha = 1 evento. Espelho do Diário de orquestrador.py. */
class Diario(val path: File) {
    fun registrar(ev: Evento) {
        path.parentFile?.mkdirs()
        path.appendText(ev.json() + "\n")
    }

    fun todos(): List<Evento> =
        if (!path.exists()) emptyList()
        else path.readLines().filter { it.isNotBlank() }.map { Evento.doJson(it) }

    fun doTipo(tipo: String, n: Int? = null): List<Evento> {
        val evs = todos().filter { it.tipo == tipo }
        return if (n != null) evs.takeLast(n) else evs
    }

    fun temRotinaHoje(): Boolean {
        val hoje = LocalDate.now().toString()
        return doTipo("rotina").any { it.quando.startsWith(hoje) }
    }
}

/**
 * Núcleo de negócio do app — o equivalente Kotlin de MonitorIdoso (Python).
 * As medições (rPPG/urina/BCG) chegam prontas das engines; aqui vivem as
 * regras, o diário, o contexto de saúde e o SOS.
 */
class MonitorIdosoCore(
    val nome: String,
    val contatos: List<Contato>,
    diarioPath: File
) {
    val diario = Diario(diarioPath)

    // ---------- ROTINA DIURNA ----------
    fun registrarRotina(
        fcRepouso: Double, fcPe: Double, urina: Int?, tontura: Boolean,
        avisosUrina: List<String> = emptyList()
    ): Evento {
        val tendencias = tendenciasRotina(diario.doTipo("rotina"), fcRepouso, urina)
        val avisos = avaliarOrtostatica(fcRepouso, fcPe, tontura) + avisosUrina + tendencias
        val nivel = nivelAlerta(avisos, urina)
        val msg = if (avisos.isEmpty())
            "Rotina ok: FC repouso ${fcRepouso.toInt()} bpm, em pé ${fcPe.toInt()} bpm" +
                (urina?.let { ", urina nível $it" } ?: ".")
        else avisos.joinToString("; ")
        val ev = Evento("rotina", nivel, msg, dados = mapOf(
            "fc_repouso" to fcRepouso, "fc_pe" to fcPe, "urina" to urina, "tontura" to tontura
        ))
        diario.registrar(ev)
        return ev
    }

    // ---------- NOITE (pad BCG) ----------
    fun registrarNoite(fc: Double, fr: Double, dados: Map<String, Any?> = emptyMap()): Evento {
        val noitesOk = diario.doTipo("noite", 7).filter { (it.dados["ok"] as? Boolean) == true }
        val baseFc = noitesOk.mapNotNull { (it.dados["fc_bpm"] as? Number)?.toDouble() }
        val baseFr = noitesOk.mapNotNull { (it.dados["fr_irpm"] as? Number)?.toDouble() }
        val (nivel, avisos) = avaliarNoite(fc, fr, baseFc, baseFr)
        val msg = if (avisos.isNotEmpty()) avisos.joinToString("; ")
        else "Noite estavel: FC ${fc.toInt()} bpm, FR ${fr.toInt()} irpm."
        val ev = Evento("noite", nivel, msg, dados = dados + mapOf("fc_bpm" to fc, "fr_irpm" to fr))
        diario.registrar(ev)
        return ev
    }

    // ---------- EMERGÊNCIA ----------
    fun emergencia(
        localizacao: Localizacao?, chamar: GatewayChamada, sms: GatewaySms,
        confirmar: (() -> Boolean)? = null,
        audioPath: String? = "audio/09_mensagem_panico.mp3"
    ): Evento {
        val rel = acionarPanico(nome, contatos, chamar, sms, audioPath, localizacao,
            confirmar, contextoSaude())
        if (rel.status != "acionado")
            return Evento("emergencia", Nivel.NENHUM, "SOS nao disparado (${rel.status}).")
        val atendidos = rel.tentativas.filter { it.atendeu }.map { it.nome }
        val msg = "SOS disparado: ${rel.tentativas.size} ligacoes, " +
            "atendeu: ${if (atendidos.isEmpty()) "ninguem" else atendidos.joinToString(", ")}; " +
            "SMS para ${rel.smsEnviados.size} contatos."
        val ev = Evento("emergencia", Nivel.URGENTE, msg, dados = mapOf(
            "tentativas" to rel.tentativas.map { mapOf("nome" to it.nome, "atendeu" to it.atendeu) },
            "sms" to rel.mensagemSms
        ))
        diario.registrar(ev)
        return ev
    }

    // ---------- SENSORES (radar/queda, gás, porta, remédio) ----------
    fun registrarEventoSensor(
        tipo: String, payload: Map<String, Any?>, hora: Int,
        chamar: GatewayChamada? = null, sms: GatewaySms? = null,
        localizacao: Localizacao? = null, confirmar: (() -> Boolean)? = null
    ): Evento {
        val decisao = decidirEvento(tipo, payload, hora)
        val dados = mutableMapOf<String, Any?>(
            "sensor" to tipo, "payload" to payload, "acao" to decisao.acao
        )
        var nivel = decisao.nivel
        var mensagem = decisao.mensagem
        if (decisao.acao == "sos" && chamar != null && sms != null) {
            val sos = emergencia(localizacao, chamar, sms, confirmar)
            dados["sos"] = sos.mensagem
            nivel = Nivel.URGENTE
            mensagem = "$mensagem ${sos.mensagem}"
        }
        val ev = Evento("sensor", nivel, mensagem, dados = dados)
        diario.registrar(ev)
        return ev
    }

    // ---------- INATIVIDADE (só software — sem hardware) ----------
    fun checarInatividade(
        agora: java.time.LocalDateTime, limiteH: Int = 10, limiteM: Int = 0
    ): Evento? {
        val hoje = agora.toLocalDate().toString()
        if (agora.hour < limiteH || (agora.hour == limiteH && agora.minute < limiteM))
            return null
        val temRotina = diario.doTipo("rotina").any { it.quando.startsWith(hoje) }
        val temAlerta = diario.doTipo("inatividade").any { it.quando.startsWith(hoje) }
        if (temRotina || temAlerta) return null
        val ev = Evento(
            "inatividade", Nivel.ATENCAO,
            "$nome ainda não fez a rotina da manhã " +
                "(limite ${String.format("%02d", limiteH)}h${String.format("%02d", limiteM)}) " +
                "— ligar para verificar.",
            quando = agora.withNano(0).toString()
        )
        diario.registrar(ev)
        return ev
    }

    // ---------- O QUE AMARRA TUDO ----------
    fun contextoSaude(dias: Int = 7): String {
        val partes = mutableListOf<String>()
        val ultRot = diario.doTipo("rotina").lastOrNull()
        if (ultRot != null && ultRot.nivel != Nivel.NENHUM)
            partes.add("ultimo alerta da rotina: ${ultRot.mensagem.take(90)}")
        val noites = diario.doTipo("noite", dias).filter { it.nivel != Nivel.NENHUM }
        if (noites.isNotEmpty())
            partes.add("${noites.size} noites com sinal de alerta, a mais recente: ${noites.last().mensagem.take(90)}")
        return if (partes.isEmpty()) "sem alertas recentes registrados." else partes.joinToString("; ")
    }

    fun resumoDoDia(): Map<String, Any?> {
        val hoje = LocalDate.now().toString()
        val evs = diario.todos().filter { it.quando.startsWith(hoje) }
        val pior = piorNivel(evs.map { it.nivel })
        val texto = if (evs.isEmpty()) "Nenhum evento em $hoje."
        else "Resumo de $hoje — $nome (${pior.name.lowercase()}):\n" +
            evs.joinToString("\n") { "[${it.nivel.name}] ${it.tipo}: ${it.mensagem}" }
        return mapOf("data" to hoje, "eventos" to evs.size,
            "pior_nivel" to pior.name.lowercase(), "texto" to texto)
    }
}
