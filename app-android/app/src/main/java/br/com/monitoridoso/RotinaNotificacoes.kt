package br.com.monitoridoso

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Lembrete gentil (ex.: 8h) + alerta de inatividade (10h) se a rotina não foi feita. */
class LembreteRotinaWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val store = PerfilStore(applicationContext)
        val monitor = store.monitor()
        if (monitor.diario.temRotinaHoje()) return Result.success()
        if (store.lembreteEnviadoEm(LocalDate.now())) return Result.success()

        val (h, m) = store.horaLembrete()
        val agora = LocalDateTime.now()
        if (agora.hour < h || (agora.hour == h && agora.minute < m)) return Result.success()

        val nome = store.nomeIdoso()
        RotinaNotificacoes.enviar(
            applicationContext,
            NOTIF_LEMBRETE,
            "Rotina da manhã",
            "Hora da rotina de $nome — FC, ortostática e urina (~3 min). Toque para começar.",
            intentRotina(applicationContext)
        )
        store.marcarLembreteEnviado(LocalDate.now())
        return Result.success()
    }

    companion object {
        const val WORK = "lembrete_rotina_manha"
        private const val NOTIF_LEMBRETE = 41
    }
}

class InatividadeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val agora = LocalDateTime.now()
        if (agora.hour < 10) return Result.success()
        val ev = PerfilStore(applicationContext).monitor().checarInatividade(agora)
        if (ev != null) {
            RotinaNotificacoes.enviar(
                applicationContext,
                NOTIF_INATIVIDADE,
                "Margarida — rotina pendente",
                ev.mensagem,
                intentRotina(applicationContext)
            )
        }
        return Result.success()
    }

    companion object {
        const val WORK = "inatividade_manha"
        private const val NOTIF_INATIVIDADE = 42
    }
}

private fun intentRotina(ctx: Context): PendingIntent {
    val i = Intent(ctx, RotinaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(
        ctx, 0, i,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

object RotinaNotificacoes {

    private const val CANAL = "rotina_manha"

    fun enviar(ctx: Context, id: Int, titulo: String, texto: String, acao: PendingIntent?) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Rotina da manhã", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val builder = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
        if (acao != null) {
            builder.setContentIntent(acao)
            builder.addAction(android.R.drawable.ic_media_play, "Fazer rotina", acao)
        }
        nm.notify(id, builder.build())
    }
}

object RotinaNotificacoesScheduler {

    fun agendar(ctx: Context) {
        val store = PerfilStore(ctx)
        val (lh, lm) = store.horaLembrete()

        val lembrete = PeriodicWorkRequestBuilder<LembreteRotinaWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(minutosAte(lh, lm), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            LembreteRotinaWorker.WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            lembrete
        )

        val inatividade = PeriodicWorkRequestBuilder<InatividadeWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(minutosAte(10, 5), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            InatividadeWorker.WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            inatividade
        )
    }

    private fun minutosAte(hora: Int, minuto: Int): Long {
        val agora = LocalDateTime.now()
        var alvo = agora.withHour(hora).withMinute(minuto).withSecond(0).withNano(0)
        if (!alvo.isAfter(agora)) alvo = alvo.plusDays(1)
        return java.time.Duration.between(agora, alvo).toMinutes().coerceAtLeast(1)
    }
}

/** @deprecated use [RotinaNotificacoesScheduler] */
typealias InatividadeScheduler = RotinaNotificacoesScheduler
