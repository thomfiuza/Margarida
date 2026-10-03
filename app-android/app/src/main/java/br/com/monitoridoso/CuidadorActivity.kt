package br.com.monitoridoso

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import br.com.monitoridoso.core.Nivel
import java.io.File

/**
 * MODO CUIDADOR — painel do diário + início da rotina guiada por voz.
 */
class CuidadorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val monitor = PerfilStore(this).monitor()

        val resumo = monitor.resumoDoDia()
        val eventos = monitor.diario.todos().takeLast(20).reversed()

        val txt = TextView(this).apply {
            textSize = 16f
            setPadding(32, 64, 32, 32)
            text = buildString {
                appendLine(resumo["texto"] as String)
                appendLine()
                appendLine("Últimos eventos:")
                for (e in eventos) {
                    val marca = when (e.nivel) {
                        Nivel.URGENTE -> "🔴"
                        Nivel.ATENCAO -> "🟡"
                        else -> "🟢"
                    }
                    appendLine("$marca [${e.tipo}] ${e.mensagem}")
                }
            }
        }
        val codigo = TextView(this).apply {
            textSize = 16f
            setPadding(32, 8, 32, 16)
            text = "Código da casa (cole no sensor / Home Assistant): ${PerfilStore(this@CuidadorActivity).tokenPonte()}"
        }
        val btnRotina = Button(this).apply {
            text = "Iniciar rotina da manhã"
            textSize = 20f
            minimumHeight = 160
            setOnClickListener {
                startActivity(Intent(this@CuidadorActivity, RotinaActivity::class.java))
            }
        }
        val btnExportar = Button(this).apply {
            text = "Exportar diário do piloto"
            textSize = 18f
            minimumHeight = 120
            setOnClickListener { exportarDiario() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(this@CuidadorActivity).apply { addView(txt) })
            addView(codigo)
            addView(btnRotina)
            addView(btnExportar)
        })
    }

    private fun exportarDiario() {
        val origem = File(filesDir, "diario.jsonl")
        if (!origem.exists()) origem.writeText("")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", origem)
        val enviar = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Diário Margarida")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(enviar, "Exportar diário"))
    }
}
