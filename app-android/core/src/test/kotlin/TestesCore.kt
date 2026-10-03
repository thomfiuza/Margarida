package br.com.monitoridoso.core

import java.io.File
import kotlin.system.exitProcess

/**
 * Testes JVM do núcleo Kotlin — espelham testes_panico.py (5), os limiares de
 * orquestrador.py e o round-trip do JSON. Rodam sem Android:
 *   java -jar core-testes.jar
 */
private val TMP = File(System.getProperty("java.io.tmpdir"), "core_test_${System.nanoTime()}")
private val CONTATOS = listOf(
    Contato("Filha Ana", "+5534999990001"),
    Contato("Vizinho Beto", "+5534999990002"),
    Contato("SAMU", "192")
)
private val LOC = Localizacao(-19.5937, -46.9409, 8.0)

private class ChamadaFalsa(private val atendem: Set<String> = emptySet()) : GatewayChamada {
    val registro = mutableListOf<Pair<String, String?>>()
    override fun ligar(telefone: String, audioPath: String?): Boolean {
        registro.add(telefone to audioPath)
        return telefone in atendem
    }
}

private class SmsFalso(private val falham: Set<String> = emptySet()) : GatewaySms {
    val mensagens = mutableMapOf<String, String>()
    override fun enviar(telefone: String, texto: String): Boolean {
        if (telefone in falham) return false
        mensagens[telefone] = texto
        return true
    }
}

private fun novoMonitor(nome: String) =
    MonitorIdosoCore("Dona Maria", CONTATOS, File(TMP, "$nome.jsonl"))

private fun testSosSequenciaEContexto() {
    val m = novoMonitor("sos_ctx")
    // rotina urgente (urina 8) + noite com FC 16 bpm acima da média
    val r = m.registrarRotina(70.0, 75.0, urina = 8, tontura = false)
    check(r.nivel == Nivel.URGENTE) { "rotina urina 8 deveria ser urgente, foi ${r.nivel}" }
    repeat(5) { m.registrarNoite(64.0, 14.0, mapOf("ok" to true)) }
    val n = m.registrarNoite(80.0, 14.0, mapOf("ok" to true))
    check(n.nivel == Nivel.URGENTE) { "noite +16 bpm deveria ser urgente, foi ${n.nivel}" }
    check("acima da media" in n.mensagem)
    // SOS carrega o contexto de saúde
    val sms = SmsFalso()
    val ev = m.emergencia(LOC, ChamadaFalsa(setOf("192")), sms)
    check(ev.nivel == Nivel.URGENTE && "SAMU" in ev.mensagem)
    val texto = sms.mensagens["192"]!!
    check("EMERGENCIA" in texto && "maps.google.com" in texto)
    check("Contexto de saude" in texto && "noites com sinal de alerta" in texto) {
        "SMS do SOS sem contexto: $texto"
    }
    println("  SOS: 3 ligações, contexto de saúde no SMS ✔")
}

private fun testSosSemLocalizacaoECancelamento() {
    val sms = SmsFalso()
    val rel = acionarPanico("Dona Maria", CONTATOS, ChamadaFalsa(), sms, localizacao = null)
    check("indisponivel" in rel.mensagemSms)
    val rel2 = acionarPanico("Dona Maria", CONTATOS, ChamadaFalsa(), SmsFalso(), confirmar = { false })
    check(rel2.status == "cancelado" && rel2.tentativas.isEmpty())
    val rel3 = acionarPanico("Dona Maria", emptyList(), ChamadaFalsa(), SmsFalso())
    check(rel3.status == "sem_contatos")
    println("  sem GPS → avisa; confirmação 'não' → zero ligações ✔")
}

private fun testRegrasOrtostaticas() {
    check(nivelAlerta(avaliarOrtostatica(70.0, 72.0, tontura = true), null) == Nivel.URGENTE)
    check(avaliarOrtostatica(70.0, 105.0, false).any { ">30 bpm" in it })
    check(avaliarOrtostatica(110.0, 115.0, false).any { "100 bpm" in it })
    check(nivelAlerta(emptyList(), 5) == Nivel.ATENCAO)
    check(nivelAlerta(emptyList(), 7) == Nivel.URGENTE)
    check(nivelAlerta(emptyList(), 3) == Nivel.NENHUM)
    println("  ortostática/urina: HO=urgente, Δ>30, FC>100, urina 5/7 ✔")
}

private fun testJsonRoundTrip() {
    val ev = Evento("noite", Nivel.ATENCAO, "mensagem com \"aspas\" e acentuação",
        dados = mapOf("fc_bpm" to 72.5, "ok" to true, "lista" to listOf(1, 2)))
    val volta = Evento.doJson(ev.json())
    check(volta == ev) { "round-trip falhou: ${ev.json()}" }
    println("  JSON Evento: escrita → leitura idênticas ✔")
}

private fun testResumoDoDia() {
    val m = novoMonitor("resumo")
    m.registrarRotina(70.0, 75.0, null, false)
    m.registrarNoite(64.0, 14.0, mapOf("ok" to true))
    m.emergencia(LOC, ChamadaFalsa(), SmsFalso())
    val r = m.resumoDoDia()
    check(r["eventos"] == 3 && r["pior_nivel"] == "urgente") { "$r" }
    println("  resumo do dia: 3 eventos, pior nível urgente ✔")
}

private fun testSensorQuedaDisparaSos() {
    val m = novoMonitor("sensor_kt")
    m.registrarRotina(70.0, 75.0, urina = 8, tontura = false)   // deixa alerta p/ contexto
    val sms = SmsFalso()
    val ev = m.registrarEventoSensor("queda", mapOf("imovel_s" to 30), hora = 14,
        chamar = ChamadaFalsa(setOf("192")), sms = sms)
    check(ev.nivel == Nivel.URGENTE && "QUEDA CONFIRMADA" in ev.mensagem) { ev.mensagem }
    check("SOS disparado" in ev.mensagem)
    check("Contexto de saude" in sms.mensagens["192"]!!)
    println("  Kotlin: radar queda 30 s -> SOS sequencial com contexto ✔")
}

private fun testRegrasSensoresPortadas() {
    check(decidirEvento("queda", mapOf("imovel_s" to 8), 15).nivel == Nivel.ATENCAO)
    check(decidirEvento("gas", mapOf("detectado" to true), 14).acao == "sos")
    check(decidirEvento("porta", mapOf("aberta" to true), 2).nivel == Nivel.ATENCAO)
    check(decidirEvento("porta", mapOf("aberta" to true), 10).nivel == Nivel.NENHUM)
    check(decidirEvento("remedio", mapOf("aberto_hoje" to false), 11).nivel == Nivel.ATENCAO)
    check(decidirEvento("saida_leito", mapOf("minutos" to 14), 3).nivel == Nivel.ATENCAO)
    check(decidirEvento("saida_leito", mapOf("minutos" to 3), 3).nivel == Nivel.NENHUM)
    println("  Kotlin: queda/gás/porta/remédio/leito iguais ao Python ✔")
}

private fun testTendenciasRotina() {
    val m = novoMonitor("tend_kt")
    repeat(3) { m.registrarRotina(70.0, 75.0, urina = 3, tontura = false) }
    val ev = m.registrarRotina(82.0, 88.0, urina = 6, tontura = false)
    check(ev.mensagem.contains("média recente")) { ev.mensagem }
    check(ev.mensagem.contains("mais escura")) { ev.mensagem }
    check(ev.nivel == Nivel.ATENCAO) { "tendência + urina 6 -> atenção, foi ${ev.nivel}" }
    println("  tendências FC/urina vs linha de base ✔")
}

private fun testWakeWordSocorroEConfirmacao() {
    check(ehWakeWordSocorro("Socorro!"))
    check(ehWakeWordSocorro("me socorre agora"))
    check(ehWakeWordSocorro("Socoro"))
    check(!ehWakeWordSocorro("está tudo bem"))
    check(!ehWakeWordSocorro("socorrista passou"))
    check(decidirConfirmacaoSos(listOf("não, foi engano"), 1_000) == DecisaoWake.CANCELAR)
    check(decidirConfirmacaoSos(emptyList(), 9_000) == DecisaoWake.AGUARDAR)
    check(decidirConfirmacaoSos(emptyList(), 10_000) == DecisaoWake.DISPARAR)
    check(decidirConfirmacaoSos(listOf("sim"), 500) == DecisaoWake.DISPARAR)
    println("  wake word socorro + 10 s sem 'não' dispara ✔")
}

private fun testPonteTuyaMatter() {
    val queda = normalizarEventoSensor(
        """{"protocolo":"tuya","papel":"radar","hora":15,"token":"abc","status":[{"code":"fall_state","value":"fall"},{"code":"motionless_time","value":30}]}""",
        12
    )!!
    val d1 = decidirEvento(queda.tipo, queda.payload, queda.hora)
    check(d1.nivel == Nivel.URGENTE && d1.acao == "sos")
    check("token" !in queda.payload)
    val maybe = normalizarEventoSensor(
        """{"protocolo":"tuya","papel":"radar","hora":15,"status":[{"code":"fall_state","value":"maybe"},{"code":"motionless_time","value":8}]}""",
        12
    )!!
    check(decidirEvento(maybe.tipo, maybe.payload, maybe.hora).acao == "notificar")
    val gas = normalizarEventoSensor(
        """{"protocolo":"tuya","papel":"gas","hora":14,"status":[{"code":"gas_sensor_state","value":"alarm"}]}""",
        12
    )!!
    check(decidirEvento(gas.tipo, gas.payload, gas.hora).acao == "sos")
    val porta = normalizarEventoSensor(
        """{"protocolo":"matter","papel":"porta","hora":2,"attributes":{"StateValue":true}}""",
        12
    )!!
    check(decidirEvento(porta.tipo, porta.payload, porta.hora).nivel == Nivel.ATENCAO)
    val remedio = normalizarEventoSensor(
        """{"protocolo":"tuya","papel":"remedio","hora":11,"status":[{"code":"doorcontact_state","value":false}]}""",
        12
    )!!
    check(decidirEvento(remedio.tipo, remedio.payload, remedio.hora).nivel == Nivel.ATENCAO)
    val leito = normalizarEventoSensor(
        """{"protocolo":"matter","papel":"leito","hora":3,"attributes":{"BedExitMinutes":14}}""",
        12
    )!!
    check(decidirEvento(leito.tipo, leito.payload, leito.hora).nivel == Nivel.ATENCAO)
    val canon = normalizarEventoSensor("""{"tipo":"queda","hora":14,"imovel_s":30,"token":"abc"}""", 12)!!
    check(decidirEvento(canon.tipo, canon.payload, canon.hora).acao == "sos")
    check("token" !in canon.payload)
    check(normalizarEventoSensor("""{"protocolo":"zigbee","papel":"radar"}""", 12) == null)
    check(normalizarEventoSensor("""{"protocolo":"tuya","papel":"radar","status":[{"code":"fall_state","value":"normal"}]}""", 12) == null)
    val raiz = Json.parse("""{"token":"abc"}""") as Map<String, Any?>
    check(tokenConfere(raiz, "abc") && !tokenConfere(raiz, "outro") && !tokenConfere(emptyMap(), ""))
    println("  ponte Tuya/Matter/canônico → mesmas decisões dos sensores ✔")
}

private fun testQuadroPadBcg() {
    val bruto = codificarQuadroPad(7, 100, doubleArrayOf(1.0, -0.5, 0.0))
    val q = decodificarQuadroPad(bruto)!!
    check(q.seq == 7 && q.fsHz == 100)
    check(kotlin.math.abs(q.amostrasG[0] - 1.0) < 1e-9)
    check(kotlin.math.abs(q.amostrasG[1] + 0.5) < 1e-9)
    check(decodificarQuadroPad(byteArrayOf(0, 1, 2)) == null)
    check(decodificarQuadroPad(bruto.copyOf(bruto.size - 1)) == null)
    val sinal = DoubleArray(1000) { i -> if (i in 400..599) 0.0 else 1.0 }
    val sil = detectarSaidaLeito(sinal, fs = 10.0, janelaS = 20.0)
    check(sil.maiorSilencioS == 20.0) { "$sil" }
    check(sil.silencios == listOf(40.0 to 60.0)) { sil.silencios.toString() }
    val cheio = detectarSaidaLeito(DoubleArray(800) { 0.8 }, fs = 10.0, janelaS = 20.0)
    check(cheio.silencios.isEmpty())
    println("  pad ADXL: quadro 1 g sobrevive; 20 s de silêncio no leito ✔")
}

private fun testFalasRotina() {
    val esperados = listOf(
        "01_boas_vindas.mp3", "02_sente.mp3", "03_grava_repouso.mp3",
        "04_levante.mp3", "05_grava_pe.mp3", "06_urina.mp3",
        "07_agua.mp3", "08_feito.mp3", "09_mensagem_panico.mp3"
    )
    check(FalasRotina.porArquivo.keys.toList() == esperados)
    check(FalasRotina.porArquivo.values.all { it.length > 12 })
    check("ajuda" in FalasRotina.falaDe("09_mensagem_panico.mp3"))
    println("  9 falas da rotina (TTS quando o MP3 não está no APK) ✔")
}

private fun testInatividadeManha() {
    val m = novoMonitor("inativo_kt")
    val agora = java.time.LocalDateTime.now().withHour(10).withMinute(30).withSecond(0)
    val ev = m.checarInatividade(agora)
    check(ev != null && ev.nivel == Nivel.ATENCAO) { "deveria alertar" }
    check(m.checarInatividade(agora.plusHours(1)) == null)   // 1 alerta por dia
    val cedo = java.time.LocalDateTime.now().withHour(8).withMinute(0)
    check(m.checarInatividade(cedo) == null)                 // antes do limite, nada
    println("  Kotlin: rotina não feita até 10h -> 1 alerta/dia ✔")
}

fun main() {
    TMP.mkdirs()
    val testes: List<Pair<String, () -> Unit>> = listOf(
        "testSosSequenciaEContexto" to ::testSosSequenciaEContexto,
        "testSosSemLocalizacaoECancelamento" to ::testSosSemLocalizacaoECancelamento,
        "testRegrasOrtostaticas" to ::testRegrasOrtostaticas,
        "testJsonRoundTrip" to ::testJsonRoundTrip,
        "testResumoDoDia" to ::testResumoDoDia,
        "testSensorQuedaDisparaSos" to ::testSensorQuedaDisparaSos,
        "testRegrasSensoresPortadas" to ::testRegrasSensoresPortadas,
        "testTendenciasRotina" to ::testTendenciasRotina,
        "testWakeWordSocorroEConfirmacao" to ::testWakeWordSocorroEConfirmacao,
        "testPonteTuyaMatter" to ::testPonteTuyaMatter,
        "testQuadroPadBcg" to ::testQuadroPadBcg,
        "testFalasRotina" to ::testFalasRotina,
        "testInatividadeManha" to ::testInatividadeManha
    )
    var falhas = 0
    for ((nome, t) in testes) {
        try {
            t()
            println("PASS  $nome")
        } catch (e: Throwable) {
            falhas++
            println("FAIL  $nome: ${e.message}")
        }
    }
    println("\n${testes.size - falhas}/${testes.size} testes do núcleo Kotlin passaram")
    exitProcess(if (falhas > 0) 1 else 0)
}
