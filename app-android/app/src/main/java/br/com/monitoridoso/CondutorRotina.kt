package br.com.monitoridoso

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import br.com.monitoridoso.core.MaquinaRotina
import br.com.monitoridoso.core.MonitorIdosoCore
import br.com.monitoridoso.core.PassoRotina
import java.util.Locale

/**
 * Conduz a ROTINA POR VOZ dentro do APK (Estação 7): boca = TextToSpeech,
 * ouvidos = SpeechRecognizer, cérebro = MaquinaRotina (:core, testada 12/12),
 * pulso = FcCamera + Pulso.estimarBpm (:core).
 *
 * Ao chegar em RESUMO grava monitor.registrarRotina(...) — mesmas regras de
 * alerta já testadas (ortostática, urina, tontura).
 */
class CondutorRotina(
    private val activity: AppCompatActivity,
    private val monitor: MonitorIdosoCore,
    private val status: TextView
) {
    private val mao = Handler(Looper.getMainLooper())
    private val maq = MaquinaRotina(monitor.nome)
    private var tts: TextToSpeech? = null
    private var pronto = false
    private var recognizer: SpeechRecognizer? = null
    private var ativo = false

    fun iniciar() {
        if (ativo) return
        ativo = true
        if (pronto) { avancar() } else {
            tts = TextToSpeech(activity) { st ->
                pronto = st == TextToSpeech.SUCCESS
                if (pronto) {
                    tts?.language = Locale("pt", "BR")
                    avancar()
                } else {
                    status.text = "Voz indisponível neste aparelho."
                    ativo = false
                }
            }
        }
    }

    fun destruir() {
        ativo = false
        try { recognizer?.stopListening(); recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
    }

    // ---------- fluxo ----------
    private fun avancar() {
        if (!ativo) return
        when (maq.passo) {
            PassoRotina.FC_SENTADA -> medir { bpm ->
                if (bpm == null) desistirMedicao() else { maq.informarFc(bpm); avancar() }
            }
            PassoRotina.ORTOSTATICA -> {
                falar(maq.fala()) {
                    mao.postDelayed({
                        medir { bpm ->
                            if (bpm == null) desistirMedicao() else { maq.informarFc(bpm); avancar() }
                        }
                    }, 60_000)   // 1 minuto em pé antes da 2ª medida
                }
            }
            PassoRotina.RESUMO -> {
                val ev = monitor.registrarRotina(
                    maq.fcSentada ?: 0.0, maq.fcEmPe ?: 0.0,
                    urina = maq.urina, tontura = maq.tontura ?: false
                )
                falar(maq.fala() + " " + ev.mensagem) { escutar() }
            }
            PassoRotina.FIM, PassoRotina.ABORTADA -> {
                falar(maq.fala()) { ativo = false }
            }
            else -> falar(maq.fala()) { escutar() }   // ABERTURA, TONTURA, URINA
        }
    }

    private fun desistirMedicao() {
        ativo = false
        falar("Não consegui medir o pulso agora. Vamos deixar a conferência para logo mais.", null)
    }

    private fun medir(cb: (Double?) -> Unit) {
        status.text = "Medindo pulso pela câmera... cubra a lente com o dedo."
        FcCamera.medir(activity, activity, 30, cb)
    }

    // ---------- voz ----------
    private fun falar(texto: String, aoTerminar: (() -> Unit)?) {
        mao.post {
            status.text = texto
            Log.i("Rotina", "TTS: $texto")
            if (!pronto) { aoTerminar?.invoke(); return@post }
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { mao.post { aoTerminar?.invoke() } }
                @Deprecated("deprecated") override fun onError(id: String?) { mao.post { aoTerminar?.invoke() } }
            })
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "rotina")
        }
    }

    private fun escutar() {
        mao.post {
            if (!ativo) return@post
            try {
                val r = SpeechRecognizer.createSpeechRecognizer(activity)
                recognizer = r
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                }
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(res: android.os.Bundle) {
                        val texto = res.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull() ?: ""
                        tratar(texto)
                    }
                    override fun onError(e: Int) { tratar("") }   // silêncio: repete a fala
                    override fun onReadyForSpeech(p: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(v: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(p: android.os.Bundle?) {}
                    override fun onEvent(t: Int, p: android.os.Bundle?) {}
                })
                r.startListening(intent)
            } catch (e: Exception) {
                Log.w("Rotina", "escuta indisponível: ${e.message}")
            }
        }
    }

    private fun tratar(texto: String) {
        if (!ativo) return
        val entendeu = maq.ouvir(texto)
        if (!entendeu) {
            falar("Não entendi. " + maq.fala()) { escutar() }
        } else {
            avancar()
        }
    }
}
