# Instagram real: calibração da rodada 1 (só leitura)

Para: João (dono). Base: `ADAPTAR-LISTALOCAL-INSTAGRAM.md` (seção 6). Medido em 25/09/2026, das 20:00 às 21:10, pelo PC via `adb`, sem nenhum envio.

**Aparelho:** Samsung SM-A146M, Android 15 (One UI), pt-BR, 1080x2408, Teclado Samsung. **Instagram:** 448.0.0.52.84 (versionCode 385412078). **Conta:** @frutacarecafc (profissional; 388 seguidores, 413 seguindo, 15 posts).

**Capturas:** pasta `C:/Users/User/AppData/Local/Temp/claude/C--Users-User-AppData-Roaming-Claude-scratch-workspaces-ea4cf4ff-4c61-4f82-8468-12398470c2ef-3584b435-a1c0-4de5-8723-d19b002aeed7-scratch-2026-09-23-e8517b/c064a739-cf67-4036-b5f3-e87b20ed2239/scratchpad/ig` (abaixo `ig/`). Por tela: `<nome>.png` (print), `v_<nome>.jpg` (print reduzido), `<nome>.xml` (uiautomator, quando funcionou), `vv/<nome>.zip` + `<nome>_v.txt` (árvore de Views com ids e posições, ver 0.2). Os brutos têm nomes, @ e mensagens de terceiros; aqui tudo vai mascarado.

**O que foi feito no app:** só abrir telas, rolar, digitar em campos de BUSCA (e apagar), marcar e desmarcar 2 pessoas na folha de compartilhar, adicionar e remover 2 "pílulas" em Nova mensagem, e digitar 1 letra no campo de MENSAGEM de uma conversa real e apagá-la (ver o risco abaixo). **Nada foi enviado, curtido, seguido, adicionado, publicado ou apagado.** Na caixa de entrada (`t04.xml` × `t16_final.xml`), a lista de conversas, a ordem e as contagens de não lidas ficaram iguais. Os dois arquivos não são idênticos (md5 diferentes): mudaram o status online de contatos ("Online agora" virou "Online há 1 h"), dois "Usuário presente" e um "Abrir story" sumiram, e o carrossel de notas/música (`pog_*`) mudou. Isso é estado dinâmico do Instagram e não foi causado pelo teste.

**Risco (efeito sobre terceiro):** a letra foi digitada no composer (`row_thread_composer_edittext`) da conversa com @t****2, e não num campo de busca. O botão Enviar chegou a ficar habilitado (`t06e.png`). Digitar numa conversa aberta normalmente mostra "digitando..." em tempo real para a outra pessoa, então é provável que @t****2 tenha visto esse indicador por alguns segundos. Isso foge da regra "só digitar em campos de BUSCA". **Na rodada 2, testes de digitação no composer só em conversa com conta de teste ou do próprio dono; em conversa real com terceiro, não digitar.**

**Conversas abertas (todas LIDAS, nenhuma com "não lidas"):** J. H. V. (@j****_, "Visto ontem"), @t****2 (conta de torcida, "Online há 3 h"), @m****s (última mensagem do dono, "Visto"), @j****n ("Toque para conversar") e @instagram (conta oficial, conversa inexistente). A letra de teste foi digitada e apagada no campo de mensagem da de @t****2 (provável "digitando..." visível para ela; ver "Risco" acima).

**Incidente:** às 20:21 o celular saiu do USB; às 20:28 voltou "unauthorized" e alguém autorizou a depuração na tela. O Instagram reabriu com outra atividade principal (`InstagramMainActivity` virou `com.instagram.android.activity.MainTabActivity`). Nada foi perdido.

---

## 0. Resumo para o João

- **O link `ig.me/m/<@>` abre direto a conversa certa**, em cerca de 1,8 s, sem digitar nada e sem risco de grupo. É o melhor caminho. Com @ que não existe, o Instagram simplesmente não sai da tela (sem aviso).
- **"Bate-papo" não existe mais.** Em Nova mensagem, tocar numa pessoa já abre a conversa embutida com o campo de mensagem; tocar numa segunda pessoa vira prévia de grupo. Esse caminho é o mais arriscado.
- **O @ aparece no cabeçalho da conversa** (embaixo do nome; em "conversa comercial" aparece no lugar do nome). Falta confirmar, com o APK, se a Acessibilidade consegue ler esse texto.
- **"Melhores amigos" agora se chama "Amigos Próximos"** e fica em Configurações. Você já tem **162 pessoas** nela. Não há botões Adicionar/Remover: cada linha tem uma caixa de marcar e embaixo há "Concluir". Há também "Limpar tudo", que o app nunca pode tocar. A tela diz que ninguém é avisado.
- **"Enviar separadamente" existe** quando 2 ou mais pessoas estão marcadas no compartilhar, junto com "Enviar para nova conversa em grupo (2)".
- **A lista de seguidores pode ser lida inteira:** 388 de 388, rolando devagar, em 6,6 min.
- **Canal de transmissão:** não aparece para esta conta.
- **Problema técnico sério:** a leitura de tela padrão (`uiautomator` e `rootInActiveWindow`) **não enxerga** as telas de conversa, Nova mensagem, busca do Direct e Amigos Próximos neste celular. O APK precisa ler as janelas de outro jeito (seção 7.1), e isso precisa ser provado na rodada 2 antes de ligar qualquer modo.

---

## 0.1 Como o Instagram organiza as telas (importante para o dev)

| Atividade (janela) | Telas | `uiautomator dump` |
|---|---|---|
| `MainTabActivity` (antes da reinicialização: `InstagramMainActivity`) | Feed, Direct (caixa de entrada), perfil próprio, seguidores/seguindo, Configurações e atividade, post/reel da grade, folha de compartilhar, Pedidos | Funciona (falha com vídeo tocando: "could not get idle state"\*) |
| `com.instagram.modal.ModalActivity` | Nova mensagem, conversa, detalhes da conversa, busca do Direct, Amigos Próximos, criar nota | **Falha sempre:** "null root node returned by UiTestAutomationBridge"\* |
| `com.instagram.url.UrlHandlerActivity` | Perfil aberto por link (`instagram://user`, `instagram.com/<@>`) | **Pior:** devolve sem erro a árvore VELHA da atividade principal (a caixa de entrada) enquanto a tela mostra o perfil (`t07d.xml` × `t07d.png`) |
| `urlhandlers.igmemessage.IgMeMessageUrlHandlerActivity` | Trampolim do `ig.me/m/...` | Some sozinho e abre a ModalActivity |

\* As duas mensagens de erro entre aspas foram lidas ao vivo no terminal durante o teste e **não foram salvas em arquivo**; não dá para auditá-las pela pasta `ig/`. Na rodada 2, salvar a saída do `uiautomator dump` num log.

**Causa medida:** `dumpsys accessibility` mostra "Active Window Id" preso na janela da atividade principal e "Top Focused Window Id = -1" enquanto a ModalActivity está na frente, embora ela esteja registrada como janela "Instagram". O único dump salvo (`ig/_acc.txt`) mostra "Active Window Id = 262", "Top Focused Window Id = -1" e duas janelas com título "Instagram": 262 (a ativa) e 271. Os ids 272 e 278, também com título "Instagram", foram vistos ao vivo no terminal em outras leituras, sem arquivo salvo. Reproduzido antes e depois da reinicialização do app. O serviço do Lista Local ativo no celular tem `retrieveInteractiveWindows=false`.

## 0.2 Ferramenta usada onde o uiautomator falha

`adb exec-out cmd window dump-visible-window-views > x.zip` grava a árvore de Views de cada janela visível (formato `ViewHierarchyEncoder`). O decodificador está em `ig/vdec.py` (`python vdec.py x.zip Modal`). Dá classe, id, posição (com `translationX/Y`), clicável (C), marcado (K), selecionado (S) e desabilitado (X). **Não dá texto nem content-desc** (o Android esconde as "user properties"), e o miolo de telas em Compose aparece só como `AndroidComposeView`. Os textos abaixo vêm dos prints. Leva 0,8 s por leitura, e por isso também serviu para medir tempos.

**Aviso sobre o helper `dump.sh`:** quando o dump falha 5 vezes ele copia o arquivo antigo do celular. `nova.xml` e `s00.xml` são cópias idênticas de `inbox.xml` (mesmo md5) e **não** mostram a tela Nova mensagem. O `ig/ig.sh` apaga o arquivo antes e só copia em caso de sucesso.

---

## 1. Abrir conversa 1:1: todas as alternativas

Estado inicial de cada teste: aba Direct (caixa de entrada) aberta. Alvo principal: J. H. V. (@j****_), seguido pelo dono (botão "Seguindo" no perfil, `t07d.png`), conversa lida.

### 1.a Link direto (deep link)

`adb shell am start -W -a android.intent.action.VIEW -d "<url>" com.instagram.android`

| URL | Resultado | Captura |
|---|---|---|
| `https://ig.me/m/<@>` | **Abre a conversa certa** (IgMeMessageUrlHandlerActivity → ModalActivity). Campo pronto em 1,6 a 2,7 s. Nada é enviado. | `t07a` |
| `https://ig.me/m/<@>` sem `com.instagram.android` | Igual: o domínio `ig.me` está **verificado** como App Link do Instagram (`pm get-app-links`), sem seletor nem navegador | `t07b` |
| `https://ig.me/m/<@ EM MAIÚSCULAS>` | Abre a mesma conversa (não diferencia maiúsculas) | `t07f1` |
| `https://ig.me/m/<@ inexistente>` | Nada acontece: continua na caixa de entrada, **sem aviso** (1 s e 3,5 s depois) | `t07c1`, `t07c2` |
| `https://ig.me/m/<@ sem conversa>` (@instagram) | Abre conversa nova: cartão com nome, @, "686 mi seguidores · 8,6 mil posts", "Vocês não se seguem no Instagram", "Ver comunidade"; bandeja **"Diga 'olá' enviando uma figurinha"** (tocar numa figurinha ENVIA na hora); botão enviar já visível e **desabilitado** | `t07h` |
| `https://ig.me/m/<@>` com o Instagram em segundo plano (tela inicial do Android) | Abre a conversa em 1,75 s; Voltar leva ao Instagram, não à tela inicial | `t07i` |
| `instagram://user?username=<@>` | Abre o **perfil** (UrlHandlerActivity), não a conversa | `t07d` |
| `https://www.instagram.com/<@>` | Abre o perfil | `t07f5` |
| `https://ig.me/<@>` | Cai no **feed** (IgMeExternalUrlHandlerActivity) | `t07f6` |
| `https://www.instagram.com/direct/t/<@>` | Cai no feed | `t07f2` |
| `https://www.instagram.com/direct/t/<número fictício>` | Cai no feed. Com o id real da conversa não foi testado: o app não mostra esse id em lugar nenhum | `t07f3` |
| `https://www.instagram.com/direct/inbox/` | Cai no feed | `t07f4` |

Voltar a partir da conversa aberta pelo link volta para onde o Instagram estava (caixa de entrada).

### 1.b Direct > Nova mensagem > busca

1. Caixa de entrada → ícone lápis, content-desc **"Nova mensagem"** (995,144). Abre em ~1,4 s (ModalActivity).
2. Tela (`t05b`, `t05j`): título "Nova mensagem" (`action_bar_title`), rótulo "Para:" (`direct_new_chat_to_field`), campo "Pesquisar" (`search_edit_text`, classe `SearchWithDeleteEditText`, dentro de `recipient_picker_typeahead_pill`), atalhos **"Conversa em grupo"** (`direct_ff_group_chat_entry_point`) e **"Conversas com IA"** (`direct_ai_agents_entry_point`), cabeçalho "Sugestões" (`header_text`) e linhas com "×" (`recipient_typeahead_add`, remove a sugestão; não tocar). Lista: `recipients_list` (RecyclerView).
3. Ao digitar o @ (`t05d`): seções "Sugestões" e **"Mais no Instagram"**. Linha = `IgFrameLayout` clicável com `row_user_primary_name` (nome) e `row_user_secondary_name` (@). Aparecem homônimos (3 contas com o mesmo nome e @ quase igual, uma delas verificada): **só casar pelo @ exato**. Linha extra "Procurando alguém? Convide amigos para o Ins..." com botão **"Convidar"** (`row_direct_action_button`): nunca tocar.
4. **Tocar na linha** (`t05e`): NÃO aparece "Bate-papo" nem "Conversar". A pessoa vira uma pílula com o NOME (não o @) no "Para:" (classe ofuscada `0CGd`, clicável) e **a conversa 1:1 abre embutida logo abaixo**, com histórico, cartão (nome + @ + "Ver perfil") e o campo "Mensagem..." (`row_thread_composer_edittext`). O foco continua no campo de busca: o que for digitado vira busca de um 2º destinatário.
5. **Segunda pessoa** (`t05f`, `t05g`): digitar outro @ esconde a prévia; tocar na linha cria 2 pílulas e mostra a prévia de GRUPO ("Nome1, Nome2", avatares sobrepostos) com o campo "Mensagem...". **Enviar ali criaria o grupo, sem nenhuma confirmação.** Não há botão "Criar grupo".
6. **Desfazer** (`t05h`, `t05i`, `t05j`): com a busca vazia, o 1º Apagar (DEL) destaca a última pílula em azul e o 2º a remove. Voltar 2 vezes fecha teclado e tela.

Tempo total (toque em Nova mensagem → conversa embutida): **5,3 s**.

### 1.c Perfil > botão "Mensagem"

Perfil (por link ou busca) → botão **"Mensagem"** (`button_container`, IgdsButton, ao lado de "Seguindo" = `profile_header_follow_button`) → conversa em **1,3 s** (`t07e`). Voltar volta ao perfil. O @ do perfil fica na barra de título (ProfileActionBar).

### 1.d Busca do Direct (`search_row`)

1. Caixa de entrada → "Pesquisar" (`search_row`, testTag do Compose, ~540,313) → ModalActivity com o campo `search_bar_real_field` (SearchEditText), "×" (`dismiss_button`), seta Voltar (`back_arrow`) e um avião azul `meta_ai_share_button` (não tocar). Sem texto mostra "Mais sugestões" (`t08a`).
2. Ao digitar o @ (`t08b`): 1ª linha é a conta exata; depois **"Mais contas"** com "Ver tudo" (`header_action_button`) e homônimos, inclusive um verificado com @ quase igual. Linha = `row_inbox_container` (clicável) com `row_inbox_username` (NOME) e `row_inbox_digest` (@); às vezes `row_search_user_secondary_subtitle` (bio).
3. Tocar na linha → conversa em 1,5 s (`t08c`). Total de ponta a ponta (toque na busca → campo pronto): **4,5 s**.

### 1.e Lista de seguidores > "Mensagem" (alternativa extra)

Cada linha da lista de seguidores tem o botão **"Mensagem"** (`follow_list_row_large_follow_button`, content-desc "Enviar mensagem para <nome>"). Buscar o @ na lista e tocar → conversa em **2,4 s** (`t10g`, `t10h`).

### 1.f A tela de conversa (vale para todas as entradas)

Captura base: `t06_views.txt`, `t06a`, `t07a`, `t06c`.

| Parte | id / classe | Texto pt-BR | Posição (sem teclado) |
|---|---|---|---|
| Cabeçalho | `direct_thread_header` | | topo, y 65–223 |
| Voltar | `header_left_button` | | x 0–158 |
| Avatar | `header_avatar` (clicável) | | x 191–292 |
| Nome | `header_title` (IgTextView) | pessoa: nome completo, **truncado com "..."** (ex.: "J*** H*******..."); conversa comercial: **o @** | y 96–146 |
| Subtítulo | `header_subtitle` (**IgView**, texto desenhado, não TextView) | pessoa: **o @** ("j****_"); conversa comercial: **"Conversa comercial"** | y 146–191 |
| Toque no nome | `header_title_subtitle_container` (clicável) | abre "detalhes": só o nome (`thread_title`), atalhos Perfil / Pesquisar / Silenciar / Opções, "Personalizar", "Mensagens temporárias: Desativado", "Controles da conversa", "Privacidade e segurança", "Apelidos", **"Criar uma conversa em grupo"** (`t06g`) | |
| Botões à direita | `header_right_buttons` com filhos de id gerado (`0x15`...`0x17`), **não estáveis** | pessoa: adicionar pessoas, vídeo, bandeira; comercial: telefone, vídeo, bandeira | |
| Cartão do topo | Compose (`MetaComposeView`) | foto, nome, @, "1,4 mil seguidores · 3 posts", **"Vocês se seguem mutuamente no Instagram"** ou "Vocês não se seguem no Instagram", "Vocês seguem X e outros 42 perfis", "Ver perfil" (carrega depois do resto, `t08d`) | só no começo do histórico; em conversa longa fica fora da tela (`t06c`) |
| Mensagens | `message_list` (CustomFadingEdgeRecyclerView); bolhas de texto em Compose | enviadas à direita (roxo), recebidas à esquerda | y 223–2181 |
| Estado da última enviada | `seen_state_text` (TightTextView) | "Visto", "Visto ontem" | logo acima do campo |
| Campo | `row_thread_composer_edittext` (**ComposerAutoCompleteTextView**, um EditText) | dica "Mensagem..."; com mensagens temporárias ligadas: **"Mensagem tempo[rária]..."** e borda tracejada (`t10h`) | y 2109–2250 |
| Botões sem texto | `row_thread_composer_button_camera`, `_voice`, `_button_gallery`, `_button_sticker`, `_button_overflow` | câmera, microfone, galeria, figurinha, "+" | |
| **Enviar** | `row_thread_composer_send_button_container` (clicável), `_send_button_background`, `_send_button_icon` | avião de papel em pílula roxa | **só aparece com texto** e substitui microfone/galeria/figurinha/+; a câmera vira `row_thread_composer_button_sticker_shortcut` (`t06e`). Apagou, volta ao normal (`t06f`). Em conversa nova já aparece, desabilitado (`t07h`) |

**Atenção:** `t06e`/`t06f` vêm de 1 letra digitada e apagada no composer de uma conversa real com terceiro (@t****2), o que provavelmente mostrou "digitando..." para ela. Não repetir em conversa real (ver "Risco" no topo).

**Com o teclado aberto** (`t06b`, `t06e`): o campo sobe para y 1213–1400 (`message_composer_bar`) e a lista encolhe para y 223–1308. A tecla Enter do Teclado Samsung é **"↵" (nova linha), não envia**.

**Nomes enganosos:** dentro de algumas mensagens recebidas (menção em comentário) existem `floating_send_container`, `send_button_pill_container` e `send_label`. Eles NÃO são o botão Enviar. O app nunca deve procurar "send" por substring.

**Evidência de envio disponível sem abrir a conversa:** a linha da caixa de entrada muda para "<nome>, Enviado" (visto em outras conversas) e sobe para o topo. As linhas não lidas vêm como "<nome>, não lidas, N novas mensagens · <tempo>".

### 1.g Tempos medidos

A leitura de tela leva cerca de 0,8 s por volta, e os tempos incluem esse passo.

| Caminho | Tempo até o campo pronto |
|---|---|
| `ig.me/m/<@>` com o Instagram na frente | 1,6 / 1,7 / 1,8 / 2,4 / 2,7 s (TotalTime do `am start`: 157–500 ms) |
| `ig.me/m/<@>` com o Instagram em segundo plano | 1,75 s |
| Perfil já aberto → "Mensagem" | 1,3 s (+0,3 s para abrir o perfil por link) |
| Seguidores → busca → "Mensagem" | 2,4 s depois do toque (+ busca ~2,5 s) |
| Busca do Direct (ponta a ponta) | 4,5 s |
| Nova mensagem (ponta a ponta, até a conversa embutida) | 5,3 s |

---

## 2. Seguidores

**Como chegar:** Perfil (`profile_tab`, 972,2205) → número de seguidores (`profile_header_followers_stacked_familiar`, content-desc "388seguidores", 654,418). Fica na atividade principal, e o uiautomator funciona. Capturas: `t10a` a `t10h`.

- **Abas** (`title`, Button): "388 seguidores" (S = selecionada), "413 seguindo", "0 assinaturas", "Sinalizadas". Barra: "frutacarecafc", Voltar, "Central de amizade".
- **Busca:** `row_search_edit_text` (EditText, dica "Pesquisar"). Casa por @ e por nome, **sem acento** (buscar um nome sem acento achou a versão acentuada). Classificação: `sorting_entry_row_option` "Classificado por Padrão".
- **Linha:** `follow_list_container` (clicável), **`follow_list_username` = @**, `follow_list_subtitle` = nome, botão "Mensagem" (`follow_list_row_large_follow_button`) e "×" com content-desc **"Ignorar"** (não tocar). Altura de 203 px, **8 linhas por tela** (9 a 10 nós contando as parciais).
- **Rolagem:** `android:id/list` (ListView virtualizado, só as linhas visíveis existem na árvore). Com arraste rápido (fling) pulam linhas: 291 de 388. Com **arraste lento (1000 px em 1,5 s)** houve sobreposição em todos os passos: **388 de 388 em 72 arrastes, 399 s** (com uma leitura de ~4 s por passo).
- **Fim da lista:** começa "Sugestões para você", com ids diferentes (`recommended_user_row_content_identifier`, `row_recommended_user_username`, `row_recommended_user_follow_button` **"Seguir" / "Seguir de volta"**, `row_recommended_hide_icon_button` "Ignorar") e "Ver todas as sugestões" (`t10f`). O leitor deve parar no primeiro `recommended_user_row`.
- **Armadilha medida:** a tecla `KEYCODE_MOVE_END` com o foco fora do campo trocou a aba para "Sinalizadas" (`t10c`). Não usar teclas de navegação.

## 3. Amigos Próximos (antes "Melhores amigos")

**Como chegar:** Perfil → "Opções" (☰, 989,144) → **"Configurações e atividade"** (Bloks, sem resource-id; itens = View com content-desc) → rolar até **"Quem pode ver seu conteúdo"** → **"Amigos Próximos"** (content-desc "Amigos Próximos, 162"). Abre na ModalActivity (sem uiautomator). Capturas: `t01a` a `t01d`, `t02a`, `t02c` (não existe `t02b`: não foi salvo), `t03a`, `t03b`.

| Parte | id | Texto |
|---|---|---|
| Aviso | `audience_picker_disclaimer_text` | "Não enviamos notificações quando você edita sua lista Amigos Próximos. Como funciona." |
| Busca | `search_edit_text` (BackInterceptEditText) em `search_box`; ao digitar surge "Cancelar" (`search_exit_button`) | "Pesquisar" |
| Cabeçalho 1 | `row_header_textview` + **`row_header_action`** | "162 pessoas" + **"Limpar tudo"** (NUNCA tocar) |
| Linha | `row_user_container` (clicável), `row_user_username` (@), `row_user_info` (nome), **`IgdsCheckBox` sem id** à direita (estado marcado legível: K) | |
| Cabeçalho 2 | `row_header_textview` | "Sugestões" (linhas desmarcadas) |
| Botão | `done_button` (IgdsButton) | **"Concluir"** (fixo embaixo) |

- **Não existem botões "Adicionar"/"Remover".** O modelo é caixa de marcar + "Concluir". Não foi medido se marcar grava na hora ou só no "Concluir".
- **A busca acha qualquer conta:** @instagram apareceu com caixa desmarcada. Um membro buscado pelo @ exato aparece sozinho e marcado (`t03b`).
- Ordem da lista: membros (marcados) primeiro, depois "Sugestões". 169 px por linha, ~9 por tela.
- Saída sem mudança: Cancelar + Voltar volta a Configurações, e o contador continua "162" (`_cc2.png`).
- Caminho pelo story não foi medido: exigiria criar conteúdo na câmera.

## 4. Compartilhar (post ou reel próprio)

**Como chegar:** Perfil → 1º item da grade (reel fixado) → rolar até a barra de ações → avião de papel **`row_feed_button_share`** (458,1815). Cuidado: `row_feed_button_like` fica a 390 px à esquerda. A folha abre dentro da atividade principal, mas o uiautomator falha com o vídeo tocando. Capturas: `t11a`, `t11b`, `t12a` a `t12i`.

- **Folha fechada** (`t12a`): alça, busca "Pesquisar" (`search_edit_text` em `direct_private_share_sticky_search_box`), botão de grupo `direct_private_share_create_group_button`, grade 3×N de pessoas (`direct_share_sheet_grid_view_pog`, com `grid_view_pog_text_view_first_line`/`second_line` = nome em 2 linhas, **não o @**) e linha externa (`direct_external_reshare_row`): "Adicionar ao story", "Copiar link", "WhatsApp", "Compartilhar", "Baixar".
- **Tocar FORA da folha a fecha** (aconteceu uma vez por coordenada errada; o toque caiu no vídeo, sem curtir).
- **Busca** (`t12d`): linha com `row_user_primary_name` (nome), `row_user_secondary_name` (@) e **caixa `recipient_toggle` (IgdsCheckBox)**. **Não há "Enviar" por linha.**
- **1 marcada** (`t12e`): a busca limpa; a pessoa vai para o 1º quadro com um ✓ azul; embaixo aparecem **"Escreva uma mensagem..."** (`direct_private_share_message_box`, ComposerAutoCompleteTextView) e **"Enviar"** (`direct_send_button_multi_select`).
- **2 marcadas** (`t12g`): **"Enviar separadamente"** (MESMO id `direct_send_button_multi_select`) e **"Enviar para nova conversa em grupo (2)"** (`direct_send_to_group_button_vertical`, GroupSendButton). O app tem de ler o TEXTO do botão, não só o id.
- **Com o teclado aberto** (`t12f`), o campo e o botão "Enviar" sobem para o meio da tela (y ≈ 1100–1330).
- **Desmarcar:** tocar de novo no quadro (`t12h`, `t12i`). Com zero marcadas, a linha externa volta. Voltar fecha a folha.
- Limite de pessoas por envio: não medido.

## 5. Canal de transmissão e notas

- **Canal de transmissão: não existe para esta conta nesta versão.** Não aparece no menu "Criar" do perfil (Reel, Edits, Post, Story, Destaques, Live, Anúncio: `t15a`), nem em Nova mensagem, nem em Filtros do Direct (`t15b`).
- **Notas:** no topo do Direct (`cf_hub_recycler_view`; `pog_root_view`, `pog_bubble_text`, `pog_name`). A sua aparece como "Sua nota", com content-desc "Adicionar nota" e uma frase-sugestão que muda ("Primeira nota da semana…"): o dono não tem nota ativa. Tocar abre o criador (ModalActivity, `notes_creation_top_container`, `t14a`): "Nota...", música, GIF, **"Compartilhar com amigos >"** e "Compartilhar". O público (`t14b`) é **"Amigos: seguidores que você também segue"** ou **"Amigos Próximos: 161 pessoas que você também segue"**. A nota não chega a quem só segue você. Fechado com Cancelar + Voltar, sem publicar.
- **Filtros do Direct** (`t15b`): Não lidos, Sem resposta, **Respostas a stories**, Perfis verificados, Mais de 10 mil seguidores, Ver mais; Pastas.
- **Abas do Direct:** "Principal 6" (`cPrincipal 6`), "Pedidos" ("Nenhum pedido de contato ainda", "Pedidos ocultos", `t15c`) e "Geral".

## 6. Sinais de alerta

- **Nenhum aviso de bloqueio, limite ou "Tente novamente mais tarde" apareceu** em 70 min de uso (incluindo 72 arrastes seguidos na lista de seguidores).
- **Botões perigosos vistos (lista de "nunca tocar"):** "Convidar" (Nova mensagem), figurinhas de "Diga 'olá' enviando uma figurinha" (conversa nova, envia com 1 toque), "Criar uma conversa em grupo" (detalhes), "Enviar para nova conversa em grupo (N)", "Limpar tudo" e "Concluir" (Amigos Próximos), "Seguir"/"Seguir de volta"/"Ignorar" (fim e linhas dos seguidores), "Turbinar post" e "Criar anúncio de mensagens" (Direct), o "×" das sugestões.
- **Estado que muda a entrega:** "Mensagens temporárias" ligadas numa conversa (dica "Mensagem tempo..." e borda tracejada). A mensagem some depois de vista, e a evidência fica fraca.

---

## 7. Recomendação

### 7.1 Pré-requisito (antes de qualquer modo)

O serviço **não pode usar só `rootInActiveWindow`**, que é o que o `NodeOps.kt` do Lista Local faz hoje (linha 30). Neste celular, a "janela ativa" fica presa na atividade principal: o resultado é null nas telas Modal e, pior, **a árvore antiga da caixa de entrada** quando o perfil foi aberto por link. Na configuração da Acessibilidade: `accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"`, `packageNames="com.instagram.android"`. No código: percorrer `service.windows`, escolher a janela `TYPE_APPLICATION` do Instagram com `isFocused`/`isActive` e maior `layer`, conferir que ela contém o id esperado da tela, e recusar quando nada bate. **Não medido:** se com isso o serviço enxerga os nós da ModalActivity e o texto do `header_subtitle` (IgView). Esse é o 1º teste da rodada 2 e do "Conferir o Instagram". Se falhar, os modos C e A ficam desligados.

### 7.2 Caminho mais robusto para abrir a conversa e provar o @

1. **Abrir:** `Intent(ACTION_VIEW, Uri.parse("https://ig.me/m/" + username)).setPackage("com.instagram.android")` com `FLAG_ACTIVITY_NEW_TASK`. Motivos: ~1,8 s, o @ exato vai na própria URL (sem busca, sem homônimos, sem linhas para escolher), não passa por pílulas e não tem como virar grupo.
2. **Esperar até 5 s** pela janela com `direct_thread_header` + `row_thread_composer_edittext`. Se a tela não mudar, o @ não existe ou não abriu, e o desfecho é `NAO_ENCONTRADO`.
3. **Provar:** `header_title` OU `header_subtitle` igual ao @ (sem diferenciar maiúsculas). O subtítulo tem o @ nas conversas com pessoas; o título tem o @ nas "Conversa comercial". Se nenhum bater, provar pelo caminho longo: tocar no cabeçalho → "Perfil" → título da barra do perfil = @ → Voltar 2x. Sem prova, o desfecho é FALHA.
4. **Antes de preencher, recusar:** conversa com mensagens temporárias (dica "Mensagem temporária"), conversa nova com a bandeja de figurinhas (nunca tocar nela) e qualquer tela de Nova mensagem com pílulas.
5. **Evidência:** o botão Enviar só aparece com texto (`row_thread_composer_send_button_container`, habilitado). Depois do toque, o campo volta a mostrar o microfone e o `seen_state_text` muda. As bolhas são Compose, então o texto exato da mensagem enviada depende da rodada 2. Reforço barato: a linha da caixa de entrada vira "<nome>, Enviado".
6. **Reserva:** busca do Direct (4,5 s; casar `row_inbox_digest` = @ exato). **Não usar** Nova mensagem para o modo C.

### 7.3 Ajustes no desenho

- **Modo A:** trocar "Adicionar → conferir Remover" por "marcar a caixa da linha exata → conferir K → no fim, Concluir". Medir na rodada 2 se "Concluir" é obrigatório para gravar. Colocar "Limpar tudo" na lista de bloqueio. A busca acha qualquer conta, então quem segue quem vem do CSV do Merlin.
- **Modo B:** viável para a 0.2.0. Conferir o texto "Enviar separadamente" (o id é o mesmo do "Enviar") e a presença de `direct_send_to_group_button_vertical`, e nunca tocar nele. A grade mostra nomes, então marcar só pela busca (linha com @).
- **Leitura de seguidores (0.2.0):** possível, com arraste lento, ~1 s por seguidor.

---

## 8. Afirmações [VERIFICAR] do desenho

| # | Afirmação | Resultado |
|---|---|---|
| 1 | "Melhores amigos" no menu do perfil, com busca e Adicionar/Remover por linha | **Diferente:** chama "Amigos Próximos", fica em Configurações e atividade > Quem pode ver seu conteúdo; tem busca; caixa de marcar + "Concluir" + "Limpar tudo", sem Adicionar/Remover |
| 2 | Entrar na lista não avisa; ninguém vê os outros | **Confirmado** o não aviso (texto da tela). Ninguém ver os outros: não medido |
| 3 | Tamanho máximo; uma lista só | Uma lista só: **confirmado** (uma entrada, 162 pessoas). Tamanho máximo: não medido |
| 4 | Story para a lista só é visto por ela; resposta chega como DM 1:1 | Resposta como DM: **indício confirmado** (conversas 1:1 mostram "Respondeu ao seu story"; filtro "Respostas a stories"). O resto: não medido |
| 5 | Busca de Melhores amigos acha qualquer conta ou só seguidores | **Qualquer conta** (@instagram apareceu) |
| 6 | Compartilhar tem "Enviar separadamente" com 2+; limite; ou "Enviar" por linha | **Confirmado** "Enviar separadamente" + "Enviar para nova conversa em grupo (2)"; **sem** "Enviar" por linha (caixa por linha). Limite e entrega 1:1: não medidos |
| 7 | `ig.me/m/<username>` abre direto a conversa | **Confirmado** (App Link verificado, maiúsculas indiferentes; @ inexistente = nada acontece) |
| 8 | Nova mensagem: busca, linhas com @, marcador, "Bate-papo"; 2 = grupo | **Diferente:** busca e @ na linha sim; sem marcador na linha e **sem "Bate-papo"**: tocar já abre a conversa embutida; 2 pessoas = prévia de grupo com campo, sem confirmação |
| 9 | Cabeçalho mostra o @ exato | **Confirmado na tela** (subtítulo para pessoas, título para "Conversa comercial"). Leitura pela Acessibilidade: não medido |
| 10 | Campo aceita texto; Enviar com rótulo estável; mensagem enviada visível; marcador de falha | Campo = EditText `row_thread_composer_edittext`: **confirmado**; Enviar só com texto, id estável `row_thread_composer_send_button_container`: **confirmado**; rótulo, ACTION_SET_TEXT, texto da bolha e marcador de falha: não medidos |
| 11 | Textos de restrição e de conta que não recebe | Não medido (nenhum apareceu) |
| 12 | Ids estáveis e versão legível | **Confirmado** (ids listados; versão por `dumpsys package`). Exceções: botões do cabeçalho (`0x15`...), pílula (`0CGd`), caixas de Amigos Próximos (sem id) e telas Bloks/Compose (sem id) |
| 13 | DM para quem segue cai na Principal, não em Pedidos | Não medido (depende do destinatário). No app a aba chama "Pedidos" ("pedido de contato") |
| 14 | Lista de seguidores tem busca e pode ser lida até o fim | **Confirmado:** busca por @ e nome; 388/388 com arraste lento |
| 15 | Rótulos pt-BR e en | pt-BR: **confirmado** (neste documento); en: não medido |
| 16 | No canal de transmissão só a própria pessoa entra | **Diferente:** não há canal para esta conta nesta versão |
| 17 | `uiautomator dump` funciona nas telas do Instagram | **Diferente:** só na atividade principal (e não com vídeo tocando); falha nas telas Modal e mostra árvore velha no perfil aberto por link. Alternativa de leitura: `cmd window dump-visible-window-views` (ids e posições, sem texto) |

## 9. Não medido nesta rodada (vai para a rodada 2, com o APK)

1. Se o serviço com `flagRetrieveInteractiveWindows` lê os nós das telas Modal e o texto do `header_subtitle`.
2. `ACTION_SET_TEXT` no `row_thread_composer_edittext` e o content-desc do botão Enviar.
3. Texto das bolhas enviadas (Compose) e marcador de "não enviada".
4. Se marcar em Amigos Próximos grava sem "Concluir".
5. Limite do "Enviar separadamente" e entrega 1:1 de cada um.
6. Rótulos em inglês.
7. Início a frio (Instagram fechado): não foi forçado, porque exigiria parar o app.

Regra para a rodada 2: qualquer teste que digite no composer (item 2 inclusive) só em conversa com conta de teste ou do próprio dono, nunca em conversa real com terceiro (gera "digitando..." para a outra pessoa). Salvar em arquivo os erros do `uiautomator` e cada `dumpsys accessibility` citado.
