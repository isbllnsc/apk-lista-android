# LEIA PRIMEIRO — Lista Local WhatsApp (para o Antigravity)

Você vai construir o **Lista Local WhatsApp**: um APK Android que envia mensagens 1:1 no WhatsApp, uma pessoa por vez, só para **contatos salvos na agenda do celular**, por um serviço de Acessibilidade que o dono liga à mão. Você não tem acesso à conversa que originou o pedido: tudo está neste pacote.

## Ordem de leitura

1. `ListaLocal-WhatsApp-PLANO-ANTIGRAVITY.md` — o plano, **inteiro**. Comece pela seção 0 (regras R1 a R27, decisões pendentes D1 a D10, mapa de caminhos do pacote em 0.1).
2. `bases/merlin-insta-fonte/listalocal-instagram-android/` — o motor a reaproveitar (Merlin Insta 0.2.2, tag `v0.2.2`, sem `.git`). Arquivos na ordem da seção 0.2 do plano.
3. `referencia/EnvioListaTest.kt` — o teste que enviou 362 mensagens reais (ficou fora do git da base).
4. `docs/INSTAGRAM-APP-REAL.md`, `docs/TESTE-ENVIO-REAL.md`, `docs/Merlin-Insta-v0.2.2-CACADA.md`, `docs/Merlin-Insta-COBERTURA-CELULARES.md` — o que o Instagram real ensinou (as 9 armadilhas).
5. `referencia/PROBE-LEITURA.md`, `referencia/E2E-REAL.md`, `referencia/ListaLocal-Instagram-v0.2.0-LEIA.md`, `referencia/vdec.py`.
6. `bases/listalocal-0.15-fonte/listalocal-android/` — a parte WhatsApp e agenda (Lista Local 0.15): `README.md`, `ContactsRepository`, `ContactNormalizer`, `Regions`, `ContactExclusionPolicy`, `TargetApp`, perfil `WA_2_26`.
7. `docs/WHATSAPP-DOM-REAL.md` — calibração do WhatsApp **Web** (textos de status e regras; não serve para ids do app). `WHATSAPP-DOM-REAL-claude.md` é idêntico.

## Primeiro comando

Etapa E0 do plano (nada de código ainda): compilar a base **numa cópia** e ver os 449 testes passarem.

```powershell
$env:JAVA_HOME = "C:\Users\User\Documents\Codex\2026-08-28\key-de\.worktrees\lista-local-gate1\tools\appium-spike\.tools\jdk\jdk-21.0.12.1+1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$pacote = "<pasta onde este zip foi extraído>"
$copia  = "$env:TEMP\base-merlin-insta-e0"
Copy-Item "$pacote\bases\merlin-insta-fonte\listalocal-instagram-android" $copia -Recurse
Set-Content -Encoding ascii "$copia\local.properties" 'sdk.dir=C\:\\Users\\User\\Downloads\\Claudinho\\lista-local\\tools\\android-sdk'
Set-Location $copia
.\gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug
```

Aceite da E0: 449 testes, 0 falhas, e a linha `OK: nenhuma permissao de rede nos manifestos mesclados.` Depois, mande ao dono as decisões D1 a D10 (Apêndice E do plano) e siga para a E1. Fora do PC do dono, ajuste JDK e SDK (seção 0.4).

## Nunca

- Enviar, abrir conversa de terceiro ou escrever nela sem a autorização do dono no chat (D9, R23, R24).
- Tocar por coordenada, mexer em configurações do Android, rodar `connectedAndroidTest` ou desinstalar o app do celular do dono (R10, R11, R21, R22).
- Ler dumps brutos, prints ou `celular.local.cmd`, ou escrever número real em comando, arquivo ou chat (R27).
- Editar as bases deste pacote ou sobrescrever arquivos em `outputs\` (R26).

Riscos honestos (seção 8 do plano): os Termos do WhatsApp proíbem automação não autorizada, e o número pode ser banido.
