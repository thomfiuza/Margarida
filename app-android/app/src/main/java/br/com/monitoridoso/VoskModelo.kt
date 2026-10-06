package br.com.monitoridoso

import android.content.Context
import java.io.File

/** Modelo pt-BR offline — não vai no Git (≈40 MB). Ver `assets/vosk/LERME.txt`. */
object VoskModelo {

    const val PASTA = "vosk-model-small-pt-0.3"

    fun caminho(ctx: Context): String? {
        val dir = File(ctx.filesDir, PASTA)
        if (File(dir, "am/final.mdl").exists()) return dir.absolutePath
        return null
    }
}
