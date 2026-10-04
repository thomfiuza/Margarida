# 🗺️ ROADMAP ABERTO do Margarida

Base criada em 01/10/2026 a partir do feedback da comunidade e das parcerias.
Marco Antônio se ofereceu para transformar isto em issues/projects do GitHub.
Quiser pegar uma tarefa? Abra um PR a partir da branch `dev` (staging). A `main`
é protegida e só recebe mudança por PR aprovado.

## 🌼 Essência intocável (filtro de TODO item abaixo)

- SEM wearable obrigatório, SEM central 24h, SEM mensalidade.
- 100% no aparelho (sem nuvem): a privacidade do idoso não se negocia.
- Código aberto (MIT) e voz em primeiro lugar, para quem lê ou enxerga com dificuldade.
- Posicionamento honesto: bem-estar e alerta a cuidadores, não dispositivo médico.

## ✅ Já entregue (testado)

- [x] SOS por voz e botão, ligações em sequência + SMS com mapa (testado no S23)
- [x] 6 recursos da comunidade: remédios, check-in ativo, camadas de gatilho físico, SAMU-first, sensor geladeira, modo Alzheimer
- [x] Interoperabilidade v1: fonte PARCEIRO (apps parceiros confiáveis embutem o SOS) + exportação local do diário em JSON (`core/Integracao.kt`)
- [x] Resumo semanal do cuidador, gerado 100% local (`core/ResumoDiario.kt`)
- [x] Núcleo 21/21 testes JVM
- [x] Fluxo de contribuição: `dev` (staging) + `main` protegida por PR

## 🔨 Próximos passos (virar issues)

- [ ] Wake word "socorro" com motor open source (Vosk pt-BR é o candidato; bateria a validar no S23)
- [ ] Intent/deep link Android para o SOS parceiro (`margarida://sos?fonte=parceiro`), piloto da integração com app de histórico de saúde
- [ ] Avaliar Kotlin Multiplatform para o núcleo rodar também no iOS (interesse real: idosos com iPhone)
- [ ] IA on-device: intenções de voz mais espertas e resumos melhores, sempre sem nuvem
- [ ] Rotina por voz dentro do APK (Estação 7)
- [ ] Sensores de casa Matter/Tuya (radar de queda, porta, gás)
- [ ] Piloto casal de idosos em que um cuida do outro (cenário clássico do SOS + diário)
- [ ] Pad noturno BCG (hardware v2)
- [ ] Piloto com cuidadores (CEP)

## 🤝 Perfis que chegaram junto (papéis, sem dados pessoais)

- Full-stack sênior com CI/CD/AWS (fluxo staging→main, revisão)
- Full-stack/AI sênior (IA on-device, iOS/KMP, organização do roadmap)
- Head of Product + médica (integração histórico×diário, piloto)
- Geriatra, UX, cuidadores e devs vindos dos comentários do post

Se você é dev, da saúde, cuidador ou só se comoveu com a causa: tem lugar na mesa. 🌼
