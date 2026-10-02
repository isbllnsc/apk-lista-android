# Lista Local Instagram (Claude) 0.2.2 — caça às travas

27/09/2026. App `com.listalocal.instagram.claude`, versão 0.2.2 (código 10). Autoria: João Victor Girão (marca intacta).

## Por que esta caça

No seu celular o app parou duas vezes numa situação que estava certa:

1. "A operação é de @jvsgirao, mas o Instagram conferido é @frutacarecafc" (duas contas; corrigido na 0.1.2).
2. "A lista aberta não é a aba Seguidores" numa lista de seguidores certa (a barra de abas aparece depois das linhas; corrigido na 0.2.1).

Nas duas vezes os testes passavam, porque eram feitos com telas inventadas. Nesta rodada os testes passaram a usar as telas de verdade do seu Instagram (Galaxy A14, Instagram 448 em português), com os nomes das pessoas trocados. A caça procurou dois tipos de erro:

- o app para sem motivo;
- o app deixa passar algo perigoso (mandar para a pessoa errada, criar grupo, tocar em "Limpar tudo" etc.).

## Resumo

| | Achadas | Confirmadas com tela real ou teste que falhava antes | Corrigidas |
|---|---:|---:|---:|
| Travava você à toa | 36 | 36 | 36 |
| Podia enviar ou tocar errado | 30 | 30 | 30 |
| **Total** | **66** | **66** | **66** |

- "Confirmada" quer dizer que cada item tem um teste que falhava antes da correção e passa depois. Na maioria desses testes o app percorre as telas reais anonimizadas; nos outros ele usa o Instagram falso dos testes.
- 1 achado foi contestado por um revisor ("lista de Amigos Próximos vazia nunca libera"). Mesmo assim a correção ficou, porque não traz risco: o "Limpar tudo" continua protegido.
- No fechamento desta versão, a folha de compartilhar de um post entrou no corpus e foi testada. É o lugar mais perigoso do Instagram: tem "Enviar" e "Enviar para nova conversa em grupo" sem confirmação. Nenhuma trava nova apareceu ali. Nenhuma parte do app confunde essa folha com uma conversa, com Amigos Próximos ou com uma lista.
- Resultado: 449 testes, nenhuma falha. O `assembleDebug` passou.

## Travava você à toa (36): o que você via e agora não vê mais

**Seus seguidores**

1. O perfil, o Direct ou Configurações eram tomados pela lista de seguidores: aparecia "A lista não abriu" ou "Fim da lista" com 0 pessoas.
2. Se você tinha pesquisado alguém na lista durante a pausa, o app dizia "Concluído — Fim da lista" depois de 1 pessoa e perdia o ponto de parada.
3. Com a internet lenta, "Fim da lista" aparecia com metade das pessoas lidas.
4. Com mais de 10 mil seguidores ("12,3 mil"), o app não achava o número e não abria a lista.
5. Se você tocava em Pausar logo no começo, aparecia "A lista não abriu no Instagram." em vez de pausar.
6. Num Instagram com ids diferentes, "Seus seguidores" parava no perfil e não chegava à lista.

**Conversas do Direct**

7. Toda conversa aberta pela caixa "não abria" (a janela da conversa no A14 vem sem raiz), e a fila parava.
8. Conversas de loja ou comerciais (o título é o @ e a caixa mostra o nome da loja) viravam "ilegível", e 3 lojas seguidas paravam a fila.
9. O app não lia o @ da conversa, porque o cabeçalho some na leitura do serviço.
10. Pessoa "Online agora" com o @ só no cartão do topo era pulada.
11. Grupos com nome legível ("Ana, Bia e mais 2") contavam para a parada por conversa ilegível.
12. Logo depois de um envio, a caixa ainda se mexia e a pessoa da linha tocada virava "não é a da linha tocada" para sempre.
13. Um toque recusado numa linha parava a operação na hora (agora o app tenta mais 1 vez).
14. Uma nota com música chamada "Pedidos" (ou "Requests") no topo fazia o app achar que estava em Pedidos, e ele parava.
15. "Pesquisar" era tomado pela 1ª conversa e abria a busca.
16. Com a caixa deixada rolada, as conversas de cima ficavam de fora sem aviso.
17. As paradas de Pedidos e de filtro mandavam tocar em "Continuar", mas o botão certo é "Retomar de onde parou".
18. Mensagem de amigo com palavras de aviso ("não é possível enviar"...) virava "restrição do Instagram".
19. Com o teclado aberto, o campo de mensagem não era achado.
20. Conversa nova, sem histórico, não era reconhecida.
21. Leitura vazia no instante errado, Parar enquanto a conversa abria, conversa não lida, rascunho no campo ou toque recusado viravam trava ou FALHA errada.
22. Recusa por regra (mensagens temporárias, não segue) parava a operação inteira em vez de pular a pessoa.
23. Com "começar a partir de @", as conversas ilegíveis antes do @ de partida contavam para a parada.
24. Na retomada, a conversa que ficou "não lida" não era refeita.
25. Num Instagram com ids diferentes, "Conversas do Direct" parava em "A lista não abriu" e cada conversa em "as mensagens não foram lidas".
26. Conversa aberta que o app não reconhecia era gravada como "Conta não encontrada".

**Conferir o Instagram, conta e pausa**

27. Num Instagram com ids diferentes o modo DM ficava bloqueado para sempre, e começando pelo perfil o app lia "15posts" como a conta.
28. O "..." de um post de terceiro era tomado pelo menu Opções: aparecia "Configurações e atividade não abriu", e o toque ia no menu do post.
29. Depois de trocar de conta, Amigos Próximos travava com "a conferência dessa conta não passou" sem ter tentado.
30. Se você tocava em Pausar logo antes da escrita, a pessoa virava FALHA "não consegui escrever no campo" e era pulada.
31. Com a tela apagada ou bloqueada, o app parava com "Não consegui ver qual conta está aberta". Agora ele pausa sozinho e diz o motivo.
32. Um "Enviar" recusado porque outra coisa estava na frente (ligação, alarme) ficava INCERTO: a pessoa era bloqueada para sempre e a operação parava.
33. Na retomada, FALHA e "não encontrado" (casos em que nada saiu) viravam exclusão calada, e essas pessoas nunca recebiam.

**Amigos Próximos**

34. O app dizia "não achou" cedo demais (1,2 s), antes de a busca do Instagram responder.
35. Se você tocava em Pausar enquanto Amigos Próximos abria, aparecia "Configurações não abriu", ou a operação terminava em "fluxo fora de ordem" na retomada.
36. Na tela reaberta, uma busca que não tocou em ninguém trocava "ADICIONADO" por FALHA no relatório. Além disso, com a lista vazia (primeira vez), o modo nunca liberava.

## Podia enviar ou tocar errado (30): o que poderia ter acontecido

**Pessoa ou lista errada**

1. Na lista de seguidores, o app tocava em "Enviar mensagem para <seguidor>" (abria a conversa de alguém) ou na foto de um seguidor (abria o story), achando que eram as abas Direct e Perfil.
2. Se na pausa você abrisse a lista de seguidores da sua outra conta, o app conferia pela conta errada. Um lote lido na outra conta podia receber a mensagem.
3. Na retomada com "começar a partir de @", quem vinha antes do @ de partida recebia a mensagem.
4. O rótulo da aba "Sinalizadas" virava um seguidor chamado "@sinalizadas".
5. Uma aba Direct aprendida errada (o botão "Enviar mensagem para <seguidor>") ficava gravada e era tocada depois: abria a conversa de um terceiro.
6. Uma nota "Para quem vai...?" no topo da caixa liberava o modo DM sem o app ter visto a tela Nova mensagem.
7. A conferência gravava o que tinha aprendido mesmo quando falhava, e podia gravar o rótulo de uma pessoa como se fosse um botão.
8. O @ de um perfil compartilhado dentro da conversa podia "provar" a pessoa errada.

**Contas**

9. Se você trocava de conta no meio, o app abria a 1ª conversa da outra conta e marcava como lida a mensagem de um amigo pessoal.
10. Se você iniciava com o Instagram na outra conta, o app convertia a operação, apagava a salva e mandava a mensagem de uma conta como a outra, do começo.
11. Em "Trocar de conta", o seletor fechava sozinho achando que a conta já estava escolhida.

**Direct: envio e caixa**

12. Um contador era zerado a cada @ lido. Por isso, depois do 1º envio, o app tocava em Enviar sem confirmação na caixa inteira, até sob bloqueio leve.
13. Pedidos só era conferido ao abrir. Depois de uma pausa, o app podia seguir enviando em Pedidos.
14. Com um filtro ligado ("Não lidas" etc.), o app lia só a lista filtrada como se fosse a caixa toda.
15. Um "pare" que a pessoa mandou antes, fora da tela, não barrava o envio.
16. Na retomada, conversas já feitas eram reabertas e marcadas como lidas.

**Registro, retomada e horários**

17. Um ENVIADO virava FALHA numa segunda operação, e a terceira mandava de novo (mensagem repetida).
18. Parar no meio de uma sequência não entrava no relatório. Com o serviço religado, a 1ª mensagem saía de novo.
19. Com Parar e Retomar logo depois de um envio, o próximo saía em 2 a 5 s, sem o intervalo que você escolheu.
20. "Parar às 22:00": ao retomar às 22:05, o app entendia "amanhã às 22:00" e enviava a noite toda.
21. Se o Instagram se atualizava de noite (Play Store), o app seguia escrevendo e tocando em Enviar sem nova conferência.
22. O botão do painel mostrava "Pausar" quando a fila já estava pausada. Seu toque para garantir a parada RETOMAVA o envio logo depois de um aviso de restrição.
23. Durante uma ligação, o app puxava o Instagram por cima da chamada e apertava Voltar na tela de bloqueio.

**Amigos Próximos**

24. Logo que a tela abre, os 9 primeiros membros vêm "sem marca" na leitura. O app lia "desmarcado", tocava e TIRAVA um amigo próximo da lista.
25. Com a lista ainda deslizando, o toque no ponto podia cair na linha de outra pessoa.
26. Sem uma nova leitura logo antes do toque, o app tocava no ponto da leitura anterior.
27. A busca sozinha "provava" a tela. Mas a Nova mensagem e a folha de compartilhar também têm essa busca, e ali marcar alguém é escolher um destinatário.
28. Quem pediu para parar e estava no plano era marcado e desmarcado (dois toques) em vez de só sair.
29. Numa retomada em outra conta, pessoas eram puladas por causa do resultado da outra conta.
30. A pergunta do Concluir não dizia de qual conta era a lista. Agora pergunta "... Amigos Próximos de @conta?".

## O que ficou sem corrigir, e por quê

- **Provar na conferência que tocar numa linha da caixa abre a conversa.** Para provar, o app teria de abrir a conversa de um terceiro. Em vez disso, a operação para na 1ª conversa que não abre, diz o motivo e grava as janelas no log.
- **Rodar a leitura das telas modais no seu celular antes da próxima operação real.** Esta etapa foi feita sem o celular, por ordem sua. O comando é `rodar-teste-celular.cmd` (teste ModalReadProbeTest).
- **Mostrar se a caixa está em "Principal" ou "Geral", e não encerrar a campanha no fim de "Principal".** Isso é decisão de produto.
- **Pedidos com pedidos de verdade.** Não há tela real disso: a sua caixa de Pedidos estava vazia. O teste usa o Instagram falso.
- **Lista de Amigos Próximos vazia de verdade.** Nunca foi capturada. A liberação sem "Limpar tudo" vale só com a lista sem nenhuma pessoa marcada, e a proteção do "Limpar tudo" continua igual.
- **Tocar pelo ponto numa linha da caixa como 2ª tentativa.** O app segue tocando só pelo item da tela, que é mais seguro.
- **Pessoa "Online agora" sem @ em nenhum lugar.** Continua PULADA, com o motivo no relatório.
- **Um "pare" com mais de 10 telas de histórico para trás.** Só é pego pela lista "Não enviar para".
- **Serviço morto pelo Android no meio.** A retomada reabre as conversas desde o topo, mas não envia de novo.
- **Telas modais sem texto no corpus.** O dump de Views não traz texto, então nelas só se testa estrutura e estado. Os textos de interface vêm da documentação e estão marcados como supostos.
- **Histórico do git.** Os arquivos atuais e o zip de fonte estão limpos. Mas commits antigos (desde `c6249c2`, incluindo a mensagem do commit da tag v0.2.1.1) ainda têm @ e nomes de 6 terceiros. Limpar exige reescrever o histórico, e isso muda os hashes das tags já entregues (v0.1.1 até v0.2.2). Por isso ficou para você decidir. O script está pronto e fora do git: `reescrever-historico.sh`, na pasta de rascunho da sessão.

## Corpus de árvores reais criado

- **51 telas reais** do seu celular, anonimizadas, em `app/src/test/resources/real/`:
  - 33 lidas pelo uiautomator: perfil (4), caixa de entrada (6), Pedidos, lista de Seguidores (topo, 13 passos de rolagem, fim, só Sugestões, busca com 1 e com 3 linhas), abas Seguindo e Sinalizadas, Configurações (4);
  - 18 lidas pelo dump de Views: Amigos Próximos (topo, fim dos membros, busca de um membro, rolada no meio), Nova mensagem (vazia, 1 pessoa, prévia de grupo), conversa (normal, com texto, nova, temporária), post aberto pela grade e folha de compartilhar (fechada, com busca, 1 marcada, 2 marcadas).
- Na anonimização, pessoas viram "pessoaNN", "Pessoa NN", "amigoNN" ou "Amigo NN"; mensagens viram "mensagem NN"; suas contas viram "minhaconta" e "outraconta". Ids, classes, posições, marcado, selecionado e clicável ficam como na tela, e os textos do Instagram ficam iguais.
- A ferramenta é `app/src/test/tools/anonimizar_arvores.py`. Ela refaz o corpus inteiro, igual byte a byte, a partir dos dumps, que ficam fora do git.
- **107 testes** rodam o código real do app sobre essas telas, em 8 arquivos de `com.listalocal.real`. Cada tela também é testada com os ids trocados, simulando outro aparelho ou outra versão do Instagram. Outros testes andam pelas telas como o Instagram anda de verdade: cada toque leva à tela real seguinte. Qualquer toque fora do roteiro (numa pessoa, em Enviar, em "Limpar tudo", em Concluir) faz o teste falhar.
- Nenhum dump bruto, @ ou nome de terceiro entra no git ou no zip. Isso foi conferido nos arquivos atuais e no zip.

## Arquivos

- `ListaLocal-Instagram-v0.2.2-debug.apk`: sha256 `ff52367fc335157e360f65adf7f1eed5eaaf106b844deb18c16456e33aef02ae`
- `ListaLocal-Instagram-v0.2.2-fonte.zip`: sha256 `3381a14201655adb84a3807f61ca8130d0a46523c8dc58bafc281928ee081fa7`. Sem build, .gradle, keystore, local.properties nem dumps brutos.
- Git: commit `release: 0.2.2` (01bcd9d), tag `v0.2.2`.
