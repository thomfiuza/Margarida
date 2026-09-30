package br.com.monitoridoso.core

/**
 * Máquina de estados da ROTINA POR VOZ dentro do APK (Estação 7) — pura.
 *
 * O serviço Android (RotinaVozService) faz o papel de boca e ouvidos:
 * lê [fala] no TextToSpeech e entrega o que o microfone ouviu em [ouvir];
 * as medições de FC pela câmera chegam por [informarFc]. Ao chegar em
 * RESUMO, o serviço passa fcSentada/fcEmPe/tontura/urina para
 * MonitorIdosoCore.registrarRotina() — mesmas regras já testadas.
 */
enum class PassoRotina {
    ABERTURA, FC_SENTADA, ORTOSTATICA, TONTURA, URINA, RESUMO, FIM, ABORTADA
}

class MaquinaRotina(
    private val nome: String,
    /** Modo Alzheimer/demência (pedido de Edna e Rafael nos comentários):
     *  menos perguntas, mais observação — só FC + resumo. */
    private val simplificada: Boolean = false
) {
    var passo: PassoRotina = PassoRotina.ABERTURA; private set
    var fcSentada: Double? = null; private set
    var fcEmPe: Double? = null; private set
    var tontura: Boolean? = null; private set
    var urina: Int? = null; private set

    /** O que o TTS deve dizer agora. */
    fun fala(): String = when (passo) {
        PassoRotina.ABERTURA ->
            "Olá, $nome. Vamos fazer a conferência rápida de saúde? Diga começar para iniciar, ou depois para deixar para mais tarde."
        PassoRotina.FC_SENTADA ->
            "Sente-se confortável e coloque o dedo na câmera, com a luz acesa. Fique parada por trinta segundos."
        PassoRotina.ORTOSTATICA ->
            "Agora levante-se devagar, fique um minuto em pé e coloque o dedo na câmera por trinta segundos. Se sentir insegurança, sente-se e diga parar."
        PassoRotina.TONTURA ->
            "Você sentiu tontura ou fraqueza ao ficar em pé? Responda sim ou não."
        PassoRotina.URINA ->
            "Se já foi ao banheiro, compare a cor da urina com o cartão e me diga o número, de um a dez. Senão, diga pular."
        PassoRotina.RESUMO -> resumo()
        PassoRotina.FIM ->
            "Conferência concluída. Guardei tudo no diário. Tenha um ótimo dia!"
        PassoRotina.ABORTADA ->
            "Tudo bem, tento mais tarde. Se precisar de algo, é só chamar socorro."
    }

    /**
     * Entrega o que o microfone ouviu. Retorna true se entendeu
     * (avançou, repetiu ou abortou); false se o serviço deve dizer
     * "não entendi" e repetir a fala.
     */
    fun ouvir(texto: String): Boolean {
        val t = texto.lowercase()
        if (t.contains("repet")) return true                       // serviço relê a fala
        if (t.split(Regex("[^\\p{L}\\p{N}]+")).any { it in setOf("parar", "pare", "depois", "sair") } ||
            t.contains("agora nao") || t.contains("agora não")
        ) {
            passo = PassoRotina.ABORTADA
            return true
        }
        val pula = t.split(Regex("[^\\p{L}\\p{N}]+")).contains("pular")

        return when (passo) {
            PassoRotina.ABERTURA -> when {
                WakeWordGate.pareceSim(t) || t.contains("comec") || t.contains("começ") || t.contains("vamos") -> {
                    passo = PassoRotina.FC_SENTADA; true
                }
                WakeWordGate.pareceNao(t) -> { passo = PassoRotina.ABORTADA; true }
                else -> false
            }
            PassoRotina.FC_SENTADA -> false                        // só avança via informarFc
            PassoRotina.ORTOSTATICA -> if (pula) { passo = PassoRotina.TONTURA; true } else false
            PassoRotina.TONTURA -> when {
                WakeWordGate.pareceSim(t) -> { tontura = true; passo = PassoRotina.URINA; true }
                WakeWordGate.pareceNao(t) -> { tontura = false; passo = PassoRotina.URINA; true }
                else -> false
            }
            PassoRotina.URINA -> when {
                pula -> { passo = PassoRotina.RESUMO; true }
                else -> numeroUrina(t)?.let { n -> urina = n; passo = PassoRotina.RESUMO; true } ?: false
            }
            PassoRotina.RESUMO -> {
                passo = PassoRotina.FIM; true
            }
            PassoRotina.FIM, PassoRotina.ABORTADA -> true
        }
    }

    /** Resultado da medição rPPG da câmera. true se era o passo esperado. */
    fun informarFc(bpm: Double): Boolean = when (passo) {
        PassoRotina.FC_SENTADA -> { fcSentada = bpm; passo = PassoRotina.ORTOSTATICA; true }
        PassoRotina.ORTOSTATICA -> {
            fcEmPe = bpm
            passo = if (simplificada) PassoRotina.RESUMO else PassoRotina.TONTURA
            true
        }
        else -> false
    }

    private fun resumo(): String {
        val dif = if (fcSentada != null && fcEmPe != null) (fcEmPe!! - fcSentada!!).toInt() else null
        val s = StringBuilder("Conferência concluída. ")
        if (fcSentada != null) s.append("Coração sentado ${fcSentada!!.toInt()}. ")
        if (fcEmPe != null) s.append("Em pé ${fcEmPe!!.toInt()}, diferença ${dif}. ")
        s.append(
            when (tontura) {
                true -> "Sentiu tontura ao levantar: vou marcar atenção no diário. "
                false -> "Sem tontura. "
                null -> ""
            }
        )
        s.append(
            when (urina) {
                null -> "Cor da urina não informada hoje."
                else -> "Cor da urina $urina no cartão."
            }
        )
        return s.toString()
    }

    companion object {
        private val EXTENSO = mapOf(
            "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "três" to 3,
            "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8,
            "nove" to 9, "dez" to 10
        )

        /** Aceita "três", "3", "cor oito" etc. Somente 1..10. */
        fun numeroUrina(t: String): Int? {
            for (tok in t.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))) {
                EXTENSO[tok]?.let { return it }
                tok.toIntOrNull()?.let { if (it in 1..10) return it }
            }
            return null
        }
    }
}
