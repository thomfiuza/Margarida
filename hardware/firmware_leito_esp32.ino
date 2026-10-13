// ============================================================
// MARGARIDA, nó de leito (ESP32), versão 0.2
// Fita sob o colchão: 4 células de carga em ponte + HX711.
// Detecta presença, saída do leito e respiração (BCG),
// publicando eventos MQTT no broker local da instituição.
//
// STATUS: rascunho de engenharia. Passa em teste de bancada
// (hardware/TESTE_BANCADA.md). NÃO validado em pessoa idosa.
//
// O que mudou da 0.1 para a 0.2. Cada item foi encontrado pela
// bancada de simulação em hardware/bancada/ e está coberto por
// um cenário em hardware/bancada/rodar_testes.py.
//
//   1. O ALERTA DE RESPIRAÇÃO NUNCA DISPARAVA. O bloco de
//      presença zerava tRpmBaixa em toda iteração, e o idoso
//      deitado é justamente a condição do alerta. O contador
//      voltava a zero 4 vezes por segundo e nunca chegava aos
//      20 s. Este era o defeito mais grave do projeto: a
//      função inteira estava morta.
//   2. rpm estava pela metade. Cada respiração gera DOIS
//      cruzamentos da linha de base, então a conversão é 6.0,
//      não 3.0. No 0.1, 12 respirações por minuto eram lidas
//      como 6.
//   3. As janelas de 15 s e 20 s contavam 100 ms por laço, mas
//      o laço dura 200 a 250 ms (leitura do HX711 + delay). O
//      alerta de LEVANTOU saía 38 s depois da saída do leito,
//      quando a proposta do projeto promete menos de 10 s.
//      Agora o tempo é medido com millis() e o período real do
//      laço vai no tópico debug.
//   4. reconectar() bloqueava o laço inteiro enquanto o MQTT
//      estivesse fora. Numa queda de rede a placa parava de ler
//      o peso e podia reiniciar por watchdog. Agora a reconexão
//      é por tentativa com intervalo de 5 s.
//   5. O nó só tentava conectar 5 s depois de ligado e jogava
//      fora tudo que acontecesse nesse intervalo. Um idoso que
//      levantasse da cama nesse minuto não gerava alerta. Agora
//      existe fila: o que não consegue publicar fica guardado e
//      sai quando a rede volta.
//   6. A linha de base era atualizada 10 vezes por segundo e
//      passava a acompanhar o próprio sinal de respiração em vez
//      de filtrá-lo. Agora atualiza 1 vez por segundo e só conta
//      cruzamento depois de convergir.
//   7. O alerta respiratório exigia 20 s contínuos, mas a leitura
//      de uma janela pula entre 6 e 9 numa respiração estável de
//      7. Uma janela boa segurava o contador para sempre. Agora a
//      decisão usa média suavizada de 20 s e o contador não zera
//      por uma janela isolada.
//   8. A média suavizada partia do zero, ficava "abaixo do mínimo"
//      nos primeiros 20 s e dava alarme falso em todo boot. Agora
//      a primeira leitura semeia a média.
//   9. A primeira janela de rpm fechava em 1 s em vez de 10 e
//      reportava um número errado.
//  10. O campo ts mandava millis() em vez de horário real,
//      contrariando o PROTOCOLO_MQTT.md. Agora tem NTP.
//  11. Calibração feita por MQTT (margarida/{inst}/leito/{id}/calibra),
//      sem precisar regravar a placa a cada leito.
// ============================================================

#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <HX711.h>
#include <time.h>

// ---- configuração por unidade (gravada no build) ----
const char* WIFI_SSID  = "MARGARIDA-SV";        // rede mesh dedicada
const char* WIFI_PASS  = "margarida2026";
const char* BROKER     = "192.168.77.2";        // tablet do posto
const int   PORTA      = 1883;
const char* INST       = "sv";
const char* LEITO      = "SV-017";
const char* FW         = "0.2.0";
const char* NTP_SERVER = "a.st1.ntp.br";
const char* FUSO       = "BRT3";

// ---- pinos ----
const int DT = 4, SCK = 5;

// ---- parâmetros clínicos (ajustáveis via tópico config) ----
float limiarPresencaKg = 20.0;   // abaixo disso por 15 s = saiu do leito
int   rpmMinimo        = 8;      // respiração abaixo disso = alerta
unsigned long msLevantar  = 15000;
unsigned long msRespirar  = 20000;
unsigned long msHeartbeat = 30000;
unsigned long msDebug     = 10000;

// ---- constantes de processamento ----
const float CRUZAMENTOS_POR_RESPIRACAO = 2.0f;   // sobe e desce da linha de base
const float ALFA_PESO   = 0.20f;   // suavização do peso lido
const float ALFA_LINHA  = 0.005f;  // linha de base, atualizada 1x por segundo

HX711 balanca;
WiFiClient wifi;
PubSubClient mqtt(wifi);

enum Estado { VAZIO, DEITADO, LEVANTADO } estado = VAZIO;

// ---- estado do laço ----
unsigned long ultimoLoop   = 0;   // millis() do laço anterior
unsigned long tSemPeso     = 0;   // tempo acumulado abaixo do limiar
unsigned long tRpmBaixa    = 0;
unsigned long tHeartbeat   = 0;
unsigned long tDebug       = 0;
unsigned long ultimaBase   = 0;   // última atualização da linha de base
unsigned long ultimaTentativa = 0;

float pesoSuave = 0, rpm = 0, rpmSuave = 0, mediaLenta = 0;
int   cruzamentos = 0;
bool  acima = false;
float dtMs = 0;                   // período real do laço, medido
unsigned long inicioJanela = 0;   // início da janela de 10 s do rpm
bool  mediaIniciada = false;      // primeira leitura de rpm semeia a média

// ---- fila de eventos pendentes ----
// O nó pode demorar para conseguir WiFi e broker. Enquanto isso ele não
// pode simplesmente jogar alerta fora: o idoso pode levantar da cama
// exatamente nesse intervalo. Fica na fila e sai quando conectar.
#define MAX_PENDENTES 24
struct Pendente { char topico[112]; char carga[200]; bool retain; };
Pendente pendentes[MAX_PENDENTES];
int nPendentes = 0;

// ---- protótipos (o Arduino IDE gera sozinho; aqui ficam explícitos
//      para o firmware compilar também fora da IDE, na bancada) ----
void garantirConexao();
void reenviarPendentes();
void enfileirar(const char* topico, const char* carga, bool retain);
unsigned long agoraUnix();
void publicarEvento(const char* tipo, float conf, float rpmAtual);
void publicarRespiracao(float rpmAtual);
void publicarStatus();
void publicarDebug();
void enviar(const char* sufixo, StaticJsonDocument<160>& doc, bool retain);
void aoReceber(char* topico, byte* carga, unsigned int n);

void setup() {
  Serial.begin(115200);
  balanca.begin(DT, SCK);
  balanca.set_scale(1.0f);   // recalibrada por MQTT no teste de bancada
  balanca.tare(20);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  configTime(0, 0, NTP_SERVER);
  setenv("TZ", FUSO, 1);
  tzset();
  mqtt.setServer(BROKER, PORTA);
  mqtt.setCallback(aoReceber);
  ultimoLoop = millis();
  inicioJanela = millis();      // senão a primeira janela de rpm sai curta
  ultimaTentativa = 0;          // primeira tentativa de conexão é imediata
}

void loop() {
  garantirConexao();
  mqtt.loop();

  // ---- tempo real decorrido neste laço ----
  unsigned long agora = millis();
  dtMs = (float)(agora - ultimoLoop);
  ultimoLoop = agora;

  // ---- leitura do peso ----
  float peso = balanca.get_units(1);
  pesoSuave = (1.0f - ALFA_PESO) * pesoSuave + ALFA_PESO * peso;

  // ---- linha de base atualizada uma vez por segundo ----
  if (mediaLenta == 0) mediaLenta = peso;   // semeia, senão leva 3 min pra subir
  if (agora - ultimaBase >= 1000UL) {
    ultimaBase = agora;
    mediaLenta = (1.0f - ALFA_LINHA) * mediaLenta + ALFA_LINHA * peso;
  }

  // ---- respiração: cruzamentos da linha de base ----
  // Não conta enquanto a linha de base não estiver perto do peso lido:
  // no primeiro minuto ela ainda está subindo e o contador mentiria,
  // gerando um RESPIRACAO falso logo depois de ligar a unidade.
  float diferenca = pesoSuave - mediaLenta;
  if (diferenca < 0) diferenca = -diferenca;
  bool basePronta = (pesoSuave > 1.0f) && (diferenca < 0.05f * pesoSuave);
  bool agoraAcima = peso > mediaLenta;
  if (agoraAcima != acima) {
    acima = agoraAcima;
    if (basePronta) cruzamentos++;
  }

  // ---- rpm a cada 10 s, com o tempo real medido ----
  if (agora - inicioJanela >= 10000UL) {
    float segundos = (agora - inicioJanela) / 1000.0f;
    rpm = (cruzamentos / CRUZAMENTOS_POR_RESPIRACAO) * (60.0f / segundos);
    cruzamentos = 0;
    inicioJanela = agora;
    // Suavização com constante de 20 s. Uma janela sozinha é ruidosa: contar
    // cruzamentos de zero num sinal de 20 gramas dá leitura que pula entre 6
    // e 9 numa respiração estável de 7. Sem suavizar, uma janela boa no meio
    // segura o contador e o alerta demora demais.
    // A primeira janela semeia a média em vez de misturar com zero: partindo
    // do zero ela fica "abaixo do mínimo" por uns 20 s e o nó dá alarme falso
    // em quem está respirando normalmente, todo santo boot.
    if (!mediaIniciada) { rpmSuave = rpm; mediaIniciada = true; }
    else                { rpmSuave = 0.61f * rpmSuave + 0.39f * rpm; }
  }

  // ---- máquina de estados de presença, com tempo real ----
  if (pesoSuave >= limiarPresencaKg) {
    tSemPeso = 0;
    // Atenção: NÃO zerar tRpmBaixa aqui. Este bloco roda em toda iteração em
    // que o idoso está deitado, e é justamente deitado que o contador de
    // respiração precisa acumular. Na versão 0.1 esse reset estava aqui e o
    // alerta de respiração nunca disparou em nenhuma situação.
    if (estado != DEITADO) { estado = DEITADO; publicarEvento("DEITOU", 0.90f, rpm); }
  } else {
    tSemPeso += (unsigned long)dtMs;
    if (estado == DEITADO && tSemPeso >= msLevantar) {
      estado = LEVANTADO;
      tRpmBaixa = 0;                  // saiu do leito: a contagem não vale mais
      publicarEvento("LEVANTOU", 0.94f, rpm);   // crítico: painel fica vermelho
    }
    if (estado == LEVANTADO && tSemPeso >= 5UL * 60000UL) {
      estado = VAZIO;                           // cama vazia prolongada
      tRpmBaixa = 0;
    }
  }

  // ---- anomalia respiratória: presente e rpm baixo sustentado ----
  // Critério: acumula 20 s com a média suavizada abaixo do mínimo. Uma janela
  // isolada acima do mínimo não zera o contador, apenas para de somar.
  // rpmSuave > 0 também exclui os 10 primeiros segundos, antes da primeira
  // janela fechar. Sem isso a média parte do zero, fica "abaixo do mínimo"
  // de graça e o nó dá um alarme falso logo depois de ligado.
  if (estado == DEITADO && rpmSuave > 0 && rpmSuave < (float)rpmMinimo) {
    tRpmBaixa += (unsigned long)dtMs;
    if (tRpmBaixa >= msRespirar) {
      tRpmBaixa = 0;
      publicarRespiracao(rpmSuave);
    }
  }

  // ---- heartbeat e telemetria de bancada ----
  if (agora - tHeartbeat >= msHeartbeat) { tHeartbeat = agora; publicarStatus(); }
  if (agora - tDebug     >= msDebug)     { tDebug     = agora; publicarDebug(); }

  delay(100);
}

// ============================================================
// Conexão: não bloqueia o laço, tenta a cada 5 s
// ============================================================
void garantirConexao() {
  if (mqtt.connected()) return;
  unsigned long agora = millis();
  if (agora - ultimaTentativa < 5000UL) return;   // espera entre tentativas
  ultimaTentativa = agora;

  if (WiFi.status() != WL_CONNECTED) {
    WiFi.reconnect();
    return;                                       // tenta o MQTT na próxima
  }

  char cliente[32];
  snprintf(cliente, sizeof(cliente), "leito-%s", LEITO);
  if (mqtt.connect(cliente, "margarida", "local")) {
    char topico[96];
    snprintf(topico, sizeof(topico), "margarida/%s/leito/%s/config", INST, LEITO);
    mqtt.subscribe(topico);
    snprintf(topico, sizeof(topico), "margarida/%s/leito/%s/calibra", INST, LEITO);
    mqtt.subscribe(topico);
    reenviarPendentes();       // nada que aconteceu offline se perde
  }
}

// ============================================================
// Fila do que não conseguiu ser publicado
// ============================================================
void reenviarPendentes() {
  int i = 0;
  while (i < nPendentes && mqtt.connected()) {
    if (!mqtt.publish(pendentes[i].topico, pendentes[i].carga, pendentes[i].retain)) break;
    pendentes[i] = pendentes[nPendentes - 1];      // troca com o último e encolhe
    nPendentes--;
  }
}

void enfileirar(const char* topico, const char* carga, bool retain) {
  if (nPendentes >= MAX_PENDENTES) {
    // fila cheia: descarta o mais antigo para não perder o mais recente
    for (int i = 1; i < nPendentes; i++) pendentes[i - 1] = pendentes[i];
    nPendentes--;
  }
  snprintf(pendentes[nPendentes].topico, sizeof(pendentes[0].topico), "%s", topico);
  snprintf(pendentes[nPendentes].carga, sizeof(pendentes[0].carga), "%s", carga);
  pendentes[nPendentes].retain = retain;
  nPendentes++;
}

// ============================================================
// Publicação
// ============================================================
unsigned long agoraUnix() {
  time_t t = time(nullptr);
  return (t > 1700000000L) ? (unsigned long)t : (millis() / 1000UL);
}

void publicarEvento(const char* tipo, float conf, float rpmAtual) {
  StaticJsonDocument<160> doc;
  doc["leito"] = LEITO;
  doc["ts"]    = agoraUnix();
  doc["tipo"]  = tipo;
  doc["conf"]  = conf;
  if (rpmAtual > 0) doc["rpm"] = (int)roundf(rpmAtual);
  enviar("evento", doc, false);
}

void publicarRespiracao(float rpmMedio) {
  StaticJsonDocument<160> doc;
  doc["leito"] = LEITO;
  doc["ts"]    = agoraUnix();
  doc["tipo"]  = "RESPIRACAO";
  // "rpm" é a leitura bruta da última janela, que é o número que a
  // enfermagem quer ver. "rpm_medio" é a média suavizada de 20 s que
  // decidiu o alerta, e vai junto para auditoria.
  doc["rpm"]       = (int)roundf(rpm);
  doc["rpm_medio"] = (int)roundf(rpmMedio);
  doc["conf"]      = 0.88f;
  enviar("evento", doc, false);
}

void publicarStatus() {
  StaticJsonDocument<160> doc;
  doc["leito"]   = LEITO;
  doc["ts"]      = agoraUnix();
  doc["online"]  = true;
  doc["fw"]      = FW;
  doc["peso_kg"] = roundf(pesoSuave * 10) / 10.0f;
  doc["rpm"]     = (int)roundf(rpm);
  enviar("status", doc, true);                    // retain
}

void publicarDebug() {
  StaticJsonDocument<160> doc;
  doc["leito"]  = LEITO;
  doc["ts"]     = agoraUnix();
  doc["dt_ms"]  = (int)roundf(dtMs);              // período real do laço
  doc["peso"]   = roundf(pesoSuave * 10) / 10.0f;
  doc["rpm"]    = roundf(rpm * 10) / 10.0f;
  doc["rpm_suave"] = roundf(rpmSuave * 10) / 10.0f;
  doc["estado"] = estado == DEITADO ? "DEITADO" : (estado == LEVANTADO ? "LEVANTADO" : "VAZIO");
  enviar("debug", doc, false);
}

void enviar(const char* sufixo, StaticJsonDocument<160>& doc, bool retain) {
  char buf[200], topico[112];
  serializeJson(doc, buf, sizeof(buf));
  snprintf(topico, sizeof(topico), "margarida/%s/leito/%s/%s", INST, LEITO, sufixo);
  if (!mqtt.connected()) {
    enfileirar(topico, buf, retain);   // sem rede: guarda, não descarta
    return;
  }
  if (!mqtt.publish(topico, buf, retain)) enfileirar(topico, buf, retain);
}

// ============================================================
// Recebimento: config clínica e calibração
// ============================================================
void aoReceber(char* topico, byte* carga, unsigned int n) {
  StaticJsonDocument<256> doc;
  if (deserializeJson(doc, carga, n)) return;

  if (strstr(topico, "/calibra")) {
    // {"peso_conhecido_kg":70}  -> com esse peso sobre a cama
    if (doc.containsKey("peso_conhecido_kg")) {
      float conhecido = doc["peso_conhecido_kg"];
      if (conhecido > 1.0f) {
        float fator = balanca.get_units(10) / conhecido;
        if (fator > 0) {
          balanca.set_scale(fator);
          StaticJsonDocument<128> resp;
          resp["leito"]  = LEITO;
          resp["ts"]     = agoraUnix();
          resp["set_scale"] = fator;
          StaticJsonDocument<160> d(resp);
          enviar("debug", d, false);
        }
      }
    }
    if (doc.containsKey("tarar")) { balanca.tare(20); }
    return;
  }

  if (doc.containsKey("limiar_presenca_kg")) limiarPresencaKg = doc["limiar_presenca_kg"];
  if (doc.containsKey("rpm_min"))            rpmMinimo        = doc["rpm_min"];
  if (doc.containsKey("ms_levantar"))        msLevantar       = doc["ms_levantar"];
  if (doc.containsKey("ms_respirar"))        msRespirar       = doc["ms_respirar"];
}
