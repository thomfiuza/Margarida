# Guia passo a passo — do zero ao app instalado no seu celular

Escrito para quem NUNCA programou. Regra de ouro: **uma etapa por vez**.
Cada etapa tem um "✔ como saber que funcionou" e um "🆘 se der errado".
Travou? Copie a mensagem de erro EXATA (ou print) e me mande. Eu corrijo daqui.

---

## O que você precisa antes de começar

- [ ] Um computador (Windows 10/11, macOS ou Linux) com **8 GB de RAM ou mais**
      e **15 GB livres** em disco;
- [ ] Internet razoável (vai baixar ~3 GB de ferramentas);
- [ ] Seu celular Android com **cabo USB** (o que carrega ele);
- [ ] 2 a 3 horas livres na primeira vez (depois, cada atualização leva minutos);
- [ ] Paciência: a primeira compilação demora e pode dar erro. É normal.
      Erro não é fracasso — é informação, e eu leio ela com você.

**O que o APK traz agora:** cadastro (nome + contatos), **rotina da manhã**
(voz + vídeo rPPG + foto de urina via Chaquopy), **SOS** (ligações + SMS),
painel do cuidador e alerta de **inatividade** se a rotina não for feita até 10h.
A primeira compilação com Chaquopy baixa numpy/OpenCV — pode levar **20–40 min**.

---

## ETAPA 1 — Instalar o Android Studio (a "fábrica" de apps)

1. Acesse: https://developer.android.com/studio
2. Clique em **Download Android Studio** e aceite os termos.
3. Execute o instalador: clique **Next** em tudo (padrões estão bons).
4. Abra o Android Studio uma vez. Na primeira tela de setup, deixe marcado
   "Standard" e **Next** até terminar (ele baixa o SDK do Android — demora).

✔ **Como saber que funcionou:** abre uma janela com "New Project" / "Open".
🆘 **Se der errado:** me diga em qual tela travou e o que apareceu escrito.

## ETAPA 2 — Levar o projeto para o seu computador

1. Baixe a pasta `app-android/` deste workspace para o seu PC (botão de
   download do arquivo/zip na interface onde você me vê).
2. Descompacte em um lugar simples, ex.: `C:\monitor-idoso\` ou `~/monitor-idoso/`.
   **Evite** caminhos com acento ou espaço.

✔ **Como saber que funcionou:** dentro da pasta existem `settings.gradle.kts`,
`app/` e `core/`.

## ETAPA 3 — Abrir o projeto e compilar (a parte que mais dá erro — e tudo bem)

1. No Android Studio: **Open** → selecione a pasta `app-android` → OK.
2. Confie nos pop-ups ("Trust project", "Use Gradle wrapper" → OK).
3. **Espere.** O canto inferior mostra "Gradle sync" / "Indexing". A primeira
   vez leva de 10 a 30 minutos. NÃO feche.
4. Quando parar de mexer sozinho: menu **Build → Build App Bundle(s)/APK(s) →
   Build APK(s)**. Espere de novo.

✔ **Como saber que funcionou:** notificação "APK(s) generated" com um link
`locate` — o arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.
🆘 **Se der erro (vermelho embaixo):** clique na aba **Build**, copie o texto
inteiro do erro e me mande. Erros comuns de primeira vez (versão do Java,
licença do SDK, memória) eu resolvo com você em 1 ou 2 mensagens.

## ETAPA 4 — Instalar no seu celular

1. No celular: **Configurações → Sobre o telefone → toque 7 vezes em "Número
   da versão"** (vira desenvolvedor). Depois em **Opções do desenvolvedor →
   Ativar "Depuração USB"**.
2. Conecte o cabo USB no PC. No celular, autorize o computador (pop-up).
3. No Android Studio, seu celular aparece na barra de cima. Clique no botão
   verde **Run ▶**.

✔ **Como saber que funcionou:** o app "Monitor do Idoso" abre no celular com
os dois botões.
🆘 **Celular não aparece:** troque o cabo (alguns só carregam), e me diga a
marca/modelo do celular — cada marca tem um truque.

## ETAPA 5 — Testar de verdade (com rede de segurança)

1. **Primeiro cadastre SEU número como contato de emergência** (no código está
   "Dona Maria/SAMU" de exemplo — me peça que eu troco pelos seus dados antes
   de você compilar).
2. Toque em **SOCORRO** → confira: ligou para você? Chegou SMS com o link do
   mapa? O link abre no lugar certo?
3. Toque em **Rotina diária** → veja o painel do diário.

✔ **Critério desta etapa:** você recebeu a ligação E o SMS no número cadastrado.

## ETAPA 6 — As contas (só quando o básico estiver funcionando)

1. **Picovoice** (wake word "socorro"): conta grátis em console.picovoice.ai →
   criar AccessKey (Android) → treinar a palavra "socorro" em português →
   me mandar os dois arquivos/chave que eu integro aqui.
2. **Twilio** (rota A — ligação que TOCA a mensagem): conta teste grátis →
   me mandar as 3 credenciais (Account SID, Auth Token, número) → eu monto o
   backend mínimo e troco a rota.
3. **Google Play** (só para publicar para outras pessoas; US$ 25 uma vez):
   fica para depois do piloto.

---

## O que faremos JUNTOS em cada erro

Você me manda: (1) a etapa em que está, (2) o texto/print do erro,
(3) o que você fez antes. Eu devolvo a correção exata. Nenhuma pergunta é
boba demais — quem pula etapa paga em dobro depois.

## O que você NÃO fará sozinho (e nem precisa)

- **Piloto com idosos e comitê de ética (CEP):** precisa de pessoas reais,
  cuidadores e uma instituição — o plano já está escrito
  (`../monitor-idoso/piloto_cuidadores.md`); quando chegarmos lá, eu preparo
  cada documento e você leva a uma universidade/UBS parceira.
- **Validação clínica e ANVISA:** só se o produto crescer para alegação médica.
- **Manter o ritmo:** o risco real de projetos assim não é erro técnico — é
  parar na etapa 3. Por isso: uma etapa por vez, sempre com ✔ e 🆘.
