# WhatsApp — DOM real medido no aparelho (2026-09-28)

Captura só-leitura no Galaxy A14 (serial RXCWB01XZKZ, Android 15), WhatsApp em pt-BR,
via `uiautomator dump`. Nenhuma mensagem enviada. Todo conteúdo de terceiro (nomes,
números, textos de mensagem) foi anonimizado no corpus. Fonte para calibrar o
`SelectorProfile.WA` e o `DmFlow` do futuro Lista Local WhatsApp.

Corpus anonimizado (10 árvores) fica local em `igt/wa/anon/` — não vai em zip com números reais.

## Pacotes e telas
- App: `com.whatsapp` (e `com.whatsapp.w4b` no Business — não medido aqui).
- Lista de conversas: `com.whatsapp.home.ui.HomeActivity`.
- Conversa (onde se escreve e envia): `com.whatsapp.Conversation`.

## Lista de conversas (home)
| Papel | resource-id |
|---|---|
| Linha de conversa (container clicável) | `com.whatsapp:id/contact_row_container` |
| Nome do contato na linha | `com.whatsapp:id/conversations_row_contact_name` |
| Data/hora da linha | `com.whatsapp:id/conversations_row_date` |
| Busca | `com.whatsapp:id/my_search_bar` |
| Barra inferior | `com.whatsapp:id/bottom_nav` |
| FAB nova conversa | `com.whatsapp:id/fabText` / `com.whatsapp:id/extended_mini_fab` |

## Conversa aberta — os nós que o DmFlow precisa
| Papel | resource-id | class | clicável | observação |
|---|---|---|---|---|
| **Prova: nome/numero no topo** | `com.whatsapp:id/conversation_contact_name` | TextView | não | mostra o nome salvo; se o contato não está salvo, mostra o **número**. |
| Subtítulo do topo | `com.whatsapp:id/conversation_contact_status` | TextView | não | "online"/"digitando…"/"visto por último". **Troca o nome igual ao Instagram** → provar pelo nome com espera curta, não decidir no subtítulo. |
| **Campo de texto** | `com.whatsapp:id/entry` | EditText | sim | vazio mostra `text="Mensagem"` (hint). Escrever por `ACTION_SET_TEXT` (aceita acento; `adb input text` não). |
| Contêiner do campo | `com.whatsapp:id/text_entry_layout` / `input_layout` | — | — | — |
| Anexar | `com.whatsapp:id/input_attach_button` | ImageButton | sim | `content-desc="Anexar"`. |
| **Mic (campo vazio)** | `com.whatsapp:id/voice_note_btn` | ImageButton | — | aparece quando o campo está vazio; `content-desc` longo "Mensagem de voz…". |
| **Enviar (campo com texto)** | `com.whatsapp:id/send` | ImageButton | **sim** | `content-desc="Enviar"`. Dentro de `com.whatsapp:id/send_container` (esse é não-clicável). **Este é o único nó a tocar, uma vez.** |
| (alt) botão de ação do campo | `com.whatsapp:id/conversation_entry_action_button` | — | — | presente; usar `send`/desc "Enviar" como principal. |

### Transição medida (a regra do DmFlow)
- Campo **vazio** → `voice_note_btn` (mic) presente, nenhum nó com `content-desc="Enviar"`.
- Campo **com texto** → aparece `com.whatsapp:id/send` com `content-desc="Enviar"`, clicável, no mesmo lugar do mic.
- Confirmado ao apagar: o texto volta a `"Mensagem"` e o mic reaparece.

Fluxo WhatsApp = mesmo motor do Instagram:
1. Abrir por `https://wa.me/<numero>` com `setPackage("com.whatsapp")` (só número da agenda).
2. Provar topo: `conversation_contact_name` bate com o contato (reserva: número).
3. `ACTION_SET_TEXT` no `entry` com o texto inteiro.
4. Conferir que `entry` contém o texto.
5. Gravar COMMIT.
6. Achar `send` (`content-desc="Enviar"`), `ACTION_CLICK` **uma vez**.
7. Confirmar pela bolha nova (relógio → 1 tique). Sem confirmação = falha, nunca reenvia.

## Avisos (dialog padrão do Android)
Número inválido/di não registrado → **AlertDialog** padrão:
| Papel | resource-id | conteúdo medido |
|---|---|---|
| Texto do aviso | `android:id/message` | "+&lt;numero&gt; não é um número de telefone válido." |
| Botão | `android:id/button1` | "OK" |

Reusar esse padrão (`android:id/message` + `button1`) para detectar "não foi possível enviar"
e "o número não está no WhatsApp": se a conversa não abrir e surgir esse dialog → **INDISPONIVEL**,
não conta como envio, fecha no OK e segue a fila.

## Armadilha confirmada ao vivo
Toque por **coordenada fixa** na home abriu o **Mercado Libre** (uma conversa tinha link e o
toque caiu no lugar errado). Repete a lição do Instagram: **navegar por intent + tocar só em
nó achado por id**, nunca por posição fixa. O serviço já é assim; os testes de captura também
passam a ser.

## SelectorProfile.WA (esqueleto para o Kotlin)
```
CONTA/PROVA:  conversation_contact_name        (id exato; nunca por texto parecido)
STATUS_TOPO:  conversation_contact_status       (ignorar para identidade)
CAMPO:        entry                             (EditText; hint "Mensagem" = vazio)
MIC_VAZIO:    voice_note_btn
ENVIAR:       send  + content-desc="Enviar"     (clicável; toque único)
DIALOG_MSG:   android:id/message
DIALOG_OK:    android:id/button1
LINHA_HOME:   contact_row_container / conversations_row_contact_name
```
