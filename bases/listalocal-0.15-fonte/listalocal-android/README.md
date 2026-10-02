# Lista Local 0.15.0 (Android)

Aplicativo Android que organiza contatos do aparelho e cria listas de transmissão
no WhatsApp ou WhatsApp Business por meio de um serviço de Acessibilidade.
O app não envia mensagens. A operação depende da interface do WhatsApp e deve
ser conferida no aparelho antes de uso com uma agenda maior.

Identidade visual Merlin aplicada com os arquivos fornecidos no kit de marca.
Desenvolvido por João Girão — jvictorgirao@poli.ufrj.br.

## O que mudou nesta versão

- Seleção por nome ou número para excluir pessoas das listas somente durante a
  operação atual. A agenda do celular não é alterada. Se dois contatos tiverem
  o mesmo número, excluir um remove esse número de todas as listas planejadas.
- Normalização de cada telefone em uma única passagem e ajuste dos lotes para
  evitar uma última lista com apenas uma pessoa.
- O modo turbo é desligado quando há exclusões, pois sua busca por prefixo não
  consegue garantir que uma pessoa excluída seja pulada.
- Após solicitar todas as criações, o operador precisa conferir no WhatsApp se
  cada lista apareceu. Só então toca em “Confirmei todas; encerrar uso”. O app
  registra o bloqueio local, desliga a Acessibilidade e impede novas operações
  enquanto continuar instalado. Se a inspeção mostrar uma lista ausente, o
  operador pode voltar ao planejamento para corrigir a operação.
- Aviso destacado e escolha explícita antes de abrir os ajustes de Acessibilidade.
- Leitura e interação da árvore de tela bloqueadas fora do WhatsApp selecionado.
- Remoção da capacidade de consultar outras janelas interativas.
- Falha segura se o toque para criar a lista não acontecer.
- Texto de consentimento corrigido e checagem exata do serviço ativo.
- Paleta, marca e ícone Merlin; crédito de desenvolvimento na abertura.
- Variante temporária que bloqueia o app em 05/10/2026, 00h, no horário de São Paulo.
- Registros de contatos identificáveis por código reduzido apenas no build de depuração.
- Assinatura de release configurada fora do código-fonte.

## Requisitos

- JDK 17 ou superior.
- Android SDK com plataforma 35 e build tools 34 ou superior.
- Gradle wrapper incluído.
- Para testar a interface, um aparelho ou emulador Android.

Crie `local.properties` na raiz com `sdk.dir=<caminho do Android SDK>`.
Esse arquivo e a chave de assinatura não devem entrar em distribuições do fonte.

## Compilar e testar

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:assembleDebugAndroidTest
```

Os testes de interface compilam no segundo comando. Para executá-los, conecte
um aparelho ou emulador e use `:app:connectedDebugAndroidTest`.

Para gerar uma atualização assinada, configure as quatro variáveis de ambiente
abaixo com a chave já usada nas versões anteriores:

```text
LISTALOCAL_KEYSTORE_PATH
LISTALOCAL_STORE_PASSWORD
LISTALOCAL_KEY_ALIAS
LISTALOCAL_KEY_PASSWORD
```

Elas também podem ser propriedades globais do Gradle. Depois execute:

```powershell
.\gradlew.bat :app:assembleRelease
# Copie app\build\outputs\apk\release\app-release.apk: versão normal, código 19.
.\gradlew.bat :app:assembleRelease -Pexpira04102026=true
# Copie o APK de saída novamente: versão com prazo, código 18.
```

O build falha se faltar alguma credencial. As duas variantes usam o mesmo pacote
`com.listalocal` e a mesma chave. A versão normal atualiza a versão com prazo;
o inverso exige desinstalação, porque seu código de versão é menor.
Preserve a chave original: outra chave não instala como atualização do mesmo
pacote. Nunca publique a chave nem as senhas junto com o projeto.

A versão com prazo permite uso até 04/10/2026, inclusive. Ao chegar a
05/10/2026, 00h em São Paulo, ela bloqueia a tela e as ações, cancela a execução
e tenta desligar o serviço de Acessibilidade. Após observar o vencimento, grava
o horário máximo para resistir a um recuo posterior do relógio. Por ser offline,
não há como comprovar a hora real se o relógio for atrasado antes de o app
observar a data de corte.

## Permissões e limites

- `READ_CONTACTS`: permite ler a agenda para formar as listas. O app não altera
  os contatos.
- Acessibilidade: ativação manual nas Configurações do Android. O serviço observa
  apenas `com.whatsapp` e `com.whatsapp.w4b`, e o código valida o pacote antes
  de ler ou tocar na interface.
- O manifesto não declara permissão de Internet. O gate
  `assertNoInternetPermission` verifica isso em cada build.
- O bloqueio de uso único é salvo nos dados privados da instalação. Esses dados
  somem ao desinstalar ou limpar dados, então a reinstalação **não está bloqueada**
  por esta versão. O diretório de servidor de referência entregue à parte mostra
  a base para essa verificação, mas ainda exige hospedagem, autenticação e
  integração do APK. Não é correto distribuir esta versão prometendo bloqueio
  garantido após reinstalação.
- O progresso da operação ainda fica em memória. Encerrar o processo pode exigir
  novo planejamento dos contatos restantes.
- A interface do WhatsApp muda entre versões e contas; teste com poucos contatos
  e confira a lista criada antes de ampliar o uso.
