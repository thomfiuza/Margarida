package br.com.monitoridoso.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * SOS — porte fiel de panico.py (5/5 testes lá, espelhados em TestesCore.kt).
 * Gateways são interfaces: o Android injeta a ROTA A (nuvem/Twilio) ou a
 * ROTA B (chamadas nativas + SmsManager); a lógica aqui é a mesma.
 */
interface GatewayChamada {
    /** true = contato ATENDEU. Implementação decide como (nuvem ou nativa). */
    fun ligar(telefone: String, audioPath: String?): Boolean
}

interface GatewaySms {
    fun enviar(telefone: String, texto: String): Boolean
}

data class Tentativa(val nome: String, val telefone: String, val atendeu: Boolean)

class RelatorioPanico(val status: String, val localizacao: Localizacao? = null) {
    val tentativas = mutableListOf<Tentativa>()
    val smsEnviados = mutableListOf<String>()
    val smsFalhas = mutableListOf<String>()
    var mensagemSms = ""
    val alguemAtendeu: Boolean get() = tentativas.any { it.atendeu }
}

fun montarMensagemSms(nomeIdoso: String, loc: Localizacao?, contexto: String? = null): String {
    val quando = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    val cab = "ALERTA DE EMERGENCIA de $nomeIdoso em $quando: " +
        "acionou o botao de socorro e esta passando mal."
    var corpo = if (loc == null) cab else "$cab ${loc.resumo()}"
    if (loc == null) corpo += " Localizacao indisponivel no momento."
    if (!contexto.isNullOrEmpty()) corpo += " Contexto de saude: $contexto"
    return corpo
}

fun acionarPanico(
    nomeIdoso: String,
    contatos: List<Contato>,
    chamar: GatewayChamada,
    sms: GatewaySms,
    audioPath: String? = null,
    localizacao: Localizacao? = null,
    confirmar: (() -> Boolean)? = null,
    contexto: String? = null
): RelatorioPanico {
    // 1) confirmação rápida contra falso positivo do wake word
    if (confirmar != null && !confirmar()) return RelatorioPanico("cancelado")
    if (contatos.isEmpty()) return RelatorioPanico("sem_contatos")

    val rel = RelatorioPanico("acionado", localizacao)
    // 2) ligações EM SEQUÊNCIA, uma por uma; atendeu ou não, segue para a próxima
    for (c in contatos) {
        rel.tentativas.add(Tentativa(c.nome, c.telefone, chamar.ligar(c.telefone, audioPath)))
    }
    // 3) SMS com localização (e contexto de saúde) para TODOS
    rel.mensagemSms = montarMensagemSms(nomeIdoso, localizacao, contexto)
    for (c in contatos) {
        (if (sms.enviar(c.telefone, rel.mensagemSms)) rel.smsEnviados else rel.smsFalhas)
            .add(c.telefone)
    }
    return rel
}
