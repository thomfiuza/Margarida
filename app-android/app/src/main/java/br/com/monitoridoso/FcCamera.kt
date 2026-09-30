package br.com.monitoridoso

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import br.com.monitoridoso.core.Pulso
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Mede a FC por rPPG com a câmera traseira, SEM preview (dedo na lente).
 * Coleta a média de luminância (plano Y) por frame; a matemática vive em
 * :core (Pulso.estimarBpm — testada 12/12 na JVM).
 *
 * v1: luminância no lugar do canal verde puro (YUV_420); suficiente para
 * dedo cobrindo a lente, onde o sinal é forte. Trocar por RGB é pontual.
 */
object FcCamera {

    private const val TAG = "FcCamera"

    /**
     * Mede por [segundos] e devolve bpm (ou null se não conseguiu) NO THREAD
     * MAIN. O dedo deve cobrir a lente traseira durante toda a medição.
     */
    fun medir(
        owner: LifecycleOwner,
        ctx: android.content.Context,
        segundos: Int,
        aoTerminar: (Double?) -> Unit
    ) {
        val amostras = ArrayList<Pair<Double, Double>>()
        var executor: ExecutorService? = null
        val main = Handler(Looper.getMainLooper())

        val futuro = ProcessCameraProvider.getInstance(ctx)
        futuro.addListener({
            try {
                val provider = futuro.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                executor = Executors.newSingleThreadExecutor().also { ex ->
                    analysis.setAnalyzer(ex, { image: ImageProxy ->
                        val y = image.planes[0]
                        val buf = y.buffer
                        val stride = y.rowStride
                        val w = image.width
                        val h = image.height
                        var soma = 0L
                        var n = 0
                        // amostra 1 pixel a cada 4 linhas/colunas (rápido e estável)
                        var row = 0
                        while (row < h) {
                            var col = 0
                            while (col < w) {
                                soma += (buf.get(row * stride + col).toInt() and 0xFF)
                                n++
                                col += 4
                            }
                            row += 4
                        }
                        if (n > 0) amostras.add(
                            System.nanoTime() / 1e9 to soma.toDouble() / n
                        )
                        image.close()
                    })
                }
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)

                main.postDelayed({
                    try { provider.unbindAll() } catch (_: Exception) {}
                    executor?.shutdownNow()
                    val bpm = if (amostras.size >= 2) {
                        val fs = amostras.size / (amostras.last().first - amostras.first().first)
                        Pulso.estimarBpm(amostras.map { it.second }, fs)
                    } else null
                    Log.i(TAG, "amostras=${amostras.size} bpm=${bpm}")
                    aoTerminar(bpm)
                }, segundos * 1000L)
            } catch (e: Exception) {
                Log.w(TAG, "câmera indisponível: ${e.message}")
                main.post { aoTerminar(null) }
            }
        }, ContextCompat.getMainExecutor(ctx))
    }
}
