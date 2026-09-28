package br.com.monitoridoso

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import br.com.monitoridoso.core.Contato
import br.com.monitoridoso.core.MonitorIdosoCore
import java.io.File

/**
 * Tela inicial — dois modos, botões grandes (mínimo 64dp de altura):
 *  MODO IDOSO   -> SOS (wake word + botão vermelho)
 *  MODO CUIDADOR-> rotina guiada por voz + painel do diário
 *
 * MVP: layout em código para manter o esqueleto enxuto; o dev substitui por
 * XML/Compose à vontade — a lógica vive em :core e nas Activities.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        // Em produção: tela de cadastro grava isso em Room (contrato em
        // especificacao_android.md, seção 7).
        fun monitor(app: android.content.Context): MonitorIdosoCore = MonitorIdosoCore(
            nome = "Usuario de Teste",
            contatos = listOf(
                Contato("Contato A", "+5511900000001"),
                Contato("Contato B", "+5511900000002")
            ),
            diarioPath = File(app.filesDir, "diario.jsonl")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this

        val titulo = TextView(this).apply {
            text = "Margarida"
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
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titulo); addView(btnIdoso); addView(btnCuidador)
        })

        // wake word sempre ativa enquanto o app vive — blindado: se o serviço
        // não puder subir (Android rigoroso), a tela e o botão seguem vivos.
        try {
            startForegroundService(Intent(this, WakeWordService::class.java))
        } catch (e: Exception) {
            android.util.Log.w("Margarida", "wake word indisponível: ${e.message}")
        }
    }
}
