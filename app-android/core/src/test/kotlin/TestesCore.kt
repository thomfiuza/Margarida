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

private fun testWakeWordGate() {
    val g = WakeWordGate(janelaMs = 5_000, debounceMs = 10_000)
    // 1) detectou -> pergunta; 2º "socorro" durante a pergunta não duplica
    check(g.aoDetectar(1_000) == AcaoWake.PERGUNTAR)
    check(g.aoDetectar(1_500) == AcaoWake.NADA)
    // 2) "sim" -> SOS; debounce bloqueia redetectar logo em seguida
    check(g.aoResponder("sim, pode ligar!", 2_000) == AcaoWake.DISPARAR_SOS)
    check(g.aoDetectar(3_000) == AcaoWake.NADA)
    // 3) passado o debounce, "não" cancela
    check(g.aoDetectar(13_000) == AcaoWake.PERGUNTAR)
    check(g.aoResponder("não, foi engano", 14_000) == AcaoWake.CANCELAR)
    // 4) silêncio expira = não (nunca liga na dúvida)
    check(g.aoDetectar(25_000) == AcaoWake.PERGUNTAR)
    check(g.aoExpirar(31_000) == AcaoWake.CANCELAR)
    check(g.aoResponder("sim", 32_000) == AcaoWake.NADA) // janela já fechou
    // 5) modo direto: sem pergunta
    val d = WakeWordGate(modoDireto = true)
    check(d.aoDetectar(0) == AcaoWake.DISPARAR_SOS)
    check(d.aoDetectar(1) == AcaoWake.NADA)
    // 6) tokenizer não confunde substring ("cantando" != "sim"... e "nada" = não)
    check(WakeWordGate.pareceSim("Sim."))
    check(!WakeWordGate.pareceSim("estou cantando uma música"))
    check(WakeWordGate.pareceNao("nada, foi engano"))
    println("  WakeWordGate: confirmação, silêncio=não, debounce e modo direto ✔")
}

private fun testRotinaVozCompleta() {
    val r = MaquinaRotina("Dona Maria")
    check(r.passo == PassoRotina.ABERTURA && "começar" in r.fala())
    check(!r.ouvir("blá blá blá"))                     // não entendeu -> reler
    check(r.ouvir("pode repetir?") && r.passo == PassoRotina.ABERTURA)
    check(r.ouvir("vamos começar") && r.passo == PassoRotina.FC_SENTADA)
    check(r.informarFc(72.0) && r.passo == PassoRotina.ORTOSTATICA)
    check(r.ouvir("tudo bem") == false)               // ortostática só avança com FC
    check(r.informarFc(80.0) && r.passo == PassoRotina.TONTURA)
    check(r.ouvir("não") && r.passo == PassoRotina.URINA && r.tontura == false)
    check(r.ouvir("três") && r.passo == PassoRotina.RESUMO && r.urina == 3)
    val resumo = r.fala()
    check("72" in resumo && "80" in resumo && "3 no cartão" in resumo)
    check(r.ouvir("obrigada") && r.passo == PassoRotina.FIM)
    println("  Rotina por voz: fluxo completo sentado→pé→urina por extenso ✔")
}

private fun testRotinaAbortoPuloENumeros() {
    // abortar em qualquer ponto
    val a = MaquinaRotina("Seu João")
    a.ouvir("sim"); a.informarFc(70.0)
    check(a.ouvir("parar") && a.passo == PassoRotina.ABORTADA)
    // tontura sim + número por dígito
    val b = MaquinaRotina("Dona Zefa")
    b.ouvir("começar"); b.informarFc(66.0); b.informarFc(74.0)
    check(b.ouvir("sim, um pouco") && b.tontura == true)
    check(b.ouvir("cor 8") && b.urina == 8)
    // pular a urina -> resumo sem número
    val c = MaquinaRotina("Dona Rita")
    c.ouvir("sim"); c.informarFc(60.0); c.informarFc(64.0); c.ouvir("não")
    check(c.ouvir("pular") && c.passo == PassoRotina.RESUMO && c.urina == null)
    check("não informada" in c.fala())
    // números fora de 1..10 não avançam
    val d = MaquinaRotina("Seu Zé")
    d.ouvir("sim"); d.informarFc(60.0); d.informarFc(64.0); d.ouvir("não")
    check(!d.ouvir("quarenta"))
    check(MaquinaRotina.numeroUrina("onze") == null)
    println("  Rotina por voz: abortar, pular e números 1–10 só ✔")
}

private fun testIntencoes() {
    // emergência sem a palavra "socorro"
    check(Intencoes.classificar("ai, eu caí no banheiro") == Intencao.EMERGENCIA)
    check(Intencoes.classificar("não consigo me levantar") == Intencao.EMERGENCIA)
    check(Intencoes.classificar("me ajuda, estou passando mal") == Intencao.EMERGENCIA)
    check(Intencoes.classificar("dor no peito") == Intencao.EMERGENCIA_CLINICA) // SAMU primeiro
    check(Intencoes.classificar("socorro") == Intencao.EMERGENCIA)
    // respostas normais
    check(Intencoes.classificar("sim") == Intencao.SIM)
    check(Intencoes.classificar("não, foi engano") == Intencao.NAO)
    check(Intencoes.classificar("pode repetir?") == Intencao.REPETIR)
    check(Intencoes.classificar("que dia bonito") == Intencao.OUTRA)
    // gate: "eu caí" DURANTE a confirmação vira SOS, não cancelamento
    val g = WakeWordGate()
    check(g.aoDetectar(0) == AcaoWake.PERGUNTAR)
    check(g.aoResponder("ai eu cai, não consigo levantar", 1_000) == AcaoWake.DISPARAR_SOS)
    println("  Intenções: 'eu caí'/'me ajuda' = EMERGENCIA e disparam o SOS ✔")
}

private fun testSensorGeladeira() {
    // Mamoriko (JP): geladeira fechada há 6h+ em horário de refeição = alerta
    val d = decidirEvento("geladeira", mapOf("horas_sem_abrir" to 7), 14)
    check(d.nivel == Nivel.ATENCAO && "refeição" in d.mensagem) { "esperava ATENCAO, veio ${d.nivel}" }
    val ok = decidirEvento("geladeira", mapOf("horas_sem_abrir" to 2), 14)
    check(ok.nivel == Nivel.NENHUM)
    val noite = decidirEvento("geladeira", mapOf("horas_sem_abrir" to 8), 3)
    check(noite.nivel == Nivel.NENHUM)   // madrugada não conta
    println("  Sensor geladeira (Mamoriko): 6h+ sem abrir em horário de refeição ✔")
}

private fun testLembretesMed() {
    // dentro da janela: nada
    check(LembretesMed.checar("Losartana", 8, 9, false) == null)
    // passou da janela sem confirmar: ATENCAO
    val a = LembretesMed.checar("Losartana", 8, 11, false)!!
    check(a.nivel == Nivel.ATENCAO)
    // 2ª falta seguida: URGENTE (ligar)
    val u = LembretesMed.checar("Losartana", 8, 20, false, faltasAnteriores = 1)!!
    check(u.nivel == Nivel.URGENTE && "ligar" in u.mensagem)
    // confirmado: registra ok
    val c = LembretesMed.checar("Metformina", 8, 11, true)!!
    check(c.nivel == Nivel.NENHUM)
    println("  Lembretes de remédio: janela, 2ª falta vira URGENTE ✔")
}

private fun testCheckInMimamori() {
    val ok = CheckIn.avaliar(respondeu = true)
    check(ok.nivel == Nivel.NENHUM)
    val um = CheckIn.avaliar(respondeu = false)
    check(um.nivel == Nivel.ATENCAO && "2 h" in um.mensagem)
    val dois = CheckIn.avaliar(respondeu = false, semRespostaAnteriores = 1)
    check(dois.nivel == Nivel.URGENTE && "acionar" in dois.mensagem)
    println("  Check-in ativo (MIMAMORI): 2 dias sem resposta = acionar contatos ✔")
}

private fun testFontesSos() {
    // só a wake word pergunta; sinal físico já é confirmação
    check(PlanoSos.requerConfirmacao(FonteSos.WAKE_WORD))
    check(!PlanoSos.requerConfirmacao(FonteSos.BOTAO_EXTERNO))
    check(!PlanoSos.requerConfirmacao(FonteSos.PULSEIRA))
    check(!PlanoSos.requerConfirmacao(FonteSos.QUEDA_RADAR))
    check("à prova d'água" in PlanoSos.descricao(FonteSos.BOTAO_EXTERNO))
    println("  Camadas de gatilho: botão/pulseira/radar disparam direto ✔")
}

private fun testSamuPrioridade() {
    val fila = Intencoes.ordemChamadas(CONTATOS, Intencoes.classificar("dor no peito"))
    check(fila.first().telefone == "192") { "SAMU deveria abrir a fila, veio ${fila.first()}" }
    check(fila.size == CONTATOS.size + 1)
    // queda comum: fila da família preservada
    val queda = Intencoes.ordemChamadas(CONTATOS, Intencoes.classificar("eu caí"))
    check(queda.first().nome == "Filha Ana")
    // gate: "dor no peito" durante confirmação também dispara
    val g = WakeWordGate()
    check(g.aoDetectar(0) == AcaoWake.PERGUNTAR)
    check(g.aoResponder("estou com dor no peito", 500) == AcaoWake.DISPARAR_SOS)
    println("  Sinal clínico: SAMU abre a fila de ligações ✔")
}

private fun testRotinaSimplificadaAlzheimer() {
    val r = MaquinaRotina("Dona Cida", simplificada = true)
    r.ouvir("começar")
    r.informarFc(70.0)
    r.informarFc(74.0)
    // modo simplificado pula tontura/urina e vai ao resumo
    check(r.passo == PassoRotina.RESUMO) { "esperava RESUMO, veio ${r.passo}" }
    check(r.tontura == null && r.urina == null)
    check("70" in r.fala())
    // modo normal continua perguntando tontura
    val n = MaquinaRotina("Dona Maria")
    n.ouvir("começar"); n.informarFc(70.0); n.informarFc(74.0)
    check(n.passo == PassoRotina.TONTURA)
    println("  Modo Alzheimer: menos perguntas, mesma segurança ✔")
}

private fun testPulsoRppg() {
    val fs = 30.0
    val aleatorio = java.util.Random(42)
    // seno de 80 bpm + 10% de ruído, 30 s
    val alvo = 80.0 / 60.0
    val sinal = List((fs * 30).toInt()) { i ->
        kotlin.math.sin(2 * Math.PI * alvo * i / fs) + 0.1 * aleatorio.nextDouble()
    }
    val bpm = Pulso.estimarBpm(sinal, fs)!!
    check(kotlin.math.abs(bpm - 80.0) <= 3.0) { "esperava ~80, veio $bpm" }
    // 120 bpm curtos (10 s) também resolvem
    val rapido = List((fs * 10).toInt()) { i -> kotlin.math.sin(2 * Math.PI * 2.0 * i / fs) }
    check(kotlin.math.abs(Pulso.estimarBpm(rapido, fs)!! - 120.0) <= 3.0)
    // sinais ruins -> null (não inventa número)
    check(Pulso.estimarBpm(List(60) { 1.0 }, fs) == null)          // reta
    check(Pulso.estimarBpm(List(20) { 1.0 }, fs) == null)          // curto demais
    val lento = List((fs * 10).toInt()) { i -> kotlin.math.sin(2 * Math.PI * 0.3 * i / fs) }
    check(Pulso.estimarBpm(lento, fs) == null)                     // 18 bpm: fora da faixa
    println("  Pulso rPPG: 80 e 120 bpm medidos; reta/curto/fora da faixa = null ✔")
}

private fun testIntegracaoParceiro() {
    // app parceiro confiável vira fonte PARCEIRO, disparo direto (sem pergunta)
    val ok = Integracao.validar(
        PedidoSosParceiro("app-historico-mariana", true, "SOS acionado na tela do parceiro")
    )
    check(ok == FonteSos.PARCEIRO) { "parceiro confiavel devia virar PARCEIRO" }
    check(!PlanoSos.requerConfirmacao(FonteSos.PARCEIRO)) { "PARCEIRO nao pede confirmacao" }
    // app desconhecido ou não identificado é barrado na porta
    check(Integracao.validar(PedidoSosParceiro("app-qualquer", false)) == null)
    check(Integracao.validar(PedidoSosParceiro("   ", true)) == null)
    // histórico exportado é JSON local com tudo que está no diário
    val m = novoMonitor("integracao")
    m.registrarRotina(72.0, 78.0, 3, false)
    val hist = Integracao.historicoJson(m.diario)
    check(hist.startsWith("[") && hist.endsWith("]")) { "historico deve ser array JSON" }
    check(hist.contains("rotina")) { "historico deve conter o evento registrado" }
    println("  Integração: SOS parceiro sem confirmação, desconhecido barrado, histórico local ✔")
}

private fun testResumoSemana() {
    val m = novoMonitor("resumo_semana")
    m.registrarRotina(70.0, 74.0, 2, false)              // rotina tranquila
    m.registrarRotina(75.0, 100.0, 5, true)             // rotina com atenção
    m.registrarNoite(66.0, 14.0, mapOf("ok" to true))   // noite tranquila
    val antigo = Evento("rotina", Nivel.NENHUM, "fora da janela",
        quando = java.time.LocalDateTime.now().minusDays(10).withNano(0).toString())
    m.diario.registrar(antigo)                          // não deve entrar no resumo
    val r = ResumoSemana.gerar(m.diario, 7)
    check(r.contains("nenhum SOS")) { "resumo: $r" }
    check(r.contains("Rotinas: 1") && r.contains("1 com ponto de atencao")) { "resumo: $r" }
    check(r.contains("Noites: 1")) { "resumo: $r" }
    check(!r.contains("fora da janela")) { "evento antigo nao pode entrar no resumo" }
    println("  Resumo semanal: 100% local, janela de 7 dias, texto humano ✔")
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
        "testInatividadeManha" to ::testInatividadeManha,
        "testWakeWordGate" to ::testWakeWordGate,
        "testRotinaVozCompleta" to ::testRotinaVozCompleta,
        "testRotinaAbortoPuloENumeros" to ::testRotinaAbortoPuloENumeros,
        "testIntencoes" to ::testIntencoes,
        "testSensorGeladeira" to ::testSensorGeladeira,
        "testLembretesMed" to ::testLembretesMed,
        "testCheckInMimamori" to ::testCheckInMimamori,
        "testFontesSos" to ::testFontesSos,
        "testSamuPrioridade" to ::testSamuPrioridade,
        "testRotinaSimplificadaAlzheimer" to ::testRotinaSimplificadaAlzheimer,
        "testPulsoRppg" to ::testPulsoRppg,
        "testIntegracaoParceiro" to ::testIntegracaoParceiro,
        "testResumoSemana" to ::testResumoSemana,
        "testTendenciasRotina" to ::testTendenciasRotina,
        "testWakeWordSocorroEConfirmacao" to ::testWakeWordSocorroEConfirmacao,
        "testPonteTuyaMatter" to ::testPonteTuyaMatter,
        "testQuadroPadBcg" to ::testQuadroPadBcg,
        "testFalasRotina" to ::testFalasRotina
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
