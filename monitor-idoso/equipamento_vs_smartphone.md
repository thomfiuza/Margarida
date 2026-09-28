# Equipamento dedicado vs. smartphone — a decisão

**Recomendação: smartphone-first.** O hub é o celular do cuidador; hardware dedicado só
onde a física impede o celular (noite e banheiro). Construir um "tudo-em-um" agora é o
pior caminho possível: caro, regulado, difícil de manter — e não resolve nada que o
celular não resolva.

---

## 1. Por que o smartphone ganha a v1

| Fato | Fonte | Consequência |
|---|---|---|
| 80,3% dos brasileiros 60+ possuem celular (era 78,3% em 2024 — maior crescimento entre todas as faixas) | PNAD Contínua TIC 2025, IBGE | O hardware já existe no bolso; não precisa vender nada |
| 74,5% dos 60+ usam internet (70,1% em 2024) | PNAD TIC 2025 | Conectividade para sincronizar com o cuidador |
| 65+ passam ~22 h/semana no smartphone | Nielsen | O canal do cuidador é o app, não um novo objeto |
| Principal motivo de NÃO usar tecnologia: não saber usar | PNAD TIC 2025 | Reforça o fluxo guiado por voz: o idoso não opera, só responde |
| Um dispositivo novo = ANVISA (cadastro/registro, BGMP), Inmetro, garantia, suporte, baterias | regulação BR | Custo de R$ 100 mil+ e meses antes do 1º usuário |
| Bem-estar/alerta ao cuidador no celular não exige registro ANVISA (desde que não faça alegação de diagnóstico) | regulação BR | v1 pode ir a piloto amanhã |

E a paridade de recursos (rPPG de FC, ortostática sentada→pé, urina com cartão de cor,
voz, tendências) já está **implementada e testada neste repositório** — sem um grama de
hardware novo.

## 2. Onde a física exige hardware (e só aí)

### 2a. Noite — pad BCG sob o colchão ✔ justificada
À noite não existe câmera nem dedo: ninguém segura celular dormindo. O recuo mecânico
do corpo a cada batimento (balistocardiograma) atravessa o colchão. Tendência de FC/FR
noturna é o sinal clássico de descompensação de IC e infecção **antes dos sintomas** —
exatamente a aposta do projeto.

- **Software: pronto e testado aqui** — `bcg_noturno.py` + `testes_bcg.py` (3/3:
  recupera FC 58/70/82 bpm e FR 12/15/18 irpm de sinal sintético; rejeita ruído puro e
  janela curta). O DSP viveria no firmware do pad ou no app.
- **Hardware estimado:** ESP32 + ADXL345 (acelerômetro) + bateria LiPo + case =
  BOM ~US$ 10–15 em protótipo (estimativa de balcão, não cotação). Sem tela, sem botão:
  liga, esquece embaixo do colchão.
- **Honestidade:** o teste valida a matemática. Precisão clínica exige pad físico
  calibrado por colchão/posição — isso é trabalho de bancada, semanas.

### 2b. Banheiro — módulo de urina no vaso ✔ justificada (v2.5)
A foto da urina no fluxo atual depende de o cuidador fotografar. Automatizar no vaso é
uma categoria real e **cara**: Kohler Dekoda US$ 599 + assinatura (consumidor, out/2025);
TrueLoo/Toi Labs em ILPIs; OutSense aguardando FDA; Stanford só pesquisa.

- A evidência diz que dá para ser barato: protótipo in-toilet com **cor + condutividade**
  explica **85% da variância do USG** (R²=0,85, 514 amostras, ScienceDirect 2025);
  sensor TCS34725 + nuvem atinge 84% de acurácia vs USG (Procedia CS 2018).
- **Lacuna:** um módulo barato (fita + sensor de cor, faixa de R$ 200–400) voltado ao
  idoso brasileiro não existe no mercado — os players estão todos acima de US$ 500.
- Mesmo assim: v2.5, depois que o app provar adesão.

### 2c. O que NÃO precisa de PCB
| Sinal | Solução | Motivo |
|---|---|---|
| Pressão arterial calibrada | Manguito BT de farmácia (R$ 100–200) | Aparelho registrado já existe; o app só lê via BLE |
| FC contínua de repouso | Fone/anel PPG de prateleira | PPG de orelha tem viés pequeno (~0,8 bpm); não vale projetar |
| PA por rPPG/câmera | **Não fazer** | ±8–15 mmHg na literatura; promessa clínica insustentável |

## 3. Matriz: sinal → plataforma

| Sinal | Smartphone | Hardware dedicado | Decisão |
|---|---|---|---|
| FC por rPPG | ✅ pronto (`fc_camera.py`) | — | smartphone |
| Ortostática (sentada→pé) | ✅ pronto (`fluxo_idoso.py`) | — | smartphone |
| Urina por foto + cartão | ✅ pronto (`urina.py`) | — | smartphone (v1) |
| Fluxo guiado por voz | ✅ pronto (8 áudios pt-BR) | — | smartphone |
| Tendências/alertas cuidador | ✅ pronto (JSONL + relatório) | — | smartphone |
| PA calibrada | — (rPPG-BP não) | manguito BT de prateleira | periférico comprado |
| FC/FR noturna | ❌ física impede | pad BCG (ESP32+ADXL345) | **hardware v2** |
| Urina automática | ❌ exige foto manual | módulo no vaso (cor+condutividade) | **hardware v2.5** |

## 4. Roadmap

1. **Agora — v1 smartphone (0 hardware):** piloto com cuidadores já planejado
   (`piloto_cuidadores.md`: n=15–20, 4 semanas, CEP/Conep, referências manguito+USG).
   O celular do cuidador roda tudo; o idoso só ouve e responde.
2. **v1.5 — periféricos de prateleira:** integrar manguito BT (calibração de PA) e, se
   quiser, fone PPG. Zero PCB, zero ANVISA.
3. **v2 — protótipo do pad BCG:** DSP já testado aqui; bancada = acelerômetro + colchões
   reais + validação contra oxímetro noturno. Só se o piloto mostrar demanda por noite.
4. **v2.5 — módulo sanitário barato:** cor + condutividade vs USG, alvo R$ 200–400 —
   lacuna real contra Dekoda US$ 599 / TrueLoo.
5. **v3 — integração "tudo-em-um": só se 2–4 provarem tração.** Aí sim vale enfrentar
   ANVISA/Inmetro — com receita que paga a certificação.

## 5. Linha vermelha (não muda com hardware)

Bem-estar e alerta ao cuidador; nunca diagnóstico. Alegação de dispositivo médico só
depois de validação clínica + registro ANVISA. O pad e o módulo de banheiro, se virarem
produto com alegação clínica, entram na mesma régua.
