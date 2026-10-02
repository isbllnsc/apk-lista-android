@echo off
rem Teste no celular real (RXCWB01XZKZ) a um comando. Nada roda em connectedAndroidTest.
rem   rodar-teste-celular.cmd                 probe + conferencia (nada digitado)
rem   rodar-teste-celular.cmd ENVIAR [texto]  idem + UM envio para @jvsgirao (texto sem espacos; padrao oiiiiiiii)
rem O envio so roda se a conferencia passou. Nunca desinstala o app (derrubaria a Acessibilidade).
setlocal EnableExtensions
set "ADB=C:\Users\User\Downloads\Claudinho\lista-local\tools\android-sdk\platform-tools\adb.exe"
set "S=RXCWB01XZKZ"
set "REPO=%~dp0"
set "APP_APK=%REPO%app\build\outputs\apk\debug\app-debug.apk"
set "TEST_APK=%REPO%app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
set "DOCS=C:\Users\User\Documents\Codex\2026-09-16\va\work\merlin-adaptar-listalocal"
set "APP=com.listalocal.instagram.claude"
set "RUNNER=%APP%.test/androidx.test.runner.AndroidJUnitRunner"
set "JAVA_HOME=C:\Users\User\Documents\Codex\2026-08-28\key-de\.worktrees\lista-local-gate1\tools\appium-spike\.tools\jdk\jdk-21.0.12.1+1"
rem Os @ de seguidores reais nunca entram no git: ficam em celular.local.cmd (ignorado), com
rem   set "PROBE_USUARIOS=jvsgirao,um_seguidor,um_inexistente"
rem   set "PROVAR=seguidores que existem, separados por virgula"
rem   set "AUSENTES=@ que nao existem"
if not exist "%REPO%celular.local.cmd" (echo PARADO: falta celular.local.cmd ao lado deste arquivo ^(os @ de teste ficam fora do git^). & exit /b 1)
call "%REPO%celular.local.cmd"
set "ENVIAR="
if /i "%~1"=="ENVIAR" set "ENVIAR=1"
set "TEXTO=%~2"
if "%TEXTO%"=="" set "TEXTO=oiiiiiiii"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "STAMP=%%i"
set "LOG=%DOCS%\celular-%STAMP%.log"
set "TMPD=%TEMP%\listalocal-celular-%STAMP%"
mkdir "%TMPD%" >nul 2>&1

echo [0] Conferindo o aparelho %S%...
"%ADB%" -s %S% get-state 2>nul | findstr /x device >nul || (echo PARADO: o celular %S% nao esta conectado ^(adb devices^). & exit /b 1)
"%ADB%" -s %S% shell dumpsys power | findstr /c:"mWakefulness=Awake" >nul || (echo PARADO: a tela esta apagada. Acenda, desbloqueie e rode de novo. & exit /b 1)
"%ADB%" -s %S% shell dumpsys window | findstr /c:"isKeyguardShowing=true" /c:"mShowingLockscreen=true" /c:"mDreamingLockscreen=true" >nul && (echo PARADO: o celular esta bloqueado. Desbloqueie e rode de novo. & exit /b 1)
"%ADB%" -s %S% shell dumpsys activity activities | findstr /c:"mKeyguardShowing=true" >nul && (echo PARADO: o celular esta bloqueado. Desbloqueie e rode de novo. & exit /b 1)
"%ADB%" -s %S% shell pm path com.instagram.android | findstr package: >nul || (echo PARADO: o Instagram nao esta instalado. & exit /b 1)

echo [1] Compilando ^(offline^)...
pushd "%REPO%"
call "%REPO%gradlew.bat" --offline -q assembleDebug assembleDebugAndroidTest
if errorlevel 1 (popd & echo PARADO: a compilacao falhou. & exit /b 1)
popd

rem O APK de teste usa o codigo do app instalado: ele precisa ser esta mesma compilacao.
rem install -r ATUALIZA (mantem dados e a Acessibilidade ligada); nunca desinstala.
rem (sem aspas no ADB aqui: o caminho nao tem espaco, e aspas a mais quebrariam o for /f)
set "REMOTO="
for /f %%h in ('%ADB% -s %S% shell "sha256sum $(pm path %APP% | cut -d: -f2) </dev/null 2>/dev/null"') do set "REMOTO=%%h"
for /f %%h in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%APP_APK%').Hash.ToLower()"') do set "LOCAL=%%h"
if /i not "%REMOTO%"=="%LOCAL%" (
  echo [2] Atualizando o app no celular ^(install -r: dados e Acessibilidade ficam^)...
  "%ADB%" -s %S% install -r "%APP_APK%" || (echo PARADO: o app nao atualizou ^(assinatura diferente?^). Nada foi desinstalado. & exit /b 1)
) else (
  echo [2] App no celular ja e esta compilacao.
)
echo [3] Instalando so o APK de teste...
"%ADB%" -s %S% install -r -t "%TEST_APK%" || (echo PARADO: o APK de teste nao instalou. & exit /b 1)

"%ADB%" -s %S% logcat -c
echo celular %S% %STAMP% envio=%ENVIAR% > "%LOG%"

echo [4] Probe de leitura...
call :instr probe "-e probe 1 -e usuarios %PROBE_USUARIOS% -e class com.listalocal.probe.ModalReadProbeTest"
echo [5] Conferencia sem digitar...
call :instr conferencia "-e real 1 -e provar %PROVAR% -e ausentes %AUSENTES% -e class com.listalocal.e2e.ConferenciaRealTest"

set "R_envio=nao pedido"
if defined ENVIAR (
  if "%R_conferencia%"=="OK" (
    echo [6] UM envio para @jvsgirao...
    call :instr envio "-e enviar jvsgirao -e texto %TEXTO% -e class com.listalocal.e2e.EnvioRealTest"
  ) else (
    set "R_envio=NAO RODOU (a conferencia falhou)"
  )
)
if defined ENVIAR echo %date% %time% @jvsgirao texto=%TEXTO% resultado=%R_envio% log=celular-%STAMP%.log>> "%DOCS%\ENVIOS-NOITE.txt"

echo === logcat >> "%LOG%"
"%ADB%" -s %S% logcat -d -v time -s E2E:I PROBE:I TestRunner:I AndroidRuntime:E >> "%LOG%"

echo [7] Desinstalando so o APK de teste ^(o app fica^)...
"%ADB%" -s %S% uninstall %APP%.test >nul
"%ADB%" -s %S% shell dumpsys window | findstr /c:"mCurrentFocus" > "%TMPD%\foco.txt"
type "%TMPD%\foco.txt"
findstr /c:"com.instagram.android" "%TMPD%\foco.txt" >nul || echo AVISO: o Instagram nao esta na frente; abra o Direct a mao.

echo.
echo probe=%R_probe%  conferencia=%R_conferencia%  envio=%R_envio%
echo Log: %LOG%
findstr /c:"=== " /c:"] <" /c:"seguidores " /c:"caixa " "%LOG%"
if not "%R_conferencia%"=="OK" exit /b 1
if defined ENVIAR if not "%R_envio%"=="OK" exit /b 1
exit /b 0

:instr
echo === %~1 >> "%LOG%"
"%ADB%" -s %S% shell am instrument -w -r %~2 %RUNNER% > "%TMPD%\%~1.txt" 2>&1
type "%TMPD%\%~1.txt" >> "%LOG%"
findstr /c:"OK (1 test)" "%TMPD%\%~1.txt" >nul && (set "R_%~1=OK") || (set "R_%~1=FALHOU")
exit /b 0
