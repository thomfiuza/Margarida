# 📡 Especificação do protocolo MQTT (modo institucional)

Nível: rascunho de engenharia para o piloto de 100 leitos. Válida após anuência.

## Topologia

- Broker MQTT local por instituição (Mosquitto no tablet do posto ou num mini PC).
- Rede mesh dedicada às enfermarias; o broker NÃO fica exposto à internet.
- Espelho de nuvem (item 3 do orçamento) recebe apenas agregados sem identificação, por túnel iniciado pelo tablet.

## Tópicos

margarida/{inst}/leito/{id}/evento      QoS 1, sem retain
margarida/{inst}/leito/{id}/status      QoS 1, retain (heartbeat 30 s)
margarida/{inst}/painel/alerta          QoS 1 (o broker republica eventos críticos)
margarida/{inst}/leito/{id}/config      QoS 1 (sensibilidade por leito, vinda do painel)
margarida/{inst}/leito/{id}/calibra     QoS 1 (calibração na bancada, firmware 0.2)
margarida/{inst}/leito/{id}/debug       QoS 0 (telemetria de bancada, pode ser ignorado pelo painel)

{inst} = sv (São Vicente) ou cc (Casa do Caminho). {id} = SV-001 a SV-050, CC-001 a CC-050.

## Payloads (JSON)

Evento:
{"leito":"SV-017","ts":1767225600,"tipo":"LEVANTOU","conf":0.94}
{"leito":"SV-017","ts":1767225900,"tipo":"RESPIRACAO","rpm":6,"rpm_medio":7,"conf":0.88}
{"leito":"SV-017","ts":1767226200,"tipo":"DEITOU","conf":0.9}

No RESPIRACAO, "rpm" é a leitura bruta da última janela de 10 s, que é o número
que a enfermagem quer ver. "rpm_medio" é a média suavizada de 20 s que decidiu o
alerta, e existe para auditoria.

Debug (só bancada):
{"leito":"SV-017","ts":1767226300,"dt_ms":250,"peso":69.7,"rpm":12,"rpm_suave":11.8,"estado":"DEITADO"}

Calibra (só bancada):
{"peso_conhecido_kg":70}   com esse peso sobre a cama
{"tarar":true}             com a cama vazia

Status (heartbeat):
{"leito":"SV-017","ts":1767226500,"online":true,"bateria":null,"fw":"0.1.0","peso_kg":68.4}

Config (painel para o leito):
{"leito":"SV-017","limiar_presenca_kg":20,"rpm_min":8,"modo":"NOITE"}

## Regras de decisão (herdadas do core testado)

- LEVANTOU = peso abaixo do limiar por mais de 15 s contínuos, em janela noturna configurável.
- RESPIRACAO = média suavizada de rpm (constante de 20 s) abaixo do mínimo por
  20 s acumulados, com sinal presente (idoso deitado). Não é "20 s contínuos":
  a leitura de uma janela isolada pula entre 6 e 9 numa respiração estável de
  7, e exigir continuidade faria o alerta nunca disparar.
- Ausência de heartbeat por 90 s = painel marca o leito como OFFLINE (falha técnica, não alerta clínico).
- Falso positivo conhecido (peso de cuidador em ronda): evento PRESENTE_EXTRA com conf baixa, não aciona som, só registra.

## Segurança e LGPD

- Broker local sem exposição; autenticação por usuário/senha internos da rede dedicada.
- Payloads não contêm nome do residente: o painel mapeia leito para quarto fisicamente (etiqueta), e o mapa nominado fica somente no tablet, criptografado.
- Nuvem recebe apenas: contagem de leitos online, alertas por dia por instituição, uptime. Zero payload clínico identificado.

## Teste de qualidade

Antes da placa: `python3 hardware/bancada/rodar_testes.py` (simulação no PC,
confere lógica e tempos). Antes do leito: os 8 testes de `TESTE_BANCADA.md`,
com a unidade na mão, mais 24 h publicando heartbeat sem queda. Aprovou,
etiqueta e caixa.
