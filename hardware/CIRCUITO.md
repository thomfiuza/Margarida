# 🔌 Circuito e montagem do nó de leito

Uma placa só. Quatro células de carga em paralelo, um HX711 e um ESP32.

## Esquema geral

```
          ESTRADO DA CAMA
   ┌──────────┬──────────┬──────────┬──────────┐
   │ CELULA 1 │ CELULA 2 │ CELULA 3 │ CELULA 4 │   50 kg cada, uma por canto
   └────┬─────┴────┬─────┴────┬─────┴────┬─────┘
        │ 4 fios   │          │          │
        └──────────┴────┬─────┴──────────┘
                        │  4 fios em paralelo (junta por cor)
                   ┌────┴────┐
                   │  HX711  │  A+, A-, B+, B-, E+, E-
                   └────┬────┘
                        │  DOUT (pino 4) e SCK (pino 5)
                   ┌────┴────┐
                   │  ESP32  │  5 V pela USB, comum com o GND do HX711
                   └────┬────┘
                        │  WiFi
                   ┌────┴────┐
                   │ BROKER  │  Mosquitto local, porta 1883
                   └─────────┘
```

## Cores dos fios da célula de carga

O padrão mais comum no mercado brasileiro é este. **Confira com o seu
lote antes de soldar**: se vier diferente, identifique com multímetro
(resistência entre pares fica em torno de 1 kΩ para excitação e para sinal).

| Cor | Função | Liga em |
|---|---|---|
| Vermelho | E+ (excitação positiva) | HX711 `E+` |
| Preto | E- (excitação negativa) | HX711 `E-` |
| Verde | A- (sinal negativo) | HX711 `A-` |
| Branco | A+ (sinal positivo) | HX711 `A+` |

As quatro células entram **em paralelo**: vermelho com vermelho, preto com
preto, verde com verde, branco com branco. Use termocontrátil em cada junta e
não deixe emenda solta dentro do case. É a junta mal feita que vira ruído
intermitente, e ruído intermitente em sensor de respiração é o pior defeito
possível: ele só aparece de madrugada.

## Pinagem

| HX711 | ESP32 (placa de desenvolvimento 30 pinos) |
|---|---|
| VCC | 5V (ou 3V3 se o módulo for a versão de 3,3 V) |
| GND | GND |
| DOUT | GPIO 4 |
| SCK | GPIO 5 |

Se você usar outros pinos, mude as duas constantes no topo do firmware:

```cpp
const int DT = 4, SCK = 5;
```

GPIO 4 e 5 são escolha deliberada: ficam fora dos pinos de boot (0, 2, 12, 15),
que causam comportamento estranho na inicialização do ESP32.

## Montagem mecânica sob o colchão

```
        ┌───────────────────────────────┐
        │           COLCHAO             │
        └───┬───────────────────────┬───┘
        ┌───┴───┐               ┌───┴───┐
        │ CEL 1 │               │ CEL 2 │      célula fica entre a tábua do
        └───┬───┘               └───┬───┘      estrado e o colchão, com a
        ────┴───────────────────────┴────      face sensível para CIMA
        │         ESTRADO            │
        ────┬───────────────────────┬────
        ┌───┴───┐               ┌───┴───┐
        │ CEL 3 │               │ CEL 4 │
        └───┬───┘               └───┬───┘
        ┌───┴───────────────────────┴───┐
        │      ESTRUTURA DA CAMA        │
        └───────────────────────────────┘

   case do ESP32 fixado na lateral do estrado, fora do alcance de líquido
```

Regras que mudam o resultado da medição:

1. **As quatro células têm que trabalhar.** Se o colchão é mole e apoia no meio
   do estrado, parte do peso não passa pelas células e a leitura de respiração
   cai. Teste com uma pessoa sentada no centro antes de confiar.
2. **Face sensível para cima.** A célula de carga tem uma seta ou um recorte.
   Montada invertida ela funciona, mas a sensibilidade cai muito.
3. **Nada de espuma ou pano entre célula e estrado.** Qualquer coisa macia
   absorve a oscilação de 20 gramas que é o sinal de respiração.
4. **O case do ESP32 fica fora do colchão**, fixado na lateral do estrado com
   abraçadeira, longe de onde possa cair soro, água ou xixi.
5. **Cabo com alívio de tensão.** A enfermeira troca o lençol puxando. Se o
   cabo estiver tenso, a junta da célula é o primeiro ponto a romper.

## Alimentação e segurança elétrica

- 5 V por USB, fonte bivolt com selo Inmetro, ligada numa tomada da enfermaria.
- **Fusível de 1 A na entrada da fonte.** É a proteção contra curto dentro do case.
- Nenhuma parte metálica do conjunto fica exposta ao alcance do residente.
- O nó **não tem bateria**, de propósito. Lítio sob colchão, sem supervisão, é
  risco de incêndio. Se faltar energia no lar, falta luz no quarto inteiro:
  isso já é uma emergência maior que a leitura de respiração.
- Consumo medido na bancada: na casa dos 100 mA em 5 V. Cem leitos consomem
  menos que duas lâmpadas LED.

## Antes de energizar

Confira com multímetro, com a fonte desligada:

- [ ] Não há curto entre 5 V e GND no borne de entrada do case.
- [ ] As quatro juntas de célula estão isoladas com termocontrátil.
- [ ] O GND do HX711 e o GND do ESP32 são o mesmo ponto.
- [ ] O cabo de força passa pelo alívio de tensão e não está esticado.
