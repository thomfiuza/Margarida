# 🛏️ Nó de leito (hardware v1)

O pedaço físico do Margarida: uma placa sob o colchão que sente peso e
respiração sem câmera, sem pulseira e sem obra, e avisa o posto de enfermagem
quando o idoso levanta da cama de noite ou quando a respiração cai.

Este diretório é a **especificação completa para fabricação**. Qualquer
laboratório de eletrônica, escola técnica ou maker com impressora 3D consegue
montar uma unidade a partir daqui, sem depender do autor.

> ⚠️ **Aviso honesto:** bem-estar e alerta a cuidadores. Não é dispositivo
> médico, não diagnostica e não substitui atendimento profissional.

## Estado

**Rascunho 0.2 de engenharia.** O DSP de peso e respiração está testado em
software (`../monitor-idoso/bcg_noturno.py`). O firmware abaixo roda em
bancada, mas **a validação em leito real ainda não aconteceu**. Nada aqui foi
medido com uma pessoa idosa. A primeira validação depende de anuência assinada
das instituições.

## O que tem aqui

| Arquivo | O que é |
|---|---|
| `BOM.md` | Lista de componentes com custo por unidade e custo para 100 leitos |
| `CIRCUITO.md` | Ligação elétrica, pinagem, cores dos fios e montagem mecânica |
| `firmware_leito_esp32.ino` | Firmware da placa (Arduino/ESP32), versão 0.2 |
| `PROTOCOLO_MQTT.md` | Tópicos, payloads, regras de decisão e LGPD |
| `TESTE_BANCADA.md` | Roteiro de teste de qualidade que toda unidade tem que passar antes de ir pro leito |
| `bancada/` | Simulador que roda o firmware num PC, sem placa, e confere os tempos e o cálculo de rpm |

## Como o nó de leito funciona

Quatro células de carga de 50 kg ficam entre o estrado e o colchão, nos quatro
cantos. Elas medem o peso total sobre a cama dez vezes por segundo.

Dessa série de peso saem três leituras:

- **Presença**: o peso está acima de um limiar (padrão 20 kg), então tem alguém deitado.
- **Saida do leito**: o peso caiu abaixo do limiar e ficou lá por mais de 15 segundos. É o alerta crítico.
- **Respiração (BCG)**: cada batimento do coração e cada movimento do peito faz o peso oscilar uns poucos gramas. O firmware separa essa oscilação do peso parado e conta respirações por minuto.

O nó publica tudo em MQTT numa rede local dedicada da instituição. O painel do
posto (especificado em `PROTOCOLO_MQTT.md`) acende vermelho e soa. Nenhum
dado sai da rede do lar: o broker é local, o payload não leva nome de
residente e o leito é identificado por etiqueta física na porta.

## Custo

Uma unidade fechada sai por **R$ 114,46** em componentes. Para 100 leitos,
**R$ 13.160** com 15% de folga para peças com defeito. Detalhe em `BOM.md`.

Os R$ 65.000 previstos no orçamento do projeto não são componentes: são
**mão de obra de montagem, solda, impressão 3D dos cases e teste de qualidade
unitário** num laboratório local. Componente é eletrônica comum de prateleira.

## Testar o firmware sem placa

Existe um banco de prova que compila o `.ino` num PC comum, alimenta com uma
série de peso sintética e confere se os tempos e o cálculo de respiração batem
com a especificação. Precisa só de `g++` e `python3`:

```bash
python3 hardware/bancada/rodar_testes.py
```

Ele roda quatro cenários: respiração normal a 12 por minuto (não pode dar
alarme), respiração lenta a 7 (tem que dar), saída do leito (o alerta tem que
chegar em até 20 s) e queda de rede de 30 s (a placa não pode travar). Sai
`4/4` se o firmware estiver bom.

Isso não substitui o `TESTE_BANCADA.md`, que é com a placa na mão. O que ele
faz é pegar erro de lógica antes de você gastar tempo soldando. Foi assim que
a versão 0.2 nasceu: o simulador mostrou que o alerta de respiração da 0.1
nunca tinha disparado em nenhuma situação.

## Como montar a primeira unidade

1. Compre um kit de 4 células + HX711 e uma placa de desenvolvimento ESP32.
2. Ligue como está em `CIRCUITO.md`.
3. Grave o `firmware_leito_esp32.ino` (Arduino IDE 2.x, placa "ESP32 Dev Module").
4. Ajuste `WIFI_SSID`, `WIFI_PASS`, `BROKER`, `INST` e `LEITO` no topo do arquivo.
5. Suba um broker Mosquitto local: `mosquitto -p 1883`.
6. Siga `TESTE_BANCADA.md` do começo ao fim. Não instale em leito nada que não tenha passado nos 8 testes.

## Licença

MIT, igual ao resto do projeto. Se você fabricar uma unidade e melhorar o
design do case, a calibração ou o filtro, mande um PR a partir da branch `dev`.
