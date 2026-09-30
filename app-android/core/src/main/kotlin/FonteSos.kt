package br.com.monitoridoso.core

/**
 * Arquitetura em CAMADAS de gatilho do SOS (pedida nos comentários:
 * pulseira/botão físico à prova d'água tipo Telehelp/LifeAlert/FallCall).
 * Regra: sinal FÍSICO (botão externo, ping de pulseira, radar de queda)
 * já É a confirmação — dispara direto. Só a wake word precisa perguntar
 * (voz pode vir da TV).
 */
enum class FonteSos { WAKE_WORD, BOTAO_TELA, BOTAO_EXTERNO, PULSEIRA, QUEDA_RADAR }

object PlanoSos {
    fun requerConfirmacao(fonte: FonteSos): Boolean = fonte == FonteSos.WAKE_WORD

    fun descricao(fonte: FonteSos): String = when (fonte) {
        FonteSos.WAKE_WORD -> "wake word 'socorro' (com confirmação por voz)"
        FonteSos.BOTAO_TELA -> "botão vermelho da tela"
        FonteSos.BOTAO_EXTERNO -> "botão físico/pendente Bluetooth (à prova d'água)"
        FonteSos.PULSEIRA -> "pulseira parceira (ping de SOS)"
        FonteSos.QUEDA_RADAR -> "queda confirmada pelo radar"
    }
}
