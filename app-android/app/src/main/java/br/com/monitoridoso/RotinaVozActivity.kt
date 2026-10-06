package br.com.monitoridoso

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import br.com.monitoridoso.core.MaquinaRotina
import br.com.monitoridoso.core.PassoRotina
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Estação 7 — rotina guiada só por voz + [MaquinaRotina] (core testado).
 * FC via vídeo curto da câmera (Chaquopy); urina/tontura por voz.
 */
class RotinaVozActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var btnAcao: Button
    private lateinit var maquina: MaquinaRotina
    private var escuta: EscutaFala? = null
    private var tts: TextToSpeech? = null
    private var ttsPronto = false
    private var player: MediaPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var aguardandoVideo = false
    private var passoVideo: PassoRotina? = null

    private val permissoes = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    private val gravarVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        aguardandoVideo = false
        if (res.resultCode != RESULT_OK) {
            status.text = "Gravação cancelada. Toque em medir de novo."
            retomarEscuta()
            return@registerForActivityResult
        }
        val uri = res.data?.data ?: run {
            status.text = "Vídeo não recebido."
            retomarEscuta()
            return@registerForActivityResult
        }
        val dest = File(cacheDir, "rppg_${System.currentTimeMillis()}.mp4")
        contentResolver.openInputStream(uri)?.use { inp ->
            dest.outputStream().use { out -> inp.copyTo(out) }
        }
        executor.execute {
            val r = EnginePython.medirFcVideo(dest.absolutePath)
            runOnUiThread {
                if (!r.ok || r.fcBpm == null) {
                    status.text = "Não medi FC: ${r.motivo ?: "repita com luz e dedo parado"}."
                    retomarEscuta()
                    return@runOnUiThread
                }
                if (!maquina.informarFc(r.fcBpm)) {
                    status.text = "Medição fora de ordem. Repita a rotina."
                    return@runOnUiThread
                }
                aposPasso()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PerfilStore(this)
        maquina = MaquinaRotina(store.nomeIdoso(), store.rotinaModoAlzheimer())

        status = TextView(this).apply { textSize = 20f; setPadding(32, 64, 32, 24) }
        btnAcao = Button(this).apply {
            textSize = 20f
            minimumHeight = 160
            visibility = android.view.View.GONE
            setOnClickListener {
                when (maquina.passo) {
                    PassoRotina.FC_SENTADA, PassoRotina.ORTOSTATICA -> abrirGravacaoFc()
                    else -> {}
                }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(btnAcao)
        })
        pedirPermissoes()
        aposPasso()
    }

    private fun pedirPermissoes() {
        val faltam = permissoes.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltam.isNotEmpty()) ActivityCompat.requestPermissions(this, faltam.toTypedArray(), 7)
    }

    private fun aposPasso() {
        when (maquina.passo) {
            PassoRotina.FC_SENTADA, PassoRotina.ORTOSTATICA -> {
                btnAcao.visibility = android.view.View.VISIBLE
                btnAcao.text = if (maquina.passo == PassoRotina.FC_SENTADA)
                    "Medir FC sentado (câmera)"
                else
                    "Medir FC em pé (câmera)"
                status.text = maquina.fala()
                retomarEscuta()
            }
            PassoRotina.ABORTADA, PassoRotina.FIM -> {
                escuta?.parar()
                btnAcao.visibility = android.view.View.GONE
                falar(maquina.fala()) { finish() }
            }
            PassoRotina.RESUMO -> registrarNoDiario()
            else -> {
                btnAcao.visibility = android.view.View.GONE
                falar(maquina.fala()) { retomarEscuta() }
            }
        }
    }

    private fun registrarNoDiario() {
        btnAcao.visibility = android.view.View.GONE
        escuta?.parar()
        status.text = "Salvando no diário..."
        executor.execute {
            val m = PerfilStore(this).monitor()
            val ev = m.registrarRotina(
                fcRepouso = maquina.fcSentada ?: 0.0,
                fcPe = maquina.fcEmPe ?: maquina.fcSentada ?: 0.0,
                urina = maquina.urina,
                tontura = maquina.tontura ?: false
            )
            runOnUiThread {
                status.text = ev.mensagem
                falar(maquina.fala()) { retomarEscuta() }
            }
        }
    }

    private fun retomarEscuta() {
        if (maquina.passo == PassoRotina.FIM || maquina.passo == PassoRotina.ABORTADA) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return
        escuta?.parar()
        escuta = EscutaFala(this, continuo = true) { texto -> processarVoz(texto) }
        escuta?.iniciar()
    }

    private fun processarVoz(texto: String) {
        if (aguardandoVideo) return
        val entendeu = maquina.ouvir(texto)
        if (!entendeu) {
            falar("Não entendi. ${maquina.fala()}") {
                if (maquina.passo == PassoRotina.FC_SENTADA || maquina.passo == PassoRotina.ORTOSTATICA) {
                    /* mantém botão câmera */
                } else retomarEscuta()
            }
            return
        }
        if (texto.lowercase().contains("repet")) {
            falar(maquina.fala()) { aposPasso() }
            return
        }
        aposPasso()
    }

    private fun abrirGravacaoFc() {
        if (aguardandoVideo) return
        passoVideo = maquina.passo
        aguardandoVideo = true
        escuta?.parar()
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 18)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }
        gravarVideo.launch(intent)
    }

    private fun falar(texto: String, depois: (() -> Unit)?) {
        val id = "rotina-voz-${System.nanoTime()}"
        val soltar = {
            val motor = tts
            if (motor == null) {
                depois?.invoke()
            } else {
                motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id) runOnUiThread { depois?.invoke() }
                    }
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id) runOnUiThread { depois?.invoke() }
                    }
                })
                motor.speak(texto, TextToSpeech.QUEUE_FLUSH, android.os.Bundle(), id)
            }
        }
        if (ttsPronto) {
            soltar()
            return
        }
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pt", "BR")
                ttsPronto = true
                soltar()
            } else depois?.invoke()
        }
    }

    override fun onDestroy() {
        escuta?.parar()
        player?.release()
        tts?.shutdown()
        executor.shutdown()
        super.onDestroy()
    }
}
