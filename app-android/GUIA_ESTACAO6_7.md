# Guia didático — Estações 6 e 7 (wake word "socorro" + rotina por voz no APK)

**O que já está pronto no código (testado onde dá para testar sem o celular):**

| Peça | Onde | Teste |
|---|---|---|
| `WakeWordGate` — política anti-falso-positivo (pergunta, silêncio=não, debounce) | `core/.../WakeWordGate.kt` | 12/12 JVM |
| `MaquinaRotina` — cérebro da rotina por voz | `core/.../Rotina.kt` | 12/12 JVM |
| `Pulso.estimarBpm` — matemática do rPPG | `core/.../Pulso.kt` | 12/12 JVM |
| `WakeWordService` v3 — Porcupine + confirmação por voz | `app/.../WakeWordService.kt` | exige celular |
| `CondutorRotina` + `FcCamera` — rotina guiada dentro do APK | `app/.../*.kt` | exige celular |

O que **só você** faz: conta Picovoice (grátis), colar a AccessKey, buildar e
testar no S23. Passo a passo abaixo.

---

## 🎙️ Estação 6 — ligar a wake word "socorro" (Picovoice)

1. **Conta:** abra https://console.picovoice.ai → Sign up (grátis, e-mail).
2. **AccessKey:** na tela inicial já aparece sua *AccessKey* (começa com
   letras/números longos). Copie.
3. **Colar no app:** no Android Studio, abra
   `app/src/main/java/br/com/monitoridoso/WakeWordService.kt` e troque
   `COLE_AQUI_A_ACCESSKEY_PICOVOICE` pela sua chave (mantenha as aspas).
4. **Palavra "socorro":** no console, menu **Picovoice Wake Word** (ou
   *Custom wake words*) → crie a palavra `socorro` em **Portuguese (pt)** →
   baixe o arquivo **.ppn** (treine com sensibilidade padrão).
5. **Colocar no app:** crie a pasta `app/src/main/assets/` (botão direito em
   `app/src/main` → New → Directory → `assets`) e copie o .ppn para dentro
   **com o nome** `socorro_pt.ppn`.
6. Build APK → instalar no S23 (mesmo caminho do WhatsApp de sempre).

### ✔ Teste da wake word (3 cenários)

- Diga **"socorro"** perto do celular → o app PERGUNTA em voz alta
  *"Você pediu socorro?"* → diga **"não"** → ele responde *"Entendido, fico
  por perto"* e **nada** acontece.
- Diga **"socorro"** → diga **"sim"** → abre a tela vermelha e **liga + SMS**.
  (Avise a Jalmira antes! 😄)
- Diga **"socorro"** → fique em **silêncio** → nada acontece (silêncio = não).

Sem AccessKey colada, o app continua funcionando no **modo botão** (sem gastar
bateria) — nunca quebra.

---

## 🗣️ Estação 7 — rotina por voz dentro do APK

1. Abra o app → **"Rotina diária (modo cuidador)"**.
2. Toque em **"▶ Iniciar rotina guiada por voz"**.
3. Siga a voz: dizer *começar* → dedo na câmera 30 s sentado → levantar, 1 min
   em pé, dedo na câmera 30 s → responder *sim/não* sobre tontura → número da
   urina (1–10) ou *pular* → o app fala o resumo e **grava no diário**.
4. Confira no painel: o evento 🟢/🟡/🔴 da rotina aparece nos "Últimos eventos".

Comandos a qualquer momento: *repetir*, *parar*, *pular*.

---

## 📤 Depois de testar: atualizar o GitHub (ciclo que você vai usar sempre)

Na telinha preta:

```
cd "C:\Users\Admin\Documents\Thomaz Labs\Projeto Margarida\publicar-margarida"
```
(copie os arquivos novos desta estação para a pasta `publicar-margarida`
antes — veja a lista no README) e então:

```
git add .
git commit -m "Estações 6-7: wake word com confirmacao por voz e rotina guiada"
git push
```

## 🆘 Se algo der errado

- Wake word não pergunta nada → olhe o Logcat, filtro `WakeWord`: "sem
  AccessKey" (chave não colada) ou "Porcupine não inicializado" (.ppn fora do
  lugar/nome errado).
- Rotina não mede pulso → limpe a lente, cubra com o dedo SEM apertar, quarto
  com luz acesa; se ainda falhar, o app avisa "não consegui medir" e não
  inventa número.
