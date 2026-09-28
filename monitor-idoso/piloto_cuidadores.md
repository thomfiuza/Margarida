# Plano de piloto pequeno com cuidadores (mundo real)

Este sandbox não executa pesquisa com seres humanos; isto é o plano pronto para
levar a um Comitê de Ética (CEP/Conep, Resolução CNS 510/2016 no Brasil).

## Objetivo
Testar ADERÊNCIA e UTILIDADE do fluxo guiado por voz (ortostática por rPPG +
hidratação por urina) em idosos, e a precisão dos alertas contra medidas de
referência — não eficácia clínica.

## Desenho
- **n = 15–20 idosos** (65+) com um cuidador/cada; incluir ≥5 com histórico de
  tontura/queda e ≥5 em polifarmácia (anti-hipertensivos = risco de HO).
- **Duração: 4 semanas**, medição diária guiada (manhã) + foto de urina na
  2ª micção.
- **App no celular do cuidador**; idoso interage só por voz.

## Referência (ground truth)
- Semana 0 e 4: PA ortostática com manguito validado (repouso 5 min → 1 e 3 min
  em pé; HO = queda ≥20/10 mmHg) — feita por enfermeiro, cega aos alertas.
- Urina: densidade urinária (fitas de USG) em ~10 amostras por participante,
  pareadas com as fotos.
- Usabilidade: SUS (System Usability Scale) idoso + cuidador; diário de fricção.

## Desfechos
1. Adesão: % de dias com fluxo completo (>70% = viável);
2. Taxa de conclusão sem ajuda por etapa (qual passo falha?);
3. Acurácia dos alertas de hidratação vs USG (sensibilidade/≥ nível 4);
4. Concordância FC rPPG vs oxímetro de dedo durante as visitas (MAE);
5. SUS ≥ 70 e 3 ajustes de UX priorizados.

## Critérios de prosseguir
- Adesão ≥ 70%, SUS ≥ 70, MAE FC ≤ 5 bpm nas visitas, e zero eventos de
  "idoso tomou decisão clínica sozinho por causa do app".

## Riscos e mitigações
- Ansiedade por falso alerta → alertas vão AO CUIDADOR, nunca como diagnóstico;
  texto padrão "tendência, leve ao médico".
- Idoso com déficit cognitivo → consentimento por representante + assentimento.
- Dados pessoais (vídeo de rosto) → processamento local no aparelho, nada sobe
  sem consentimento; vídeo descartado após extrair a FC.

## Orçamento enxuto (estimativa)
- 20 oxímetros de dedo + 20 aferidores validados + cartões brancos impressos +
  copos coletores: ~R$ 4–6 mil; bolsista de enfermagem 1 mês; sem custo de
  software (este protótipo).

## Cronograma realista
- Mês 1: submissão CEP + ajuste de UX com 3 idosos (teste de usabilidade);
- Mês 2–3: recrutamento e coleta;
- Mês 4: análise + decisão de prosseguir (ou pivotar).
