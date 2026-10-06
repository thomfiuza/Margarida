package br.com.monitoridoso

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import br.com.monitoridoso.core.Integracao
import br.com.monitoridoso.core.Nivel
import br.com.monitoridoso.core.ResumoSemana
import java.io.File

/**
 * MODO CUIDADOR — painel do diário, interoperabilidade e rotina guiada.
 */
class CuidadorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PerfilStore(this)
        val monitor = store.monitor()

        val resumoDia = monitor.resumoDoDia()
        val resumoSemana = ResumoSemana.gerar(monitor.diario, 7)
        val eventos = monitor.diario.todos().takeLast(20).reversed()

        val txt = TextView(this).apply {
            textSize = 16f
            setPadding(32, 64, 32, 32)
            text = buildString {
                appendLine(resumoDia["texto"] as String)
                appendLine()
                appendLine("Resumo 7 dias (local):")
                appendLine(resumoSemana)
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
            setPadding(32, 8, 32, 8)
            text = "Código da casa: ${store.tokenPonte()}"
        }
        val parceiros = EditText(this).apply {
            hint = "Apps parceiros (pacotes, separados por vírgula)"
            setText(store.parceirosConfiaveis().joinToString(", "))
            setPadding(32, 8, 32, 8)
        }
        val wakeDireto = CheckBox(this).apply {
            text = "Wake word sem confirmação (modo direto — mais falsos positivos)"
            isChecked = store.wakeWordModoDireto()
            setPadding(24, 8, 24, 8)
        }
        val rotinaAlzheimer = CheckBox(this).apply {
            text = "Rotina por voz simplificada (modo Alzheimer — menos perguntas)"
            isChecked = store.rotinaModoAlzheimer()
            setPadding(24, 8, 24, 8)
        }
        val motorWake = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@CuidadorActivity,
                android.R.layout.simple_spinner_dropdown_item,
                MotorWake.entries.map {
                    when (it) {
                        MotorWake.ESCUTA -> "Wake: reconhecedor do sistema"
                        MotorWake.PORCUPINE -> "Wake: Porcupine (chave + .ppn)"
                        MotorWake.VOSK -> "Wake: Vosk pt-BR (spike bateria)"
                    }
                }
            )
            setSelection(MotorWake.entries.indexOf(store.motorWake()).coerceAtLeast(0))
            setPadding(32, 8, 32, 8)
        }
        val btnSalvarPrefs = Button(this).apply {
            text = "Salvar interoperabilidade e motor wake"
            setOnClickListener {
                val lista = parceiros.text.toString()
                    .split(',', ';', '\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                store.definirParceirosConfiaveis(lista)
                store.definirWakeWordModoDireto(wakeDireto.isChecked)
                store.definirRotinaModoAlzheimer(rotinaAlzheimer.isChecked)
                store.definirMotorWake(MotorWake.entries[motorWake.selectedItemPosition])
                Toast.makeText(
                    this@CuidadorActivity,
                    "Salvo. Feche e abra o app para aplicar o motor wake.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val btnRotinaVoz = Button(this).apply {
            text = "Rotina por voz (Estação 7 — MaquinaRotina)"
            textSize = 18f
            minimumHeight = 140
            setOnClickListener {
                startActivity(Intent(this@CuidadorActivity, RotinaVozActivity::class.java))
            }
        }
        val btnRotina = Button(this).apply {
            text = "Rotina com vídeo (MP3 + gravação)"
            textSize = 20f
            minimumHeight = 160
            setOnClickListener {
                startActivity(Intent(this@CuidadorActivity, RotinaActivity::class.java))
            }
        }
        val btnExportarJsonl = Button(this).apply {
            text = "Exportar diário (JSONL piloto)"
            setOnClickListener { exportarJsonl() }
        }
        val btnExportarParceiro = Button(this).apply {
            text = "Exportar histórico JSON (parceiro)"
            setOnClickListener { exportarHistoricoParceiro(monitor) }
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(this@CuidadorActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(txt)
                addView(codigo)
                addView(parceiros)
                addView(wakeDireto)
                addView(rotinaAlzheimer)
                addView(motorWake)
                addView(btnSalvarPrefs)
                addView(btnRotinaVoz)
                addView(btnRotina)
                addView(btnExportarJsonl)
                addView(btnExportarParceiro)
            })
        })
    }

    private fun exportarJsonl() {
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

    private fun exportarHistoricoParceiro(monitor: br.com.monitoridoso.core.MonitorIdosoCore) {
        val json = Integracao.historicoJson(monitor.diario)
        val f = File(cacheDir, "margarida_historico.json")
        f.writeText(json)
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
        val enviar = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Histórico Margarida")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(enviar, "Exportar histórico"))
    }
}
