package br.com.monitoridoso

import android.content.Context
import br.com.monitoridoso.core.Contato
import br.com.monitoridoso.core.MonitorIdosoCore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Cadastro local (SharedPreferences) — LGPD: só no aparelho até export explícito. */
class PerfilStore(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun configurado(): Boolean =
        prefs.getBoolean(KEY_CONSENTIMENTO, false) &&
            prefs.getString(KEY_NOME, null)?.isNotBlank() == true &&
            contatos().isNotEmpty()

    fun nomeIdoso(): String = prefs.getString(KEY_NOME, "") ?: ""

    fun consentimento(): Boolean = prefs.getBoolean(KEY_CONSENTIMENTO, false)

    fun contatos(): List<Contato> {
        val raw = prefs.getString(KEY_CONTATOS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Contato(o.getString("nome"), o.getString("telefone"))
        }
    }

    fun horaLembrete(): Pair<Int, Int> =
        prefs.getInt(KEY_LEMBRETE_H, 8).coerceIn(6, 9) to
            prefs.getInt(KEY_LEMBRETE_M, 0).coerceIn(0, 59)

    fun lembreteEnviadoEm(dia: LocalDate): Boolean =
        prefs.getString(KEY_LEMBRETE_DATA, null) == dia.toString()

    fun marcarLembreteEnviado(dia: LocalDate) {
        prefs.edit().putString(KEY_LEMBRETE_DATA, dia.toString()).apply()
    }

    fun salvar(
        nome: String,
        contatos: List<Contato>,
        consentimento: Boolean,
        lembreteHora: Int = 8,
        lembreteMinuto: Int = 0
    ) {
        val arr = JSONArray()
        for (c in contatos) {
            arr.put(JSONObject().apply {
                put("nome", c.nome)
                put("telefone", c.telefone)
            })
        }
        prefs.edit()
            .putString(KEY_NOME, nome.trim())
            .putString(KEY_CONTATOS, arr.toString())
            .putBoolean(KEY_CONSENTIMENTO, consentimento)
            .putInt(KEY_LEMBRETE_H, lembreteHora.coerceIn(6, 9))
            .putInt(KEY_LEMBRETE_M, lembreteMinuto.coerceIn(0, 59))
            .apply()
    }

    /** Código que a casa (Home Assistant / Tuya) precisa repetir em cada evento. */
    fun tokenPonte(): String {
        val atual = prefs.getString(KEY_TOKEN, null)
        if (!atual.isNullOrBlank()) return atual
        val novo = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        prefs.edit().putString(KEY_TOKEN, novo).apply()
        return novo
    }

    fun monitor(): MonitorIdosoCore = MonitorIdosoCore(
        nome = nomeIdoso().ifBlank { "Idoso" },
        contatos = contatos(),
        diarioPath = File(ctx.filesDir, "diario.jsonl")
    )

    companion object {
        private const val PREFS = "margarida_perfil"
        private const val KEY_NOME = "nome_idoso"
        private const val KEY_CONTATOS = "contatos"
        private const val KEY_CONSENTIMENTO = "consentimento_lgpd"
        private const val KEY_LEMBRETE_H = "lembrete_hora"
        private const val KEY_LEMBRETE_M = "lembrete_minuto"
        private const val KEY_LEMBRETE_DATA = "lembrete_enviado_data"
        private const val KEY_TOKEN = "token_ponte"
    }
}
