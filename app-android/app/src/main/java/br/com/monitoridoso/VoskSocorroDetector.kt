package br.com.monitoridoso

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import br.com.monitoridoso.core.VOSK_DESCONHECIDO
import br.com.monitoridoso.core.ehWakeWordSocorro
import br.com.monitoridoso.core.gramaticaVoskPosWake
import br.com.monitoridoso.core.gramaticaVoskWake
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Spike Vosk pt-BR: ASR contínuo e filtro "socorro" (mesma heurística do
 * EscutaFala). Mais pesado que Porcupine, e é exatamente por isso que ele
 * existe: é o número do S23 que decide se dá pra ficar ouvindo 24/7.
 *
 * DOIS MODOS, e a diferença entre eles é o que a medição compara:
 *
 *  - DITADO (gramatica == null): decodifica o vocabulário inteiro do modelo em
 *    cada quadro de áudio. Mais flexível, mais CPU, mais bateria, mais falso
 *    positivo com televisão ligada. Era o único modo que existia.
 *
 *  - GRAMÁTICA: o reconhecedor só pode emitir as palavras do vocabulário, que
 *    mora no núcleo (`gramaticaVoskWake` / `gramaticaVoskPosWake`) e é testado
 *    na JVM. Decodifica menos, gasta menos, erra menos. O que não está na
 *    lista vira "[unk]" e é ignorado.
 *
 * Se o Vosk não aceitar a gramática, o detector CAI PARA O DITADO sozinho e
 * continua funcionando. Ficar sem wake word por causa de uma otimização seria
 * o pior resultado possível desta classe.
 */
class VoskSocorroDetector(
    private val ctx: android.content.Context,
    private val aoSocorro: () -> Unit,
    private val gramatica: String? = gramaticaVoskWake()
) {

    /** Jeito antigo de construir: ditado livre, sem gramática. */
    constructor(ctx: android.content.Context, aoSocorro: () -> Unit) :
        this(ctx, aoSocorro, null)

    private var thread: Thread? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audio: AudioRecord? = null
    private var comGramatica = false
    @Volatile
    private var ligado = false

    /** Em qual modo o detector acabou de subir. Serve para a medição no S23. */
    val modo: String get() = if (comGramatica) "GRAMATICA" else "DITADO"

    fun iniciar(): Boolean {
        parar()
        val path = VoskModelo.caminho(ctx) ?: return false
        return try {
            model = Model(path)
            recognizer = criarRecognizer(model!!)
            ligado = true
            thread = Thread({ capturar() }, "vosk-socorro").also { it.start() }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Vosk não iniciou: ${e.message}")
            parar()
            false
        }
    }

    /**
     * Tenta com gramática primeiro. O construtor com gramática pode falhar se
     * o modelo não tiver sido preparado para vocabulário restrito, e nesse
     * caso o ditado livre assume. Nunca fica sem detector.
     */
    private fun criarRecognizer(m: Model): Recognizer {
        val g = gramatica
        if (g != null) {
            try {
                val r = Recognizer(m, SAMPLE_RATE, g)
                comGramatica = true
                Log.i(TAG, "Vosk em GRAMATICA (${g.count { it == ',' } + 1} palavras)")
                return r
            } catch (e: Exception) {
                Log.w(TAG, "Gramatica recusada, caindo para ditado livre: ${e.message}")
            }
        }
        comGramatica = false
        Log.i(TAG, "Vosk em DITADO livre")
        return Recognizer(m, SAMPLE_RATE)
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
        if (texto.isBlank()) return
        // Em modo gramática o Vosk devolve "[unk]" para tudo que não está no
        // vocabulário. É o resultado mais frequente de uma noite inteira com
        // TV ligada, e não vale uma passada no filtro.
        if (comGramatica && normalizaSoParaComparar(texto) == VOSK_DESCONHECIDO) return
        if (ehWakeWordSocorro(texto)) aoSocorro()
    }

    /** Só para comparar com "[unk]", que não é texto natural. */
    private fun normalizaSoParaComparar(texto: String) =
        texto.trim().lowercase()
            .replace(Regex("[^a-z0-9\\[\\] ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        private const val TAG = "VoskWake"
        private const val SAMPLE_RATE = 16_000f

        /**
         * Gramática para usar DEPOIS que a wake word acorda o app, na janela de
         * confirmação de 10 s. Cobre o pedido de socorro, o sim, o não e as
         * emergências que disparam mesmo sem a palavra "socorro".
         */
        fun gramaticaConfirmacao(): String = gramaticaVoskPosWake()
    }
}
