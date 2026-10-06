package br.com.monitoridoso

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.monitoridoso.core.Integracao
import br.com.monitoridoso.core.PedidoSosParceiro

/**
 * Porta de interoperabilidade: deep link `margarida://sos?origem=...&obs=...`
 * ou action [ACAO]. Só dispara se [origem] estiver na lista de parceiros confiáveis.
 */
class SosEntradaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PerfilStore(this)
        if (!store.configurado()) {
            startActivity(Intent(this, CadastroActivity::class.java))
            finish()
            return
        }
        val (origem, obs) = extrairPedido(intent) ?: run {
            Toast.makeText(this, "Pedido de SOS inválido.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val confiavel = origem in store.parceirosConfiaveis()
        val fonte = Integracao.validar(PedidoSosParceiro(origem, confiavel, obs))
        if (fonte == null) {
            Toast.makeText(
                this,
                "App parceiro não autorizado. Cadastre o pacote em modo cuidador.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        startActivity(
            Intent(this, SosActivity::class.java)
                .putExtra(SosActivity.EXTRA_VIA_PARCEIRO, true)
                .putExtra(SosActivity.EXTRA_PARCEIRO_ORIGEM, origem)
                .putExtra(SosActivity.EXTRA_PARCEIRO_OBS, obs)
        )
        finish()
    }

    private fun extrairPedido(intent: Intent): Pair<String, String>? {
        when (intent.action) {
            ACAO -> {
                val origem = intent.getStringExtra(EXTRA_ORIGEM)?.trim().orEmpty()
                val obs = intent.getStringExtra(EXTRA_OBS)?.trim().orEmpty()
                if (origem.isBlank()) return null
                return origem to obs
            }
            Intent.ACTION_VIEW -> {
                val uri: Uri = intent.data ?: return null
                if (uri.scheme != "margarida" || uri.host != "sos") return null
                val origem = uri.getQueryParameter("origem")?.trim().orEmpty()
                if (origem.isBlank()) return null
                val obs = uri.getQueryParameter("obs")?.trim().orEmpty()
                return origem to obs
            }
            else -> return null
        }
    }

    companion object {
        const val ACAO = "br.com.monitoridoso.SOS_PARCEIRO"
        const val EXTRA_ORIGEM = "origem"
        const val EXTRA_OBS = "observacao"
    }
}
