package br.com.monitoridoso.core

/**
 * Falas da rotina da manhã, em pt-BR curto. Os MP3 (audio/01_…mp3) têm
 * prioridade quando estão no APK; se o arquivo não veio no repositório, o
 * app lê este texto com o TTS do celular. Mesmos nomes de fluxo_idoso.py.
 */
object FalasRotina {
    val porArquivo: Map<String, String> = linkedMapOf(
        "01_boas_vindas.mp3" to "Olá. Vamos fazer a rotina da manhã. Eu falo e você acompanha.",
        "02_sente.mp3" to "Sente com as costas retas e o rosto virado para o celular. Fique parado.",
        "03_grava_repouso.mp3" to "Agora grave o rosto parado, por cerca de quinze segundos.",
        "04_levante.mp3" to "Pode levantar devagar. Se sentir tontura, sente de novo.",
        "05_grava_pe.mp3" to "Em pé e parado, grave o rosto por mais quinze segundos.",
        "06_urina.mp3" to "Fotografe a urina junto do cartão de cores, com boa luz.",
        "07_agua.mp3" to "A cor está mais escura. Beba água ao longo do dia.",
        "08_feito.mp3" to "Rotina concluída. Obrigado.",
        "09_mensagem_panico.mp3" to "Aqui é o Margarida. A pessoa precisa de ajuda agora. Veja a mensagem no celular."
    )

    fun falaDe(arquivo: String): String = porArquivo[arquivo] ?: arquivo.substringBefore('.')
}
