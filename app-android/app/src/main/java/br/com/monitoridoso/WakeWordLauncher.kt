package br.com.monitoridoso

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/** Sobe o WakeWordService (FGS microfone) se cadastro e permissão de áudio ok. */
object WakeWordLauncher {

    private const val TAG = "Margarida"

    fun tentarIniciar(ctx: Context) {
        val app = ctx.applicationContext
        if (!PerfilStore(app).configurado()) return
        if (!temPermissaoAudio(app)) {
            Log.i(TAG, "wake word adiado: sem permissão RECORD_AUDIO")
            return
        }
        try {
            val i = Intent(app, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(i)
            } else {
                app.startService(i)
            }
        } catch (e: Exception) {
            Log.w(TAG, "wake word indisponível: ${e.message}")
        }
    }

    private fun temPermissaoAudio(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
