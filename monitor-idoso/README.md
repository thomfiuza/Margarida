# Projeto Margarida (monitor do idoso) — protótipo integrado com voz, SOS e jornada completa

> **Margarida** — em homenagem à avó do fundador, que caía muito e morreu
> dormindo. Os dois pilares do projeto são os dois riscos da história dela:
> quedas (SOS com contexto + sensores) e noite (monitoramento passivo).
> Mapa mundial de sensores de queda: `margarida_mapa_mundial.md`.

Fluxo único guiado por voz que combina:

- **B — teste ortostático sem manguito:** FC deitado/sentado → FC em pé (rPPG pela
  câmera), resposta cronotrópica, sintomas relatados;
- **A — hidratação pela urina:** escala de Armstrong 1–8 com cartão branco;
- **histórico + tendências contra a linha de base pessoal** e **relatório/alerta
  para o cuidador** (bem-estar, não diagnóstico);
- **SOS por comando de voz** (`panico.py`): ligações em sequência para os
  contatos + SMS com localização **e contexto de saúde**;
- **noite** (`bcg_noturno.py`): FC/FR sob o colchão (pad BCG, v2) + detecção
  de saída do leito pelo silêncio do sinal (`sensores.py`);
- **sensores de casa** (`sensores.py`): regras prontas para radar de queda
  (estilo FALLR1 → dispara o SOS), gás, porta na madrugada (deambulação),
  armário de remédio e saída do leito — mapa mundial em
  `margarida_mapa_mundial.md`;
- **inatividade da manhã** (só software): rotina não feita até as 10h →
  alerta ao cuidador;
- **orquestrador** (`orquestrador.py`): rotina + noite + SOS + sensores +
  inatividade convergem num diário único de eventos — é o núcleo de negócio
  do app Android (ver `especificacao_android.md`); paridade Kotlin testada em
  `../app-android/core/` (8/8).

## Rodar

```bash
python3 fluxo_idoso.py --repouso video_repouso.avi --pe video_pe.avi \
        --urina foto_com_cartao.jpg --tontura --historico historico.jsonl
```

Testes (todos com vídeo/foto em disco, pelo mesmo caminho de I/O do app):

```bash
python3 verificar_tudo.py   # TUDO: 8 suítes + arquivos, um comando só
python3 testes_fc.py        # 4/4  rPPG: 55/72/100 bpm ±3, portões de qualidade
python3 testes_urina.py     # 4/4  32 combinações nível×luz (±1 p/ níveis gêmeos da carta)
python3 testes_fluxo.py     # 5/5  integração A+B, alertas, tendência, repetição
python3 testes_panico.py    # 5/5  SOS: sequência, SMS, cancelamento, falhas
python3 testes_bcg.py       # 3/3  DSP noturno: FC/FR conhecidas recuperadas
python3 testes_orquestrador.py  # 4/4  jornada completa + SOS com contexto
python3 testes_pipeline.py  # (pasta olho-hidratacao) 8/8
```

Validação com pessoas reais: `validar_dataset.py --raiz <UBFC> --tipo ubfc`
(UBFC-RPPG é gateado por formulário; LGI-PPGI é CC-BY mas hospedado em zips de
4–7 GB instáveis — este sandbox não consegue baixar; `--selftest` verifica a
matemática do relatório).

## Paridade: o que os outros apps têm, aqui também tem

| Qualidade de app existente | Origem | Aqui |
|---|---|---|
| Protocolo ortostático guiado + log + interpretação | OrthoStat | `fluxo_idoso.py` — e a medição é **pela câmera**, não digitada |
| FC/HRV por PPG | Welltory, fones, relógios | `fc_camera.py` (sem contato) — opcional dedo-no-flash |
| Escala de hidratação + lembretes | apps de água | `urina.py` — leitura **objetiva** pela câmera com cartão |
| Alerta a contatos de cuidado | relógios com detecção de queda | nível de alerta (nenhum/atenção/urgente) + relatório |
| Tendência longitudinal | apps de sono/peso | histórico JSONL + comparação com a média pessoal |
| Acessibilidade idoso | — | fluxo 100% por voz (áudio em `audio/`), passos curtos |

## O que só este protótipo tem (lacunas de mercado implementadas)

1. Teste ortostático **medido** sem nenhum hardware além do celular;
2. Hidratação objetiva por foto, invariante a luz/aparelho (cartão branco);
3. Cruzamento dos dois sinais (ΔFC exagerado + urina escura = desidratação com
   resposta cardiovascular) num único alerta ao cuidador;
4. Portões de qualidade que **recusam medir** quando a foto/vídeo não presta —
   em vez de chutar um número.

## O que ele NÃO faz (por honestidade)

- Não mede pressão arterial absoluta (rPPG erra ±8–15 mmHg sem calibração);
- Não diagnostica; posicionamento = bem-estar + alerta a cuidador;
- Não funciona com fone bluetooth comum para FC (fone comum não tem PPG).

## Estado e próximos passos no mundo real

| Item | Estado aqui | Falta no mundo real |
|---|---|---|
| rPPG | 4/4 sintético | rodar `validar_dataset.py` no UBFC-RPPG |
| Urina | 4/4 sintético + rampa calibrada na carta oficial | fotos reais com cartão p/ calibrar |
| Fluxo A+B voz | 5/5 integrado + áudios TTS | teste de usabilidade com idosos |
| Piloto | plano escrito (`piloto_cuidadores.md`) | aprovação de CEP + recrutamento |
