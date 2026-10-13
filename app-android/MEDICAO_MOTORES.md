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
| VOSK | | | | | |

## Pergunta aberta: gramática reduzida vs ditado livre

O VoskSocorroDetector roda hoje com ditado livre + filtro ehWakeWordSocorro,
o caminho mais simples e correto para um spike. A alternativa de produção é o
reconhecedor com gramática (vocabulário curto: "socorro", "socorre", "socoro",
"me socorre", "sim", "não", "cancela", "[unk]"), que decodifica menos e tende a
gastar menos CPU e bateria, além de reduzir falsos com TV.

Decisão por número, não por opinião: se a linha VOSK da tabela reprovar por
bateria ou falsos positivos, mede de novo com gramática antes de descartar o
motor. A implementação da gramática (construtor opcional no detector) fica
para um PR seguinte, pequeno, depois que a tabela existir. Este PR não mexe em
código: só a régua e a pergunta.
