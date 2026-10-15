// ============================================================
// Bancada de simulação do firmware do nó de leito.
// Cabeçalho único com tudo que o .ino inclui do mundo Arduino.
// Não é código de produto: serve só para provar, num PC, que a
// matemática de rpm e as janelas de tempo do firmware batem.
// ============================================================
#pragma once
#include <cstdio>
#include <cstdint>
#include <cmath>
#include <map>
#include <string>
#include <vector>
#include <cstring>

typedef uint8_t byte;
typedef bool boolean;
typedef std::string String;

// ---- estado global da simulação, alimentado pelo driver Python ----
extern unsigned long g_millis;      // relógio virtual
extern bool          g_rede;        // false = MQTT/WiFi fora do ar
extern long          g_loop;        // quantas vezes o firmware deu a volta no laço

struct Amostra { unsigned long t; float peso; bool rede; unsigned long avanca; };
extern std::vector<Amostra> g_amostras;
extern size_t               g_i;
extern float                g_segura;

// ---- Arduino básico ----
inline unsigned long millis() { return g_millis; }
inline void delay(unsigned long ms) { g_millis += ms; }
inline float roundf(float v) { return std::round(v); }

struct SerialStub {
  void begin(unsigned long) {}
  template <typename T> void println(T) {}
  void println() {}
};
extern SerialStub Serial;

// ---- WiFi ----
#define WL_CONNECTED 3
class WiFiClient { public: bool connected() { return g_rede; } };
struct WiFiStub {
  void begin(const char*, const char*) {}
  int status() { return g_rede ? WL_CONNECTED : 0; }
  void reconnect() {}
};
extern WiFiStub WiFi;

inline void configTime(long, long, const char*) {}
// setenv e tzset existem de verdade em stdlib.h/time.h; usar os reais

// ---- PubSubClient ----
typedef void (*MQTT_CALLBACK)(char*, byte*, unsigned int);
struct MensagemPub { std::string topico, carga; bool retain; unsigned long ts; };
extern std::vector<MensagemPub> g_publicadas;
extern MQTT_CALLBACK g_cb;
extern std::vector<std::string> g_inbox;   // mensagens que o driver injeta

class PubSubClient {
 public:
  bool g_conectado = false;
  std::string g_cliente;
  PubSubClient(WiFiClient&) {}
  void setServer(const char*, int) {}
  void setCallback(MQTT_CALLBACK cb) { g_cb = cb; }
  bool connected() { return g_conectado && g_rede; }
  bool connect(const char* cliente, const char*, const char*) {
    g_cliente = cliente;
    g_conectado = g_rede;
    return g_conectado;
  }
  bool loop() { g_loop++; return g_conectado && g_rede; }
  bool subscribe(const char* t) { return connected(); }
  bool publish(const char* topico, const char* carga, bool retain = false) {
    if (!connected()) {
      std::fprintf(stderr, "DROP t=%lu topico=%s conectado=%d rede=%d\n",
                   g_millis, topico, (int)g_conectado, (int)g_rede);
      return false;
    }
    g_publicadas.push_back({topico, carga, retain, g_millis});
    return true;
  }
};

// ---- ArduinoJson (o mínimo que o firmware usa) ----
class Doc {
 public:
  std::map<std::string, std::string> campos;
  std::vector<std::string> ordem;

  template <typename T> void set(const std::string& k, T v) {
    if (!campos.count(k)) ordem.push_back(k);
    campos[k] = std::to_string(v);
  }
  void set(const std::string& k, const char* v) {
    if (!campos.count(k)) ordem.push_back(k);
    campos[k] = std::string("\"") + v + "\"";
  }
  void set(const std::string& k, const std::string& v) {
    if (!campos.count(k)) ordem.push_back(k);
    campos[k] = std::string("\"") + v + "\"";
  }
  void set(const std::string& k, bool v) {
    if (!campos.count(k)) ordem.push_back(k);
    campos[k] = v ? "true" : "false";
  }
  void set(const std::string& k, float v) {
    if (!campos.count(k)) ordem.push_back(k);
    char b[32]; std::snprintf(b, sizeof(b), "%g", v); campos[k] = b;
  }

  struct Proxy {
    Doc* d; std::string k;
    template <typename T> Proxy& operator=(T v) { d->set(k, v); return *this; }
    // leitura: doc["rpm"] usado como número, como o ArduinoJson permite
    operator float() const { return d->campos.count(k) ? std::stof(d->campos.at(k)) : 0.0f; }
    operator int() const { return d->campos.count(k) ? std::stoi(d->campos.at(k)) : 0; }
    operator unsigned long() const { return d->campos.count(k) ? (unsigned long)std::stoul(d->campos.at(k)) : 0UL; }
  };
  Proxy operator[](const char* k) { return Proxy{this, k}; }
  Proxy operator[](const std::string& k) { return Proxy{this, k}; }
  float operator[](const char* k) const { return campos.count(k) ? std::stof(campos.at(k)) : 0.0f; }
  float operator[](const std::string& k) const { return campos.count(k) ? std::stof(campos.at(k)) : 0.0f; }

  bool containsKey(const std::string& k) const { return campos.count(k) > 0; }
  float asFloat(const std::string& k) const { return std::stof(campos.at(k)); }
  int asInt(const std::string& k) const { return std::stoi(campos.at(k)); }
  operator float() const { return asFloat(ordem.front()); }
  operator int() const { return asInt(ordem.front()); }
};

template <size_t N> class StaticJsonDocument : public Doc {
 public:
  StaticJsonDocument() {}
  StaticJsonDocument(const Doc& outro) {
    campos = outro.campos; ordem = outro.ordem;
  }
};

inline void serializeJson(Doc& d, char* buf, size_t n) {
  std::string s = "{";
  bool primeiro = true;
  for (const auto& k : d.ordem) {
    if (!primeiro) s += ",";
    primeiro = false;
    s += "\"" + k + "\":" + d.campos[k];
  }
  s += "}";
  std::snprintf(buf, n, "%s", s.c_str());
}

inline void serializeJson(Doc& d, char* buf) { serializeJson(d, buf, 4096); }

// desserializa JSON plano, que é tudo que o firmware recebe
inline int deserializeJson(Doc& d, byte* carga, unsigned int n) {
  std::string s((char*)carga, n);
  d.campos.clear(); d.ordem.clear();
  size_t p = 0;
  while (p < s.size()) {
    size_t k1 = s.find('"', p); if (k1 == std::string::npos) break;
    size_t k2 = s.find('"', k1 + 1); if (k2 == std::string::npos) break;
    std::string chave = s.substr(k1 + 1, k2 - k1 - 1);
    size_t dois = s.find(':', k2); if (dois == std::string::npos) break;
    size_t v1 = dois + 1;
    std::string valor;
    if (s[v1] == '"') {
      size_t v2 = s.find('"', v1 + 1);
      valor = s.substr(v1 + 1, v2 - v1 - 1);
      p = v2 + 1;
    } else {
      size_t v2 = s.find_first_of(",}", v1);
      valor = s.substr(v1, v2 - v1);
      p = v2;
    }
    d.ordem.push_back(chave);
    d.campos[chave] = valor;
  }
  return 0;   // 0 = Ok
}

// ---- HX711 ----
extern float g_fator;
class HX711 {
 public:
  void begin(int, int) {}
  void set_scale(float f) { g_fator = f; }
  void tare(int) {}
  float get_units(int n) {
    float v = g_segura;
    for (int i = 0; i < n; i++) {
      if (g_i < g_amostras.size()) {
        const Amostra& a = g_amostras[g_i++];
        g_millis += a.avanca;
        g_rede = a.rede;
        v = a.peso;
      }
    }
    g_segura = v;
    return v;
  }
};
