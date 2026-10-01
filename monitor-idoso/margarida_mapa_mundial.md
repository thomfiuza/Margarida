# Projeto Margarida — mapa mundial de detecção de queda e sensores replicáveis

> **Por que "Margarida":** em homenagem à avó do fundador, que caía muito e
> morreu dormindo. Não é detalhe de marketing — é a especificação do produto.
> Os dois pilares do projeto atacam exatamente os dois riscos da história dela:
> **quedas** (detecção + SOS com contexto) e **noite** (monitoramento passivo de
> FC/FR sob o colchão — `bcg_noturno.py`, já testado 3/3).

**Sobre "plágio":** funcionalidades e ideias não são protegidas por direito
autoral — replicar atributos de produtos é concorrência legítima (o que não se
copia é código-fonte, marca e design visual). Único cuidado real: **patentes**
de método (Apple tem patentes de detecção de queda por wearable; Vayyar, de
radar). Usar a técnica de forma genérica com implementação própria é o caminho
seguro — e é o que este mapa propõe.

---

## 1. A evidência que justifica tudo (o "long lie")

- **47% dos idosos que caem sem se machucar NÃO conseguem se levantar sozinhos**
  (PMC8004721).
- **Mais de 1 hora no chão = ~50% de probabilidade de morte**, mesmo sem lesão
  grave — por desidratação, hipotermia, pneumonia (PMC8004721; BMC Public
  Health). Complicações: rabdomiólise, úlceras de pressão, hemorragia interna.
- No Brasil (dados já levantados): 48.576 internações e 9.050 mortes de idosos
  por queda no SUS em 2025 (~24/dia); ~25% dos idosos caem ao menos 1x/ano.
- Conclusão de produto: **o valor não está em detectar a queda — está em
  reduzir o tempo até o socorro.** O SOS sequencial da Margarida (já testado
  5/5) é exatamente isso.

## 2. Mapa mundial das 6 tecnologias de detecção de queda

| # | Tecnologia | Como funciona | Produtos reais | Precisão | Privacidade | Veredito p/ Margarida |
|---|---|---|---|---|---|---|
| 1 | **Vestível (acelerômetro+giroscópio)** | impacto + não-levantar | Apple Watch (SOS após 60 s), pingentes Medical Guardian | moderada; erra quedas LENTAS (típicas de idosos); teste da SafeWise: 0 detecções em 18 simulações | alta | ❌ como base: o problema do "não-uso" é fatal — tiram p/ carregar (à noite!) e p/ banhar (banheiro = maior risco) |
| 2 | **Radar mmWave 60 GHz** | nuvem de pontos + velocidade Doppler; funciona no escuro e no vapor, sem imagem | **Mindêllo FALLR1 (BR, R$ 900, Matter, 90%, 13 m²)**; Vayyar Care (líder mundial, 16 m², +respiração e long-lie, assinatura); Essence MDsense; EchoCare (teto, 40 m²); Butlr | pesquisa: até 99,5% (nuvem 1-D + Doppler) | **alta** (não captura imagem — aceitável em quarto/banheiro) | ✅ **integrar sensor de prateleira via Matter** (v1.5) — replicar a FUNCIONALIDADE sem fabricar o radar |
| 3 | **Câmera + IA (pose)** | vídeo ou "palito" | Kami (~US$ 100 + US$ 45/mês); AltumView Sentinare (só silhueta, ~US$ 200, básico grátis); SafelyYou (ILPIs) | alta com boa IA (97%+) | **baixa** — idosos recusam câmera em quarto/banheiro; risco de vazamento | ❌ não usar onde a privacidade manda (quarto/banheiro) |
| 4 | **LiDAR** | varredura 3D sem foto realista | Virtusense VSTAlert (EUA) | boa | alta | ⏸ caro p/ consumidor; observar |
| 5 | **Tapete de pressão / piso** | peso no chão ou saída do leito | tapetes de saída de leito (Nomo, Nursing Home Aids) | boa p/ leito | alta | ✅ barato: **tapete sob o colchão/cama já é o nosso pad BCG** — o mesmo hardware faz saída do leito + FC/FR noturna |
| 6 | **PIR / contato (presença, portas, gavetas)** | movimento ou abre/fecha | kits Envoy At Home, Nomo Smart Care, Guardian, Best Buy Assured Living | — | alta | ✅ baratíssimo (R$ 20–60/sensor, Tuya/Zigbee): inatividade, armário de remédio, porta de rua |

## 3. Além da queda — funcionalidades replicáveis (o "revirar o mundo")

| Funcionalidade | Quem faz lá fora | Como a Margarida replica | Rota |
|---|---|---|---|
| **Inatividade / desvio de rotina** ("não saiu do quarto até 10h") | Envoy At Home, Nomo, Guardian, Sense (energia) | se a rotina da manhã não acontece no app → alerta ao cuidador. **Dá para fazer só com o celular, sem sensor nenhum** | v1 (software) |
| **Saída do leito à noite** | tapetes de pressão (Nomo) | o pad BCG sob o colchão já vê isso (o sinal some quando a pessoa levanta) | v2 (com o pad) |
| **Queda + long-lie (imóvel no chão)** | Vayyar (long-lie alert), Mindêllo (confirma se imóvel 5–90 s) | integrar radar Matter de prateleira; o ALERTA dispara o mesmo SOS sequencial da Margarida (que os radares sozinhos não têm — só apitam na Alexa) | v1.5 |
| **Respiração/FC durante o sono** | Vayyar (radar), Oura/Emfit (colchão) | pad BCG — DSP pronto e testado (`bcg_noturno.py` 3/3) | v2 |
| **Armário de remédio aberto (ou não aberto)** | sensor de contato + app (Dwell/GoodRx) | sensor Tuya R$ 25 + evento no diário + voz da rotina lembrando | v1.5 |
| **Fogão ligado / gás** | desligadores automáticos (GoodRx) | sensor de gás R$ 60 → evento urgente no app | v1.5 |
| **Porta de rua (deambulação/demência)** | PIR + pager (Smart Caregiver, Val-U-Care) | sensor de contato + alerta "porta aberta às 2h" | v1.5 |
| **Luz noturna automática (prevenção)** | kits Matter/Tuya | a mesma integração apaga/acende luzes; queda evitada > queda detectada | v1.5 |
| **SOS que liga em sequência com contexto** | SOS Control (central paga), botões Tuya/iLinq (só apito/notificação) | **já construído e testado** — SOS + histórico de saúde da semana. Nenhum player de sensor tem isso | ✅ pronto |

## 3b. Estado da implementação (o que já está pronto deste mapa)

| Item do mapa | Estado |
|---|---|
| Regras de radar de queda (estilo FALLR1: imóvel ≥15 s confirma → SOS) | ✅ `sensores.py` 6/6 + core Kotlin |
| Gás → SOS; porta na janela de sono → deambulação; remédio esquecido | ✅ idem (regras idênticas nas duas linguagens) |
| Saída do leito pelo pad (silêncio do BCG) | ✅ `sensores.detectar_saida_leito` (detecta cama vazia em sinal sintético) |
| Inatividade da manhã (só software, conceito Envoy/Nomo) | ✅ `orquestrador.checar_inatividade` (1 alerta/dia até 10h) |
| Queda confirmada dispara o SOS sequencial com contexto de saúde | ✅ ponta a ponta, testado em Python (7/7) e Kotlin (21/21) |
| Ponte Matter/Tuya no app + sensores físicos na casa | 🔨 v1.5 (precisa hardware de prateleira + casa real) |
| Pad BCG físico sob o colchão | 🔨 v2 (DSP pronto) |

## 4. A jogada estratégica da Margarida

1. **Não fabricar radar na v1.** O FALLR1 prova que sensor de queda bom e
   barato existe no Brasil (R$ 900, Matter, USB-C, instalação simples). A
   Margarida **não compete com ele — ela o absorve**: o radar vira mais uma
   fonte de eventos no diário, e quem transforma o evento em SOCORRO REAL
   (ligação em sequência + contexto de saúde + SMS com mapa) é o app.
   Hub = celular do cuidador + gateway Matter/Tuya barato (~R$ 100).
2. **Onde a física exige hardware próprio, ele já está desenhado:** o pad sob o
   colchão (BCG noturno + saída do leito) — que é exatamente o pilar da
   história da Margarida (morte dormindo). Radar nenhum no mercado brasileiro
   faz tendência de FC/FR noturna por R$ acessível (Vayyar faz, com
   assinatura, em dólar).
3. **A ordem que minimiza risco:** v1 = SOS por voz + inatividade por software
   (zero hardware) → v1.5 = sensores de prateleira integrados (radar, porta,
   remédio, gás) → v2 = pad noturno próprio.

## 5. Fontes principais deste mapa

- Mindêllo FALLR1: mindello.com.br (specs) e iG Tecnologia (24/06/2025, origem
  do produto — sogra com Parkinson).
- Vayyar/Essence/EchoCare/Virtusense/Chiun Mai: sourcingcares.com;
  porchlightathome.com (tabela vestível vs. não-vestível); elderlydaily.com;
  forasoft.com (radar vs câmera vs pingente; validação em Scientific Reports 2026).
- Long-lie e vestíveis: PMC8004721 (1 h no chão → ~50% mortalidade; 47% não se
  levantam); falldetection.com (problema do não-uso; Apple Watch e quedas
  lentas); safewise.com (teste prático do Apple Watch).
- Sensores ambientais: guardianhome.eu (9 sistemas); dwell.com; goodrx.com.
