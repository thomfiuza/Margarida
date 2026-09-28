# App Android — Monitor do Idoso

Especificação completa: `../monitor-idoso/especificacao_android.md`.

## Estrutura

```
core/    Kotlin/JVM PURO — regras clínicas, SOS, diário, JSON.
         COMPILADO E TESTADO no servidor (kotlinc 2.0.21 + JVM 11): 5/5.
         O mesmo contrato de Evento do protótipo Python (mesmo JSONL).
app/     Casca Android — Activities, wake word, gateways de chamada/SMS.
         Escrito, mas NÃO COMPILADO aqui (sandbox sem Android SDK/emulador).
         Compila no Android Studio (Hedgehog+ / AGP 8.5, JDK 17, minSdk 26).
```

## Rodar os testes do core (qualquer máquina com JDK 11+)

```bash
# sem Gradle, direto no kotlinc (foi assim que foi validado aqui):
kotlinc core/src/main/kotlin/*.kt core/src/test/kotlin/TestesCore.kt -include-runtime -d core-testes.jar
java -Dfile.encoding=UTF-8 -jar core-testes.jar     # 5/5
```

## Abrir no Android Studio (para gerar o APK)

1. Abrir a pasta `app-android/` (o Studio cria o wrapper Gradle no primeiro sync).
2. Sync → rodar em um aparelho Android 8+.
3. Wake word: criar AccessKey gratuita em console.picovoice.ai, treinar
   "socorro" (pt), colocar o `.ppn` em `app/src/main/assets/` e colar a chave
   em `WakeWordService.kt` (instruções no arquivo).
4. Rota A do SOS (ligação com mensagem tocada): backend Twilio — contrato na
   seção 7 da especificação; a Rota B (nativa, sem internet) já está ligada.

## O que está VERIFICADO vs. PENDENTE

| Item | Estado |
|---|---|
| `core/` (SOS + regras + diário + JSON) | ✅ compilado e 5/5 testes JVM |
| Paridade core Kotlin ↔ protótipo Python | ✅ textos e limiares idênticos; testes espelhados |
| `app/` (Activities, manifest, Gradle, serviços) | ✅ compilado no PC do usuário (BUILD SUCCESSFUL, AGP 8.5 + Gradle 8.10 + JDK 17) |
| SOS em aparelho real (S23) | ✅ 28/09/2026: 2 ligações sequenciais + SMS com mapa, tela estável |
| Wake word no aparelho real | 🔨 precisa AccessKey Picovoice + teste em campo |
| Rotina por voz no app (câmera/MP3) | 🔨 semana 3–4 do cronograma da especificação |
| Publicação Play Store | 🔨 conta de dev Google (US$ 25, única vez) + assinatura |
