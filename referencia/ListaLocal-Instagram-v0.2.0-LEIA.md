# Lista Local Instagram 0.2.0: leia antes de usar

Arquivo: `ListaLocal-Instagram-v0.2.0-debug.apk` (confira o `.sha256` ao lado).
Nome no celular: **Lista Local Instagram (Claude)**. Pacote `com.listalocal.instagram.claude`, versão 0.2.0 (código 6). Instala **por cima** da 0.1.x sem apagar nada e fica junto com o app do Codex (`com.listalocal.instagram`). Feito por João Victor Girão.

## O que mudou desde a 0.1.4

### O app aprende os botões do Instagram no seu aparelho (auto-calibração)

Antes, o app só sabia onde ficam os botões do Instagram porque alguém tinha **medido à mão** num aparelho só (um Galaxy A14, em português, numa versão específica do Instagram). Em outro celular, outra versão ou outro idioma, os botões mudam de "nome interno" e de texto, e o modo ficava **bloqueado**.

Agora, quando você toca em **"Conferir o Instagram"**, o app **abre o Instagram, olha a tela de verdade e aprende sozinho** onde estão os controles daquele modo (a aba de Mensagens, o campo "Para:", a entrada de Amigos Próximos, o botão Concluir, etc.). Ele reconhece cada botão pela **forma** — o tipo do elemento, se dá para clicar ou digitar, onde ele fica na tela (topo/rodapé), e o texto em vários idiomas (português, inglês, espanhol e mais alguns) — e não só pelo "nome interno" fixo de um aparelho.

O que ele aprendeu fica **guardado só no seu celular** (nunca vai para a internet, igual à lista de enviados). Some se você desinstalar ou limpar os dados do app. Reconferir por cima atualiza o que foi aprendido.

Na hora de operar, para cada botão o app tenta nesta ordem: **(1)** o que ele aprendeu no seu aparelho → **(2)** o "nome interno" fixo já conhecido → **(3)** reconhecer pela forma ao vivo. O nome fixo que bate na tela sempre vence um palpite pela forma (é mais seguro).

### Nada muda nas regras de segurança

Tudo o que já valia continua valendo, sem exceção:

- **Só envia depois de conferir.** Se a conferência não achar um botão obrigatório, o modo **não libera** — o app diz exatamente qual botão faltou, em vez de tentar no escuro e errar.
- **Prova do @ antes de digitar**: antes de escrever para alguém, o app confere que a conversa aberta é mesmo a daquela pessoa.
- **Nunca grupo**, nunca toca em **"Limpar tudo"** (o modo Amigos Próximos só libera depois de o app reconhecer esse botão perigoso no seu aparelho, justamente para saber onde ele está e nunca encostar).
- **Enviar sem sua confirmação por lote = falha**: sem a confirmação, o motor para e espera.
- **Sem disfarce anti-bot.** O app não tenta enganar o Instagram; ele só lê a tela e toca nos botões certos, como você faria.

### Por baixo (para quem quiser saber)

A auto-calibração é lida por "assinaturas de papel" (arquivo `SignatureLibrary`): cada botão que o app usa tem uma descrição por forma + texto multi-idioma. O motor (`RoleMatcher`) casa ou **recusa** cada candidato; o `Aprendiz` guarda o melhor por papel; `PerfisAprendidos` grava por (versão do Instagram + idioma + modo) só no aparelho. Cobertura de teste automático subiu de 233 para **272 testes** (0 falhas), incluindo um teste que aprende num aparelho de "nomes internos diferentes" e outro que recusa o botão errado.

## O que ainda precisa de teste real

A auto-calibração foi validada **no computador** (272 testes automáticos, 0 falhas) e com a calibração de leitura feita no seu Galaxy A14. **Ainda não foi testada num segundo aparelho físico** de marca/versão/idioma diferentes. Antes de confiar num celular novo:

1. Instale, ligue a Acessibilidade e toque em **"Conferir o Instagram"** para cada modo que for usar.
2. Veja se libera. Se bloquear, o app diz qual botão faltou — isso é o comportamento certo (ele avisa em vez de errar).
3. Faça o primeiro envio só para **@jvsgirao** (você), como sempre, antes de qualquer pessoa real.

Veja `Merlin-Insta-COBERTURA-CELULARES.md` (ao lado) para a resposta honesta sobre "funciona em vários Androids?".

## Como instalar

1. Passe o APK para o celular (cabo, Drive ou WhatsApp para você mesmo) e toque nele. Se já tiver a 0.1.x, ele atualiza.
2. Permita **instalar de fontes desconhecidas** só para o app que abriu o arquivo. Se o Play Protect avisar, toque em "Instalar mesmo assim".

## Ligar a Acessibilidade (só você pode fazer)

1. **Configurações > Acessibilidade > Apps instalados > Lista Local Instagram (Claude)** e ligue a chave. Toque em **Permitir**.
2. Se a chave estiver cinza ou aparecer "Configuração restrita": **Configurações > Aplicativos > Lista Local Instagram (Claude) > ⋮ > Permitir configurações restritas**, confirme com o PIN e volte ao passo 1.

## Riscos

- **Bloqueio**: o Instagram pode limitar ou desativar a conta por mensagens automáticas. Usar é decisão e responsabilidade sua.
- **Termos do Instagram** proíbem automação sem permissão.
- **Ainda não testado num segundo celular físico.** A auto-calibração amplia a cobertura, mas a primeira prova num aparelho novo é sempre o "Conferir o Instagram" dele.
