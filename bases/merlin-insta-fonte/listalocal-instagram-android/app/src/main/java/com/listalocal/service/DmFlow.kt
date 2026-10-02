package com.listalocal.service

import android.util.Log
import com.listalocal.core.followers.Follower
import com.listalocal.core.ig.Evidence
import com.listalocal.core.ig.Evidence.Conversa
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.state.DmEvent
import com.listalocal.core.state.DmState
import com.listalocal.core.state.DmStateMachine
import com.listalocal.core.tree.UiNode

/**
 * Modo C: uma mensagem direta a uma pessoa, conversa 1:1 por construcao.
 * Caminho medido na rodada 1 (INSTAGRAM-APP-REAL.md, 7.2; TESTE-ENVIO-REAL.md):
 * o link https://ig.me/m/<username> abre a conversa certa em ~1,6 s, sem busca,
 * sem homonimos e sem como virar grupo. "Nova mensagem" NAO e usada para enviar:
 * la, tocar numa segunda pessoa vira previa de grupo sem confirmacao.
 *
 *  0. sair de qualquer conversa aberta (um link que falha nao pode deixar a
 *     conversa anterior na frente);
 *  1. abrir a conversa. Com @ que nao existe o Instagram nao faz nada: sem
 *     conversa em 5 s, com a tela de antes legivel na frente = NAO_ENCONTRADO;
 *     sem nada legivel do Instagram na frente (a conversa pode ter aberto numa
 *     janela que o app nao le) = FALHA [NAO_LIDA], que a retomada tenta de novo;
 *  2. provar a conversa: o @ exato no titulo ou no subtitulo do cabecalho
 *     (o subtitulo troca o @ por "Online agora"; na "Conversa comercial" o @
 *     vai no titulo) ou no cartao do topo; sem o @ no prazo, o nome da pessoa
 *     no titulo com o subtitulo mostrando so um estado. Sem prova, FALHA e
 *     nada e escrito;
 *     A conversa abre logo que a anterior confirmou; o intervalo do dono
 *     corre aqui ([vez]), com a conversa provada e nada escrito, e a tela e
 *     lida de novo depois dele;
 *  3. recusar o que ja esta na tela: restricao, pedido de parar, "voces nao
 *     se seguem", rascunho no campo, mensagens temporarias, mensagem nao
 *     enviada antiga; e o pedido de parar no historico, rolando a conversa para
 *     tras. Sem a lista de mensagens na leitura, nada disso foi conferido: FALHA;
 *  4. tocar no campo, escrever o texto inteiro de uma vez (sem digitacao
 *     simulada) e conferir o campo e o botao Enviar habilitado;
 *  5. gravar "vou enviar" no disco (commit sincrono) ANTES do toque;
 *  6. tocar Enviar uma vez;
 *  7. evidencia: o botao Enviar some (campo vazio) e a bolha com o texto
 *     aparece, sem marcador de nao enviada. Sem evidencia: INCERTO, que nunca
 *     e reenviado.
 */
class DmFlow(
    private val tela: Tela,
    private val prof: SelectorProfile,
    private val registro: Registro,
) {
    data class Resultado(
        val desfecho: Desfecho,
        val motivo: String,
        /** Aviso de restricao do Instagram na tela: a fila pausa e so o dono retoma. */
        val restricao: Boolean,
        val trilha: List<DmState>,
        /** Relogio do ultimo toque em Enviar (null = nao tocou): o intervalo do dono conta dali. */
        val tocouEm: Long? = null,
        /** Numeros das mensagens em que Enviar foi tocado (as confirmadas e, no INCERTO, a ultima). */
        val enviadas: List<Int> = emptyList(),
        /**
         * A conversa foi aberta, provada e lida, e uma regra recusou o envio (mensagens temporarias, rascunho no
         * campo, mensagem nao enviada antiga, nome da caixa diferente). Nao e tela nao conferida: a fila nao conta
         * para a parada de 3.
         */
        val recusada: Boolean = false,
    )

    /**
     * O desfecho gravado quando o ultimo [enviar] foi interrompido (Parar, prazo, servico religado) depois de algo
     * escrito ou tocado; null = nada gravado. A fila o poe no relatorio antes de repassar a parada.
     */
    var interrompida: Resultado? = null
        private set

    /** Uma mensagem so (a conferencia real e os testes da 0.1.1). */
    suspend fun enviar(
        pessoa: Follower,
        texto: String,
        exigirNome: Boolean = false,
        vez: suspend () -> Boolean = { false },
        aceitarTemporaria: Boolean = false,
    ): Resultado = enviar(pessoa, listOf(texto), exigirNome, vez, aceitarTemporaria)

    /**
     * [textos]: as mensagens desta pessoa, na ordem (uma no revezamento, ate 3
     * na sequencia). A prova do @ e feita uma vez, antes da primeira. Cada
     * seguinte so e escrita depois da anterior confirmada na conversa e de
     * conferir que a conversa na frente continua a mesma; entre elas, so a
     * espera da confirmacao (o intervalo do dono e entre pessoas). Uma falha
     * ou falta de confirmacao para a pessoa ali: as seguintes nunca saem.
     *
     * [numeros]: o numero de cada texto na operacao (1 a 3), para o registro.
     * [nossos]: todas as mensagens da operacao, que nunca contam como pedido
     * de parar ("responda PARE" numa mensagem nossa de antes).
     *
     * [exigirNome]: a conversa veio da caixa de entrada; o nome no cabecalho da
     * conversa aberta pelo @ tem de ser o da conversa tocada ([pessoa].name).
     * Um grupo (ou um @ lido errado) abre outra conversa e para aqui.
     *
     * [vez]: chamada com a conversa aberta e provada, antes de escrever. Espera
     * o intervalo do dono desde o ultimo envio; true = esperou (a tela e lida
     * de novo). Lanca [Adiado] em Pausar ou Parar: sem desfecho.
     *
     * [aceitarTemporaria]: opcao EXPLICITA (desligada por padrao) para enviar
     * numa conversa com mensagens temporarias (a mensagem some depois de vista).
     */
    suspend fun enviar(
        pessoa: Follower,
        textos: List<String>,
        exigirNome: Boolean = false,
        vez: suspend () -> Boolean = { false },
        aceitarTemporaria: Boolean = false,
        numeros: List<Int> = textos.indices.map { it + 1 },
        nossos: List<String> = textos,
        /**
         * Pausar ou Parar pedidos (a fila liga ao AutomationController). Antes do commit, sem mensagem confirmada, uma
         * falha com um deles pedido vira [Adiado]: o toque no campo e a escrita sao recusados pela pausa (NodeOps), e
         * com a tela bloqueada a conversa nem aparece. Nada saiu: sem desfecho, a mesma pessoa depois.
         */
        parado: () -> Boolean = { false },
    ): Resultado {
        require(textos.isNotEmpty() && numeros.size == textos.size)
        interrompida = null
        var m = DmStateMachine()
        val trilha = mutableListOf<DmState>()
        val u = pessoa.username
        var restricao = false
        var escreveu: UiNode? = null
        var tocouEm: Long? = null
        // Mensagens confirmadas na conversa; [tocou] = Enviar tocado na mensagem corrente.
        var confirmadas = 0
        var tocou = false
        var recusada = false

        fun fim(evento: DmEvent, motivo: String, adiavel: Boolean = true): Resultado {
            // Antes: FALHA "nao consegui escrever no campo" para quem nao recebeu nada, pulada tambem na retomada.
            if (adiavel && confirmadas == 0 && m.state < DmState.COMMIT &&
                (evento == DmEvent.Inesperado || evento == DmEvent.NaoEncontrado) && parado()
            ) throw Adiado()
            m.transition(evento)
            var d = Desfecho.de(m.state)
            var mot = motivo
            if (confirmadas > 0 && d != Desfecho.ENVIADO && d != Desfecho.INCERTO) {
                // Sequencia parada antes de tocar na mensagem corrente: as anteriores chegaram.
                d = Desfecho.ENVIADO
                mot = "$confirmadas de ${textos.size} mensagens; parou: $motivo"
            } else if (confirmadas > 0 && d == Desfecho.INCERTO) {
                mot = "$confirmadas de ${textos.size} mensagens confirmadas; a ${confirmadas + 1}ª: $motivo"
            }
            val enviadas = numeros.take(confirmadas + if (tocou && d != Desfecho.ENVIADO) 1 else 0)
            registro.gravar(u, d, mot, enviadas)
            return Resultado(d, mot, restricao, trilha + m.trail, tocouEm, enviadas, recusada)
        }

        /** Recusa por regra, com a conversa lida e conferida (ver [Resultado.recusada]). */
        fun recusa(motivo: String): Resultado {
            recusada = true
            return fim(DmEvent.Inesperado, motivo)
        }

        try {
            m.transition(DmEvent.Abrir)
            val p = abrirEProvar(pessoa)
            if (p.erro != null) return fim(if (p.naoEncontrado) DmEvent.NaoEncontrado else DmEvent.Inesperado, p.erro)
            val root = p.root!!
            when (p.conversa) {
                Conversa.PROVADA -> m.transition(DmEvent.ConversaProvada)
                Conversa.INDISPONIVEL -> return fim(DmEvent.Indisponivel, "a conta não recebe mensagens")
                Conversa.NAO_ABERTA -> return fim(DmEvent.Inesperado, "a conversa fechou antes da prova do @")
                Conversa.SEM_PROVA -> return fim(DmEvent.Inesperado, "o @ da pessoa não apareceu na conversa")
            }
            val titulo = Evidence.tituloDaConversa(root, prof)
            if (exigirNome && !Evidence.mesmoNome(titulo, pessoa.name)) {
                return recusa("a conversa do @ não é a da caixa de entrada (grupo?)")
            }
            // A vez da pessoa: o intervalo do dono corre com a conversa aberta.
            // Depois dele, tudo abaixo e decidido sobre uma leitura nova (uma so podia vir vazia
            // no instante em que o Instagram redesenha: espera a mesma conversa).
            val lida = if (vez()) {
                tela.esperarAte(PROVA_MS) { mesmaConversa(it, u, titulo) }
                    ?: return fim(DmEvent.Inesperado, "a conversa saiu da frente durante o intervalo")
            } else {
                root
            }
            // Pedido de parar, "voces nao se seguem" e mensagem nao enviada sao lidos nas
            // mensagens: sem a lista delas na leitura, nada disso foi conferido e nada e escrito.
            var aberta = lida.takeIf { Evidence.temMensagens(it, prof) }
                ?: tela.esperarAte(PROVA_MS) { mesmaConversa(it, u, titulo) && Evidence.temMensagens(it, prof) }
                ?: return fim(DmEvent.Inesperado, "as mensagens da conversa não foram lidas (o pedido de parar não pôde ser conferido)")

            // 3. O que ja esta na tela.
            if (Evidence.restricao(aberta, prof)) {
                restricao = true
                return fim(DmEvent.Inesperado, "aviso de restrição do Instagram na conversa")
            }
            if (Evidence.pediuParaParar(aberta, prof, textos.first(), u, nossos)) {
                return fim(DmEvent.PediuParaParar, "pediu para parar nesta conversa")
            }
            if (Evidence.naoSegue(aberta, prof)) return fim(DmEvent.NaoSegue, "a conversa diz que vocês não se seguem")
            if (Evidence.textoDoCampo(aberta, prof).isNotEmpty()) {
                // Escrever por cima apagaria o rascunho do dono, e com texto no campo a dica de mensagens temporarias some.
                return recusa("o campo da conversa já tem texto (um rascunho); confira à mão")
            }
            if (!aceitarTemporaria && Evidence.temporaria(aberta, prof)) {
                return recusa("mensagens temporárias ligadas nesta conversa (a mensagem some depois de vista)")
            }
            if (Evidence.falhaDeEnvio(aberta, prof, nossos)) {
                // Um marcador antigo esconderia uma falha nova: confira a mao.
                return recusa("há mensagem não enviada nesta conversa; confira à mão")
            }
            val h = historico(aberta, u, titulo, textos.first(), nossos, vez)
            if (h.pediu) return fim(DmEvent.PediuParaParar, "pediu para parar nesta conversa (numa mensagem mais antiga)")
            aberta = h.fim ?: return fim(DmEvent.Inesperado, "o histórico da conversa não pôde ser conferido (pedido de parar)")

            for ((i, texto) in textos.withIndex()) {
                if (i > 0) {
                    // A seguinte: a anterior confirmou; a mesma conversa tem de continuar na frente.
                    trilha += m.trail
                    m = DmStateMachine().also { it.transition(DmEvent.Abrir); it.transition(DmEvent.ConversaProvada) }
                    tocou = false
                    escreveu = null
                    aberta = tela.esperarAte(PROVA_MS) { mesmaConversa(it, u, titulo) }
                        ?: return fim(DmEvent.Inesperado, "a conversa saiu da frente antes da mensagem ${i + 1}")
                    if (Evidence.restricao(aberta, prof)) {
                        restricao = true
                        return fim(DmEvent.Inesperado, "aviso de restrição do Instagram na conversa")
                    }
                }

                // 4. Texto inteiro e conferencia.
                val campo = Evidence.campo(aberta, prof)
                    ?: return fim(DmEvent.Inesperado, "campo de mensagem não encontrado")
                tela.tocar(campo)
                if (!tela.escrever(campo, texto)) return fim(DmEvent.Inesperado, "não consegui escrever no campo")
                escreveu = campo
                var conferida: UiNode? = null
                var enviar: UiNode? = null
                val fimConferir = tela.agora() + CONFERIR_MS
                while (tela.agora() < fimConferir) {
                    val r = tela.ler()
                    if (r != null && mesmaConversa(r, u, titulo)) {
                        if (Evidence.naoSegue(r, prof)) return fim(DmEvent.NaoSegue, "a conversa diz que vocês não se seguem")
                        if (Evidence.textoDoCampo(r, prof) == Evidence.norm(texto)) {
                            enviar = Evidence.botaoEnviar(r, prof)
                            if (enviar != null) { conferida = r; break }
                        }
                    }
                    tela.esperarMudanca(fimConferir)
                }
                if (conferida == null || enviar == null) {
                    return fim(DmEvent.Inesperado, "o texto no campo ou o botão Enviar não conferiu")
                }
                m.transition(DmEvent.TextoConferido)
                val antes = Evidence.contarMensagem(conferida, prof, texto)

                // 5-6. Commit sincrono e um toque.
                if (!registro.marcarCommit(u)) return fim(DmEvent.Inesperado, "não consegui gravar o \"vou enviar\"")
                m.transition(DmEvent.Commit)
                // Recusado antes de acontecer (o Instagram saiu da frente: ligacao, alarme, Home, tela bloqueada): nada
                // foi tocado. Antes, sem a conversa legivel de volta em 10 s, virava INCERTO, bloqueado para sempre, e
                // na 1a pessoa parava a operacao ("confira se a mensagem chegou").
                val tocouEnviar = tela.tocarEnviar(enviar)
                    ?: return fim(DmEvent.ToqueRecusado, "o toque em Enviar foi recusado antes de acontecer (o Instagram saiu da frente); nada saiu")
                if (!tocouEnviar) {
                    // Recusado (outro app por cima, a janela sem raiz naquele instante). Com o Instagram de volta, o
                    // mesmo texto no campo, o Enviar ainda la e nenhuma bolha nova: nada saiu (FALHA, volta numa
                    // operacao futura). Sem essa prova, INCERTO: o toque pode ter acontecido.
                    val r = tela.esperarAte(EVIDENCIA_MS) { mesmaConversa(it, u, titulo) }
                    val naoSaiu = r != null && Evidence.textoDoCampo(r, prof) == Evidence.norm(texto) &&
                        Evidence.botaoEnviar(r, prof) != null && Evidence.contarMensagem(r, prof, texto) <= antes
                    return if (naoSaiu) fim(DmEvent.ToqueRecusado, "o toque em Enviar foi recusado; o texto ficou no campo e nada saiu")
                    else fim(DmEvent.Inesperado, "o toque em Enviar foi recusado")
                }
                tocou = true
                tocouEm = tela.agora()

                // 7. Evidencia, so na conversa da pessoa.
                val falhou = evidencia(u, titulo, texto, antes, nossos)
                if (falhou != null) {
                    if (falhou == RESTRICAO_DEPOIS) restricao = true
                    return fim(DmEvent.Inesperado, falhou)
                }
                confirmadas++
                if (i == textos.lastIndex) return fim(DmEvent.Enviado, "")
                m.transition(DmEvent.Enviado)
            }
            error("sem mensagens")
        } catch (e: Adiado) {
            throw e // nada escrito: sem desfecho, a fila tenta de novo
        } catch (e: Exception) {
            // Cancelamento, prazo, permissao ou servico religado no meio do passo. Antes de escrever, nada aconteceu
            // na conversa: sem desfecho, e a retomada tenta a pessoa de novo (um Parar ao abrir nao a pula para
            // sempre). Com texto escrito, FALHA; depois do commit, INCERTO. E repassa.
            if (!m.state.isTerminal && (escreveu != null || confirmadas > 0 || m.state == DmState.COMMIT)) {
                interrompida = fim(DmEvent.Inesperado, "interrompido: ${e.message ?: "parada"}", adiavel = false)
            }
            throw e
        } finally {
            // Nao deixa rascunho: sem envio confirmado, o texto nao fica no campo
            // para um toque acidental depois (se o envio aconteceu, o campo ja esta vazio).
            if (m.state != DmState.ENVIADO) escreveu?.let { runCatching { tela.escrever(it, "") } }
            runCatching { tela.sairDaConversa(prof) }
        }
    }

    /**
     * Passo 7 de uma mensagem, so na conversa da pessoa: se outra conversa vier
     * para a frente (o dono mexendo no aparelho), o mesmo texto la nao prova
     * nada. Enviou = o botao Enviar some (campo vazio) e a bolha com o texto
     * aparece e assenta. null = confirmada; senao, o motivo.
     */
    private suspend fun evidencia(u: String, titulo: String?, texto: String, antes: Int, nossos: List<String>): String? {
        val fimEvidencia = tela.agora() + EVIDENCIA_MS
        while (tela.agora() < fimEvidencia) {
            tela.esperarMudanca(fimEvidencia)
            val r = tela.ler() ?: continue
            if (Evidence.restricao(r, prof)) return RESTRICAO_DEPOIS
            if (!mesmaConversa(r, u, titulo)) continue
            if (Evidence.falhaDeEnvio(r, prof, nossos)) return "o Instagram marcou a mensagem como não enviada"
            if (Evidence.contarMensagem(r, prof, texto) > antes && Evidence.textoDoCampo(r, prof).isEmpty()) {
                // A bolha pode ser o eco local: espera assentar e confere de novo.
                tela.esperar(ASSENTAR_MS)
                val depois = tela.ler()
                if (depois == null || !mesmaConversa(depois, u, titulo) || Evidence.falhaDeEnvio(depois, prof, nossos) ||
                    Evidence.contarMensagem(depois, prof, texto) <= antes
                ) {
                    return "a mensagem apareceu e depois ficou sem confirmação"
                }
                return null
            }
        }
        return "sem evidência de envio no prazo"
    }

    /** [pediu] = um pedido de parar mais antigo que a tela; [fim] = a conversa de volta ao fim (null = nao conferido). */
    private class Historico(val pediu: Boolean = false, val fim: UiNode? = null)

    /**
     * O pedido de parar mais antigo que a tela: a lista de mensagens (RecyclerView) so traz as bolhas visiveis, e o
     * registro do app so sabe dos pedidos que ele mesmo leu. Rola a conversa para tras uma tela por vez, conferindo
     * cada leitura, ate o comeco (a rolagem nao anda) ou [HISTORICO_TELAS] telas; e volta ao fim antes de escrever
     * (a bolha nova e contada no fim). Rolagem recusada: [vez] adia a pessoa se foi Pausar ou Parar; senao, nao conferido.
     * ponytail: so as ultimas HISTORICO_TELAS telas; um "pare" mais antigo que isso, so pela lista "Nao enviar para".
     */
    private suspend fun historico(
        aberta: UiNode, u: String, titulo: String?, texto: String, nossos: List<String>, vez: suspend () -> Boolean,
    ): Historico {
        var r = aberta
        var voltas = 0
        try {
            while (voltas < HISTORICO_TELAS) {
                val lista = prof.nos(r, "message_list").firstOrNull() ?: break
                if (!tela.rolarParaTras(lista)) break
                voltas++
                tela.esperarEvento(POLL_MS.toLong())
                var pediu = false
                r = assentada(u, titulo) { if (Evidence.pediuParaParar(it, prof, texto, u, nossos)) pediu = true } ?: return Historico()
                if (pediu) return Historico(pediu = true)
            }
            if (voltas == 0) return Historico(fim = aberta)
            // De volta ao fim, conferido: a rolagem para a frente so para (false) no fim. Sem chegar la, a bolha nova
            // seria contada contra uma tela do meio: nao conferido, nada escrito.
            val lista = prof.nos(r, "message_list").firstOrNull() ?: return Historico()
            var noFim = false
            for (i in 0 until 2 * voltas + 2) {
                if (!tela.rolar(lista)) { noFim = true; break }
                tela.esperarEvento(POLL_MS.toLong())
            }
            if (!noFim) return Historico()
            return Historico(fim = assentada(u, titulo) {})
        } catch (_: Fila.FailedSafe) {
            vez() // Pausar ou Parar recusam a rolagem: adia a pessoa, sem desfecho
            return Historico()
        }
    }

    /**
     * A conversa depois de uma rolagem, assentada: duas leituras seguidas com as mesmas bolhas (a rolagem anima), ou
     * [PROVA_MS]. [cada] ve cada leitura no caminho. null = a conversa nao voltou a frente.
     */
    private suspend fun assentada(u: String, titulo: String?, cada: (UiNode) -> Unit): UiNode? {
        var antes: List<String>? = null
        var ultima: UiNode? = null
        val fim = tela.agora() + PROVA_MS
        while (true) {
            val r = tela.ler()?.takeIf { mesmaConversa(it, u, titulo) && Evidence.temMensagens(it, prof) }
            if (r != null) {
                cada(r)
                val bolhas = prof.nos(r, "message_list").flatMap { l -> l.walk().flatMap { it.labels().asSequence() }.toList() }
                if (bolhas == antes) return r
                antes = bolhas
                ultima = r
            }
            if (tela.agora() >= fim) return ultima
            if (!tela.esperarEvento(minOf(POLL_MS.toLong(), fim - tela.agora()).coerceAtLeast(1)) && r != null) return r
        }
    }

    /** Passos 0 a 2 sem nada escrito. [erro] != null: parou antes da prova. */
    class Prova(val conversa: Conversa, val root: UiNode?, val erro: String? = null, val naoEncontrado: Boolean = false)

    /**
     * Passos 0 a 2: sai da conversa aberta, abre a do @ pelo link e prova o @.
     * Nada e escrito. O [enviar] parte daqui; a conferencia real (androidTest) tambem.
     */
    suspend fun abrirEProvar(pessoa: Follower): Prova {
        val u = pessoa.username
        // 0. Nenhuma conversa na frente antes do link.
        if (!tela.sairDaConversa(prof)) return Prova(Conversa.NAO_ABERTA, null, "não consegui sair da conversa anterior")
        if (!tela.abrirConversa(u)) return Prova(Conversa.NAO_ABERTA, null, "o link da conversa não abriu o Instagram")

        // 1. A conversa (cabecalho ou campo) em ate ABRIR_MS, por busca de id; a
        // arvore inteira so e lida quando ela aparece. Com @ que nao existe o
        // Instagram nao faz nada: NAO_ENCONTRADO.
        var root: UiNode = tela.esperarIds(ABRIR_MS, prof.conversaIds) ?: run {
            Log.w(TAG, "a conversa do link nao apareceu: ${tela.diagnostico()}")
            // Com @ que nao existe o Instagram fica onde estava: uma tela dele, legivel. Nada legivel do Instagram na
            // frente (a conversa numa janela sem raiz, outro app por cima) nao prova que a conta nao existe.
            val r = tela.ler() ?: return Prova(Conversa.NAO_ABERTA, null, NAO_LIDA)
            // Uma conversa na frente sem os ids que o app conhece (outra versao do Instagram: o campo pela forma) abriu
            // e nao foi lida: nao prova que o @ nao existe. Antes, cada pessoa virava "Conta nao encontrada".
            if (prof.nos(r, "composer").isNotEmpty()) return Prova(Conversa.NAO_ABERTA, null, NAO_RECONHECIDA)
            return Prova(Conversa.NAO_ABERTA, null, "a conversa não abriu em ${ABRIR_MS / 1000} s (o @ pode não existir)", naoEncontrado = true)
        }

        // 2. A prova do @ em ate PROVA_MS: o subtitulo alterna o @ com "Online
        // agora" e o cartao do topo carrega depois do resto.
        var conversa = Evidence.conversa(root, prof, u)
        val fimProva = tela.agora() + PROVA_MS
        while (conversa != Conversa.PROVADA && conversa != Conversa.INDISPONIVEL && tela.agora() < fimProva) {
            tela.esperarMudanca(fimProva)
            // Uma leitura vazia ou sem a conversa (janela em transicao) nao desfaz o que ja foi lido: segue ate o prazo
            // com a ultima leitura boa. A escrita e o commit so acontecem sobre leituras novas da mesma conversa.
            val r = tela.ler()?.takeIf { Evidence.temConversa(it, prof) } ?: continue
            conversa = Evidence.conversa(r, prof, u)
            root = r
        }
        // Sem o @ no prazo: a linha do nome, se o subtitulo so mostra um estado.
        if (conversa == Conversa.SEM_PROVA && Evidence.nomeProva(root, prof, pessoa.name)) conversa = Conversa.PROVADA
        return Prova(conversa, root)
    }

    /**
     * A mesma conversa continua na frente: o nome do cabecalho igual ao da
     * prova (o subtitulo alterna entre o @ e "Online agora"), ou a prova de novo.
     */
    private fun mesmaConversa(r: UiNode, u: String, titulo: String?): Boolean =
        Evidence.temConversa(r, prof) && (
            (titulo != null && Evidence.tituloDaConversa(r, prof) == titulo) ||
                Evidence.conversa(r, prof, u) == Conversa.PROVADA
            )

    /**
     * Conferencia do modo C: abre o Instagram, vai ao Direct, le a conta aberta
     * no titulo e abre "Nova mensagem" so para ver se o servico enxerga a
     * ModalActivity (a janela das conversas). Nao escreve, nao toca em pessoa
     * nenhuma e volta. As chaves da conversa (cabecalho, campo, Enviar) sao
     * conferidas em cada pessoa, antes de escrever.
     */
    suspend fun conferir(
        soConta: Boolean = false,
        /**
         * Auto-calibracao: recebe a arvore de cada tela navegada para aprender os papeis (so leitura) e os papeis
         * que NAO podem ser aprendidos nela (o "Para:" so na Nova mensagem).
         */
        aprender: ((UiNode, Set<String>) -> Unit)? = null,
    ): Pair<Map<String, Boolean>, String?> {
        val achou = LinkedHashMap<String, Boolean>()
        // Busca de id direta so faz sentido para o id conhecido; com o perfil aprendido/ao vivo,
        // a resolucao por papel (Evidence/prof.nos) acha o no mesmo com id diferente.
        val para = prof.idFor("new_chat_to").orEmpty()
        // Recusado (pausado: tela bloqueada, ligacao): nada de Voltar noutra tela.
        if (!tela.abrirInstagram()) return achou to null
        var root = comAbas()
        // A tela de partida pode ser qualquer uma (o perfil de outra pessoa, a lista de seguidores): os titulos de conta
        // e a lista so sao aprendidos nas telas provadas ([SO_NA_CAIXA], [SO_NO_PERFIL]).
        root?.let { aprender?.invoke(it, SO_NA_NOVA_MENSAGEM + SO_NA_CAIXA + SO_NO_PERFIL) }
        // O titulo do perfil PROPRIO (o caminho de "Seus seguidores": FonteSeguidores le a conta no perfil e na lista so
        // por esse id). Sem aprende-lo aqui, num Instagram de ids diferentes a lista de seguidores nunca abria so com a
        // conferencia DM. So a aba Perfil: ninguem e tocado.
        if (aprender != null && root != null && aprenderNoPerfil(root, aprender)) root = comAbas()
        val direct = root?.let { prof.nos(it, "direct_tab").firstOrNull() ?: Evidence.porRotulo(it, prof, "direct_tab") }
        achou["direct_tab"] = direct != null
        if (direct == null) return achou to null
        tela.tocar(direct)
        // A conta so na caixa de entrada provada ("Nova mensagem" ou a aba Direct marcada), nunca na 1a leitura depois
        // do toque (ainda a tela de antes). Na operacao, so pelo id; na conferencia, sem o id conhecido na tela (outra
        // versao), pela forma do titulo, e a conferencia aprende o id dele aqui: sem isso nunca aprendia e o modo DM
        // ficava bloqueado para sempre num Instagram de ids diferentes.
        fun contaNa(r: UiNode) = Evidence.conta(r, prof) ?: if (soConta) null else Evidence.contaPelaForma(r)
        val inbox = tela.esperarAte(8_000) { naCaixa(it) && contaNa(it) != null } ?: tela.ler()?.takeIf(::naCaixa)
        inbox?.let { aprender?.invoke(it, SO_NA_NOVA_MENSAGEM + SO_NO_PERFIL) }
        val conta = inbox?.let(::contaNa)
        achou["inbox_title"] = conta != null
        if (soConta) return achou to conta
        val nova = inbox?.let { Evidence.porRotulo(it, prof, "new_message") }
        achou["new_message"] = nova != null
        if (nova == null) return achou to conta
        tela.tocar(nova)
        // O "Para:" so vale numa leitura que ja nao e a atividade principal (caixa de entrada, barra de abas): ali,
        // uma nota ou musica de nota no topo ("Para quem vai...", "To the Moon") casa a assinatura, e a conferencia
        // liberava o modo DM sem nunca ter lido a Nova mensagem (a janela das conversas).
        fun naNovaMensagem(r: UiNode) = !naPrincipal(r) && (r.exists(para) || prof.noPorAssinatura(r, "new_chat_to") != null)
        val comPara = tela.esperarAte(6_000, ::naNovaMensagem)
        comPara?.let { aprender?.invoke(it, SO_NA_CAIXA + SO_NO_PERFIL) }
        achou["new_chat_to"] = comPara != null
        // Volta sem escrever: o 1o Voltar fecha o teclado, o 2o a tela.
        for (i in 0 until 3) {
            val r = tela.ler() ?: break
            if (!naNovaMensagem(r)) break
            tela.voltar()
            tela.esperar(POLL_MS.toLong())
        }
        return achou to conta
    }

    /** Uma tela com a barra de abas (a aba Direct), voltando ate ela (de uma conversa, da lista de seguidores). */
    private suspend fun comAbas(): UiNode? {
        for (i in 0 until 4) {
            val r = tela.esperarAte(4_000) { prof.nos(it, "direct_tab").isNotEmpty() || Evidence.porRotulo(it, prof, "direct_tab") != null }
            if (r != null) return r
            tela.voltar()
        }
        return null
    }

    /**
     * Toca a aba Perfil e aprende o titulo no perfil PROPRIO provado (a aba Perfil marcada e o menu Opcoes, como
     * CloseFriendsFlow.abrir): o perfil de outra pessoa tambem tem titulo, e a 1a leitura depois do toque ainda e a
     * tela de antes. true = tocou (a tela mudou).
     */
    private suspend fun aprenderNoPerfil(root: UiNode, aprender: (UiNode, Set<String>) -> Unit): Boolean {
        val aba = prof.nos(root, "profile_tab").firstOrNull() ?: Evidence.porRotulo(root, prof, "profile_tab") ?: return false
        if (!tela.tocar(aba)) return false
        tela.esperarAte(6_000) { r -> Evidence.porRotulo(r, prof, "options") != null && prof.nos(r, "profile_tab").any { it.isSelected } }
            ?.let { aprender(it, SO_NA_NOVA_MENSAGEM + SO_NA_CAIXA) }
        return true
    }

    /** A caixa de entrada na frente: o botao "Nova mensagem" (o titulo da tela Nova mensagem nao e botao) ou a aba Direct marcada. */
    private fun naCaixa(r: UiNode): Boolean =
        Evidence.porRotulo(r, prof, "new_message")?.isClickable == true || prof.nos(r, "direct_tab").any { it.isSelected }

    /** A atividade principal: a lista da caixa de entrada (id) ou a barra de abas de baixo. */
    private fun naPrincipal(r: UiNode): Boolean =
        listOfNotNull(prof.idFor("inbox_list")).any(r::exists) || prof.nos(r, "direct_tab").isNotEmpty()

    /** A conta aberta no Instagram agora, na caixa de entrada (como a conferencia le). So leitura. */
    suspend fun contaAberta(): String? = conferir(soConta = true).second

    companion object {
        // Esperas fixas entre leituras da arvore: nao sao atraso sorteado.
        // Prazos por passo, no relogio (uma leitura lenta nao os estica).
        const val POLL_MS = 250
        /** ig.me abre a conversa em 1,5 a 2,7 s (rodada 1); sem ela em 5 s, NAO_ENCONTRADO. */
        const val ABRIR_MS = 5_000
        /** Com a conversa aberta, o @ (ou o nome) no cabecalho ou no cartao do topo. */
        const val PROVA_MS = 4_000
        const val CONFERIR_MS = 5_000
        const val EVIDENCIA_MS = 10_000
        const val ASSENTAR_MS = 1_500L
        /** Telas do historico conferidas para tras, no maximo, atras de um pedido de parar antigo. */
        const val HISTORICO_TELAS = 10
        private const val TAG = "ListaLocalIG"
        /** Papeis que a auto-calibracao so aprende na Nova mensagem (fora dela, o topo da caixa casa "Para ..."). */
        val SO_NA_NOVA_MENSAGEM = setOf("new_chat_to")
        /**
         * Papeis que a auto-calibracao so aprende na caixa de entrada provada: fora dela o titulo da conta e a lista
         * tem a forma de outras coisas ("15posts" e o @ da lista de seguidores; a lista de seguidores, a de sugestoes).
         */
        val SO_NA_CAIXA = setOf("inbox_title", "inbox_list")
        /** O titulo da conta no perfil: so no perfil proprio provado (na caixa, a forma dele casa o titulo da caixa). */
        val SO_NO_PERFIL = setOf("profile_title")
        private const val RESTRICAO_DEPOIS = "aviso de restrição depois do toque em Enviar"
        /** A conversa do link nao foi lida (nada do Instagram legivel na frente): FALHA que a retomada tenta de novo. */
        const val NAO_LIDA = "a conversa pode ter aberto, mas o app não conseguiu lê-la (nada do Instagram legível na frente)"
        /**
         * A conversa abriu com controles que o app nao conhece pelo id (outra versao do Instagram): nada escrito.
         * "Conferir o Instagram" nao abre conversa (tocaria numa pessoa), entao so aqui isso aparece. FALHA que a
         * retomada tenta de novo; tres seguidas param a fila.
         */
        const val NAO_RECONHECIDA = "a conversa abriu, mas o app não reconhece a tela dela nesta versão do Instagram"
    }
}
