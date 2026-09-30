package br.com.monitoridoso

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * MODO CUIDADOR — painel do diário + ROTINA GUIADA POR VOZ (Estação 7).
 * O botão "Iniciar rotina guiada" liga o CondutorRotina: TTS fala,
 * microfone escuta, câmera mede o pulso e o diário registra.
 */
class CuidadorActivity : AppCompatActivity() {

    private var condutor: CondutorRotina? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val monitor = MainActivity.monitor(this)

        for (p in arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                    2
                )
        }

        val resumo = monitor.resumoDoDia()
        val eventos = monitor.diario.todos().takeLast(20).reversed()

        val txt = TextView(this).apply {
            textSize = 16f
            setPadding(32, 48, 32, 16)
            text = buildString {
                appendLine(resumo["texto"] as String)
                appendLine()
                appendLine("Últimos eventos:")
                for (e in eventos) {
                    val marca = when (e.nivel) {
                        br.com.monitoridoso.core.Nivel.URGENTE -> "🔴"
                        br.com.monitoridoso.core.Nivel.ATENCAO -> "🟡"
                        else -> "🟢"
                    }
                    appendLine("$marca [${e.tipo}] ${e.mensagem}")
                }
            }
        }

        val status = TextView(this).apply {
            textSize = 20f
            setPadding(32, 16, 32, 16)
            text = ""
        }

        val botao = Button(this).apply {
            text = "▶ Iniciar rotina guiada por voz"
            textSize = 20f
            minimumHeight = 160
            setOnClickListener {
                text = "Rotina em andamento..."
                condutor = CondutorRotina(this@CuidadorActivity, monitor, status).also {
                    it.iniciar()
                }
            }
        }

        setContentView(android.widget.ScrollView(this).apply {
            addView(LinearLayout(this@CuidadorActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(txt); addView(status); addView(botao)
            })
        })
    }

    override fun onDestroy() {
        condutor?.destruir()
        super.onDestroy()
    }
}
