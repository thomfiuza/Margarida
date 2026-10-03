package br.com.monitoridoso

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Escuta pt-BR pelo reconhecedor do sistema. É o caminho quando a AccessKey
 * do Porcupine ainda não foi colada, e também a confirmação de "não" / "sim".
 * Gasta mais bateria que o Porcupine; o modo 24/7 de baixa potência continua
 * sendo a palavra treinada "socorro".
 */
class EscutaFala(
    private val ctx: Context,
    private val continuo: Boolean,
    private val aoTexto: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var ligada = false

    fun disponivel(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun iniciar() {
        if (!disponivel()) return
        ligada = true
        main.post { ciclo() }
    }

    fun parar() {
        ligada = false
        main.post {
            try {
                rec?.destroy()
            } catch (_: Exception) {
            }
            rec = null
        }
    }

    private fun ciclo() {
        if (!ligada) return
        try {
            rec?.destroy()
        } catch (_: Exception) {
        }
        rec = SpeechRecognizer.createSpeechRecognizer(ctx).also { r ->
            r.setRecognitionListener(ouvinte())
            try {
                r.startListening(intentReconhecimento())
            } catch (_: Exception) {
                if (continuo && ligada) main.postDelayed({ ciclo() }, 800)
            }
        }
    }

    private fun intentReconhecimento() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    private fun textos(bundle: Bundle?): List<String> =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()

    private fun ouvinte() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            if (continuo && ligada) main.postDelayed({ ciclo() }, 600)
        }
        override fun onResults(results: Bundle?) {
            textos(results).forEach(aoTexto)
            if (continuo && ligada) main.post { ciclo() }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            textos(partialResults).forEach(aoTexto)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
