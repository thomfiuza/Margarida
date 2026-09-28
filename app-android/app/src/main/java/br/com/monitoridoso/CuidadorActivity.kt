package br.com.monitoridoso

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * MODO CUIDADOR — painel do diário + botão da rotina diária.
 *
 * MVP: mostra o resumo do dia e os eventos. A rotina guiada por voz entra na
 * semana 3–4 do cronograma (especificacao_android.md, seção 10): os 9 MP3 já
 * gravados vão em app/src/main/res/raw/, a câmera chama as engines rPPG/urina
 * (Chaquopy) e o resultado vira monitor.registrarRotina(...).
 */
class CuidadorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val monitor = MainActivity.monitor(this)

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
                        br.com.monitoridoso.core.Nivel.URGENTE -> "🔴"
                        br.com.monitoridoso.core.Nivel.ATENCAO -> "🟡"
                        else -> "🟢"
                    }
                    appendLine("$marca [${e.tipo}] ${e.mensagem}")
                }
            }
        }
        setContentView(android.widget.ScrollView(this).apply { addView(txt) })
    }
}
