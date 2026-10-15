# 🔋 Medição comparativa dos motores wake (camada de decisão por número)

Complementa o PROTOCOLO_TESTES_S23.md. A dev já tem o seletor de motor no modo
cuidador (MotorWake: ESCUTA / PORCUPINE / VOSK) e o doc do modelo Vosk em
app/src/main/assets/vosk/LERME.txt. Este documento define o critério de
aprovação de cada motor e a pergunta aberta de vocabulário.

## Critério de aprovação para o 24/7

- Bateria menor que 5% por hora de escuta contínua (tela apagada, Wi-Fi on, mesma build).
- Acerto maior que 90% em 20 chamadas de "socorro" (variações: "socoro", "me socorre", de longe, de costas).
- ZERO falsos positivos em 10 negativos com TV ligada (novela/jornal) e conversa ao lado.

Se dois motores passarem, o desempate é licença e ausência de chave: Vosk
(aberto, sem validade) vira padrão, EscutaFala fica como fallback universal,
Porcupine só com chave permanente viável (hoje não existe).

## Tabela de resultados (preencher no S23, uma linha por motor)

Antes de cada linha: modo cuidador → spinner motor wake → salvar → fechar e abrir o app.

| Motor | % por hora | Acertos/20 | Falsos/10 | RAM | Veredito |
|---|---|---|---|---|---|
| ESCUTA | | | | | |
| PORCUPINE | | | | | |
| VOSK ditado livre | | | | | |
| VOSK gramatica reduzida | | | | | |

O Vosk tem duas linhas porque são dois motores diferentes na prática. Em ditado
livre ele decodifica o vocabulário inteiro do modelo a cada quadro de áudio.
Com gramática reduzida ele só pode emitir 4 palavras, que é o que a escuta 24/7
precisa. A diferença de bateria entre as duas linhas é o número que decide.

## Gramática reduzida: implementada

Estava como pergunta aberta. Agora existe código.

O vocabulário mora no núcleo, em `core/src/main/kotlin/WakeWord.kt`, e é
testado na JVM (`testGramaticaReduzidaVosk`). São três níveis cumulativos:

| Nível | Constante | Palavras | Quando |
|---|---|---|---|
| 1 | `VOCAB_WAKE_SOCORRO` | 4 | escuta 24/7 |
| 2 | `VOCAB_CONFIRMACAO` | 9 | janela de confirmação de 10 s |
| 3 | `VOCAB_EMERGENCIA` | 17 | frases que disparam sem a palavra "socorro" |

Nível 1 é o que fica ligado o dia inteiro: `socorro`, `socorre`, `socoro`,
`me socorre`, mais `[unk]`. Nível 2 e 3 só entram depois que a wake word acorda
o app, na confirmação.

Para medir no S23, o detector aceita a gramática no construtor:

```kotlin
// ditado livre (como era)
VoskSocorroDetector(ctx) { disparar() }

// gramática reduzida (o que se mede agora)
VoskSocorroDetector(ctx, { disparar() }, gramaticaVoskWake())

detector.modo   // "GRAMATICA" ou "DITADO", para registrar na tabela
```

Se o modelo recusar a gramática, o detector cai para ditado livre sozinho e
registra `modo = "DITADO"`. Nunca fica sem wake word por causa de uma
otimização.

### O que o teste da gramática garantiu

Cruzar as duas listas achou um defeito que não era de gramática nenhuma, e que
estava no caminho crítico do SOS:

O idoso diz "socorro", o app pergunta "você pediu socorro?", e ele repete. O
reconhecedor transcreve a repetição como `socoro` ou `socorre`, que são formas
que o `ehWakeWordSocorro` aceita. Mas o `WakeWordGate.pareceSim` só conhecia a
forma `socorro`. Resultado: **a confirmação virava CANCELAR e o socorro não era
feito.** A mesma coisa acontecia com "confirmo" e "confirma".

As variantes entraram no `SIM` do gate e o teste agora percorre as 30 frases do
vocabulário exigindo que cada uma dispare o SOS ou seja uma negação legítima.
Com o código antigo o teste falha em `'socorre'`. Com o novo, 27/27.

### O que ainda é decisão por número

A gramática reduz o trabalho do decodificador, mas o quanto reduz é medida, não
promessa. Se a linha `VOSK gramatica reduzida` passar em bateria e falsos
positivos, o Vosk vira o motor padrão e o Porcupine sai do caminho. Se não
passar nem com gramática, o motor é descartado e a régua continua valendo para
o próximo candidato.
