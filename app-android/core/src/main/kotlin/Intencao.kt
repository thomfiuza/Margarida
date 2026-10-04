package br.com.monitoridoso.core

/**
 * Camada semântica pós-ativação — pura e testável (mesma filosofia do
 * WakeWordGate). Depois que a wake word acorda o app, o que o microfone
 * ouvir é classificado aqui: "eu caí", "me ajuda", "não consigo levantar"
 * convergem para EMERGENCIA sem depender da palavra exata "socorro".
 *
 * Arquitetura híbrida (como sugerido pela comunidade técnica do post 1):
 *   1ª camada: keyword spotting offline (Porcupine) — leve, privada;
 *   2ª camada: esta — intenção por padrões pt-BR, sem nuvem, sem LLM.
 */
enum class Intencao { EMERGENCIA, EMERGENCIA_CLINICA, SIM, NAO, REPETIR, PARAR, OUTRA }

object Intencoes {

    private fun tokens(t: String) = t.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))

    // sinais clínicos -> SAMU entra PRIMEIRO na fila de ligações
    private val CLINICA_FRASES = listOf(
        "dor no peito", "dor toracica", "dor torácica", "aperto no peito",
        "coracao apertado", "coração apertado", "dor forte no peito"
    )

    // frases que NÃO dependem da palavra "socorro"
    private val EMERGENCIA_FRASES = listOf(
        "me ajuda", "me ajude", "ajuda eu",
        "eu cai", "eu caí", "cai no", "caí no", "caiu no banheiro",
        "nao consigo levantar", "não consigo levantar",
        "nao consigo me levantar", "não consigo me levantar",
        "me machuquei", "machuquei", "estou machucada", "to machucado",
        "dor forte",
        "passando mal", "passo mal", "to mal", "estou mal",
        "chama alguem", "chame alguem", "preciso de ajuda"
    )

    fun classificar(texto: String): Intencao {
        val t = texto.lowercase()
        if (CLINICA_FRASES.any { t.contains(it) }) return Intencao.EMERGENCIA_CLINICA
        if (EMERGENCIA_FRASES.any { t.contains(it) }) return Intencao.EMERGENCIA
        val tk = tokens(t)
        if (tk.any { it in setOf("socorro", "emergencia", "emergência") }) return Intencao.EMERGENCIA
        if (t.contains("repet")) return Intencao.REPETIR
        if (tk.any { it in setOf("parar", "pare", "cancela", "cancelar") }) return Intencao.PARAR
        if (WakeWordGate.pareceSim(t)) return Intencao.SIM
        if (WakeWordGate.pareceNao(t)) return Intencao.NAO
        return Intencao.OUTRA
    }

    /**
     * Ideia da Mariane (enfermeira): sinal clínico urgente chama o SAMU
     * ANTES da família. Nos demais casos, a fila da família é respeitada.
     */
    fun ordemChamadas(contatos: List<Contato>, intencao: Intencao): List<Contato> =
        if (intencao == Intencao.EMERGENCIA_CLINICA)
            listOf(Contato("SAMU", "192")) + contatos
        else contatos
}
