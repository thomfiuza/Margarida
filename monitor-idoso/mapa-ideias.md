# Mapa de ideias — monitoramento de idosos com celular + fone

Foco: **idosos**, não atletas. Critério de ranking: (1) evidência científica,
(2) lacuna de mercado, (3) dá para replicar hoje com o hardware que o idoso já
tem, (4) impacto clínico (quedas, desidratação, declínio cognitivo, IC).

---

## 0. Choque de realidade por sensor

| Sensor | O que mede de verdade | O que NÃO mede |
|---|---|---|
| Câmera RGB (sem contato) | FC por rPPG (pulso óptico da pele); cor de pele/conjuntiva/urina; movimento (queda, marcha, tremor) | Pressão arterial absoluta confiável; "ressonância/raio-X"; glicemia; água corporal |
| Câmera + dedo no flash (contato) | PPG de dedo: FC e HRV sólidos; tendência de PA pelo método oscilométrico de pressão do dedo (precisão 8,8 mmHg — só tendência) | PA absoluta clínica sem manguito |
| Fone bluetooth COMUM | IMU (cabeça: queda, marcha, tremor), microfone+alto-falante (audiometria, voz), às vezes temperatura | FC/SpO2: **fone comum não tem sensor óptico**. "Medir pelo contato" só com fone customizado |
| Fone com PPG intra-auricular (custom) | FC em repouso com alta concordância vs ECG (viés ~0,8 bpm em estudo clínico); morfologia do PPG p/ estresse | Precisão durante movimento ainda ruim nos comerciais |
| Câmera "virando raio-X/ressonância" | Nada disso: câmera não emite ionizante nem campo magnético. O análogo real é a **sonda de ultrassom que pluga no celular** (Butterfly iQ, US$ 2.499–3.999 + assinatura) — existe, mas é equipamento clínico, não software | Tomografia por software é física impossível |

Fontes-chave: PPG intra-auricular validado em clínica [1](https://www.frontiersin.org/journals/digital-health/articles/10.3389/fdgth.2022.909519/full); fones comerciais de esporte imprecisos em movimento [2](https://www.mdpi.com/1424-8220/19/17/3641); PA por rPPG ±8–15 mmHg e dependente de calibração [3](https://circadify.com/blog/contactless-blood-pressure-measurement); método do dedo-pressão 3,3±8,8 mmHg [4](https://www.science.org/doi/10.1126/scitranslmed.aap8674); OptiBP passou protocolo AAMI com calibração e 85% de aceitância [5](https://pmc.ncbi.nlm.nih.gov/articles/PMC8568326/); deriva/limitações de cuffless [6](https://www.sciencedirect.com/science/article/pii/S2666693623000014); Butterfly [7](https://thedevicepulse.com/devices/butterfly-iq-plus/).

---

## 1. Ideias ranqueadas (lacuna × viabilidade × impacto no idoso)

### 🥇 A. Radar de desidratação do idoso — **PROTÓTIPO PRONTO** (`urina.py`)
- **Sinal:** cor da urina (escala Armstrong 1–8) com cartão branco de calibração.
- **Evidência:** cor acompanha osmolalidade/densidade; confiável em residentes de
  casa de repouso; colorimetria por smartphone com cartão de calibração melhora
  concordância entre aparelhos/luzes.
- **Mercado:** só estudos e apps de fita reagente; **não encontrei app consumer
  de tendência de hidratação para idoso/cuidador** — os próprios autores dos
  estudos dizem que o app "ainda é a base para desenvolvimento futuro".
- **Por que idoso:** sede não funciona no idoso; desidratação é causa clássica de
  internação evitável; o cuidador recebe o alerta.
- **Estado:** 4/4 testes sintéticos (32 combinações nível×luz exatas). Falta
  calibrar com amostras reais e fotos reais.

### 🥈 B. Teste ortostático em casa sem manguito — **PROTÓTIPO DE FC PRONTO** (`fc_camera.py`)
- **Sinal:** vídeo de 30 s deitado/sentado → de pé (1 e 3 min): FC por rPPG +
  balanço pelo IMU do celular no bolso + sintomas relatados por voz/toque.
- **Evidência:** hipotensão ortostática (HO) prediz quedas/síncope; a resposta de
  FC e o sway no StS capturam o fenômeno; monitoramento batimento-a-batimento
  associa HO a quedas.
- **Mercado:** existe o app OrthoStat, mas ele só **guia o protocolo com
  aferidor externo**; **teste ortostático medido pela câmera não encontrei**.
- **Estado:** rPPG 4/4 em vídeo sintético (55/72/100 bpm ±3; rejeita sem pulso,
  vídeo curto e degrada com movimento). Falta validação em dataset público
  (UBFC-RPPG/PURE) e piloto com idosos.

### 🥉 C. Tendência noturna de FC/FR com o celular no colchão
- **Sinal:** balistocardiografia por acelerômetro/microfone do celular sob o
  colchão: FC e frequência respiratória noturnas. Alta progressiva de FR/FC
  noturna precede descompensação de **insuficiência cardíaca** e infecções.
- **Mercado:** radares de sono e wearables caros; **celular-no-colchão para
  idoso não é produto estabelecido**.
- **Reprodutível:** sim — processamento de áudio/acelerômetro é acessível.

### D. Marcha e fragilidade com dois fones (IMU estéreo de cabeça)
- **Sinal:** velocidade de marcha, assimetria esquerda/direita, tempo de giro —
  "quinto sinal vital"; assimetria nova = alerta (AVC incipiente, Parkinson).
- **Mercado:** relógio conta passo; **qualidade de marcha consumer para idoso,
  não encontrei**. Dual-fone dá assimetria que o relógio não dá.

### E. Voz como biomarcador passivo (cognição + respiratório)
- **Sinal:** pausas, prosódia, vocabulário (declínio cognitivo); tosse/disfonia
  (respiratório, Parkinson). Durante ligações normais — zero esforço.
- **Mercado:** Sonde Health (510(k) Parkinson, 2023) e Winterlight (aprovações
  Canadá/UE) são **B2B/ensaios**; produto consumer de idoso em casa, lacuna.

### F. Conjuntiva: anemia + icterícia (reusa o pipeline do projeto anterior)
- Anemia é comum e subdiagnosticada no idoso; o pipeline `olho-hidratacao/` já
  extrai a*/vermelhidão com portões de qualidade. Mercado: nada robusto.

### G. Touchscreen como exame neurológico
- Espiral desenhada na tela, ritmo de toque, digitação: bradicinesia/tremor
  (Parkinson) e rastreio cognitivo. Mercado: pesquisa/clínicas (mPower, Linus
  Health); consumer idoso, lacuna.

### H. Plataforma "fone do idoso" (hardware custom ou aparelho auditivo)
- Idoso já usa aparelho auditivo; PPG intra-auricular é **validado em repouso**
  — e idoso passa o dia em repouso! Some temperatura, IMU de queda e audiometria.
- Lacuna: ninguém faz a plataforma de saúde do aparelho auditivo para o idoso.
- Não replicável aqui (exige hardware), mas é a tese de produto mais forte.

---

## 2. A combinação que vira produto ("não existe no mercado")

Nenhum item isolado é impossível de copiar; a combinação é a lacuna:

```
 Manhã (idoso):  vídeo 30 s sentado + 30 s em pé  -> FC repouso/ortostática + sway
               foto da urina com cartão branco   -> hidratação
 Semana:        voz nas ligações + marcha c/ fone -> tendência cognição/fragilidade
 Noite:         celular no colchão                -> FC/FR noturna
 Saída:         ALERTAS DE TENDÊNCIA para o cuidador, não "diagnósticos"
```

Posicionamento regulatório honesto: **bem-estar + alerta a cuidador + relatório
para o médico**, nunca diagnóstico. Se afirmar medição (PA, HO), entra como
software médico (ANVISA/FDA) e exige validação clínica.

Sobre PA, especificamente: não persiga "PA pela câmera sem manguito" — o estado
da arte erra ±8–15 mmHg e deriva sem calibração. Use um manguito barato
(R$ 100–200) UMA vez por mês para calibrar, e o celular cuida das **tendências**
entre calibrações. É o desenho que a literatura sustenta.

---

## 3. Estado dos protótipos nesta pasta

| Arquivo | O que é | Verificação |
|---|---|---|
| `fc_camera.py` | FC por rPPG (ROI de pele → verde → FFT → SNR) | `testes_fc.py` **4/4**: 55/72/100 bpm ±3; rejeita sem pulso/vídeo curto; SNR cai com movimento |
| `urina.py` | Escala de Armstrong 1–8 com cartão branco | `testes_urina.py` **4/4**: 32 combinações nível×luz exatas; sem cartão → recusa |
| `video_sintetico.py` | Vídeo de rosto com pulso conhecido | só valida mecânica; validação real = UBFC-RPPG/PURE/MMSE-HR |

Bugs reais já resolvidos nesses protótipos (documentados nos comentários):
cartão branco some sob luz quente (S sobe) → detector por razão azul/vermelho;
fundo alaranjado sob luz quente se funde com a amostra → ancoragem pela borda;
urina clara fica verde sob luz fria → detecção na imagem corrigida; luz fraca
escurece tudo → cartão ancora também a luminância; ROI/movimento no rPPG →
portão de SNR.

Próximos passos sugeridos (em ordem de valor): integrar A+B num fluxo guiado de
voz para o idoso; validar `fc_camera.py` contra UBFC-RPPG; coletar fotos reais de
urina com cartão para calibrar a rampa; depois piloto pequeno com cuidadores.
