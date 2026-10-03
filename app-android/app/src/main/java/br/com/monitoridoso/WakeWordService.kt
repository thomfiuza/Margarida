package br.com.monitoridoso

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import br.com.monitoridoso.core.ehWakeWordSocorro
import java.io.File

/**
 * Wake word — "SOCORRO" em pt-BR.
 * Com AccessKey + socorro_pt.ppn: Porcupine, offline, baixa potência.
 * Sem a chave: reconhecedor do sistema (EscutaFala), até a chave existir.
 * A confirmação de 10 s acontece na SosActivity, com botão e com a voz.
 */
class WakeWordService : LifecycleService() {

    companion object {
        const val ACCESS_KEY = "COLE_AQUI_A_ACCESSKEY_PICOVOICE"
        const val ARQUIVO_PPN = "socorro_pt.ppn"
        const val CANAL = "sos"

        @Volatile
        var confirmacaoAberta = false

        private var emExecucao: WakeWordService? = null

        fun pausarDuranteConfirmacao() {
            emExecucao?.escuta?.parar()
        }

        fun retomarDepoisDaConfirmacao() {
            val s = emExecucao ?: return
            if (s.manager == null) s.iniciarFallback()
        }
    }

    private var manager: PorcupineManager? = null
    private var escuta: EscutaFala? = null

    override fun onCreate() {
        super.onCreate()
        emExecucao = this
        try {
            subirNotificacao(porcupine = false)
        } catch (e: Exception) {
            Log.w("WakeWord", "serviço não iniciado: ${e.message}")
            stopSelf()
            return
        }
        if (ACCESS_KEY.startsWith("COLE_AQUI")) {
            Log.i("WakeWord", "sem AccessKey — escuta do sistema até colar a chave Porcupine")
            iniciarFallback()
            return
        }
        if (!iniciarPorcupine()) iniciarFallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun subirNotificacao(porcupine: Boolean) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Alertas Margarida", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val texto = if (porcupine)
            "Diga SOCORRO em caso de emergência"
        else
            "Diga SOCORRO. Cole a chave Porcupine para a escuta de baixa bateria."
        val notificacao = NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Margarida ativa")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else
            startForeground(1, notificacao)
    }

    private fun iniciarPorcupine(): Boolean {
        return try {
            val ppn = File(filesDir, ARQUIVO_PPN)
            if (!ppn.exists()) {
                assets.open(ARQUIVO_PPN).use { entrada ->
                    ppn.outputStream().use { saida -> entrada.copyTo(saida) }
                }
            }
            val callback = PorcupineManagerCallback { _ -> abrirConfirmacao() }
            manager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeywordPath(ppn.absolutePath)
                .setSensitivity(0.6f)
                .build(this, callback)
            manager?.start()
            subirNotificacao(porcupine = true)
            true
        } catch (e: Exception) {
            Log.w("WakeWord", "Porcupine não inicializado: ${e.message}")
            false
        }
    }

    private fun iniciarFallback() {
        escuta?.parar()
        escuta = EscutaFala(this, continuo = true) { texto ->
            if (ehWakeWordSocorro(texto)) abrirConfirmacao()
        }
        if (escuta?.disponivel() != true) {
            Log.i("WakeWord", "reconhecedor indisponível — só o botão vermelho")
            return
        }
        escuta?.iniciar()
    }

    private fun abrirConfirmacao() {
        if (confirmacaoAberta) return
        confirmacaoAberta = true
        val i = Intent(this, SosActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("VIA_WAKE_WORD", true)
        try {
            startActivity(i)
        } catch (e: Exception) {
            confirmacaoAberta = false
            Log.w("WakeWord", "não abriu a confirmação: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            manager?.stop()
            manager?.delete()
        } catch (e: Exception) {
            Log.w("WakeWord", "erro ao liberar Porcupine: ${e.message}")
        }
        escuta?.parar()
        if (emExecucao == this) emExecucao = null
        super.onDestroy()
    }
}
