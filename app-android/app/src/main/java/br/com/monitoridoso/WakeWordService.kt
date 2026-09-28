package br.com.monitoridoso

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import java.io.File

/**
 * Wake word 24/7 — "SOCORRO" em pt-BR, 100% offline (Porcupine).
 * BLINDADO: qualquer falha de notificação/serviço derruba só o serviço,
 * nunca a tela principal (Android 14/15 é rigoroso com foreground services).
 */
class WakeWordService : LifecycleService() {

    companion object {
        const val ACCESS_KEY = "COLE_AQUI_A_ACCESSKEY_PICOVOICE"
        const val ARQUIVO_PPN = "socorro_pt.ppn"
        const val JANELA_CONFIRMACAO_MS = 10_000L
        const val CANAL = "sos"
    }

    private var manager: PorcupineManager? = null

    override fun onCreate() {
        super.onCreate()
        try {
            // canal de notificação PRIMEIRO (obrigatório Android 8+)
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
            // serviço de wake word é opcional: o botão vermelho segue ativo
            Log.w("WakeWord", "serviço não iniciado: ${e.message}")
            stopSelf()
            return
        }

        if (ACCESS_KEY.startsWith("COLE_AQUI")) {
            // sem AccessKey configurada: roda em modo botão, sem gastar bateria
            Log.i("WakeWord", "sem AccessKey — modo botão vermelho")
            return
        }
        try {
            val ppn = File(filesDir, ARQUIVO_PPN)
            if (!ppn.exists()) {
                assets.open(ARQUIVO_PPN).use { entrada ->
                    ppn.outputStream().use { saida -> entrada.copyTo(saida) }
                }
            }
            val callback = PorcupineManagerCallback { _ -> aoDetectarWakeWord() }
            manager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeywordPath(ppn.absolutePath)
                .setSensitivity(0.6f)
                .build(this, callback)
            manager?.start()
        } catch (e: Exception) {
            Log.w("WakeWord", "Porcupine não inicializado (modo botão): ${e.message}")
        }
    }

    private fun aoDetectarWakeWord() {
        Handler(mainLooper).postDelayed({
            val i = Intent(this, SosActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("VIA_WAKE_WORD", true)
            startActivity(i)
        }, JANELA_CONFIRMACAO_MS)
    }

    override fun onDestroy() {
        try {
            manager?.stop()
            manager?.delete()
        } catch (e: Exception) {
            Log.w("WakeWord", "erro ao liberar Porcupine: ${e.message}")
        }
        super.onDestroy()
    }
}
