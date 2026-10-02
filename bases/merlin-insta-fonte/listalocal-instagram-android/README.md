# Lista Local Instagram 0.1.1 (Android)

Aplicativo Android, separado das extensões Merlin, adaptado do Lista Local 0.15.
Pelo mesmo serviço de Acessibilidade, opera o app do Instagram
(`com.instagram.android`) em dois modos:

- **Mensagem direta, uma a uma:** lê as pessoas ao vivo no próprio Instagram
  (Perfil › Seguidores, ou as conversas 1:1 do Direct), abre a conversa de cada
  uma pelo link `https://ig.me/m/<@>`, confere o @ na conversa, escreve o texto
  do dono de uma vez e toca em Enviar uma vez.
- **Amigos Próximos:** marca os @ digitados em "Só para estes @" na lista Amigos
  Próximos para o dono publicar um story só para ela (e retira quem pediu para
  parar). Não envia mensagem.

Pacote próprio (`com.listalocal.instagram.claude`, nome "Lista Local Instagram (Claude)"):
instala ao lado do Lista Local e do app do Codex (`com.listalocal.instagram`).
Sem internet, sem contatos, sem permissão de armazenamento. Identidade visual
Merlin. Desenvolvido por João Girão — jvictorgirao@poli.ufrj.br.

## Regras que o código cumpre

- **Destinatários ao vivo, sem extração nem arquivo:** decisão do dono. O app
  não importa CSV nem guarda a lista de seguidores. Ele lê uma tela da lista
  por vez no Instagram (~8 linhas), manda para cada @ dessa tela, volta à lista
  e segue do último processado. Se o Instagram recriou a lista no topo, rola
  passando pelos já feitos. Se a lista mudou, segue pelo próximo não feito. O
  fim é a primeira linha de "Sugestões para você". Guarda no aparelho só quem
  recebeu, quem falhou e o ponto de parada (`data/Outcomes.kt`,
  `data/Campanhas.kt`), e dá para **retomar do ponto salvo**.
  - **Seus seguidores:** Perfil › Seguidores, só com a aba "seguidores"
    marcada. Rola só a lista (`android:id/list`), nunca o ViewPager, que
    trocaria para "seguindo". Nunca toca em "Mensagem", "Ignorar" ou "Seguir".
  - **Conversas do Direct:** a linha da caixa de entrada não mostra o @. O app
    abre a conversa pela linha (nunca pelo avatar, que abre story), lê o @ no
    cabeçalho e fecha. Depois envia pelo link do @ e só se o nome no
    cabeçalho for o mesmo, então um grupo nunca recebe. Grupo, conversa sem @
    legível, conversa sem mensagens (sugestão) e conta indisponível ficam de
    fora. "Pedidos" para a fila. Conversas comerciais ficam de fora só se o
    dono marcar. Abrir a conversa marca como lidas as mensagens dela.
  - A conta aberta no Instagram tem de ser a conferida. Conversa que diz
    "Vocês não se seguem no Instagram" não recebe.
- **Nunca grupo:** só a conversa 1:1 do link `ig.me/m/<@>`. "Nova mensagem"
  nunca é usada para enviar (lá, tocar numa segunda pessoa vira prévia de grupo).
- **Prova da conversa:** o @ exato (texto inteiro, sem "contém") no título ou
  no subtítulo do cabeçalho ("Conversa comercial" põe o @ no título) ou no
  cartão do topo. Se em 4 s o subtítulo só mostrar um estado ("Online agora",
  "Visto...") ou não for legível, vale o nome do seguidor no título, como no
  envio real por adb. Sem prova, falha e nada é escrito. Conversa que não abre
  em 5 s: "conta não encontrada".
- **Duas fases:** o "vou enviar" é gravado no aparelho (commit síncrono) antes
  do toque em Enviar. Sem evidência depois do toque (o botão Enviar some, ou
  seja, o campo esvazia, e a bolha com o texto aparece), a pessoa fica **sem confirmação**, que conta
  como falha e nunca é reenviada. Se o app morrer depois do commit, idem.
  Enquanto nenhum envio da operação foi confirmado na tela, o primeiro sem
  confirmação para a fila (o dono confere no Instagram antes de seguir); depois,
  três seguidos sem confirmação param.
- **Opt-out:** quem pediu para parar (lido na conversa: "pare", "sair", "não
  quero mais receber"...) não recebe e sai dos Amigos Próximos. O pedido lido
  fica gravado. Sem a lista de mensagens na leitura, o pedido não foi
  conferido: falha e nada é escrito.
- **Listas do dono, digitadas ou coladas (sem arquivo):** "Não enviar para" e
  "Só para estes @". Texto que não é @ bloqueia o início. Quem já recebeu por
  esta conta não recebe de novo.
- **Sem disfarce:** intervalo fixo escolhido pelo dono (Normal 30 s, Rápido 10 s,
  Muito rápido 1 s; a tela mostra só o nome) entre um toque em Enviar e o
  próximo. A próxima conversa abre logo que a anterior confirma e o resto do
  intervalo corre com ela aberta e provada, antes de escrever. Texto inteiro
  por `ACTION_SET_TEXT`, sem atraso sorteado, sem digitação simulada, sem
  troca de IP. Sem teto de volume.
- **Leitura enxuta:** espera por evento de Acessibilidade (com prazo), busca
  por id sem copiar a árvore, e a árvore inteira só quando a tela certa
  aparece. Medida em `DesempenhoTest` e em `OTIMIZACAO.md`.
- **Restrição:** aviso de restrição do Instagram pausa a fila; só o dono retoma.
- **Fim da lista só de verdade:** rolagem recusada (o Instagram saiu da frente)
  é parada segura com o ponto salvo, nunca "Fim da lista". Conversa sem @
  (grupo) só conta para a parada de segurança enquanto nenhum @ foi lido na
  operação, então grupos juntos no topo da caixa não travam a retomada.
- **Acessibilidade só no Instagram:** `packageNames="com.instagram.android"`;
  toda leitura e todo toque reconferem a janela, o prazo, a pausa e o
  cancelamento. A leitura procura a conversa em todas as janelas interativas
  (a "janela ativa" do Instagram fica presa na tela principal), com
  `rootInActiveWindow` e a fonte dos eventos como reserva, e não lê nada se
  outro app estiver na frente. Aviso e escolha explícita antes de abrir os ajustes.
- **Consentimento:** o app só começa depois de o dono marcar que entendeu o
  risco (os Termos do Instagram proíbem automação sem permissão) e só inicia
  com base legal (LGPD) escolhida e a confirmação de que são seguidores.
- **Parar a qualquer hora:** Pausar/Continuar e Parar na tela do app e num
  painel por cima do Instagram (que também mantém a tela acesa).

## Jornada

1. Boas-vindas e riscos. 2. Permissões e **Conferir o Instagram** (por modo:
abre o Instagram, vai até a tela do modo e volta, sem escrever nem marcar
ninguém; um modo só liga depois de passar, e vale só para aquela versão do
Instagram). 3. Destinatários (origem ao vivo, "Não enviar para", "Só para
estes @", retomar a operação salva). 4. Plano (modo, texto, velocidade, base
legal e a confirmação clara do que vai acontecer). 5. Execução ("12 enviados · 3
pulados · 1 falha", Pausar/Continuar, Parar). 6. Resultado (enviadas, puladas,
falhas e o motivo por pessoa; retomar; planilha no formato do Merlin).

## Seletores: o que está medido e o que falta

Fonte: `merlin-adaptar-listalocal/INSTAGRAM-APP-REAL.md` e
`TESTE-ENVIO-REAL.md` (Galaxy A14, Android 15, pt-BR, Instagram 448.0.0.52.84).
O perfil fica em `core/selectors/SelectorProfile.kt`; as chaves em `calibrar`
ainda não foram lidas **pela Acessibilidade** (o `uiautomator` não enxerga a
ModalActivity: conversa, Nova mensagem, Amigos Próximos) ou nunca apareceram.

PRECISA VERIFICAR NO INSTAGRAM REAL (rodada 2, com este APK, só com conta de
teste ou do próprio dono):

1. Se o serviço, lendo `windows` (`flagRetrieveInteractiveWindows`), enxerga os
   nós da ModalActivity. A conferência do modo mensagem abre "Nova mensagem" só
   para provar isso; se falhar, o modo não liga.
2. Se o texto do `header_subtitle` (IgView) sai na Acessibilidade. Ele alterna o
   @ com "Online agora"; o cartão do topo é a reserva.
3. `ACTION_SET_TEXT` no campo, o rótulo do Enviar e o texto das bolhas (Compose)
   depois do envio. Sem bolha legível, todo envio fica "sem confirmação".
4. Textos de "não enviada", restrição e "não recebe mensagens"; rótulos em inglês.
5. Se marcar em Amigos Próximos grava sem "Concluir" (o app toca Concluir no fim).
6. A leitura ao vivo: o `ACTION_SCROLL_FORWARD` na lista de seguidores e na
   caixa de entrada (medido só com arraste), as linhas em Compose da caixa de
   entrada vistas pela Acessibilidade, e se o @ do cabeçalho aparece para quem
   está "Online agora" (sem ele a conversa fica de fora; 3 seguidas param).

Decisão a confirmar com o dono: a calibração sugere recusar conversa nova (a que
mostra "Diga 'olá' enviando uma figurinha"). O app NÃO recusa, porque isso
barraria todo seguidor sem conversa anterior; ele nunca toca na bandeja de
figurinhas (só no Enviar pelo id exato, e só se habilitado). Conversa com
mensagens temporárias é recusada.

## Compilar e testar

JDK 17 ou superior (usado: Temurin 21.0.12.1), Android SDK 35, Gradle 8.9
(wrapper). `local.properties` com `sdk.dir=`.

```powershell
$env:JAVA_HOME = "C:\Users\User\Documents\Codex\2026-08-28\key-de\.worktrees\lista-local-gate1\tools\appium-spike\.tools\jdk\jdk-21.0.12.1+1"
.\gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat --offline :app:compileDebugAndroidTestKotlin
```

Os testes unitários rodam os fluxos inteiros sobre um Instagram falso
(`app/src/test/.../service/FakeInstagram.kt`, árvores em `IgFixtures.kt`, com
nomes fictícios). O emulador não tem Play Store, então o fluxo contra o
Instagram real só é testado no celular.

Para uma versão assinada, configure `LISTALOCAL_KEYSTORE_PATH`,
`LISTALOCAL_STORE_PASSWORD`, `LISTALOCAL_KEY_ALIAS` e `LISTALOCAL_KEY_PASSWORD`
com uma chave nova, fora do projeto. Build com prazo: `-Pexpira04102026=true`.
O prazo resiste a voltar o relógio no uso normal, não a limpar os dados do app
(ou reinstalar) e então voltar o relógio: sem rede, não há onde ancorar a hora.

## Fora da 0.1.0

"Enviar separadamente" ao compartilhar um post (0.2.0); lista de transmissão, turbo por prefixo, mínimo de 2,
telefones/DDD, cota do WhatsApp, uso único e a sincronização da 0.16.
