package br.com.monitoridoso

import android.content.Context
import android.util.Log
import br.com.monitoridoso.core.decodificarQuadroPad
import br.com.monitoridoso.core.detectarSaidaLeito
import java.io.File
import java.util.Calendar

/**
 * Junta quadros do pad (broadcast QUADRO_PAD, extra quadro_hex) até 120 s
 * e só então chama o DSP noturno. Silêncio longo vira saída do leito.
 */
object PadIngestor {

    const val ACAO = "br.com.monitoridoso.QUADRO_PAD"
    private const val MIN_S = 120.0

    fun ingerirHex(ctx: Context, hex: String) {
        val limpo = hex.trim().replace(" ", "")
        if (limpo.length < 20 || limpo.length % 2 != 0) return
        val bytes = ByteArray(limpo.length / 2) { i ->
            limpo.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val quadro = decodificarQuadroPad(bytes) ?: return
        val arq = File(ctx.filesDir, "pad_buffer.txt")
        synchronized(this) {
            if (!arq.exists() || arq.length() == 0L) arq.writeText("fs=${quadro.fsHz}\n")
            arq.appendText(quadro.amostrasG.joinToString("\n") + "\n")
            val linhas = arq.readLines()
            val fs = linhas.firstOrNull()?.removePrefix("fs=")?.toIntOrNull() ?: quadro.fsHz
            val amostras = linhas.drop(1).mapNotNull { it.toDoubleOrNull() }.toDoubleArray()
            if (fs <= 0 || amostras.size / fs.toDouble() < MIN_S) return
            val monitor = PerfilStore(ctx).monitor()
            try {
                val est = EnginePython.estimarNoite(amostras, fs.toDouble())
                if (est.ok && est.fcBpm != null && est.frIrpm != null)
                    monitor.registrarNoite(
                        est.fcBpm, est.frIrpm,
                        mapOf("ok" to true, "origem" to "pad")
                    )
            } catch (e: Exception) {
                Log.w("Pad", "DSP do pad indisponível: ${e.message}")
            }
            val sil = detectarSaidaLeito(amostras, fs.toDouble())
            val minutos = sil.maiorSilencioS / 60.0
            if (minutos >= 10.0) {
                val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                monitor.registrarEventoSensor(
                    "saida_leito", mapOf("minutos" to minutos), hora
                )
            }
            arq.delete()
        }
    }
}
