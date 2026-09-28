package br.com.monitoridoso

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import br.com.monitoridoso.core.GatewayChamada
import br.com.monitoridoso.core.GatewaySms

/**
 * ROTA B do SOS (sem internet): chamadas nativas em sequência + SMS local.
 * Limitação aceita (ver especificacao_android.md, seção 4): o Android não
 * permite tocar a mensagem gravada para quem atende — ela sai no alto-falante
 * do aparelho do idoso.
 *
 * ROTA A (com internet): trocar por GatewayChamadaNuvem que faz POST para o
 * backend Twilio (contrato na seção 7) — a lógica de sequência/contexto é a
 * mesma, vive em :core.
 */
class GatewayChamadaNativo(private val ctx: Context, private val atividade: Activity) : GatewayChamada {

    override fun ligar(telefone: String, audioPath: String?): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return false
        val uri = Uri.parse("tel:$telefone")
        atividade.startActivity(Intent(Intent.ACTION_CALL, uri))
        // MVP: janela fixa de 25 s para atender; depois derruba via
        // TelecomManager.endCall() (API 28+, exige ANSWER_PHONE_CALLS).
        // Detecção real de atendimento exige TelephonyCallback (API 31+).
        Thread.sleep(25_000)
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
        try {
            tm.endCall()
        } catch (e: SecurityException) {
            // sem a permissão: a próxima ligação substitui a atual
        }
        return false // rota B não confirma atendimento com confiabilidade
    }
}

class GatewaySmsAndroid(private val ctx: Context) : GatewaySms {
    override fun enviar(telefone: String, texto: String): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) return false
            @Suppress("DEPRECATION")
            val sm = SmsManager.getDefault()
            sm.sendMultipartTextMessage(
                telefone, null, sm.divideMessage(texto), null, null
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
