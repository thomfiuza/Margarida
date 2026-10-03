package br.com.monitoridoso

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/**
 * Entrada da casa para o app. Home Assistant, Tasker ou o gateway Tuya mandam:
 *   ação br.com.monitoridoso.EVENTO_SENSOR, extra "json"
 *   ação br.com.monitoridoso.QUADRO_PAD, extra "quadro_hex"
 * O JSON precisa do "token" igual ao código da casa, na tela do cuidador.
 */
class SensorEventReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            IngestorSensores.ACAO -> {
                val json = intent.getStringExtra("json") ?: return
                IngestorSensores.ingerir(ctx, json)
            }
            PadIngestor.ACAO -> {
                val hex = intent.getStringExtra("quadro_hex") ?: return
                val pendente = goAsync()
                thread(name = "pad-bcg") {
                    try {
                        PadIngestor.ingerirHex(ctx, hex)
                    } finally {
                        pendente.finish()
                    }
                }
            }
        }
    }
}
