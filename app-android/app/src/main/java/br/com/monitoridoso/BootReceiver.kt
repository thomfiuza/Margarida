package br.com.monitoridoso

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reagenda lembrete + inatividade após reiniciar o celular. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (PerfilStore(ctx).configurado()) RotinaNotificacoesScheduler.agendar(ctx)
    }
}
