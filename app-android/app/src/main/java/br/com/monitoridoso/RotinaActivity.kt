package br.com.monitoridoso

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import br.com.monitoridoso.core.FalasRotina
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Rotina guiada por voz — espelho de fluxo_idoso.py (MP3 + rPPG + urina + tontura).
 */
class RotinaActivity : AppCompatActivity() {

    private enum class Passo {
        INTRO, REPOUSO, PE, TONTURA, URINA, PROCESSANDO, FIM
    }

    private lateinit var status: TextView
    private lateinit var btnAcao: Button
    private lateinit var btnTonturaSim: Button
    private var passo = Passo.INTRO
    private var player: MediaPlayer? = null
    private var fcRepouso: Double? = null
    private var fcPe: Double? = null
    private var tontura = false
    private var videoRepouso: File? = null
    private var videoPe: File? = null
    private var fotoUrina: File? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var tts: TextToSpeech? = null
    private var ttsPronto = false

    private val permissoes = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    private val gravarVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != RESULT_OK) {
            status.text = "Gravação cancelada. Toque em repetir."
            return@registerForActivityResult
        }
        val uri = res.data?.data
        val dest = when (passo) {
            Passo.REPOUSO -> videoRepouso
            Passo.PE -> videoPe
            else -> null
        }
        if (uri == null || dest == null) {
            status.text = "Vídeo não recebido."
            return@registerForActivityResult
        }
        contentResolver.openInputStream(uri)?.use { inp ->
            dest.outputStream().use { out -> inp.copyTo(out) }
        }
        if (passo == Passo.REPOUSO) {
            passo = Passo.PE
            tocar("04_levante.mp3") { perguntarPe() }
        } else {
            passo = Passo.TONTURA
            perguntarTontura()
        }
    }

    private val fotoUrinaLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) {
            status.text = "Foto cancelada."
            return@registerForActivityResult
        }
        passo = Passo.PROCESSANDO
        finalizarRotina()
    }

    private val vozSimNao = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val falas = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val txt = falas?.firstOrNull()?.lowercase(Locale("pt", "BR")) ?: ""
        tontura = txt.contains("sim")
        passo = Passo.URINA
        tocar("06_urina.mp3") { abrirCameraUrina() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 20f; setPadding(32, 64, 32, 24) }
        btnAcao = Button(this).apply {
            textSize = 20f
            minimumHeight = 160
            setOnClickListener { aoTocarAcao() }
        }
        btnTonturaSim = Button(this).apply {
            text = "Sim, senti tontura"
            visibility = android.view.View.GONE
            setOnClickListener {
                tontura = true
                passo = Passo.URINA
                btnTonturaSim.visibility = android.view.View.GONE
                tocar("06_urina.mp3") { abrirCameraUrina() }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status); addView(btnTonturaSim); addView(btnAcao)
        })
        pedirPermissoes()
        iniciarIntro()
    }

    private fun pedirPermissoes() {
        val faltam = permissoes.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltam.isNotEmpty()) ActivityCompat.requestPermissions(this, faltam.toTypedArray(), 2)
    }

    private fun iniciarIntro() {
        status.text = "Rotina da manhã — siga as instruções em voz alta."
        btnAcao.text = "Começar"
        passo = Passo.INTRO
    }

    private fun aoTocarAcao() {
        when (passo) {
            Passo.INTRO -> tocar("01_boas_vindas.mp3") {
                tocar("02_sente.mp3") { prepararRepouso() }
            }
            Passo.REPOUSO, Passo.PE -> abrirGravacao()
            Passo.TONTURA -> {
                tontura = false
                passo = Passo.URINA
                tocar("06_urina.mp3") { abrirCameraUrina() }
            }
            Passo.URINA -> abrirCameraUrina()
            Passo.FIM -> finish()
            else -> {}
        }
    }

    private fun prepararRepouso() {
        passo = Passo.REPOUSO
        videoRepouso = File(cacheDir, "repouso.mp4")
        status.text = "Sentado e parado. Grave ~15 s do rosto (boa luz)."
        btnAcao.text = "Gravar repouso"
        tocar("03_grava_repouso.mp3", null)
    }

    private fun perguntarPe() {
        passo = Passo.PE
        videoPe = File(cacheDir, "em_pe.mp4")
        status.text = "Em pé, parado. Grave ~15 s do rosto."
        btnAcao.text = "Gravar em pé"
        tocar("05_grava_pe.mp3", null)
    }

    private fun abrirGravacao() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 18)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }
        gravarVideo.launch(intent)
    }

    private fun perguntarTontura() {
        passo = Passo.TONTURA
        status.text = "Ao levantar, sentiu tontura? Diga SIM ou NÃO (ou use os botões)."
        btnAcao.text = "Não senti tontura"
        btnTonturaSim.visibility = android.view.View.VISIBLE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Sentiu tontura ao levantar?")
            }
            try {
                vozSimNao.launch(i)
            } catch (_: Exception) { /* botões bastam */ }
        }
    }

    private fun abrirCameraUrina() {
        fotoUrina = File(cacheDir, "urina.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", fotoUrina!!)
        fotoUrinaLauncher.launch(uri)
    }

    private fun finalizarRotina() {
        status.text = "Analisando medições..."
        btnAcao.isEnabled = false
        executor.execute {
            val monitor = PerfilStore(this).monitor()
            val rRep = EnginePython.medirFcVideo(videoRepouso!!.absolutePath)
            val rPe = EnginePython.medirFcVideo(videoPe!!.absolutePath)
            if (!rRep.ok || !rPe.ok || rRep.fcBpm == null || rPe.fcBpm == null) {
                val qual = if (!rRep.ok) rRep.motivo else rPe.motivo
                runOnUiThread {
                    status.text = "Medição sem qualidade: $qual. Repita em luz e sem movimento."
                    btnAcao.isEnabled = true
                    btnAcao.text = "Repetir rotina"
                    passo = Passo.INTRO
                }
                return@execute
            }
            fcRepouso = rRep.fcBpm
            fcPe = rPe.fcBpm
            var urinaNivel: Int? = null
            val avisosUrina = mutableListOf<String>()
            fotoUrina?.let { f ->
                val ur = EnginePython.classificarUrina(f.absolutePath)
                if (ur.ok && ur.nivel != null) {
                    urinaNivel = ur.nivel
                    if (ur.nivel >= 7) {
                        avisosUrina.add(
                            "Urina nível ${ur.nivel} (escala 1-8): cor muito escura — reidratar e procurar atendimento."
                        )
                    } else if (ur.nivel >= 5) {
                        avisosUrina.add("Urina nível ${ur.nivel}: desidratação moderada — reforçar líquidos hoje.")
                    }
                } else if (ur.motivo != null) {
                    avisosUrina.add("Foto de urina não utilizável: ${ur.motivo}")
                }
            }
            val ev = monitor.registrarRotina(
                fcRepouso!!, fcPe!!, urinaNivel, tontura, avisosUrina
            )
            val mp3Fim = if ((urinaNivel ?: 0) >= 4) "07_agua.mp3" else "08_feito.mp3"
            runOnUiThread {
                status.text = ev.mensagem
                passo = Passo.FIM
                btnAcao.isEnabled = true
                btnAcao.text = "Concluir"
                tocar(mp3Fim, null)
            }
        }
    }

    private fun tocar(asset: String, depois: (() -> Unit)?) {
        try {
            player?.release()
            val afd = assets.openFd("audio/$asset")
            player = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                setOnCompletionListener { depois?.invoke() }
                start()
            }
        } catch (_: Exception) {
            falar(FalasRotina.falaDe(asset), depois)
        }
    }

    /** Os MP3 não estão neste repositório; o TTS do aparelho lê a mesma fala. */
    private fun falar(texto: String, depois: (() -> Unit)?) {
        val id = "rotina-${System.nanoTime()}"
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
            } else {
                depois?.invoke()
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        tts?.shutdown()
        executor.shutdown()
        super.onDestroy()
    }
}
