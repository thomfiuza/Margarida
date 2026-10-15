# 🧾 Lista de componentes (BOM) do nó de leito

Versão 1, 100 leitos. Preços de referência pesquisados em distribuidores
brasileiros de eletrônica em outubro de 2026. **Todo preço aqui é referência
para dimensionar orçamento. A compra real exige 3 cotações e nota fiscal.**

## Por unidade

| # | Item | Especificação mínima | Qtde | Ref. unit. | Subtotal |
|---|---|---|---|---|---|
| 1 | Célula de carga | 50 kg, meia-ponte, alumínio, fio 4 vias | 4 | R$ 7,30 | R$ 29,20 |
| 2 | Conversor A/D | HX711, 24 bits, módulo com barra de terminais | 1 | R$ 9,90 | R$ 9,90 |
| 3 | Microcontrolador | ESP32-WROOM-32, placa de desenvolvimento 30 pinos, USB | 1 | R$ 42,00 | R$ 42,00 |
| 4 | Fonte | 5 V / 2 A, saída micro-USB ou USB-C, bivolt, com selo Inmetro | 1 | R$ 18,00 | R$ 18,00 |
| 5 | Cabo de força | USB-A para micro-USB (ou USB-C), 1,5 m, com filtro de ferrite | 1 | R$ 8,00 | R$ 8,00 |
| 6 | Case | Impressão 3D em PLA ou PETG, base + tampa, ~60 g de filamento | 1 | R$ 6,00 | R$ 6,00 |
| 7 | Fio | Cabo PP 4x0,30 mm, 1,5 m | 1 | R$ 3,50 | R$ 3,50 |
| 8 | Terminais | Barra de terminais 2 vias x4 + termocontrátil + solda | 1 | R$ 2,40 | R$ 2,40 |
| 9 | Proteção elétrica | Fusível de vidro 1 A + porta-fusível na entrada da fonte | 1 | R$ 3,20 | R$ 3,20 |
| 10 | Fixação | 4 sapatas de borracha 40x40 mm + etiqueta de leito | 1 | R$ 2,26 | R$ 2,26 |
| | **TOTAL POR UNIDADE** | | | | **R$ 114,46** |

## Para 100 leitos

| Item | Valor |
|---|---|
| Componentes, 100 unidades | R$ 11.446,00 |
| Folga de 15% (peça com defeito, perda de solda, refugo de impressão 3D) | R$ 1.714,00 |
| **Total de componentes para 100 leitos** | **R$ 13.160,00** |

## Infraestrutura da instituição (não é por leito)

| Item | Qtde | Ref. unit. | Subtotal |
|---|---|---|---|
| Roteador mesh dedicado às enfermarias (rede separada da rede administrativa) | 3 | R$ 700,00 | R$ 2.100,00 |
| Tablet Android para o posto de enfermagem (10 pol, 3 GB RAM, suporte de parede) | 2 | R$ 1.300,00 | R$ 2.600,00 |
| Material elétrico, canaleta, mão de obra de instalação da rede | 1 | R$ 1.500,00 | R$ 1.500,00 |
| **Total de infraestrutura** | | | **R$ 6.200,00** |

Componentes (R$ 13.160) + infraestrutura (R$ 6.200) = **R$ 19.360**, dentro da
linha orçamentária de R$ 15.000 de infraestrutura mais a folga da linha de
nuvem e treinamento. A linha de **R$ 65.000 é mão de obra de fabricação e
teste unitário**, não é compra de peça.

## Regras de compra (não pule)

1. **Protótipo antes do lote.** Compre 1 unidade, monte, passe nos 8 testes de
   `TESTE_BANCADA.md`. Só depois faça a cotação de 100.
2. **3 cotações por item**, com CNPJ e nota fiscal. Sem nota não tem prestação
   de contas de edital.
3. **Célula de carga: compre todas do mesmo lote.** Células de lotes diferentes
   têm sensibilidade diferente e a calibração vira um problema por leito.
4. **ESP32: exija módulo com certificação Anatel.** Placa sem certificação
   dentro de um lar de idosos é risco que não vale os R$ 15 economizados.
5. **Fonte: exija selo Inmetro.** É o único item do conjunto que fica ligado na
   tomada 24 horas por dia, num ambiente com pessoa dormindo em cima.
6. Guarde 5 unidades completas de reserva. Reposição de eletrônica comum é
   barata, mas ficar 3 semanas esperando um lote é caro.

## O que deliberadamente NÃO está nesta lista

- **Sem bateria.** O nó fica na tomada. Baterias de lítio sob colchão, sem
  supervisão, são risco de incêndio e não valem a economia de um cabo.
- **Sem câmera, sem microfone, sem wearable.** É decisão de projeto, não
  economia. Está na essência intocável do `ROADMAP_ABERTO.md`.
- **Sem nuvem paga por leito.** O broker é local. A nuvem do projeto recebe
  só agregado sem identificação.
