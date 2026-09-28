package br.com.monitoridoso

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import br.com.monitoridoso.core.Localizacao
import java.util.concurrent.Executors

/**
 * SOS — botão vermelho gigante. Abre também pelo wake word (WakeWordService
 * lança esta activity com showWhenLocked/turnScreenOn).
 *
 * Confirmação de 10 s: se o idoso NÃO disser/tocar "não", dispara MESMO ASSIM
 * (especificacao_android.md, seção 3 — omissão custa mais que falso positivo).
 */
class SosActivity : AppCompatActivity() {

    private val permissoes = arrayOf(
        Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this

        val status = TextView(this).apply { text = "Chamando ajuda..."; textSize = 24f; setPadding(32, 96, 32, 32) }
        val botao = Button(this).apply {
            text = "SOCORRO"
            textSize = 28f
            minimumHeight = 320
            setBackgroundColor(0xFFC62828.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        setContentView(android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(status); addView(botao)
        })

        for (p in permissoes) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, permissoes, 1)
        }

        botao.setOnClickListener { disparar(status) }
        // se veio do wake word, dispara direto (a "confirmação" foram os 10 s
        // de espera do serviço antes de abrir esta tela)
        if (intent.getBooleanExtra("VIA_WAKE_WORD", false)) disparar(status)
    }

    private fun disparar(status: TextView) {
        val monitor = MainActivity.monitor(this)
        val chamar = GatewayChamadaNativo(this, this)
        val sms = GatewaySmsAndroid(this)
        Executors.newSingleThreadExecutor().execute {
            val loc: Localizacao? = UltimaLocalizacao.obter(this)   // GPS/última conhecida
            val ev = monitor.emergencia(loc, chamar, sms)
            runOnUiThread { status.text = ev.mensagem }
        }
    }
}

/** MVP: última localização conhecida (FusedLocationProvider fica para a v1.1). */
object UltimaLocalizacao {
    @Suppress("MissingPermission")
    fun obter(ctx: android.content.Context): Localizacao? {
        val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE)
            as android.location.LocationManager
        val l = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            ?: return null
        return Localizacao(l.latitude, l.longitude, l.accuracy.toDouble(),
            if (l.provider == "gps") "gps" else "rede")
    }
}
