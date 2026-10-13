// ============================================================
// Bancada: executa o firmware real do nó de leito num PC.
//
// g++ -std=c++17 -O1 -I bancada/stubs bancada/main.cpp -x c++ firmware_leito_esp32.ino -o bancada/bancada
// (o -x c++ é porque o g++ não reconhece a extensão .ino)
//
// O driver Python (bancada/rodar_testes.py) alimenta a simulação
// com uma série de peso sintética e interpreta o que a placa
// publica. Nada aqui é código de produto: é prova de que a
// matemática do firmware faz o que a especificação diz.
// ============================================================
#include "tudo.h"

unsigned long g_millis = 0;
bool          g_rede = true;
long          g_loop = 0;
std::vector<Amostra> g_amostras;
size_t               g_i = 0;
float                g_segura = 0.0f;
SerialStub    Serial;
WiFiStub      WiFi;
std::vector<MensagemPub> g_publicadas;
MQTT_CALLBACK g_cb = nullptr;
std::vector<std::string> g_inbox;
float         g_fator = 1.0f;

extern void setup();
extern void loop();

static unsigned long ultimaAvancada = 0;
static int paradas = 0;

int main() {
  // carrega a série de peso gerada pelo driver
  unsigned long t, av;
  float peso;
  int rede;
  while (std::scanf("%lu %f %d %lu", &t, &peso, &rede, &av) == 4) {
    g_amostras.push_back({t, peso, rede != 0, av});
  }
  if (g_amostras.empty()) return 2;
  g_segura = g_amostras[0].peso;

  setup();
  size_t publicadas = 0;

  for (size_t i = 0; i < 200000; i++) {
    loop();

    // publica o que a placa mandou e injeta o que o driver pediu
    for (; publicadas < g_publicadas.size(); publicadas++) {
      const MensagemPub& m = g_publicadas[publicadas];
      std::printf("PUB %lu %s %s%s\n", m.ts, m.topico.c_str(), m.carga.c_str(),
                  m.retain ? " RETAIN" : "");
      std::fflush(stdout);
    }
    for (const std::string& msg : g_inbox) {
      std::string copia = msg;
      if (g_cb) {
        g_cb((char*)"margarida/sv/leito/SV-017/config",
             (byte*)copia.data(), (unsigned int)copia.size());
      }
    }
    g_inbox.clear();

    // trava de segurança: se o relógio virtual parou, o firmware
    // entrou num laço bloqueante. É justamente um dos defeitos
    // que esta bancada existe para pegar.
    if (g_millis == ultimaAvancada) {
      if (++paradas > 5000) {
        std::printf("BLOQUEADO relogio_parado_em %lu ms loop %ld\n", g_millis, g_loop);
        std::fflush(stdout);
        return 0;
      }
    } else {
      paradas = 0;
      ultimaAvancada = g_millis;
    }

    if (g_i >= g_amostras.size()) break;
  }

  std::printf("FIM relogio %lu ms loop %ld amostras %zu/%zu\n",
              g_millis, g_loop, g_i, g_amostras.size());
  std::fflush(stdout);
  return 0;
}
