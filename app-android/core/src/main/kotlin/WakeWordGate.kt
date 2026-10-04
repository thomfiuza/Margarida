package br.com.monitoridoso.core

/**
 * O "cérebro" da wake word "socorro" — puro, testável na JVM.
 *
 * O serviço Android (SocorroDetector) só escuta e EXECUTA a [AcaoWake]
 * devolvida aqui: falar a pergunta, disparar o SOS, registrar no diário.
 * Toda a política anti-falso-positivo vive nesta classe:
 *
 *  - janela de confirmação ("você pediu socorro?") antes de ligar;
 *  - silêncio = NÃO (na dúvida, nunca liga);
 *  - debounce entre disparos (TV/novela dizendo "socorro" não re-dispara);
 *  - modo direto opcional, para o cuidador que prefere zero confirmação.
 */
enum class AcaoWake { NADA, PERGUNTAR, DISPARAR_SOS, CANCELAR }

class WakeWordGate(
    private val janelaMs: Long = 6_000,
    private val debounceMs: Long = 10_000,
    var modoDireto: Boolean = false
) {
    private var perguntandoDesde: Long? = null
    private var bloqueadoAte: Long = 0

    val perguntando: Boolean get() = perguntandoDesde != null

    /** O detector ouviu "socorro" (Porcupine ou fallback de voz). */
    fun aoDetectar(agoraMs: Long): AcaoWake {
        if (agoraMs < bloqueadoAte) return AcaoWake.NADA
        if (perguntando) return AcaoWake.NADA
        if (modoDireto) {
            bloqueadoAte = agoraMs + debounceMs
            return AcaoWake.DISPARAR_SOS
        }
        perguntandoDesde = agoraMs
        return AcaoWake.PERGUNTAR
    }

    /** O microfone respondeu durante a janela de confirmação. */
    fun aoResponder(texto: String, agoraMs: Long): AcaoWake {
        val desde = perguntandoDesde ?: return AcaoWake.NADA
        if (agoraMs - desde > janelaMs) return aoExpirar(agoraMs)
        perguntandoDesde = null
        bloqueadoAte = agoraMs + debounceMs
        return when (Intencoes.classificar(texto)) {
            // "eu caí", "me ajuda", "dor no peito" disparam MESMO na confirmação
            Intencao.SIM, Intencao.EMERGENCIA, Intencao.EMERGENCIA_CLINICA -> AcaoWake.DISPARAR_SOS
            else -> AcaoWake.CANCELAR
        }
    }

    /** Serviço chama quando a janela fecha sem resposta. Silêncio = não. */
    fun aoExpirar(agoraMs: Long): AcaoWake {
        if (perguntandoDesde == null) return AcaoWake.NADA
        perguntandoDesde = null
        bloqueadoAte = agoraMs + debounceMs
        return AcaoWake.CANCELAR
    }

    companion object {
        private val SIM = setOf("sim", "yes", "ajuda", "socorro", "podes")
        private val NAO = setOf("nao", "não", "cancela", "cancelar", "para", "pare", "engano", "nada")

        fun pareceSim(t: String): Boolean {
            if (t.contains("pode ligar")) return true
            return tokens(t).any { it in SIM }
        }

        fun pareceNao(t: String): Boolean = tokens(t).any { it in NAO }

        // \\W não é unicode-aware em Java: "não" viraria ["n","o"].
        // Separa por qualquer coisa que NÃO seja letra/número unicode.
        private fun tokens(t: String) = t.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
    }
}
