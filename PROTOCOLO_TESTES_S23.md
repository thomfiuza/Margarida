# Protocolo de testes — Samsung S23

Build: branch `dev`, APK debug (`app-android/FUMO_APK.md`).

Registre **PASS / FAIL** + print ou trecho de logcat por cenário.

---

## 0. Pré-requisitos

- [ ] Cadastro com nome + ≥1 contato real + consentimento
- [ ] Permissões: microfone, notificações, ligação, SMS, localização, câmera (rotina)
- [ ] Código da casa anotado (modo cuidador)

---

## 1. Fumo (item 3)

| # | Passo | Esperado |
|---|--------|----------|
| 1.1 | Instalar APK, abrir app | Sem crash; Chaquopy sobe |
| 1.2 | Notificação “Margarida ativa” | FGS wake word ativo |
| 1.3 | Reboot sem abrir app | FGS sobe se microfone já foi concedido antes |
| 1.4 | Rotina cuidador → rPPG/urina | Processamento sem crash Python |

---

## 2. SOS — três vias (item 4)

### 2.A Wake word + confirmação

1. Dizer **“socorro”** (TV desligada, ambiente quieto).
2. Ouvir pergunta TTS; janela ~6 s.
3. **Silêncio** → deve **cancelar** (não liga).
4. Repetir; dizer **“não”** → cancela.
5. Repetir; dizer **“sim”** ou **“eu caí”** → liga + SMS.

### 2.B Botão vermelho

1. Modo idoso → **SOCORRO** → liga **sem** pergunta de wake.

### 2.C Parceiro / sensor simulado

**Sensor queda** (substituir `SEU_TOKEN`):

```bash
adb shell am broadcast -a br.com.monitoridoso.EVENTO_SENSOR \
  -n br.com.monitoridoso/.SensorEventReceiver \
  --es json '{"protocolo":"tuya","papel":"radar","hora":15,"token":"SEU_TOKEN","status":[{"code":"fall_state","value":"fall"},{"code":"motionless_time","value":30}]}'
```

**App parceiro** (cadastrar pacote `br.com.teste` no modo cuidador):

```bash
adb shell am start -a android.intent.action.VIEW \
  -d 'margarida://sos?origem=br.com.teste&obs=piloto'
```

Esperado: SOS direto, sem pergunta wake.

---

## 3. Rotina guiada (item 4)

- [ ] MP3 ou TTS em cada passo
- [ ] Vídeos repouso + em pé → FC calculada
- [ ] Foto urina → nível ou aviso
- [ ] Evento no diário (modo cuidador)

---

## 4. Pad / sensores simulados (item 4)

```bash
adb shell am broadcast -a br.com.monitoridoso.QUADRO_PAD \
  -n br.com.monitoridoso/.SensorEventReceiver \
  --es quadro_hex '00112233445566778899aabbccddeeff'
```

Repetir até ingestor juntar 120 s (ver logcat `PadIngestor`).

---

## 5. Bateria 1 h (item 5)

Condições fixas: ecrã off, Wi‑Fi on, mesma build.

| Motor | Início % | Fim 1 h % | Δ % | Falsos positivos |
|-------|----------|-----------|-----|------------------|
| EscutaFala (sem chave Porcupine) | | | | |
| Porcupine (com chave + .ppn) | | | | |
| Vosk (se/spike) | | | | |

---

## 6. Cadastro real E2E (item 6)

- [ ] Contatos reais na ordem desejada
- [ ] SOS botão → 1.ª ligação + SMS com mapa
- [ ] SMS legível; ligação atende ou cai na sequência
- [ ] Diário registra emergência

---

## Logcat sugerido

```bash
adb logcat -s WakeWord Margarida python Ponte PadIngestor
```
