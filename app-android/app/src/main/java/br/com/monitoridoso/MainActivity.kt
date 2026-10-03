package br.com.monitoridoso

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Tela inicial — dois modos, botões grandes (mínimo 64dp de altura):
 *  MODO IDOSO   -> SOS (wake word + botão vermelho)
 *  MODO CUIDADOR-> rotina guiada por voz + painel do diário
 */
class MainActivity : AppCompatActivity() {

    private val pedirAudio = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { iniciarWake() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PerfilStore(this)
        if (!store.configurado()) {
            startActivity(Intent(this, CadastroActivity::class.java))
            finish()
            return
        }
        RotinaNotificacoesScheduler.agendar(this)

        val ctx = this
        val titulo = TextView(this).apply {
            text = "Margarida — ${store.nomeIdoso()}"
            textSize = 28f
            setPadding(32, 64, 32, 32)
        }
        val btnIdoso = Button(this).apply {
            text = "SOCORRO (modo idoso)"
            textSize = 22f
            minimumHeight = 192
            setBackgroundColor(0xFFC62828.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                startActivity(Intent(ctx, SosActivity::class.java))
            }
        }
        val btnCuidador = Button(this).apply {
            text = "Rotina diária (modo cuidador)"
            textSize = 20f
            minimumHeight = 160
            setOnClickListener {
                startActivity(Intent(ctx, CuidadorActivity::class.java))
            }
        }
        val btnConfig = Button(this).apply {
            text = "Ajustar cadastro"
            setOnClickListener { startActivity(Intent(ctx, CadastroActivity::class.java)) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titulo); addView(btnIdoso); addView(btnCuidador); addView(btnConfig)
        })

        val faltam = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) faltam.add(Manifest.permission.POST_NOTIFICATIONS)
        val pedir = faltam.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (pedir.isEmpty()) iniciarWake() else pedirAudio.launch(pedir.toTypedArray())
    }

    private fun iniciarWake() {
        try {
            startForegroundService(Intent(this, WakeWordService::class.java))
        } catch (e: Exception) {
            android.util.Log.w("Margarida", "wake word indisponível: ${e.message}")
        }
    }
}
