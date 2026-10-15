# ✅ Teste de bancada do nó de leito

Toda unidade passa por aqui antes de ir para um leito. Sem exceção. O teste
leva cerca de 40 minutos de trabalho mais 24 horas de máquina ligada, e pode
ser feito em série: monta dez, liga dez, testa dez.

O que você precisa: balança de banheiro, um peso conhecido (garrafão de água de
20 L serve), um metrônomo ou qualquer app de BPM, notebook com Mosquitto
(`mosquitto_sub`) e um relógio.

**Antes de pegar o ferro de solda**, rode a simulação no PC:

```bash
python3 hardware/bancada/rodar_testes.py
```

Tem que sair `4/4`. Se não sair, o problema é de lógica e não adianta montar
nada: a versão 0.1 do firmware passou meses com o alerta de respiração morto e
ninguém percebeu, porque só dava pra testar isso com a placa na mão.

## Teste 1 — Inicialização

Ligue a unidade. No monitor serial (115200 baud) devem aparecer, nesta ordem:
conexão WiFi, conexão MQTT e o primeiro heartbeat.

**Aprova se:** o heartbeat chega em menos de 60 segundos.

```bash
mosquitto_sub -h 192.168.77.2 -t 'margarida/sv/leito/+/status' -v
```

## Teste 2 — Taxa de amostragem real

Este teste existe porque o firmware assume um período de 100 ms e o HX711 não
entrega isso sempre. Se a taxa real for diferente, as janelas de 15 s e 20 s
ficam erradas e o alerta chega atrasado.

Deixe a cama vazia e rode:

```bash
mosquitto_sub -h 192.168.77.2 -t 'margarida/sv/leito/SV-017/debug' -v | ts '%H:%M:%.S'
```

Conte quantas leituras chegam em 60 segundos e calcule o período real. O
firmware 0.2 imprime esse valor como `dt_ms`.

**Aprova se:** o `dt_ms` reportado bate com o medido em até 20%. Se não bater,
o tempo do laço está errado e os Testes 4 e 5 vão mentir.

## Teste 3 — Precisão de peso

Com a cama vazia, subtraia a tara. Depois coloque peso conhecido no centro e
nos quatro cantos.

| Situação | Esperado | Medido | OK |
|---|---|---|---|
| Vazio | 0 kg ± 0,5 | | |
| 40 kg no centro | 40 kg ± 1 | | |
| 70 kg no centro | 70 kg ± 1 | | |
| 100 kg no centro | 100 kg ± 1 | | |
| 70 kg no canto | 70 kg ± 1,5 | | |

**Aprova se:** os cinco pontos passam. Se o canto falhar, uma célula está
apoiada de lado ou solta. Não ajuste o `set_scale` para compensar: conserte a
mecânica primeiro.

## Teste 4 — Detecção de saída do leito (o alerta crítico)

Com 70 kg sobre a cama, retire o peso de uma vez e cronometre.

**Aprova se:** o evento `LEVANTOU` chega no painel em **menos de 20 segundos**
depois de o peso sair. O firmware espera 15 segundos por projeto, então sobram
5 segundos de folga de rede. Na simulação o atraso medido foi de 16,1 s.

Se vier perto de 38 s, você está rodando a versão 0.1, que contava o tempo
errado e entregava o alerta com o dobro do atraso.

Depois recoloque o peso: o evento `DEITOU` deve chegar em menos de 10 segundos.

Repita **três vezes**, retirando o peso de maneiras diferentes (de uma vez,
devagar, em duas etapas). Se falhar em qualquer uma, refaça.

## Teste 5 — Respiração simulada

Aqui é onde a maioria das unidades falha, então não pule.

Deixe 70 kg parados sobre a cama. Com a mão, aplique uma oscilação suave sobre
o peso, no ritmo de um metrônomo, e confira a leitura de `rpm`:

| Metrônomo | rpm esperado | Medido | OK |
|---|---|---|---|
| 12 bpm | 12 ± 2, e nenhum alerta | | |
| 8 bpm | 8 ± 2 | | |
| 6 bpm | deve gerar `RESPIRACAO` em ~30 s | | |

**Aprova se:** as três leituras passam e o evento `RESPIRACAO` aparece cerca de
30 segundos depois de o ritmo cair (10 s para fechar a primeira janela mais 20 s
de acumulação). Não é imediato, é de propósito: a decisão usa uma média
suavizada porque a leitura de uma janela sozinha é ruidosa demais.

Confira também o que vem no payload: `rpm` é a leitura bruta e `rpm_medio` é a
média que decidiu. Se os dois vierem muito diferentes de forma persistente, a
célula está com ruído mecânico.

**A 12 bpm não pode haver alerta nenhum.** Se houver, a calibração está errada
ou a média está partindo do zero (defeito da 0.1, corrigido na 0.2).

**Se o rpm vier o dobro do esperado**, você está rodando o firmware 0.1: nele a
constante de conversão de cruzamentos por minuto era `3.0` e o correto é `6.0`
(cada respiração produz dois cruzamentos, não um). O 0.2 já vem corrigido.

**Se o rpm vier zero com oscilação clara**, a linha de base está acompanhando o
sinal. Verifique se está no firmware 0.2, que atualiza a média lenta uma vez
por segundo em vez de dez.

## Teste 6 — Ruído de ambiente

Ligue um ventilador na velocidade máxima encostado na cama e bata no chão ao
lado, três vezes.

**Aprova se:** nenhum evento `LEVANTOU` e nenhum `RESPIRACAO` são gerados.
Falso positivo de madrugada faz a enfermagem desligar o sistema na segunda
noite.

## Teste 7 — Queda de rede

Desligue o roteador por 3 minutos e religue.

**Aprova se:** o painel marca o leito como `OFFLINE` em 90 segundos e a unidade
volta sozinha, sem precisar de reset manual, em menos de 2 minutos depois de a
rede voltar. Nada que aconteceu durante a queda pode ter sido perdido: os
eventos ficam numa fila e saem quando a conexão volta.

O firmware 0.1 travava aqui: a rotina de reconexão bloqueava o laço e a placa
podia reiniciar sozinha. A simulação pega esse defeito de forma reproduzível. O 0.2 tenta reconectar com intervalo e continua lendo o
peso enquanto isso.

## Teste 8 — 24 horas ligada

Deixe a unidade ligada com a cama vazia por 24 horas.

**Aprova se:**
- nenhum intervalo maior que 90 segundos sem heartbeat;
- o peso lido no fim está a menos de 0,5 kg do peso lido no começo (deriva
  térmica aceitável);
- o `dt_ms` do fim está a menos de 20% do `dt_ms` do começo.

## Ficha da unidade

Preencha uma por unidade, imprima e cole dentro do case.

| Campo | Valor |
|---|---|
| ID do leito | |
| Data da montagem | |
| Quem montou | |
| Fator de calibração (`set_scale`) | |
| Teste 1 | OK / reprovado |
| Teste 2 (`dt_ms` medido) | |
| Teste 3 (5 pontos de peso) | OK / reprovado |
| Teste 4 (`LEVANTOU` em ___ s) | OK / reprovado |
| Teste 5 (rpm 12 / 8 / 6) | OK / reprovado |
| Simulação no PC (`rodar_testes.py`) | ___ / 4 |
| Teste 6 (ruído) | OK / reprovado |
| Teste 7 (queda de rede) | OK / reprovado |
| Teste 8 (24 h) | OK / reprovado |
| Versão do firmware | 0.2.0 |

Aprovou os oito: etiqueta, caixa e vai pro leito. Reprovou em qualquer um:
desmonta e começa de novo. Unidade que passou com "quase" é unidade que vai
falhar no turno da noite.

## O que este teste NÃO cobre

Este roteiro valida a eletrônica. Ele **não** valida a medição em pessoa
idosa real: peso de idoso é distribuído diferente de um garrafão, o movimento
do sono interfere, e o colchão de cada instituição é outro. Isso é etapa
separada, depende de anuência assinada e de protocolo aprovado, e não foi
feita até hoje.
