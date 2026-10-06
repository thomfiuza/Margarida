package br.com.monitoridoso

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import br.com.monitoridoso.core.ehWakeWordSocorro
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Spike Vosk pt-BR: ASR contínuo e filtro "socorro" (mesma heurística do EscutaFala).
 * Mais pesado que Porcupine — serve para medir bateria no S23.
 */
class VoskSocorroDetector(
    private val ctx: android.content.Context,
    private val aoSocorro: () -> Unit
) {
    private var thread: Thread? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audio: AudioRecord? = null
    @Volatile
    private var ligado = false

    fun iniciar(): Boolean {
        parar()
        val path = VoskModelo.caminho(ctx) ?: return false
        return try {
            model = Model(path)
            recognizer = Recognizer(model, SAMPLE_RATE)
            ligado = true
            thread = Thread({ capturar() }, "vosk-socorro").also { it.start() }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Vosk não iniciou: ${e.message}")
            parar()
            false
        }
    }

    fun parar() {
        ligado = false
        thread?.interrupt()
        try {
            thread?.join(2500)
        } catch (_: InterruptedException) {
        }
        thread = null
        try {
            audio?.stop()
        } catch (_: Exception) {
        }
        audio?.release()
        audio = null
        recognizer?.close()
        model?.close()
        recognizer = null
        model = null
    }

    private fun capturar() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }
        audio = rec
        val buf = ByteArray(minBuf)
        rec.startRecording()
        val recog = recognizer ?: return
        while (ligado && !Thread.currentThread().isInterrupted) {
            val lidos = rec.read(buf, 0, buf.size)
            if (lidos <= 0) continue
            if (recog.acceptWaveForm(buf, lidos)) {
                checarTexto(recog.result)
            } else {
                checarTexto(recog.partialResult)
            }
        }
        try {
            rec.stop()
        } catch (_: Exception) {
        }
        rec.release()
    }

    private fun checarTexto(json: String) {
        val texto = runCatching {
            JSONObject(json).optString("text", JSONObject(json).optString("partial", ""))
        }.getOrDefault("")
        if (texto.isNotBlank() && ehWakeWordSocorro(texto)) aoSocorro()
    }

    companion object {
        private const val TAG = "VoskWake"
        private const val SAMPLE_RATE = 16_000f
    }
}
