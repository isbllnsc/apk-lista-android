# Estrutura real do WhatsApp Web, observada em 24/09/2026

Fonte: WhatsApp Web do dono, com a interface em **português** (`<html lang="pt">`). A inspeção foi só de leitura, pelo DOM, e nada foi enviado. O grupo usado como teste foi **FrutacaLoucos**, a pedido do dono. Os contatos desse grupo são o conjunto de teste da calibração.

## Lista de conversas
- Contêiner: `#pane-side`. Cada conversa é um `[role="row"]` com cerca de 76 px de altura. Não há `a[href]`.
- O número de não lidas aparece como `aria-label` do tipo "9 mensagens não lidas" ou "1.227 mensagens não lidas", com ponto de milhar.
- Os ícones de status da prévia aparecem no `textContent` como `wds-ic-read`, `wds-ic-delivered` e `wds-ic-disappearing-messages`: são títulos dos SVG.
- Prévia da última mensagem nossa: "Você: …". A nossa própria conversa aparece como "+55 21 9xxxx-xxxx (você)".
- Busca: `input[aria-label="Pesquisar ou começar uma nova conversa"]`. É um INPUT, não um contenteditable. A busca filtra só com digitação real; atribuir `.value` não funciona. Não usar busca por número (regra do desenho).
- Com a aba em segundo plano (`document.visibilityState === "hidden"`), o WhatsApp desenha só a última mensagem da conversa. **A leitura exige a aba visível**, então o adaptador deve recusar e avisar enquanto ela estiver oculta.

## Conversa aberta (`#main`)
- Cabeçalho: `#main header`. O título é o nome da conversa. Nos grupos, um `[title]` traz os participantes separados por vírgula ("Contato 01, Contato 02, …, Você").
- Histórico: contêiner rolável com `flex-direction: column` (não usa column-reverse). Cada item é um `[role="row"]`.
- Mensagens com texto: um nó com `data-pre-plain-text="[HH:MM, DD/MM/AAAA] Remetente: "`.
  - **Recebida:** o remetente é o nome salvo do contato, por exemplo "Contato 02: ".
  - **Enviada por nós:** o remetente é o **nosso nome de perfil**, por exemplo "Girão: ". Esse nome precisa ser calibrado por conta. Leia o nome da própria conta, ou use os outros sinais abaixo.
- `data-id`: só o ID da mensagem, com 20 a 32 caracteres. **Não tem mais os prefixos `true_`/`false_`**, e o comprimento não indica autoria. **Hipótese do desenho derrubada.**
- As classes `.message-in` e `.message-out` **não existem mais**. **Hipótese derrubada.**
- Sinais de autoria confirmados:
  1. **Ícone de cauda:** `[data-icon="tail-in"]` na primeira bolha de uma sequência recebida e `[data-icon="tail-out"]` na primeira de uma sequência enviada. As bolhas seguintes da mesma sequência não têm cauda.
  2. **Lado do balão:** recebida à esquerda, enviada à direita, comparando o centro do nó com `data-pre-plain-text` e o centro de `#main`.
  3. **Status de envio:** só as mensagens nossas têm `aria-label` de status, como "Enviada" (1 check). Estados esperados: "Pendente" (relógio), "Enviada", "Entregue" e "Lida". **Calibrar os textos exatos de Pendente, Entregue e Lida.** No `textContent` também aparecem os títulos `wds-ic-delivered` e `wds-ic-read`.
  4. **Remetente:** o `data-pre-plain-text` igual ao nosso nome de perfil indica mensagem nossa.
- Linhas sem `data-pre-plain-text` (mídia, figurinha, datas, avisos de sistema) ocupam a largura total, cerca de 882 px. Para mídia, remetente e hora ficam no texto da própria linha.
- Mensagem citada: `aria-label="Mensagem citada"`. O trecho citado **não é resposta** e deve ser descartado.
- Reação: nos balões aparece o botão de hover "Reagir", que não é reação. Nenhum selo de reação foi observado nesta conversa. **Calibrar com uma conversa que tenha reação.**
- Campo de mensagem: `#main footer [contenteditable="true"][role="textbox"][data-lexical-editor]`, `data-tab="10"`, com `aria-label` "Digite uma mensagem para o grupo <nome>" ou "Digite uma mensagem para <contato>".
- Rodapé sem texto digitado: "Anexar", "Emojis, GIFs, figurinhas" e "Mensagem de voz". O botão de enviar só aparece depois de digitar. Enter envia.

## Nova conversa (fonte dos contatos)
- Botão `[aria-label="Nova conversa"]` abre um painel com o título "Nova conversa". Escape fecha.
- O painel começa pelas ações ("Novo grupo", "Novo contato", "Nova comunidade"), depois vem "(você)" e em seguida os contatos.
- Cada contato é um `[role="listitem"]` com um `[role="button"]` dentro.
  - Linha 1: o nome salvo, que pode ter emoji. Alguns contatos estão salvos com o **próprio número como nome** (ex.: "+5521…").
  - Linha 2: o recado do perfil, por exemplo "Olá! Eu estou usando o WhatsApp", ou vazia.
- **O telefone não aparece** para contatos com nome salvo. Como o desenho previa, o ID fica nulo até a etapa RESOLVER_ID, e não pode usar busca por número.
- Lista virtualizada: um contêiner rolável com `scrollHeight` de cerca de 66.600 px para cerca de 900 contatos. A coleta precisa rolar e remover duplicados.

## Grupo de teste FrutacaLoucos
- 14 participantes e "Você", todos exibidos com nome salvo (nenhum só número).
- Para os testes em conta real, use **somente** esses contatos, por decisão do dono.

## Consequências para o adaptador (substituem as hipóteses do desenho)
1. Autoria por `tail-in`/`tail-out`, lado do balão, `aria-label` de status (só nas nossas) e remetente em `data-pre-plain-text`. Remover qualquer uso de `true_`/`false_` e de `.message-in`/`.message-out`.
2. Confirmação de envio: nova linha nossa, abaixo da âncora, com o texto enviado e status passando de "Pendente" para "Enviada", "Entregue" ou "Lida". Relógio fixo continua sendo "incerto".
3. Com a aba oculta, a leitura fica indisponível e o adaptador avisa. Nunca concluir "sem resposta" nesse estado.
4. Coleta pelo painel "Nova conversa", com rolagem e deduplicação. Contatos cujo nome é um número continuam sendo contatos salvos.
5. Mensagem citada e a linha do "(você)" nunca viram resposta.

## Complemento observado em 24/09/2026, na conversa com o próprio número (sem efeito em terceiros)
- Mensagem nossa lida: `aria-label="Lida"` no nó de status, confirmado em 8 mensagens seguidas.
- Mensagens nossas também trazem `aria-label="Você:"`, um prefixo acessível do remetente. É mais um sinal forte de autoria "nossa" e não depende do nome de perfil.
- Na lista de conversas, o status da prévia aparece só como `data-icon="wds-ic-read"` (sem aria-label). "recalled" indica mensagem apagada.
- Ainda não observados: "Entregue" e "Pendente" (texto exato), selo de reação. Hipótese: "Entregue" e "Pendente", pelo mesmo padrão de "Enviada" e "Lida". Confirmar no primeiro teste real pelo diagnóstico.
