package br.com.monitoridoso

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.monitoridoso.core.Contato

/**
 * Cadastro mínimo para piloto: nome do idoso, até 3 contatos SOS, consentimento LGPD.
 */
class CadastroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PerfilStore(this)

        val titulo = TextView(this).apply {
            text = "Configurar Margarida"
            textSize = 24f
            setPadding(32, 48, 32, 16)
        }
        val nome = EditText(this).apply {
            hint = "Nome do idoso"
            setText(store.nomeIdoso())
            textSize = 18f
            setPadding(32, 16, 32, 16)
        }
        val c1n = EditText(this).apply { hint = "Contato 1 — nome"; setPadding(32, 8, 32, 8) }
        val c1t = EditText(this).apply { hint = "Contato 1 — telefone (+55...)"; setPadding(32, 8, 32, 16) }
        val c2n = EditText(this).apply { hint = "Contato 2 — nome (opcional)"; setPadding(32, 8, 32, 8) }
        val c2t = EditText(this).apply { hint = "Contato 2 — telefone"; setPadding(32, 8, 32, 16) }
        val lembreteHora = EditText(this).apply {
            hint = "Lembrete matinal (hora, 6–9) — padrão 8"
            setText(store.horaLembrete().first.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(32, 8, 32, 16)
        }
        val consent = CheckBox(this).apply {
            text = "Li e aceito: dados de saúde ficam no aparelho; alertas são tendência, não diagnóstico."
            isChecked = store.consentimento()
            setPadding(24, 16, 24, 8)
        }
        val btn = Button(this).apply {
            text = "Salvar e continuar"
            textSize = 18f
            minimumHeight = 140
            setOnClickListener {
                val contatos = mutableListOf<Contato>()
                if (c1n.text.isNotBlank() && c1t.text.isNotBlank())
                    contatos.add(Contato(c1n.text.toString().trim(), c1t.text.toString().trim()))
                if (c2n.text.isNotBlank() && c2t.text.isNotBlank())
                    contatos.add(Contato(c2n.text.toString().trim(), c2t.text.toString().trim()))
                if (nome.text.isBlank() || contatos.isEmpty() || !consent.isChecked) {
                    Toast.makeText(this@CadastroActivity, "Preencha nome, ao menos 1 contato e o consentimento.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val hora = lembreteHora.text.toString().toIntOrNull() ?: 8
                store.salvar(nome.text.toString(), contatos, true, hora, 0)
                RotinaNotificacoesScheduler.agendar(this@CadastroActivity)
                startActivity(Intent(this@CadastroActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
        }

        if (store.contatos().isNotEmpty()) {
            c1n.setText(store.contatos()[0].nome)
            c1t.setText(store.contatos()[0].telefone)
            if (store.contatos().size > 1) {
                c2n.setText(store.contatos()[1].nome)
                c2t.setText(store.contatos()[1].telefone)
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titulo); addView(nome); addView(c1n); addView(c1t); addView(c2n); addView(c2t)
            addView(lembreteHora); addView(consent); addView(btn)
        })
    }
}
