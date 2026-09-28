# "Sensor de hidratação pela câmera do celular mirando na retina"

## Veredito curto

**Do jeito descrito, não funciona — e o motivo é físico, não de software.** Há duas
afirmações embutidas na ideia, e as duas precisam ser corrigidas:

1. **"Mirar na retina"**: a retina fica no *fundo* do olho. A câmera do celular
   sozinha não a alcança. Para imagear retina é preciso óptica adicional
   (lente condensadora de +20 D, ou adaptadores tipo D-Eye / iExaminer /
   RetinaScope), sala escurecida, e pupila de **pelo menos ~5 mm** — ou seja,
   midríase. Mesmo esses aparelhos dedicados têm taxa de captura ruim quando a
   pupila está contraída abaixo de ~3,5 mm.
2. **"Hidratação" pela retina**: não encontrei nenhum estudo que estime
   hidratação corporal a partir de imagem de retina. O que a literatura mostra é
   outra coisa (abaixo).

O que **existe e é mensurável** com câmera de celular é a superfície ocular
(conjuntiva/esclera e filme lacrimal) — e mesmo isso não é hidratação corporal.

---

## O que a literatura de fato sustenta

| Alvo | Evidência | Números |
|---|---|---|
| Hiperemia conjuntival (vermelhidão) | App iOS `Conjunctiva VD`, densidade vascular | ρ = 0,780–0,807 vs. escala de Efron (3 oftalmologistas, 100 ROIs) |
| Olho seco leve | Foto de celular + ResNet-50 vs. teste do fio de fenol | acurácia 0,80 (validação) / 0,75 (teste) |
| Biomarcadores sistêmicos pela foto do olho externo | Sistema de deep learning, *Lancet Digital Health* 2023 | detecta hemoglobina < 11 g/dL, eGFR < 60, ACR ≥ 300 etc. — **não hidratação** |
| Hemoglobina pela conjuntiva palpebral | Mask R-CNN + MobileNetV3, fotos de celular | R² = 0,503, **MAE = 1,6 g/dL** (errático demais para triagem fina) |
| Desidratação por **rosto** (não olho) | "Dehydration Scan", rede siamesa sobre marcos faciais | acurácia global **76,1%**, especificidade **52,1%** — e os próprios autores apontam que os **olhos** são o marco mais afetado |
| Hidratação por **óptica de verdade** | Espectroscopia NIR multi-comprimento de onda (970/1200/1450 nm) em pele | R²CV = 0,95–0,9975 — mas exige sensor dedicado, **não** câmera RGB |
| Hidratação por bioimpedância/RF/termal | Wearables (HydroTrack, BioMind, SHS) | ±5% de conteúdo volumétrico de água; exige eletrodos ou elemento aquecido |

**Leitura honesta:** a câmera RGB do celular é um bom instrumento para *cor e
morfologia da superfície ocular*. Ela é um instrumento ruim para *conteúdo de
água no tecido*, porque água não tem assinatura visível no espectro RGB — as
bandas de absorção da água estão no infravermelho próximo (970, 1200, 1450 nm),
faixa que o filtro IR-cut do sensor do celular bloqueia de propósito.

---

## O que foi construído aqui

Um pipeline real em OpenCV puro (sem deep learning, sem dependência pesada) que
extrai da foto de um olho o que a literatura diz ser extraível — **não** um
número de hidratação.

```
olho-hidratacao/
├── analisador_olho.py     # o pipeline: qualidade → segmentação → métricas → índice
├── olho_sintetico.py      # gerador de olho paramétrico (só para testar a mecânica)
├── testes_pipeline.py     # 8 testes de regressão
├── amostras/              # 3 fotos reais de olho + 1 sintética
└── relatorios/            # JSONs e painéis de máscara
```

Uso:

```bash
python3 analisador_olho.py foto.jpg --json saida.json --debug painel.png
python3 testes_pipeline.py
```

### O que ele mede

- **Portão de qualidade** — nitidez (`var(Laplaciano)/var(imagem)`), estouro de
  luz, subexposição. Rejeita a foto em vez de devolver número falso.
- **Segmentação** — ancora na íris (único blob escuro e circular da cena),
  monta a fenda palpebral, exclui reflexo especular e pele.
- **Vermelhidão** — *Relative Redness* (Papas), R−G, R−B, hue, a\* do CIELAB.
- **Densidade vascular** — tophat + limiar de fundo+3σ.
- **Filme lacrimal** — manchas opacas por desvio do fundo local + contraste do
  menisco na margem inferior.
- **Índice de superfície ocular** (0–100) e **delta contra linha de base pessoal**.

### O que os testes verificaram

`python3 testes_pipeline.py` → **8/8 passaram**. Cada teste gera um olho
sintético com um parâmetro conhecido e confere se a métrica responde:

| Teste | Resultado |
|---|---|
| Vermelhidão ↑ → *Relative Redness* ↑ | −0,1273 → −0,0965, r = 0,976, monotônico |
| Nº de vasos ↑ → densidade ↑ | 0,1459 → 0,1738, r = 0,857 |
| Filme rompido ↑ → manchas opacas ↑ | 0,0147 → 0,0236, r = 0,937 |
| Foto borrada → rejeitada | nitidez 0,097 < 0,15 |
| Foto estourada → rejeitada | estouro em 84% dos pixels |
| Ruído puro → rejeitado | íris não localizada |

Nas **3 fotos reais** de olho (Vecteezy, 622×350):

- 2 rejeitadas corretamente — esclera visível em 5,6% e 1,3% da área do olho
  (olho pouco aberto). O portão funcionou como deveria.
- 1 aceita: `a* = 1,71` (esclera branca, saudável), densidade vascular `0,118`,
  13 manchas opacas, índice `27,6`.

---

## Bugs reais encontrados durante a construção

Valem registro porque são armadilhas típicas desse tipo de projeto:

1. **A máscara de "esclera" media a pele.** Pele clara de estúdio tem
   HSV = (15, 68, 205) — mais clara e menos saturada que a própria esclera.
   A máscara cobria 203.440 px, bbox 0–639 × 0–399: o quadro inteiro.
2. **Selecionar "o maior blob claro" falha por estrutura.** A íris divide a
   esclera em dois lobos (nasal e temporal); a elipse saía ajustada a um lobo
   só, cobrindo metade do olho.
3. **Métrica de nitidez que se auto-cancelava.** Gradiente médio / desvio-padrão
   caía de 1,198 para 1,183 numa imagem borrada com σ=6 — porque o desfoque
   reduz numerador **e** denominador. Trocado por `var(Laplaciano)/var(imagem)`,
   que vai de 1,135 a 0,006.
4. **Limiar absoluto de reflexo especular.** `V>235 & S<35` marcou 75.855 px
   (30% do quadro); `V>232 & S<40` apagava 6.187 dos 5.642 px de esclera.
   Resolvido com percentil 99,3 do brilho dentro do olho.
5. **Linha que invertia a máscara.** `reflexo[cand == 0] = 0` dentro do loop
   zerava o acumulado a cada iteração; no fim, "reflexo" era tudo que *não* era
   reflexo.
6. **Denominador que encolhia junto com o que se mede.** Vasos e manchas secas
   deixam de ser "claros", então mais vasos ⇒ máscara de esclera menor ⇒ razão
   instável. Todas as métricas passaram a usar uma região geométrica fixa
   (olho menos íris).
7. **Limiar de vasos calibrado no sintético não vale no real.** Fixo em 10,0
   dava **0,6005** de densidade vascular numa foto real (60% de "vasos").
   Fundo+3σ dá 0,1180, que é plausível.
8. **Gerador que não era comparável.** Cada vaso/mancha consumia números
   aleatórios, então mudar a quantidade mudava o ruído da imagem inteira.
9. **Íris clara quebra a âncora.** Em olhos azuis/verdes só a pupila é escura;
   a âncora acha a pupila, a exclusão subestima a íris e textura de íris entra
   na máscara de esclera (ver painel `real-3`, arco verde sobre a íris). O
   pipeline agora **detecta e avisa** (aviso nº 4 no JSON da foto real-3) em vez
   de fingir que mediu só esclera. Resolver de verdade exigiria um segmento de
   íris treinado com dados reais.

---

## O que seria preciso para virar produto

Se o objetivo é **superfície ocular / olho seco** (caminho defensável):

- Dataset próprio com referência clínica (Efron, TBUT, Schirmer, osmolaridade
  lacrimal). Sem isso não há calibração, só heurística.
- Padronização de captura: distância fixa, iluminação difusa, sem flash direto.
  A literatura mostra que tipo de câmera, calibração e nível de luz mudam
  objetivamente as métricas de vermelhidão.
- Linha de base **pessoal**: comparar a pessoa com ela mesma ao longo do tempo
  é muito mais robusto que um limiar absoluto entre pessoas.
- Enquadramento regulatório: se o app afirmar algo sobre saúde, entra na alçada
  da ANVISA como *software as a medical device*.

Se o objetivo é **hidratação corporal** (caminho que a câmera RGB não entrega):

- Bioimpedância (pulseira/anéis), ou
- Espectroscopia NIR dedicada em 970/1200/1450 nm, ou
- Sinais indiretos já disponíveis no celular: variação de peso + cor da urina +
  frequência cardíaca + sudorese estimada. Nenhum é "apontar para o olho".

---

## Avisos

O índice produzido por `analisador_olho.py` é **heurística não validada
clinicamente**. Não é dispositivo médico, não mede água corporal e não deve
orientar decisão clínica. As imagens em `amostras/` vieram de busca de imagens
(Vecteezy) e foram usadas apenas como teste de robustez do pipeline — verifique
a licença antes de qualquer outro uso.
