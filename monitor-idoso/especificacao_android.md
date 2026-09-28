# Especificação técnica — App Android "Monitor do Idoso"

**Produto:** um app, dois modos. **Modo Idoso** (celular do idoso): wake word de
emergência sempre ativa + SOS. **Modo Cuidador** (celular do filho/cuidador):
rotina diária guiada por voz + painel de alertas. Todo o núcleo de negócio já
existe e está testado em Python (`orquestrador.py` amarra rotina + noite + SOS
num diário único); o app é a casca Android em volta dele.

---

## 1. Arquitetura

```
┌──────────── CELULAR DO IDOSO (Modo Idoso) ────────────┐
│  Wake word "SOCORRO" (Porcupine, offline, 24/7)       │
│        │                                              │
│  Confirmação por voz 10 s ("Você chamou ajuda?")      │
│        │                                              │
│  ROTA A (com internet)          ROTA B (sem internet) │
│  HTTP → nuvem (Twilio/Totalk)   Ligações nativas em   │
│  liga p/ contatos e TOCA a      sequência + mensagem  │
│  mensagem 09_mensagem_panico    no alto-falante + SMS │
│        └────────────┬─────────────────┘               │
│              SMS com localização + CONTEXTO DE SAÚDE  │
└───────────────────────────────────────────────────────┘
                    │ (contexto vem do diário sincronizado)
┌──────────── CELULAR DO CUIDADOR (Modo Cuidador) ──────┐
│  Rotina guiada por voz (9 áudios pt-BR já gravados)   │
│  Câmera: rPPG FC → ortostática A+B → foto da urina    │
│  Painel: eventos do dia, tendências, relatório        │
│  (v2) BLE ← pad BCG noturno → eventos "noite"         │
└───────────────────────────────────────────────────────┘
```

**Restrição que define a rota dupla:** Android/iOS não permitem injetar áudio em
ligação nativa (restrição de segurança no nível do SO) — só a nuvem consegue
ligar e tocar a mensagem. Rota A = experiência completa; Rota B = fallback
degradado que ainda funciona sem internet.

## 2. Módulos: protótipo Python → implementação Android

| Módulo | Protótipo testado | Implementação no app |
|---|---|---|
| Núcleo/diário/eventos | `orquestrador.py` (4/4) | Kotlin + Room; MESMO JSON de Evento |
| Fluxo por voz + alertas | `fluxo_idoso.py` (5/5) | Activity guiada; TTS nativo pt-BR ou os 9 MP3 embutidos |
| FC por câmera (rPPG) | `fc_camera.py` (4/4) | Chaquopy (Python embarcado) no MVP; portar p/ Kotlin depois |
| Urina com cartão | `urina.py` (4/4) | idem (OpenCV já tem binding Android) |
| BCG noturno | `bcg_noturno.py` (3/3) | v2: BLE do pad; DSP roda no app |
| SOS (chamadas+SMS) | `panico.py` (5/5) | Retrofit→Twilio (rota A) / TelecomManager+SmsManager (rota B) |
| Wake word | — | Picovoice Porcupine, custom pt-BR ("socorro"), offline |
| Reconhecimento sim/não | — | SpeechRecognizer (pt-BR), só durante a rotina/SOS |

**Estratégia DSP:** empacotar os módulos Python com **Chaquopy** no MVP (velocidade;
a matemática já está testada) e portar os caminhos quentes para Kotlin só se houver
problema de desempenho. Nunca reescrever a matemática validada às pressas.

## 3. Wake word e falso positivo

- **Engine:** Porcupine suporta pt-BR e wake words customizadas offline (~2 MB,
  baixa potência, roda em foreground service).
- **Confirmação obrigatória (10 s):** após o wake word, o app pergunta por voz
  "Você chamou ajuda? Diga sim". Sem confirmação → dispara MESMO ASSIM
  (idoso pode estar incapaz de falar; o falso positivo é mais barato que a
  omissão — os contatos podem descartar).
- **Botão vermelho gigante** na tela como alternativa (dedo trêmulo, afasia).

## 4. SOS — rotas A e B (orquestradas por `panico.py`, já testado)

**Rota A — nuvem (recomendada):**
1. App detecta SOS → POST para backend com: lista de contatos, áudio da
   mensagem, localização, contexto de saúde.
2. Backend (Twilio Programmable Voice) liga **um por um, na ordem**, toca
   `09_mensagem_panico.mp3`, registra quem atendeu.
3. Backend envia SMS (Twilio Messaging) com localização + contexto.
4. Custo estimado: ~US$ 0,02–0,08/min de voz + US$ 0,008/SMS para o Brasil
   (tabela Twilio; cotação formal antes do piloto).

**Rota B — local (sem internet):**
1. `ACTION_CALL` sequencial (permissão CALL_PHONE): liga, espera 25 s, derruba,
   próximo — a mensagem toca no ALTO-FALANTE do idoso (quem estiver perto ouve).
2. `SmsManager` envia localização + contexto.
3. Limitação aceita e documentada: quem atende não ouve a mensagem gravada.

## 5. Permissões (cada uma com justificativa para a Play Store)

| Permissão | Uso |
|---|---|
| RECORD_AUDIO | wake word + respostas por voz |
| CAMERA | rPPG e foto da urina (só durante a rotina) |
| ACCESS_FINE/COARSE_LOCATION | localização no SOS |
| ACCESS_BACKGROUND_LOCATION | localização se SOS com app em background — usar só se necessário; preferir última localização conhecida |
| CALL_PHONE | rota B do SOS |
| SEND_SMS | SMS com localização |
| POST_NOTIFICATIONS | alertas ao cuidador |
| FOREGROUND_SERVICE (+microfone) | wake word 24/7 |
| INTERNET | rota A, sincronização, TTS opcional |

## 6. LGPD e privacidade (argumento de venda, não só obrigação)

- Vídeos e fotos **nunca saem do aparelho** (processamento local); só eventos
  (números/texto) sincronizam com o cuidador.
- SMS/SOS: só dados mínimos (nome, localização, resumo de saúde).
- Rota A usa Twilio = processador de dados: entra no contrato de consentimento
  do primeiro uso. Sem venda de dado, sem anúncio. Consentimento do idoso E do
  cuidador registrado no primeiro lançamento.

## 6b. Sensores de casa (v1.5) — ponte Matter/Tuya

Os eventos dos sensores de prateleira (radar de queda FALLR1-like, contato de
porta, gás, armário de remédio) chegam ao app via bridge Matter/Tuya e caem em
`decidirEvento()` (já portado e testado no `core/`, 8/8):

- **queda** confirmada (imóvel ≥ 15 s, janela 5–90 s do FALLR1) → dispara o
  MESMO SOS sequencial com contexto de saúde;
- **gás** → SOS imediato; **porta** aberta 22h–05h → alerta de deambulação;
  **remédio** não acessado → alerta de esquecimento;
- **saída do leito** pelo pad BCG (silêncio do sinal) → alerta se prolongada.

Tudo isso já existe como regra de negócio testada nas duas linguagens — a v1.5
só precisa da ponte de protocolo no app (biblioteca Matter/Home Assistant) e
dos sensores físicos instalados na casa do piloto.

## 7. Contrato de dados (idêntico em Python e Kotlin)

```json
// Evento (diário) — mesma estrutura de orquestrador.py
{"tipo": "rotina|noite|emergencia",
 "quando": "2026-09-19T16:23:00",
 "nivel": "nenhum|atencao|urgente",
 "mensagem": "texto para o cuidador",
 "dados": { ...módulo específico... }}

// SOS (rota A → backend)
{"nome_idoso": "...", "contatos": [{"nome": "...", "telefone": "..."}],
 "audio_msg_url": "...", "localizacao": {"lat": -19.59, "lon": -46.94,
 "precisao_m": 8, "origem": "gps"}, "contexto_saude": "...",
 "quando": "..."}
```

Níveis: `nenhum` (registro normal), `atencao` (aviso no painel), `urgente`
(notificação push + badge). Um SOS é sempre `urgente`.

## 8. Armazenamento, bateria, offline

- **Local:** SQLite/Room (espelho do JSONL). Histórico de rotina mantém as
  últimas N entradas para as tendências (FC 7 dias, urina 3 registros).
- **Sincronização cuidador:** push do evento via FCM no momento do registro;
  fila local se offline (SOS tem prioridade na fila).
- **Bateria:** wake word Porcupine foi feita para 24/7; câmera só abre na
  rotina; sem GPS contínuo (só no SOS).
- **Offline total:** rotina e diário funcionam 100%; SOS cai para a rota B.

## 9. Testes

| Camada | O quê | Estado |
|---|---|---|
| Unidade/matemática | 7 suítes Python (29 testes) | ✅ prontas e verdes |
| Instrumentado Android | câmera real, TTS, wake word, SMS de teste | 🔨 com o dev do app |
| Campo | GPS indoor, falso positivo, latência do SOS, pele idosa | 🔨 no piloto |

## 10. MVP — sequência de construção (8–10 semanas, 1 dev Android)

1. **Sem. 1–2:** projeto Kotlin, Chaquopy com os módulos Python, navegação
   dois modos, Room com contrato de Evento.
2. **Sem. 3–4:** Modo Cuidador — rotina por voz (9 MP3 embutidos) + câmera
   (rPPG + urina) via Chaquopy + painel do diário.
3. **Sem. 5:** Modo Idoso — botão vermelho + rota B do SOS (nativo, sem nuvem).
4. **Sem. 6–7:** wake word Porcupine + confirmação; backend mínimo Twilio
   (rota A) + FCM cuidador.
5. **Sem. 8:** LGPD (consentimento, exportar/apagar dados), beta fechado.
6. **Sem. 9–10:** buffer de estabilização + entrada do piloto
   (`piloto_cuidadores.md`).

**Fora do MVP (v2+):** pad BCG noturno (BLE), módulo sanitário, manguito BT.
