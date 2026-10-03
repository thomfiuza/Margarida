package br.com.monitoridoso.core

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Quadro do pad noturno (ESP32 + ADXL345, eixo Z contra o colchão).
 *
 * Layout, little-endian, a partir do offset 0:
 *  0xAA 0x55 | versão u8 = 1 | flags u8 | seq u16 | fs_hz u16 | n u16
 *  | n amostras int16 do eixo Z, 256 LSB = 1 g (faixa ±2 g do ADXL345).
 *
 * A FC/FR continua no DSP já testado (bcg_noturno.py). Aqui só entra o quadro
 * e a detecção de silêncio — a pessoa saiu do leito — com a mesma regra de
 * sensores.py (energia da janela < 5% do máximo).
 */
data class QuadroPad(val seq: Int, val fsHz: Int, val amostrasG: DoubleArray)

data class SilencioLeito(val silencios: List<Pair<Double, Double>>, val maiorSilencioS: Double)

fun codificarQuadroPad(seq: Int, fsHz: Int, amostrasG: DoubleArray): ByteArray {
    val n = amostrasG.size
    val out = ByteArray(10 + n * 2)
    out[0] = 0xAA.toByte()
    out[1] = 0x55.toByte()
    out[2] = 1
    out[3] = 0
    putU16(out, 4, seq)
    putU16(out, 6, fsHz)
    putU16(out, 8, n)
    for (i in 0 until n) {
        val cru = (amostrasG[i] * 256.0).roundToInt().coerceIn(-32768, 32767)
        putU16(out, 10 + i * 2, if (cru < 0) cru + 65536 else cru)
    }
    return out
}

fun decodificarQuadroPad(dados: ByteArray): QuadroPad? {
    if (dados.size < 10) return null
    if (dados[0] != 0xAA.toByte() || dados[1] != 0x55.toByte()) return null
    if ((dados[2].toInt() and 0xFF) != 1) return null
    val seq = u16(dados, 4)
    val fs = u16(dados, 6)
    val n = u16(dados, 8)
    if (n <= 0 || fs <= 0 || dados.size < 10 + n * 2) return null
    val amostras = DoubleArray(n) { i -> s16(dados, 10 + i * 2) / 256.0 }
    return QuadroPad(seq, fs, amostras)
}

fun detectarSaidaLeito(
    sinal: DoubleArray,
    fs: Double = 100.0,
    janelaS: Double = 20.0
): SilencioLeito {
    val n = (janelaS * fs).toInt()
    if (n <= 0 || sinal.size < n * 2) return SilencioLeito(emptyList(), 0.0)
    val quadros = sinal.size / n
    val energia = DoubleArray(quadros) { i ->
        var acc = 0.0
        val base = i * n
        for (k in 0 until n) {
            val v = sinal[base + k]
            acc += v * v
        }
        sqrt(acc / n)
    }
    val maxE = energia.maxOrNull() ?: 0.0
    val limiar = 0.05 * maxE
    val silencios = mutableListOf<Pair<Double, Double>>()
    var ini: Int? = null
    for (i in 0 until quadros) {
        val quieto = energia[i] < limiar
        if (quieto && ini == null) ini = i
        else if (!quieto && ini != null) {
            silencios.add(round1(ini * janelaS) to round1(i * janelaS))
            ini = null
        }
    }
    if (ini != null) silencios.add(round1(ini * janelaS) to round1(quadros * janelaS))
    val maior = silencios.maxOfOrNull { it.second - it.first } ?: 0.0
    return SilencioLeito(silencios, maior)
}

private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0

private fun putU16(b: ByteArray, o: Int, v: Int) {
    b[o] = (v and 0xFF).toByte()
    b[o + 1] = ((v shr 8) and 0xFF).toByte()
}

private fun u16(b: ByteArray, o: Int): Int =
    (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

private fun s16(b: ByteArray, o: Int): Int {
    val v = u16(b, o)
    return if (v >= 32768) v - 65536 else v
}
