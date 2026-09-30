package br.com.monitoridoso

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import br.com.monitoridoso.core.AcaoWake
import br.com.monitoridoso.core.WakeWordGate
import java.io.File
import java.util.Locale

/**
 * Wake word 24/7 — "SOCORRO" em pt-BR, offline (Porcupine) — v3.
 *
 * Novo: confirmação por voz ANTES de ligar, com a política testada em
 * :core (WakeWordGate, 12/12 testes JVM):
 *   detectou -> TTS pergunta "você pediu socorro?" -> escuta 6 s
 *   "sim"    -> abre SosActivity (dispara ligações + SMS)
 *   "não"    -> "entendido, fico por perto" (nada acontece)
 *   silêncio -> nada acontece (silêncio = não; na dúvida, nunca liga)
 *   modoDireto=true -> dispara sem perguntar (config do cuidador)
 *
 * BLINDADO (v2): qualquer falha de notificação/serviço/Porcupine derruba só
 * o serviço — a tela e o botão vermelho seguem vivos.
 */
class WakeWordService : LifecycleService() {

    companion object {
        const val ACCESS_KEY = "COLE_AQUI_A_ACCESSKEY_PICOVOICE"
        const val ARQUIVO_PPN = "socorro_pt.ppn"
        const val MODO_DIRETO = false           // true = dispara sem confirmar
        const val CANAL = "sos"
        private const val TAG = "WakeWord"
    }

    private var manager: PorcupineManager? = null
    private val gate = WakeWordGate(modoDireto = MODO_DIRETO)
    private lateinit var mao: Handler
    private var tts: TextToSpeech? = null
    private var ttsPronto = false
    private var recognizer: SpeechRecognizer? = null
    private var expirar: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        mao = Handler(mainLooper)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(CANAL, "Alertas Margarida",
                        NotificationManager.IMPORTANCE_LOW)
                )
            }
            val notificacao = NotificationCompat.Builder(this, CANAL)
                .setContentTitle("Margarida ativa")
                .setContentText("Diga SOCORRO em caso de emergência")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(1, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else
                startForeground(1, notificacao)
        } catch (e: Exception) {
            Log.w(TAG, "serviço não iniciado: ${e.message}")
            stopSelf()
            return
        }

        tts = TextToSpeech(this) { st ->
            ttsPronto = st == TextToSpeech.SUCCESS
            if (ttsPronto) tts?.language = Locale("pt", "BR")
        }

        if (ACCESS_KEY.startsWith("COLE_AQUI")) {
            Log.i(TAG, "sem AccessKey — modo botão vermelho")
            return
        }
        try {
            val ppn = File(filesDir, ARQUIVO_PPN)
            if (!ppn.exists()) {
                assets.open(ARQUIVO_PPN).use { e -> ppn.outputStream().use { e.copyTo(it) } }
            }
            // invoke(int) = índice da palavra detectada (javap 3.0.2); >= 0 detectou
            val callback = PorcupineManagerCallback { idx ->
                if (idx >= 0) mao.post { aoDetectar() }
            }
            manager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeywordPath(ppn.absolutePath)
                .setSensitivity(0.6f)
                .build(this, callback)
            manager?.start()
            Log.i(TAG, "Porcupine ativo: wake word 'socorro'")
        } catch (e: Exception) {
            Log.w(TAG, "Porcupine não inicializado (modo botão): ${e.message}")
        }
    }

    private fun aoDetectar() {
        when (gate.aoDetectar(SystemClock.elapsedRealtime())) {
            AcaoWake.PERGUNTAR -> perguntar()
            AcaoWake.DISPARAR_SOS -> abrirSos()
            else -> {}
        }
    }

    private fun perguntar() {
        val pergunta = "Você pediu socorro? Diga sim para chamar a família, ou não para cancelar."
        val r = Runnable {
            when (gate.aoExpirar(SystemClock.elapsedRealtime())) {
                AcaoWake.CANCELAR -> Log.i(TAG, "janela expirou: silêncio = não")
                else -> {}
            }
            pararEscuta()
        }
        expirar = r
        mao.postDelayed(r, 6_500)   // fala (~2 s) + janela da gate (6 s)
        falar(pergunta) { escutar() }
    }

    private fun aoResponder(texto: String) {
        expirar?.let { mao.removeCallbacks(it) }; expirar = null
        pararEscuta()
        when (gate.aoResponder(texto, SystemClock.elapsedRealtime())) {
            AcaoWake.DISPARAR_SOS -> abrirSos()
            AcaoWake.CANCELAR -> falar("Entendido, fico por perto.", null)
            else -> {}
        }
    }

    private fun abrirSos() {
        val i = Intent(this, SosActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("VIA_WAKE_WORD", true)
        startActivity(i)
    }

    // ---------- voz ----------
    private fun falar(texto: String, aoTerminar: (() -> Unit)?) {
        mao.post {
            if (!ttsPronto) { aoTerminar?.invoke(); return@post }
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { mao.post { aoTerminar?.invoke() } }
                @Deprecated("deprecated") override fun onError(id: String?) { mao.post { aoTerminar?.invoke() } }
            })
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "margarida")
        }
    }

    private fun escutar() {
        mao.post {
            try {
                val r = SpeechRecognizer.createSpeechRecognizer(this)
                recognizer = r
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(res: android.os.Bundle) {
                        val texto = res.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                        aoResponder(texto)
                    }
                    override fun onError(e: Int) { aoResponder("") }  // sem resposta = silêncio = não
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
                Log.w(TAG, "escuta indisponível: ${e.message}")
                aoResponder("")
            }
        }
    }

    private fun pararEscuta() {
        try { recognizer?.stopListening(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    override fun onDestroy() {
        expirar?.let { mao.removeCallbacks(it) }
        pararEscuta()
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        try { manager?.stop(); manager?.delete() } catch (e: Exception) {
            Log.w(TAG, "erro ao liberar Porcupine: ${e.message}")
        }
        super.onDestroy()
    }
}
