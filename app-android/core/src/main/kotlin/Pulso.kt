package br.com.monitoridoso.core

import kotlin.math.abs

/**
 * Estimador de FC por rPPG — parte PURA e testável (a câmera do Android só
 * coleta a média do canal verde por frame e chama [estimarBpm]).
 * Espelha o protótipo Python de monitor-idoso/:
 *   1) remove deriva lenta (média móvel ~2 s);
 * 2) conta ciclos por cruzamento de zero com histerese (anti-ruído);
 * 3) só aceita 40–180 bpm, senão null ("não consegui medir").
 */
object Pulso {

    fun estimarBpm(sinal: List<Double>, fs: Double): Double? {
        if (fs <= 0 || sinal.size < (fs * 5).toInt()) return null   // mínimo 5 s
        val win = (fs * 2).toInt().coerceAtLeast(1)
        val deriva = List(sinal.size) { i ->
            val a = (i - win / 2).coerceAtLeast(0)
            val b = (i + win / 2 + 1).coerceAtMost(sinal.size)
            var s = 0.0
            for (j in a until b) s += sinal[j]
            s / (b - a)
        }
        val d = List(sinal.size) { sinal[it] - deriva[it] }
        val amp = d.maxOf { abs(it) }
        if (amp <= 0.0) return null
        val h = amp * 0.15
        var ciclos = 0
        var estado = 0
        for (v in d) {
            if (v > h && estado <= 0) { ciclos++; estado = 1 }
            else if (v < -h) estado = -1
        }
        val segundos = sinal.size / fs
        val bpm = ciclos / segundos * 60.0
        return if (bpm in 40.0..180.0) bpm else null
    }
}
