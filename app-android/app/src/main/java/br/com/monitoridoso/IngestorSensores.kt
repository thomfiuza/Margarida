package br.com.monitoridoso

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.monitoridoso.core.Json
import br.com.monitoridoso.core.decidirEvento
import br.com.monitoridoso.core.normalizarEventoSensor
import br.com.monitoridoso.core.tokenConfere
import java.util.Calendar

/**
 * Recebe o JSON da casa (Tuya, Matter ou canônico) e aplica a regra já testada.
 * Queda confirmada e gás abrem o SOS. O resto entra no diário e, se for
 * atenção, numa notificação. O token da casa recusa evento de outro app.
 */
object IngestorSensores {

    const val ACAO = "br.com.monitoridoso.EVENTO_SENSOR"

    fun ingerir(ctx: Context, json: String) {
        val raiz = Json.parse(json) as? Map<String, Any?> ?: return
        val store = PerfilStore(ctx)
        if (!tokenConfere(raiz, store.tokenPonte())) {
            Log.w("Ponte", "evento recusado: token da casa não confere")
            return
        }
        val horaAgora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val norm = normalizarEventoSensor(raiz, horaAgora) ?: return
        val decisao = decidirEvento(norm.tipo, norm.payload, norm.hora)
        if (decisao.acao == "sos") {
            abrirSos(ctx, json, decisao.mensagem)
            return
        }
        store.monitor().registrarEventoSensor(norm.tipo, norm.payload, norm.hora)
        if (decisao.acao == "notificar") notificar(ctx, decisao.mensagem, abrirSos = false, json = null)
    }

    private fun abrirSos(ctx: Context, json: String, mensagem: String) {
        val i = Intent(ctx, SosActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("VIA_SENSOR", true)
            .putExtra("SENSOR_JSON", json)
        notificar(ctx, mensagem, abrirSos = true, json = json)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            Log.w("Ponte", "activity de SOS bloqueada, notificação em tela cheia: ${e.message}")
        }
    }

    private fun notificar(ctx: Context, texto: String, abrirSos: Boolean, json: String?) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel("alertas", "Alertas da casa", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val builder = NotificationCompat.Builder(ctx, "alertas")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Margarida")
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            if (abrirSos && json != null) {
                val i = Intent(ctx, SosActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("VIA_SENSOR", true)
                    .putExtra("SENSOR_JSON", json)
                val pi = PendingIntent.getActivity(
                    ctx, 42, i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(pi, true)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
            }
            nm.notify(if (abrirSos) 42 else texto.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.w("Ponte", "notificação não exibida: ${e.message}")
        }
    }
}
