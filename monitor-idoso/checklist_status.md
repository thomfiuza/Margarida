# Checklist do projeto — o que TEM, o que FALTA, o que NÃO DÁ

Gerado com `python3 verificar_tudo.py` (roda todas as suítes e checa arquivos).

## ✅ PRONTO E TESTADO (software, roda hoje)

| # | Recurso | Arquivo | Testes |
|---|---|---|---|
| 1 | FC pela câmera do rosto (rPPG) — aponta, espera 15 s, mede | `fc_camera.py` | 4/4 |
| 2 | Teste ortostático A+B (sentada → em pé) com aviso de tontura | `fluxo_idoso.py` | 5/5 |
| 3 | Cor da urina com cartão de referência calibrado (1–8) | `urina.py` + `rampa_calibrada.json` | 4/4 |
| 4 | Fluxo guiado por voz em pt-BR (8 áudios reais) | `fluxo_idoso.py` + `audio/*.mp3` | nos testes do fluxo |
| 5 | Alertas + tendências + relatório para o cuidador (histórico JSONL) | `fluxo_idoso.py` | 5/5 |
| 6 | Protótipo olho/hidratação (mudança relativa à linha de base) | `../olho-hidratacao/` | 8/8 |
| 7 | Processador BCG noturno (FC/FR sob colchão) — o DSP do futuro pad | `bcg_noturno.py` | 3/3 |
| 8 | Botão de pânico por voz — liga em sequência + SMS com localização **e contexto de saúde** | `panico.py` | 5/5 |
| 9 | Mensagem de voz de pânico (áudio real pt-BR) | `audio/09_mensagem_panico.mp3` | — |
| 13 | Orquestrador — rotina + noite + SOS + sensores + inatividade num diário único | `orquestrador.py` | 7/7 |
| 14 | Especificação Android (2 modos, wake word, rotas A/B, permissões, LGPD, MVP 8–10 sem.) | `especificacao_android.md` | — |
| 15 | Camada de sensores (queda estilo FALLR1, gás, porta/deambulação, remédio, saída do leito pelo pad) | `sensores.py` | 6/6 |
| 16 | Inatividade da manhã (rotina não feita até 10h → alerta; 1/dia; só software) | `orquestrador.py` | no 7/7 |
| 17 | Mapa mundial de queda/sensores (Mindêllo, Vayyar, long-lie, 6 tecnologias) | `margarida_mapa_mundial.md` | — |
| 18 | Paridade Kotlin do núcleo (sensores + inatividade no core do app) | `app-android/core/` | 8/8 JVM |
| 19 | **App instalado e SOS testado em celular REAL (S23): 2 ligações + SMS com mapa, tela estável** | celular do usuário | ✔ 28/09/2026 |
| 10 | Validador para datasets reais (UBFC/LGI) — MAE, viés, Bland-Altman | `validar_dataset.py` | selftest OK |
| 11 | Plano de piloto (n=15–20, CEP, referências manguito+USG) | `piloto_cuidadores.md` | — |
| 12 | Decisão hardware vs. smartphone + matriz sinal→plataforma | `equipamento_vs_smartphone.md` | — |

## 🔨 FALTA (não é código — é dado, gente ou hardware)

| # | Item | Por que falta | Esforço |
|---|---|---|---|
| 1 | Validar FC com vídeos REAIS (UBFC/LGI) | datasets são gated (pedido de acesso); o validador já está pronto | semanas após liberação |
| 2 | Calibrar urina com ~100 fotos REAIS | carta oficial deu a rampa; falta foto real (luz de banheiro, vasos variados) | 2–4 semanas de coleta |
| 3 | Testar FC em pele idosa real (fina, escura, enrugada) | rPPG perde sinal nessas condições; é a pergunta nº 1 do piloto | piloto |
| 4 | Teste de usabilidade com idosos de verdade | voz escrita ≠ voz que idoso entende | 1–2 semanas, 5–8 idosos |
| 5 | Piloto com 15–20 cuidadores, 4 semanas, com CEP/Conep | exige comitê de ética + recrutamento | 4–6 meses no total |
| 6 | App Android (hoje é Python/CLI no PC) | o produto vive no celular do cuidador | 2–3 meses de dev |
| 7 | Pad BCG físico (ESP32+ADXL345) + calibração por colchão | o DSP está pronto e testado; falta a bancada | v2, só se o piloto pedir |
| 8 | Módulo sanitário de urina (cor+condutividade no vaso) | hardware + validação vs USG | v2.5, lacuna vs Dekoda US$ 599 |
| 9 | Integração com manguito BT de farmácia (PA calibrada) | periférico existe; falta o pareamento BLE no app | 2–4 semanas |
| 10 | SOS: wake word offline no Android (ex.: Porcupine pt-BR "socorro") | existe pronto; falta integrar | 1–2 semanas |
| 11 | SOS: conta Twilio/Totalk p/ ligações com a mensagem de voz na nuvem | Android não injeta áudio em ligação nativa; nuvem resolve | 1 semana + custo/min |
| 12 | SOS: teste de campo (GPS interno, falso positivo do wake word, latência) | só com idosos reais | piloto |
| 13 | Ponte Matter/Tuya no app (radar FALLR1-like, porta, gás, remédio de prateleira) | hardware + gateway em casa real; as REGRAS já estão prontas e testadas (`sensores.py`) | v1.5 |
| 14 | Pad físico sob o colchão (BCG + saída do leito) | DSP pronto (3/3 + detecção de silêncio 6/6); falta bancada | v2 |

## ❌ NÃO DÁ (física ou regulação — não insistir)

| # | Item | Motivo |
|---|---|---|
| 1 | Fotografar a retina pela câmera do celular | física: pupila 2–4 mm, foco mínimo ~10 cm, sem iluminação adequada |
| 2 | Pressão arterial pela câmera (rPPG-BP) | ±8–15 mmHg na literatura; alegação clínica insustentável sem manguito |
| 3 | Glicemia, sódio ou "nível de hidratação" absoluto pela câmera | não existe via óptica validada; só tendência relativa à linha de base |
| 4 | Promessa de diagnóstico/cura | sem validação clínica + registro ANVISA é risco legal, não produto |

## Ordem sugerida

**1 → 4 → 5 → 6** (validação → usabilidade → piloto → app) com **2 e 9** em paralelo.
7 e 8 só depois que o piloto provar adesão.
