package br.com.monitoridoso.core

/**
 * Módulo de REMÉDIOS (ideia da Thais Caroline nos comentários + padrão de
 * mercado COCO/Nonno): família cadastra, o app lembra por voz, o idoso
 * confirma com um toque. Sem confirmação na janela → cuidador avisado;
 * 2 faltas seguidas → URGENTE.
 * Puro e testável; o Android só agenda o TTS e recebe o toque.
 */
object LembretesMed {

    /**
     * [horaPrevista] hora do remédio; [horaAgora] hora atual; [janelaH]
     * tolerância em horas; [faltasAnteriores] quantas faltas seguidas já
     * vinham antes desta checagem.
     */
    fun checar(
        nome: String,
        horaPrevista: Int,
        horaAgora: Int,
        confirmado: Boolean,
        faltasAnteriores: Int = 0,
        janelaH: Int = 2
    ): DecisaoSensor? {
        if (horaAgora < horaPrevista + janelaH) return null      // ainda na janela
        if (confirmado) return DecisaoSensor(Nivel.NENHUM, "registrar", "$nome confirmado.")
        val faltas = faltasAnteriores + 1
        return if (faltas >= 2)
            DecisaoSensor(Nivel.URGENTE, "notificar",
                "$nome sem confirmação pela ${faltas}ª vez seguida — ligar para conferir.")
        else
            DecisaoSensor(Nivel.ATENCAO, "notificar",
                "$nome não confirmado na janela das ${horaPrevista}h — lembrar de novo.")
    }
}
