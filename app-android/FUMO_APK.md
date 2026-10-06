# Fumo APK — Chaquopy, boot e FGS (item 3)

Checklist para validar no **S23** (ou emulador arm64) antes da aceitação completa
(`PROTOCOLO_TESTES_S23.md`).

## Compilar

1. Android Studio → Open `app-android/` → **Build → Build APK(s)** (debug).
2. Primeira vez: Gradle baixa SDK + Chaquopy instala **numpy** e **opencv-python**
   (20–40 min é normal).
3. APK: `app/build/outputs/apk/debug/app-debug.apk`.

✔ **Passou:** build termina sem erro vermelho no painel Build.

## Instalar

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

✔ **Passou:** `Success` e ícone Margarida na gaveta.

## Boot limpo (cadastro já feito)

1. Conceder **microfone** e **notificações** na primeira abertura.
2. Reiniciar o aparelho (ou `adb reboot`).
3. **Não** abrir o app manualmente; esperar ~1 min.

✔ **Passou:**

- Notificação persistente “Diga SOCORRO…” (FGS `WakeWordService`).
- `adb shell dumpsys activity services br.com.monitoridoso | grep WakeWord`
  mostra serviço ativo.

Se não subir: permissão de microfone só existe depois de abrir o app uma vez —
comportamento esperado do Android; anotar no relatório.

## Chaquopy vivo

1. Abrir **Rotina diária (modo cuidador)** e iniciar fluxo que usa câmera ou urina.

✔ **Passou:** sem crash em `MargaridaApp` / `Python.start`; rPPG ou urina retorna
(número ou mensagem), não `ModuleNotFoundError` no logcat (`adb logcat -s python`).

## Simular casa (parceiro / sensor) — útil p/ item 4

Token: em **Ajustar cadastro** ou modo cuidador, copiar o **código da casa**
(`PerfilStore.tokenPonte()`). Substituir `SEU_TOKEN` abaixo.

Queda (deve abrir SOS):

```bash
adb shell am broadcast -a br.com.monitoridoso.EVENTO_SENSOR \
  -n br.com.monitoridoso/.SensorEventReceiver \
  --es json '{"protocolo":"tuya","papel":"radar","hora":15,"token":"SEU_TOKEN","status":[{"code":"fall_state","value":"fall"},{"code":"motionless_time","value":30}]}'
```

Pad BCG (quadro sintético — repetir até juntar 120 s no ingestor):

```bash
adb shell am broadcast -a br.com.monitoridoso.QUADRO_PAD \
  -n br.com.monitoridoso/.SensorEventReceiver \
  --es quadro_hex '00112233445566778899aabbccddeeff'
```

## Log útil

```bash
adb logcat -s WakeWord Margarida python Ponte PadIngestor
```

## Falhas comuns

| Sintoma | Provável causa |
|--------|----------------|
| Gradle “SDK not found” | Instalar SDK 34 no Android Studio |
| Chaquopy pip timeout | Rede; repetir sync |
| FGS morto após boot | Microfone ainda não concedido |
| Evento ignorado | `token` no JSON ≠ código da casa |
