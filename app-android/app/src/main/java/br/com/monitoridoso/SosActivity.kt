package br.com.monitoridoso

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import br.com.monitoridoso.core.AcaoWake
import br.com.monitoridoso.core.FALA_CONFIRMACAO_WAKE
import br.com.monitoridoso.core.Json
import br.com.monitoridoso.core.Localizacao
import br.com.monitoridoso.core.WakeWordGate
import br.com.monitoridoso.core.normalizarEventoSensor
import br.com.monitoridoso.core.tokenConfere
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

/**
 * SOS — botão vermelho, wake word (com [WakeWordGate]), sensor de queda/gás
 * e app parceiro confiável (disparo direto).
 */
class SosActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIA_PARCEIRO = "VIA_PARCEIRO"
        const val EXTRA_PARCEIRO_ORIGEM = "PARCEIRO_ORIGEM"
        const val EXTRA_PARCEIRO_OBS = "PARCEIRO_OBS"
        private const val JANELA_MS = 6_000L
    }

    private val permissoes = arrayOf(
        Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private val handler = Handler(Looper.getMainLooper())
    private val gate = WakeWordGate(janelaMs = JANELA_MS, modoDireto = false)
    private var disparou = false
    private var inicioJanela = 0L
    private var escuta: EscutaFala? = null
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView

    private val tick = object : Runnable {
        override fun run() {
            if (disparou || isFinishing || !gate.perguntando) return
            val agora = SystemClock.elapsedRealtime()
            if (agora - inicioJanela >= JANELA_MS) {
                when (gate.aoExpirar(agora)) {
                    AcaoWake.CANCELAR -> cancelar()
                    AcaoWake.DISPARAR_SOS -> disparar(status)
                    else -> {}
                }
                return
            }
            val faltam = ((JANELA_MS - (agora - inicioJanela)) / 1000).coerceAtLeast(0)
            status.text =
                "Confirme com sim ou diga não. Silêncio cancela. Tempo: ${faltam}s."
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gate.modoDireto = PerfilStore(this).wakeWordModoDireto()
        val viaWake = intent.getBooleanExtra("VIA_WAKE_WORD", false)
        val viaWakeDireto = intent.getBooleanExtra("VIA_WAKE_DIRETO", false)
        val viaSensor = intent.getBooleanExtra("VIA_SENSOR", false)
        val viaParceiro = intent.getBooleanExtra(EXTRA_VIA_PARCEIRO, false)
        if (viaWake || viaWakeDireto) WakeWordService.confirmacaoAberta = true

        status = TextView(this).apply {
            text = "Toque no botão para chamar ajuda."
            textSize = 24f
            setPadding(32, 96, 32, 32)
        }
        val botao = Button(this).apply {
            text = "SOCORRO"
            textSize = 28f
            minimumHeight = 320
            setBackgroundColor(0xFFC62828.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        val botaoNao = Button(this).apply {
            text = "Não foi engano"
            textSize = 20f
            minimumHeight = 140
            visibility = android.view.View.GONE
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status); addView(botao); addView(botaoNao)
        })

        for (p in permissoes) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, permissoes, 1)
        }

        when {
            viaParceiro -> {
                botao.setOnClickListener { disparar(status) }
                status.text = "SOS do app parceiro. Ligando..."
                dispararParceiro(status)
            }
            viaSensor -> {
                botao.setOnClickListener { dispararSensor(status) }
                dispararSensor(status)
            }
            viaWakeDireto -> {
                status.text = "Socorro detectado (modo direto). Ligando..."
                disparar(status)
            }
            viaWake -> {
                botao.text = "Ligar agora"
                botao.setOnClickListener { disparar(status) }
                botaoNao.visibility = android.view.View.VISIBLE
                botaoNao.setOnClickListener { processarResposta("não") }
                iniciarConfirmacaoWake()
            }
            else -> botao.setOnClickListener { disparar(status) }
        }
    }

    private var janelaAberta = false

    private fun iniciarConfirmacaoWake() {
        status.text = FALA_CONFIRMACAO_WAKE
        WakeWordService.pausarDuranteConfirmacao()
        val abrirJanela = Runnable {
            if (disparou || janelaAberta || isFinishing) return@Runnable
            janelaAberta = true
            inicioJanela = SystemClock.elapsedRealtime()
            gate.aoDetectar(inicioJanela)
            escuta = EscutaFala(this, continuo = true) { texto -> processarResposta(texto) }
            escuta?.iniciar()
            handler.post(tick)
        }
        handler.postDelayed(abrirJanela, 8_000)
        tts = TextToSpeech(this) { st ->
            if (st != TextToSpeech.SUCCESS) {
                handler.removeCallbacks(abrirJanela)
                abrirJanela.run()
                return@TextToSpeech
            }
            tts?.language = Locale("pt", "BR")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        handler.removeCallbacks(abrirJanela)
                        abrirJanela.run()
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        handler.removeCallbacks(abrirJanela)
                        abrirJanela.run()
                    }
                }
            })
            tts?.speak(FALA_CONFIRMACAO_WAKE, TextToSpeech.QUEUE_FLUSH, null, "confirma-sos")
        }
    }

    private fun processarResposta(texto: String) {
        if (disparou || !gate.perguntando) return
        when (gate.aoResponder(texto, SystemClock.elapsedRealtime())) {
            AcaoWake.DISPARAR_SOS -> disparar(status)
            AcaoWake.CANCELAR -> cancelar()
            else -> {}
        }
    }

    private fun cancelar() {
        if (disparou) return
        disparou = true
        handler.removeCallbacks(tick)
        escuta?.parar()
        tts?.stop()
        status.text = "Socorro cancelado. Nada foi ligado."
        handler.postDelayed({ finish() }, 1600)
    }

    private fun disparar(status: TextView) {
        if (disparou) return
        disparou = true
        handler.removeCallbacks(tick)
        escuta?.parar()
        tts?.stop()
        status.text = "Chamando ajuda..."
        val monitor = PerfilStore(this).monitor()
        val chamar = GatewayChamadaNativo(this, this)
        val sms = GatewaySmsAndroid(this)
        Executors.newSingleThreadExecutor().execute {
            val loc: Localizacao? = UltimaLocalizacao.obter(this)
            val ev = monitor.emergencia(loc, chamar, sms)
            runOnUiThread { status.text = ev.mensagem }
        }
    }

    private fun dispararParceiro(status: TextView) {
        if (disparou) return
        disparou = true
        val origem = intent.getStringExtra(EXTRA_PARCEIRO_ORIGEM).orEmpty()
        val obs = intent.getStringExtra(EXTRA_PARCEIRO_OBS).orEmpty()
        status.text = "Parceiro ($origem). Ligando..."
        val monitor = PerfilStore(this).monitor()
        val chamar = GatewayChamadaNativo(this, this)
        val sms = GatewaySmsAndroid(this)
        Executors.newSingleThreadExecutor().execute {
            val loc: Localizacao? = UltimaLocalizacao.obter(this)
            val ev = monitor.emergencia(loc, chamar, sms)
            val msg = if (obs.isBlank()) ev.mensagem else "${ev.mensagem} ($obs)"
            runOnUiThread { status.text = msg }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun dispararSensor(status: TextView) {
        if (disparou) return
        val json = intent.getStringExtra("SENSOR_JSON") ?: return
        val raiz = Json.parse(json) as? Map<String, Any?> ?: return
        if (!tokenConfere(raiz, PerfilStore(this).tokenPonte())) {
            status.text = "Sensor recusado."
            return
        }
        val norm = normalizarEventoSensor(
            raiz, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        ) ?: return
        disparou = true
        status.text = "Sensor pediu ajuda. Ligando..."
        val monitor = PerfilStore(this).monitor()
        val chamar = GatewayChamadaNativo(this, this)
        val sms = GatewaySmsAndroid(this)
        Executors.newSingleThreadExecutor().execute {
            val loc: Localizacao? = UltimaLocalizacao.obter(this)
            val ev = monitor.registrarEventoSensor(
                norm.tipo, norm.payload, norm.hora, chamar, sms, loc
            )
            runOnUiThread { status.text = ev.mensagem }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        escuta?.parar()
        tts?.shutdown()
        if (intent.getBooleanExtra("VIA_WAKE_WORD", false) ||
            intent.getBooleanExtra("VIA_WAKE_DIRETO", false)
        ) {
            WakeWordService.confirmacaoAberta = false
            WakeWordService.retomarDepoisDaConfirmacao()
        }
        super.onDestroy()
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
        return Localizacao(
            l.latitude, l.longitude, l.accuracy.toDouble(),
            if (l.provider == "gps") "gps" else "rede"
        )
    }
}
