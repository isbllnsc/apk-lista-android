# Funciona em vários Androids? Resposta honesta (Lista Local Instagram 0.2.0)

Para: João. Sobre o app **Lista Local Instagram (Claude)** 0.2.0, com a auto-calibração.
Resumo curto: **funciona em mais aparelhos do que a versão anterior, mas não em "qualquer" Android sem antes conferir.** A regra de ouro continua: em cada aparelho novo, o app só libera um modo depois que **você** toca em "Conferir o Instagram" e ele acha os botões na tela de verdade. Se não achar, ele **bloqueia e avisa** — nunca chuta.

## Como era antes (0.1.4) e o que mudou

- **Antes:** os botões do Instagram estavam fixos, medidos num aparelho só (Galaxy A14, português, uma versão do Instagram). Em outro celular/versão/idioma, os "nomes internos" e textos mudavam e o modo ficava bloqueado.
- **Agora (0.2.0):** ao conferir, o app **aprende os botões no próprio aparelho** pela forma deles (tipo do elemento, se clica/digita, posição na tela, texto em vários idiomas), guarda isso só no celular, e usa o aprendido na hora de operar. Isso amplia a cobertura sem abrir mão da segurança.

## O que a auto-calibração COBRE bem

1. **Versões novas do Instagram no mesmo idioma.** O Instagram muda quase toda semana. Enquanto a estrutura da tela e os textos continuarem parecidos, o app reconhece e reaprende os botões — não precisa de uma nova medição manual a cada versão.
2. **Celulares com "nomes internos" diferentes.** Se outro aparelho usa ids diferentes para os mesmos botões, o app acha pela forma e aprende os ids daquele aparelho. (Há um teste automático que simula exatamente isso: um aparelho de ids trocados, e o app acerta.)
3. **Idiomas cobertos pelos textos que o app conhece:** português, inglês e espanhol com boa cobertura; italiano e alemão parciais. Nesses idiomas os botões que dependem de texto (Nova mensagem, Opções, Configurações, Amigos Próximos, Concluir, Limpar tudo) são reconhecidos.
4. **Faixa de Android ampla por build:** funciona de **Android 8 (2017)** a **Android 15** (minSdk 26, targetSdk 35).

## O que AINDA depende (limites honestos)

1. **Fabricantes que restringem a Acessibilidade.** Xiaomi/MIUI, alguns Samsung ("Configuração restrita"), Huawei, e "otimizadores de bateria" podem desligar ou bloquear o serviço de Acessibilidade. O app **não tem como** e **não deve** burlar isso — quem liga a Acessibilidade e libera as "configurações restritas" é você, na mão. Se o Android matar o serviço em segundo plano, a operação para.
2. **Instagram muito diferente do medido.** O Instagram faz testes A/B (telas diferentes para contas diferentes) e algumas telas são desenhadas de um jeito que a Acessibilidade **não enxerga como botões** (telas "Bloks"/desenhadas como imagem; na calibração de leitura, algumas telas de Nova mensagem/conversa/Amigos Próximos não apareciam para o leitor automático). Onde a tela não expõe os botões para a Acessibilidade, nem a auto-calibração nem o app conseguem agir — e aí ele bloqueia e avisa.
3. **Idioma fora da lista de textos.** Botões que exigem texto (ex.: "Concluir", "Nova mensagem") num idioma que o app ainda não conhece (ex.: francês, japonês) **não são aprendidos** e o modo fica bloqueado, dizendo qual faltou. Ampliar é acrescentar a palavra daquele idioma no código — tarefa pequena, mas manual.
4. **Primeira calibração por aparelho.** Nada vem aprendido de fábrica para o seu celular. Em cada aparelho (e a cada versão nova do Instagram, ou troca de idioma), é preciso tocar em **"Conferir o Instagram"** uma vez por modo antes de operar. É rápido e só leitura, mas é obrigatório.
5. **Sem coordenada de pixel.** O app clica em botões que a Acessibilidade lista, nunca "no ponto X,Y da tela". Isso é mais seguro e estável, mas significa que ele só age no que a árvore de Acessibilidade mostra.

## Como testar num aparelho novo (passo a passo)

1. Instale o APK (`ListaLocal-Instagram-v0.2.0-debug.apk`) e confira o `.sha256`.
2. **Ligue a Acessibilidade:** Configurações > Acessibilidade > Lista Local Instagram (Claude) > ligar. Se a chave estiver cinza, libere "Permitir configurações restritas" no menu ⋮ do app.
3. Abra o **Instagram**, na **conta e no idioma** que vai usar, e deixe no Direct.
4. No app, toque em **"Conferir o Instagram"** para cada modo (DM e/ou Amigos Próximos).
   - **Liberou:** o app achou os botões daquele aparelho. Pode seguir.
   - **Bloqueou:** o app diz **qual botão faltou** naquele aparelho/versão/idioma. Isso é o comportamento certo — ele avisa em vez de errar. Anote a mensagem e mande para quem cuida do app; costuma ser um texto de idioma novo ou uma tela que a Acessibilidade não lê.
5. **Primeiro envio sempre só para @jvsgirao (você)**, para conferir que chega certo, antes de qualquer pessoa real.

## Veredito

Não prometa "funciona em todo Android". Prometa isto, que é verdade: **em cada aparelho, o "Conferir o Instagram" diz na hora se funciona ali ou não.** A 0.2.0 aumenta muito a chance de funcionar num celular/versão/idioma novos sem precisar de nova medição manual, e mantém a trava de segurança: sem conferir, não opera; e se algo mudar, ele bloqueia e avisa em vez de mandar errado.
