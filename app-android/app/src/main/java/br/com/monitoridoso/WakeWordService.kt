package br.com.monitoridoso

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import br.com.monitoridoso.core.AcaoWake
import br.com.monitoridoso.core.WakeWordGate
import br.com.monitoridoso.core.ehWakeWordSocorro
import java.io.File

/**
 * Wake word "SOCORRO" — motor escolhido no modo cuidador:
 * [MotorWake.ESCUTA], [MotorWake.PORCUPINE] ou [MotorWake.VOSK] (spike bateria).
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
            emExecucao?.pausarMotores()
        }

        fun retomarDepoisDaConfirmacao() {
            emExecucao?.retomarMotores()
        }
    }

    private var manager: PorcupineManager? = null
    private var escuta: EscutaFala? = null
    private var vosk: VoskSocorroDetector? = null
    private val gate = WakeWordGate()
    private var motorAtual: MotorWake = MotorWake.ESCUTA
    private var ultimoWakeMs = 0L

    override fun onCreate() {
        super.onCreate()
        emExecucao = this
        gate.modoDireto = PerfilStore(this).wakeWordModoDireto()
        try {
            subirNotificacao(MotorWake.ESCUTA)
        } catch (e: Exception) {
            Log.w("WakeWord", "serviço não iniciado: ${e.message}")
            stopSelf()
            return
        }
        iniciarMotorEscolhido()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun iniciarMotorEscolhido() {
        pararMotores()
        motorAtual = PerfilStore(this).motorWake()
        when (motorAtual) {
            MotorWake.VOSK -> {
                subirNotificacao(MotorWake.VOSK)
                vosk = VoskSocorroDetector(this) { abrirConfirmacao() }
                if (vosk?.iniciar() != true) {
                    Log.w("WakeWord", "Vosk indisponível — caindo para EscutaFala")
                    motorAtual = MotorWake.ESCUTA
                    subirNotificacao(MotorWake.ESCUTA)
                    iniciarFallback()
                }
            }
            MotorWake.PORCUPINE -> {
                if (ACCESS_KEY.startsWith("COLE_AQUI") || !iniciarPorcupine()) {
                    Log.i("WakeWord", "Porcupine indisponível — EscutaFala")
                    motorAtual = MotorWake.ESCUTA
                    subirNotificacao(MotorWake.ESCUTA)
                    iniciarFallback()
                }
            }
            MotorWake.ESCUTA -> {
                subirNotificacao(MotorWake.ESCUTA)
                iniciarFallback()
            }
        }
    }

    private fun subirNotificacao(motor: MotorWake) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Alertas Margarida", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val texto = when (motor) {
            MotorWake.PORCUPINE -> "Diga SOCORRO (Porcupine, baixa bateria)"
            MotorWake.VOSK -> "Diga SOCORRO (Vosk pt-BR — teste bateria)"
            MotorWake.ESCUTA -> "Diga SOCORRO (reconhecedor do sistema)"
        }
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
            subirNotificacao(MotorWake.PORCUPINE)
            motorAtual = MotorWake.PORCUPINE
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

    private fun pausarMotores() {
        escuta?.parar()
        vosk?.parar()
        try {
            manager?.stop()
        } catch (_: Exception) {
        }
    }

    private fun retomarMotores() {
        when (motorAtual) {
            MotorWake.PORCUPINE -> try {
                manager?.start()
            } catch (e: Exception) {
                iniciarFallback()
            }
            MotorWake.VOSK -> {
                if (vosk == null) vosk = VoskSocorroDetector(this) { abrirConfirmacao() }
                if (vosk?.iniciar() != true) iniciarFallback()
            }
            MotorWake.ESCUTA -> iniciarFallback()
        }
    }

    private fun pararMotores() {
        escuta?.parar()
        escuta = null
        vosk?.parar()
        vosk = null
        try {
            manager?.stop()
            manager?.delete()
        } catch (_: Exception) {
        }
        manager = null
    }

    private fun abrirConfirmacao() {
        if (confirmacaoAberta) return
        val agora = SystemClock.elapsedRealtime()
        if (gate.modoDireto) {
            when (gate.aoDetectar(agora)) {
                AcaoWake.DISPARAR_SOS -> abrirDisparoDireto()
                else -> {}
            }
            return
        }
        if (agora - ultimoWakeMs < 10_000) return
        ultimoWakeMs = agora
        abrirConfirmacaoComPergunta()
    }

    private fun abrirConfirmacaoComPergunta() {
        if (confirmacaoAberta) return
        confirmacaoAberta = true
        pausarMotores()
        val i = Intent(this, SosActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("VIA_WAKE_WORD", true)
        try {
            startActivity(i)
        } catch (e: Exception) {
            confirmacaoAberta = false
            retomarMotores()
            Log.w("WakeWord", "não abriu a confirmação: ${e.message}")
        }
    }

    private fun abrirDisparoDireto() {
        if (confirmacaoAberta) return
        confirmacaoAberta = true
        pausarMotores()
        val i = Intent(this, SosActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("VIA_WAKE_DIRETO", true)
        try {
            startActivity(i)
        } catch (e: Exception) {
            confirmacaoAberta = false
            retomarMotores()
            Log.w("WakeWord", "não abriu SOS direto: ${e.message}")
        }
    }

    override fun onDestroy() {
        pararMotores()
        if (emExecucao == this) emExecucao = null
        super.onDestroy()
    }
}
