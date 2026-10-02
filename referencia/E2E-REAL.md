# E2E no celular real (RXCWB01XZKZ)

**Situação: celular desconectado desde 22:33; teste pendente. Agora ele roda a UM comando: `rodar-teste-celular.cmd` (abaixo).**

## Registro (relógio do PC, 26/09/2026)

Conferência com `adb devices` a cada 5 min, de 02:23 até 08:20. O log completo das checagens está no fim deste arquivo.

- 02:23: só `emulator-5554`. O RXCWB01XZKZ não aparece.
- 02:24 a 08:19: 72 checagens (uma a cada 5 min), todas com o RXCWB01XZKZ ausente. Entre elas, `adb -s RXCWB01XZKZ wait-for-device` ficou esperando o tempo todo, sem retorno.
- 08:20: prazo final. `adb devices` mostra só `emulator-5554`. Teste no aparelho encerrado sem rodar.

Nada foi instalado, aberto, digitado ou enviado no celular. O emulador não foi tocado. `ENVIOS-NOITE.txt` continua vazio.

**Para o João:** o celular não estava ligado ao computador a noite toda, por isso o teste no aparelho real não rodou. Para rodar: ligue o celular no USB, desbloqueie, abra o Instagram no Direct e rode `rodar-teste-celular.cmd` (seção abaixo). Sem ENVIAR, nada é digitado; a leitura toda leva uns 2 a 3 minutos (mais a compilação).

## Teste no celular a UM comando (pronto desde 26/09, 08:40)

Pré-condições: celular no USB e desbloqueado, tela acesa, Instagram aberto no Direct, nenhuma outra sessão usando o celular. A Acessibilidade do app NÃO precisa estar ligada para o teste (ele lê pelo UiAutomation), e o script nunca a desliga.

No Windows (cmd ou duplo clique):

```bat
C:\Users\User\Documents\Codex\2026-09-16\va\work\listalocal-instagram-android\rodar-teste-celular.cmd
```

Com o envio real (UM envio para @jvsgirao, só se a conferência passar; texto sem espaços, padrão `oiiiiiiii`):

```bat
C:\Users\User\Documents\Codex\2026-09-16\va\work\listalocal-instagram-android\rodar-teste-celular.cmd ENVIAR oiiiiiiii
```

O que o script faz, em ordem (só `adb -s RXCWB01XZKZ`):

0. Confere o aparelho: conectado, tela acesa, sem bloqueio, Instagram instalado. Senão, para sem tocar em nada.
1. Compila (`gradlew --offline assembleDebug assembleDebugAndroidTest`).
2. O APK de teste usa o código do app instalado, então o app no celular precisa ser esta mesma compilação. Se o hash do app no celular for outro (ou se ele não estiver instalado), faz `install -r` do app: é uma atualização que mantém os dados e a Acessibilidade. **Nunca desinstala o app.**
3. Instala só o APK de teste (`com.listalocal.instagram.claude.test`).
4. Probe de leitura (`ModalReadProbeTest`, `-e probe 1`), igual ao PROBE-LEITURA.md.
5. Conferência sem digitar (`com.listalocal.e2e.ConferenciaRealTest`, `-e real 1`): abre e prova i**********c, e*********n, f*********o e jvsgirao. Confere "não encontrado" em d********s e j*********2, lê 3 telas de Seguidores e termina na caixa de entrada (Direct).
6. Só com `ENVIAR` e com a conferência OK: `com.listalocal.e2e.EnvioRealTest` (`-e enviar jvsgirao -e texto ...`). Faz UM envio pelo motor (`DmFlow.enviar`, com a opção explícita `aceitarTemporaria`). Prova o @ antes de escrever e grava "vou enviar" antes do toque. Sem evidência dá falha e nada é reenviado. O teste recusa qualquer @ que não seja jvsgirao. A linha com hora e resultado vai para `ENVIOS-NOITE.txt`.
7. Salva o logcat (tags `E2E`, `PROBE`, `TestRunner`, `AndroidRuntime`) e a saída de cada teste em `merlin-adaptar-listalocal\celular-<data-hora>.log`. Depois desinstala só o APK de teste e mostra o foco (deve ser o Instagram). No fim, imprime `probe=… conferencia=… envio=…` e o resumo do log.

Saída 0 = tudo OK. Como ler o log:

- `[cN] <hash…> esperado=PROVADA|NAO_ENCONTRADO conversa=… ms=… OK|FALHOU`: um caso por @. O @ não aparece, só `Redaction.mask`.
- `seguidores abrir … ms=` e `seguidores tela=N linhas=… ms=`: o tempo de cada tela.
- `caixa conta=… linhas=… ms=`.
- `=== conferencia fim ms=… falhas=[…]`.
- `[envio] vou enviar …`, `=== envio fim desfecho=… trilha=… ms=…`.

Como funciona: `TelaReal` (androidTest) usa o MESMO `NodeOps` do serviço (o construtor agora recebe de onde vêm as janelas; o serviço passa `service.windows`, o teste passa `UiAutomation.windows` com `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`). Por cima dele rodam `DmFlow.abrirEProvar` (passos 0 a 2 do envio, extraídos sem mudar a lógica), `FonteSeguidores` e `FonteConversas`. Única diferença: o link abre por `am start` (shell), porque o Android pode barrar o `startActivity` do processo de teste como início em segundo plano.

Nada disso roda sozinho: sem `-e real 1` / `-e enviar`, os testes são pulados. **Não use `./gradlew connectedDebugAndroidTest` no celular do dono**, porque ele desinstala o app e derruba a Acessibilidade. O `am instrument` reinicia o processo do app, e o Android religa sozinho o serviço de Acessibilidade, se estiver ligado.

Validação sem celular (26/09, 08:40): compilação (`assembleDebug assembleDebugAndroidTest`), 204 testes de unidade sem falha, e o script rodado a seco com um adb falso. Os três caminhos conferidos: tudo OK; conferência falhou, com o envio bloqueado; e sem ENVIAR. **Ainda não rodou no aparelho.**

## Comandos manuais (referência, só o probe)

Pré-condições: celular no USB e desbloqueado (tela com PIN = parar), Instagram aberto no Direct, nenhuma outra sessão usando o celular.

```bash
export MSYS_NO_PATHCONV=1
export JAVA_HOME=C:/Users/User/Documents/Codex/2026-08-28/key-de/.worktrees/lista-local-gate1/tools/appium-spike/.tools/jdk/jdk-21.0.12.1+1
ADB="C:/Users/User/Downloads/Claudinho/lista-local/tools/android-sdk/platform-tools/adb.exe -s RXCWB01XZKZ"
REPO=C:/Users/User/Documents/Codex/2026-09-16/va/work/listalocal-instagram-android
APK=$REPO/app/build/outputs/apk

# 0. conferir aparelho e tela
C:/Users/User/Downloads/Claudinho/lista-local/tools/android-sdk/platform-tools/adb.exe devices   # RXCWB01XZKZ   device
$ADB shell dumpsys window | grep mCurrentFocus     # esperado: com.instagram.android/...MainTabActivity
$ADB shell dumpsys window | grep -i "mDreamingLockscreen\|isStatusBarKeyguard"   # bloqueado = parar

# 1. compilar app + APK de teste
cd $REPO && ./gradlew --offline assembleDebug assembleDebugAndroidTest

# 2. teste de leitura (probe) - só o APK de teste entra e sai
$ADB install -r $APK/debug/app-debug.apk                      # com.listalocal.instagram.claude (NÃO toca no com.listalocal.instagram do Codex)
$ADB install -r -t $APK/androidTest/debug/app-debug-androidTest.apk
$ADB logcat -c
$ADB shell am instrument -w -r -e probe 1 -e usuarios jvsgirao,i**********c,d********s \
  -e class com.listalocal.probe.ModalReadProbeTest \
  com.listalocal.instagram.claude.test/androidx.test.runner.AndroidJUnitRunner
$ADB logcat -d -s PROBE:I > C:/Users/User/Documents/Codex/2026-09-16/va/work/merlin-adaptar-listalocal/probe-log.txt
$ADB uninstall com.listalocal.instagram.claude.test         # só o APK de teste; o app fica
$ADB shell dumpsys window | grep mCurrentFocus               # deve voltar ao Direct
```

Ler `probe-log.txt` com a tabela "Como decidir com o log" de `PROBE-LEITURA.md` e ajustar `NodeOps`/`Janelas.escolher` se preciso (commit `fix(e2e): ...`, com teste).

## Log das checagens de `adb devices`

```
02:24:04 RXCWB01XZKZ=ausente
02:29:04 RXCWB01XZKZ=ausente
02:34:04 RXCWB01XZKZ=ausente
02:39:04 RXCWB01XZKZ=ausente
02:44:04 RXCWB01XZKZ=ausente
02:49:05 RXCWB01XZKZ=ausente
02:54:05 RXCWB01XZKZ=ausente
02:59:05 RXCWB01XZKZ=ausente
03:04:05 RXCWB01XZKZ=ausente
03:09:06 RXCWB01XZKZ=ausente
03:14:06 RXCWB01XZKZ=ausente
03:19:06 RXCWB01XZKZ=ausente
03:24:06 RXCWB01XZKZ=ausente
03:29:06 RXCWB01XZKZ=ausente
03:34:07 RXCWB01XZKZ=ausente
03:39:07 RXCWB01XZKZ=ausente
03:44:07 RXCWB01XZKZ=ausente
03:49:07 RXCWB01XZKZ=ausente
03:54:07 RXCWB01XZKZ=ausente
03:59:08 RXCWB01XZKZ=ausente
04:04:08 RXCWB01XZKZ=ausente
04:09:08 RXCWB01XZKZ=ausente
04:14:08 RXCWB01XZKZ=ausente
04:19:09 RXCWB01XZKZ=ausente
04:24:09 RXCWB01XZKZ=ausente
04:29:09 RXCWB01XZKZ=ausente
04:34:09 RXCWB01XZKZ=ausente
04:39:10 RXCWB01XZKZ=ausente
04:44:10 RXCWB01XZKZ=ausente
04:49:10 RXCWB01XZKZ=ausente
04:54:10 RXCWB01XZKZ=ausente
04:59:10 RXCWB01XZKZ=ausente
05:04:11 RXCWB01XZKZ=ausente
05:09:11 RXCWB01XZKZ=ausente
05:14:11 RXCWB01XZKZ=ausente
05:19:11 RXCWB01XZKZ=ausente
05:24:11 RXCWB01XZKZ=ausente
05:29:12 RXCWB01XZKZ=ausente
05:34:12 RXCWB01XZKZ=ausente
05:39:12 RXCWB01XZKZ=ausente
05:44:12 RXCWB01XZKZ=ausente
05:49:13 RXCWB01XZKZ=ausente
05:54:13 RXCWB01XZKZ=ausente
05:59:13 RXCWB01XZKZ=ausente
06:04:13 RXCWB01XZKZ=ausente
06:09:13 RXCWB01XZKZ=ausente
06:14:14 RXCWB01XZKZ=ausente
06:19:14 RXCWB01XZKZ=ausente
06:24:14 RXCWB01XZKZ=ausente
06:29:14 RXCWB01XZKZ=ausente
06:34:15 RXCWB01XZKZ=ausente
06:39:15 RXCWB01XZKZ=ausente
06:44:15 RXCWB01XZKZ=ausente
06:49:16 RXCWB01XZKZ=ausente
06:54:17 RXCWB01XZKZ=ausente
06:59:17 RXCWB01XZKZ=ausente
07:04:17 RXCWB01XZKZ=ausente
07:09:17 RXCWB01XZKZ=ausente
07:14:18 RXCWB01XZKZ=ausente
07:19:18 RXCWB01XZKZ=ausente
07:24:18 RXCWB01XZKZ=ausente
07:29:18 RXCWB01XZKZ=ausente
07:34:19 RXCWB01XZKZ=ausente
07:39:19 RXCWB01XZKZ=ausente
07:44:19 RXCWB01XZKZ=ausente
07:49:19 RXCWB01XZKZ=ausente
07:54:20 RXCWB01XZKZ=ausente
07:59:20 RXCWB01XZKZ=ausente
08:04:20 RXCWB01XZKZ=ausente
08:09:20 RXCWB01XZKZ=ausente
08:14:21 RXCWB01XZKZ=ausente
08:19:21 RXCWB01XZKZ=ausente
```
