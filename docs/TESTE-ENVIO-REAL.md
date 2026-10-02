# Teste real de envio (modo C), 26/09/2026

Celular do dono (Galaxy A14, Android 15, Instagram 448.0.0.52.84), conta @frutacarecafc, por adb. Texto: "oiiiiiiii". Destinatários escolhidos pelo dono. Script: `C:/Users/User/igt/envio.py` (cópia em scratchpad/ig/envio.py).

## Caminho usado (o mesmo que o APK deve seguir)
1. Voltar ao Instagram principal (foco `com.instagram.android/...` fora da `ModalActivity`), para um link que falhe nunca deixar aberta a conversa anterior.
2. `am start -a VIEW -d https://ig.me/m/<@> -p com.instagram.android`.
3. Esperar a `ModalActivity` com `direct_thread_header` e `row_thread_composer_edittext` (lidos por `cmd window dump-visible-window-views` + `vdec.py`, porque o uiautomator não enxerga essa tela).
4. Prova: rodada só de leitura antes, com captura do cabeçalho conferida a olho; no envio, a linha do NOME (`header_title`, y 90–146) é comparada com a captura da prova (diferença 0,0 nos quatro). O subtítulo não serve de prova sozinho: troca o @ por "Online agora" ou "Conversa comercial".
5. Campo vazio = `row_thread_composer_send_button_container` ausente. Tocar no campo, `input text`, esperar o botão Enviar aparecer, tocar nele uma vez.
6. Evidência: o botão Enviar some (campo esvazia) e a bolha roxa com o texto aparece (conferido nas capturas).

## Resultado
| Destinatário | Espera antes | Abrir | Tocou Enviar em | Total | Resultado |
|---|---|---|---|---|---|
| @i*********c | 0 s | 1,49 s | 4,14 s | 5,58 s | enviado |
| @e*********n | 1 s | 1,54 s | 5,88 s | 7,29 s | enviado |
| @f*********o | 10 s | 1,64 s | 5,94 s | 7,29 s | enviado |
| @j******o | 30 s | 1,58 s | 6,15 s | 7,72 s | enviado |
| @d********s | — | não abriu em 8 s | — | — | não enviado (link não abriu: @ provavelmente inexistente) |
| @j*********2 | — | não abriu em 8 s | — | — | não enviado (idem) |

Nenhum aviso de limite ou bloqueio nas três velocidades (1 s, 10 s, 30 s).

## Achados para o APK
- `ig.me/m/<@>` é o caminho certo: 1,5–1,6 s, sem busca e sem risco de grupo. @ inexistente: nada acontece (o app precisa de prazo e marcar "não encontrado").
- Prova pela linha do nome, comparada com a captura da prova ou com o nome do seguidor importado; o subtítulo varia.
- Uma das conversas estava com mensagens temporárias ligadas (borda tracejada no campo): a mensagem some depois de vista. O app deve avisar.
- Tempo por pessoa com leitura da tela a cada passo: 5,6–7,7 s; a maior parte é a leitura da árvore (0,8 s por leitura).
