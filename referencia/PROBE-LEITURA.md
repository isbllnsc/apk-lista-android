# Probe: o app consegue ler as telas modais do Instagram?

Rodada 2, noite de 25/09/2026. Base: `INSTAGRAM-APP-REAL.md` (0.1 e 7.1).

## Situação: teste pronto, AINDA NÃO RODADO no celular

- **O celular do dono não estava ligado ao PC.** De 22:33 a 00:00, `adb devices` só mostrou o emulador, e o Windows não listou nenhum aparelho Samsung no USB (nenhum dispositivo com VID 04E8). Esperei em blocos de 10 min (`adb -s RXCWB01XZKZ wait-for-device`), sem resultado.
- **Nada foi aberto, digitado ou enviado.** `ENVIOS-NOITE.txt` continua vazio.
- **Emulador não usado.** Ele não tem o Instagram, e outra sessão instalou nele um `com.listalocal.instagram` 0.2.0 às 22:40. O `am instrument` reiniciaria esse app.
- O teste compila (`./gradlew --offline assembleDebugAndroidTest`) e está no commit `test(probe): leitura das telas modais no celular real`.

**Para o João:** o teste que prova se o app consegue ler a conversa do Instagram está pronto, mas não pôde rodar porque o celular estava desconectado do computador. Quando o celular voltar ao USB (desbloqueado, com o Instagram aberto no Direct), o teste roda em menos de 1 minuto, só abre duas conversas autorizadas e não digita nada.

## O teste

`app/src/androidTest/java/com/listalocal/probe/ModalReadProbeTest.kt` (repositório `listalocal-instagram-android`).

- **Canal:** `UiAutomation` com as mesmas flags do serviço (`FLAG_REPORT_VIEW_IDS | FLAG_RETRIEVE_INTERACTIVE_WINDOWS`) e com `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`, para não desligar, nem por um instante, os serviços de Acessibilidade do dono.
- **Trava:** só roda com `-e probe 1`. Um `connectedAndroidTest` comum pula o teste e não abre o Instagram.
- **Casos, em ordem:** `ig.me/m/jvsgirao`, `ig.me/m/i**********c` e `ig.me/m/d********s` (@ inexistente, para ver o "não encontrado"). Cada link é aberto com `Intent(VIEW).setPackage("com.instagram.android")` a partir do contexto do app, como faz o serviço. Entre um caso e outro, o teste dá BACK até a conversa sumir (no máximo 3, conferindo antes de cada um). Por isso a árvore de uma conversa não vale como prova da seguinte, e o celular termina onde estava, no Direct.
- **Ids medidos:** `direct_thread_header`, `header_title`, `header_subtitle`, `row_thread_composer_edittext`, `row_thread_composer_send_button_container` (esperado AUSENTE: só aparece com texto), `message_list`, `seen_state_text`.
- **O que vai para o logcat (tag `PROBE`):**
  - `tempo <id> windows=<ms> servico=<ms> active=<ms>`: tempo desde o `startActivity` até o id aparecer. `windows` é qualquer janela de `getWindows()`. `servico` são as janelas que o `NodeOps` escolhe desde o commit "fix(leitura)" (`Janelas.escolher`: a janela do Instagram que tem `direct_thread_header`/`row_thread_composer_edittext`, e `rootInActiveWindow` como reserva). `active` é o `rootInActiveWindow`. Um "-" quer dizer que o id não apareceu em 8 s.
  - `janela w<id> type layer active focused title root=<pacote>/<classe>`: todas as janelas, no início, em cada caso e no fim.
  - `windows w<id> <id> cls text desc hint en click vis b`: o que `findAccessibilityNodeInfosByViewId` devolve em cada janela, e o mesmo para `active`.
  - **Privacidade (revisão da noite 1):** o log não traz @, nome nem texto de mensagem. Os casos aparecem como `c1`, `c2`, `c3` (a linha `[c1] = <hash…ao>` diz qual é qual). Cada `text`/`desc`/`title` sai como tamanho + `Redaction.mask` (hash curto e as 2 últimas letras), por exemplo `8c<1a2b3c4d…ao>`, com ` =@` no fim quando o texto É o @ do caso. Os @ não estão mais no código do teste: vêm de `-e usuarios`.
  - `percurso notImportant=false|true nos=<n> ms=<ms>`: percurso por `getChild`, igual ao do `NodeAdapter`, sem e com `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS`, e os ids que ele acha. Isso mostra se o `header_subtitle` (IgView, texto desenhado) some do percurso ou vem sem texto.
  - `bolha ... textos=[...]`: o último filho da `message_list` (o mais baixo na tela) e os textos dentro dele (tamanho + hash). Em `c1` (`jvsgirao`), deve aparecer `9c<ea7dfd91…ii>`, que é o "oiiiiiiii" do teste de envio.
  - `evento win=<id> type=<t> src=<true|false> x<n>`: eventos do Instagram por janela. É a reserva caso `getWindows()` não enxergue nada.

## Como rodar (menos de 1 min no celular)

Antes: celular no USB e desbloqueado, Instagram aberto no Direct, foco conferido (`dumpsys window | grep mCurrentFocus`) e nenhuma conversa aberta por outra sessão.

```bash
export MSYS_NO_PATHCONV=1
ADB="C:/Users/User/Downloads/Claudinho/lista-local/tools/android-sdk/platform-tools/adb.exe -s RXCWB01XZKZ"
APP=C:/Users/User/Documents/Codex/2026-09-16/va/work/listalocal-instagram-android/app/build/outputs/apk
$ADB shell pm path com.listalocal.instagram.claude || $ADB install -r $APP/debug/app-debug.apk   # applicationId novo: convive com o app do Codex (com.listalocal.instagram)
$ADB install -r -t $APP/androidTest/debug/app-debug-androidTest.apk
$ADB logcat -c
$ADB shell am instrument -w -r -e probe 1 -e usuarios jvsgirao,i**********c,d********s -e class com.listalocal.probe.ModalReadProbeTest \
  com.listalocal.instagram.claude.test/androidx.test.runner.AndroidJUnitRunner
$ADB logcat -d -s PROBE:I > probe-log.txt
$ADB uninstall com.listalocal.instagram.claude.test   # só o APK de teste
$ADB shell dumpsys window | grep mCurrentFocus  # deve estar no Instagram (MainTabActivity)
```

**Não use `./gradlew connectedDebugAndroidTest` no celular do dono.** Ele desinstala o app no fim, e com isso cairia a Acessibilidade que o dono tiver ligado. Ele também roda os testes de tela do Compose.

## Como decidir com o log

| O que o log mostra | O que fazer no serviço |
|---|---|
| `windows` e `servico` com tempo, e `servico escolhe w<X>` igual à janela que tem os ids | Manter o `NodeOps` (já escolhe a janela do Instagram com a conversa, desde o commit "fix(leitura)") |
| `windows` com tempo, mas `servico=-` | A janela da conversa não é de aplicativo ou está atrás de uma janela ilegível: anotar `type`/`layer` das janelas e ajustar `Janelas.escolher` |
| `header_subtitle` achado por id, mas com `text=null` e `desc=null` (ou ausente no `percurso notImportant=false`) | O @ não é legível no subtítulo. Provar pela linha do nome (`header_title`), como no TESTE-ENVIO-REAL. Se `notImportant=true` trouxer o texto, ligar `flagIncludeNotImportantViews` |
| `percurso` não acha ids que `findAccessibilityNodeInfosByViewId` acha | Trocar o retrato inteiro (`NodeAdapter.from`) por busca por id nos passos críticos. Comparar o custo com `percurso ms` e `leitura_media_ms` |
| Tudo "-" em `windows` e `active`, mas há `evento win=<modal> src=true` | Ler pela fonte dos eventos (`event.source` de `TYPE_WINDOW_CONTENT_CHANGED`, subindo com `getParent`) |
| Tudo "-" e nenhum evento com `src=true` | O app não enxerga a conversa. Os modos C e A ficam desligados. O `dump-visible-window-views` do PC exige shell e não serve dentro do app |
| `active` com tempo | Contraria a calibração: anotar a versão do Instagram e do Android |
| `d********s`: nenhum id em 8 s e as janelas iguais às do início | Prazo de 5 s e `NAO_ENCONTRADO`, como no desenho |

**Recomendação provisória (NÃO MEDIDA):** é o que o `NodeOps` já faz desde a 0.1.0 (`flagRetrieveInteractiveWindows` e janela `TYPE_APPLICATION` do Instagram de maior camada), mais três ajustes baratos. (1) Conferir que a janela escolhida contém o id esperado da tela. (2) Usar `findAccessibilityNodeInfosByViewId` nos ids críticos. (3) Provar pelo `header_title` ou pelo `header_subtitle`, o que bater, sem depender só do subtítulo, que troca o @ por "Online agora" ou "Conversa comercial". Nenhum modo deve ser ligado antes de o log acima confirmar a primeira linha da tabela.
