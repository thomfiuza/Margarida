package br.com.monitoridoso

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reagenda lembrete + wake word após reiniciar o celular (se cadastro ok). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = PerfilStore(ctx)
        if (!store.configurado()) return
        RotinaNotificacoesScheduler.agendar(ctx)
        WakeWordLauncher.tentarIniciar(ctx)
    }
}
