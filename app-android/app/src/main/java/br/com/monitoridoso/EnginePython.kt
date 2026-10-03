package br.com.monitoridoso

import com.chaquo.python.Python
import org.json.JSONObject

/** rPPG e urina — mesma matemática de fc_camera.py / urina.py (Chaquopy). */
object EnginePython {

    private val mod by lazy {
        Python.getInstance().getModule("margarida_engine")
    }

    data class FcResult(val ok: Boolean, val fcBpm: Double?, val motivo: String?) {
        companion object {
            fun fromJson(s: String): FcResult {
                val o = JSONObject(s)
                return FcResult(
                    ok = o.optBoolean("ok", false),
                    fcBpm = if (o.has("fc_bpm") && !o.isNull("fc_bpm")) o.getDouble("fc_bpm") else null,
                    motivo = o.optString("motivo", null)
                )
            }
        }
    }

    data class UrinaResult(val ok: Boolean, val nivel: Int?, val motivo: String?, val avisos: List<String>) {
        companion object {
            fun fromJson(s: String): UrinaResult {
                val o = JSONObject(s)
                val avisos = mutableListOf<String>()
                o.optJSONArray("avisos")?.let { arr ->
                    for (i in 0 until arr.length()) avisos.add(arr.getString(i))
                }
                return UrinaResult(
                    ok = o.optBoolean("ok", false),
                    nivel = if (o.has("nivel_armstrong")) o.getInt("nivel_armstrong") else null,
                    motivo = o.optString("motivo", null),
                    avisos = avisos
                )
            }
        }
    }

    fun medirFcVideo(path: String): FcResult =
        FcResult.fromJson(mod.callAttr("medir_fc_video", path).toString())

    fun classificarUrina(path: String): UrinaResult =
        UrinaResult.fromJson(mod.callAttr("classificar_urina_foto", path).toString())

    data class NoiteResult(
        val ok: Boolean,
        val fcBpm: Double?,
        val frIrpm: Double?,
        val motivo: String?
    ) {
        companion object {
            fun fromJson(s: String): NoiteResult {
                val o = JSONObject(s)
                return NoiteResult(
                    ok = o.optBoolean("ok", false),
                    fcBpm = if (o.has("fc_bpm") && !o.isNull("fc_bpm")) o.getDouble("fc_bpm") else null,
                    frIrpm = if (o.has("fr_irpm") && !o.isNull("fr_irpm")) o.getDouble("fr_irpm") else null,
                    motivo = if (o.has("motivo") && !o.isNull("motivo")) o.optString("motivo") else null
                )
            }
        }
    }

    fun estimarNoite(amostrasG: DoubleArray, fs: Double): NoiteResult {
        val json = amostrasG.joinToString(prefix = "[", postfix = "]")
        return NoiteResult.fromJson(mod.callAttr("estimar_noite", json, fs).toString())
    }
}
