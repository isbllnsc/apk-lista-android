package com.listalocal.service

import com.listalocal.core.ig.Evidence
import com.listalocal.core.match.ContactMatcher
import com.listalocal.core.match.MatchOutcome
import com.listalocal.core.match.MatchResult
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.selectors.SignatureLibrary
import com.listalocal.core.state.BroadcastEvent
import com.listalocal.core.state.BroadcastState
import com.listalocal.core.state.BroadcastStateMachine
import com.listalocal.core.tree.UiNode

/**
 * Modo A: montar a lista Amigos Proximos para o dono postar um story so para
 * ela. Espelha o Lista Local ("o app cria a lista e voce envia"): buscar o @,
 * so a linha EXATA e UNICA, um toque, conferir a marca; sem mudanca, nenhum
 * segundo toque e parada segura. A maquina do Lista Local agora dirige o fluxo
 * de verdade: evento fora de ordem = parada, nunca toque.
 *
 * O Instagram diz na tela: "Nao enviamos notificacoes quando voce edita sua
 * lista Amigos Proximos" (visto na rodada 1). Ninguem ve os outros da lista:
 * PRECISA VERIFICAR NO INSTAGRAM REAL.
 */
class CloseFriendsFlow(
    private val tela: Tela,
    private val prof: SelectorProfile,
    /**
     * Auto-calibracao: recebe a arvore de cada tela navegada e o conjunto de papeis
     * a NAO aprender nela (so leitura). Os papeis de linha/@ (cf_row, cf_username)
     * so entram na tela do seletor de Amigos Proximos; nas telas anteriores (Feed,
     * Perfil, Configuracoes) eles ficam de fora, senao uma lista de posts do Feed
     * roubaria o slot deles pela assinatura fraca. Ver Aprendiz.viu.
     */
    private val aprender: ((UiNode, Set<String>) -> Unit)? = null,
) {
    open class ParadaSegura(motivo: String) : Exception(motivo)

    /** A tela Amigos Proximos nao esta na frente no comeco de uma pessoa: nada foi escrito nem tocado. */
    class ForaDaTela : ParadaSegura(FORA_DA_TELA)

    var fsm = BroadcastStateMachine()
        private set
    private var marcados = 0

    /** Para abrir de novo do comeco (pausa no meio da abertura, tela perdida depois de uma pausa). */
    fun reiniciar() {
        fsm = BroadcastStateMachine()
        marcados = 0
    }

    /** Chaves vistas pelo caminho ate a tela (a conferencia mostra o que faltou). */
    val encontradas = LinkedHashMap<String, Boolean>()

    /** O @ da conta aberta, lido no titulo do perfil proprio. */
    var conta: String? = null
        private set

    /** "Conferir o Instagram" (so leitura), nao a operacao: so aqui a conta pode ser lida pela forma do titulo. */
    private var conferindo = false

    private fun passo(e: BroadcastEvent, seFalhar: String) {
        val s = fsm.transition(e)
        if (s in PARADAS) throw ParadaSegura(seFalhar)
    }

    private fun id(key: String) = prof.idFor(key).orEmpty()

    private suspend fun esperarAte(ms: Int, cond: (UiNode) -> Boolean) = tela.esperarAte(ms, cond)

    private fun porRotulo(root: UiNode, key: String) = Evidence.porRotulo(root, prof, key)

    /** No do papel por id (aprendido -> fixo) ou, sem id na tela, pela assinatura/rotulo ao vivo. Como o DmFlow resolve direct_tab. */
    private fun no(root: UiNode, key: String) = prof.nos(root, key).firstOrNull() ?: porRotulo(root, key)

    /**
     * Chega a Amigos Proximos pelo caminho medido (INSTAGRAM-APP-REAL.md, 3):
     * aba Perfil > Opcoes (menu) > Configuracoes e atividade > "Quem pode ver
     * seu conteudo" > Amigos Proximos (desc "Amigos Proximos, 162"). La: caixa
     * de marcar por linha, "Concluir" embaixo, "Limpar tudo" (NUNCA). So
     * navega: nao toca em nenhuma linha.
     */
    suspend fun abrir() {
        val achou = encontradas
        // Papeis de linha/@ so sao aprendidos na tela do seletor (tela2), nunca nas telas
        // anteriores (Feed/Perfil/Config), onde a assinatura fraca deles casaria um post.
        val semLinha = SignatureLibrary.ROTULO_PESSOAL
        // O titulo da conta so no perfil proprio provado (comOpcoes): na tela de partida (a lista de seguidores, o
        // perfil de outra pessoa) a forma do titulo casa o @ de outra conta.
        val semConta = semLinha + "profile_title"
        passo(BroadcastEvent.Start, "fluxo fora de ordem")
        // Recusado (pausado): nada de Voltar noutra tela.
        if (!tela.abrirInstagram()) { passo(BroadcastEvent.UiUnknown, "o Instagram não abriu"); return }
        var root: UiNode? = null
        for (i in 0 until 4) {
            // aba Perfil pelo papel (id ou assinatura ao vivo): sem isso, num aparelho de id
            // diferente a auto-calibracao nunca decolaria (profile_tab so se aprende clicando nele).
            root = esperarAte(4_000) { no(it, "profile_tab") != null }
            if (root != null) break
            tela.voltar()
        }
        root?.let { aprender?.invoke(it, semConta) }
        achou["profile_tab"] = root != null
        val perfil = root?.let { no(it, "profile_tab") }
        if (perfil == null) { passo(BroadcastEvent.UiUnknown, "aba Perfil não encontrada"); return }
        passo(BroadcastEvent.AppOpened, "fluxo fora de ordem")
        // Recusado (pausa, o Instagram fora da frente): nada adiante. A Fila espera a pausa e abre de novo.
        if (!tela.tocar(perfil)) { passo(BroadcastEvent.UiUnknown, "o toque na aba Perfil foi recusado"); return }

        // O perfil PROPRIO: a aba Perfil marcada. A 1a leitura depois do toque ainda e a tela de antes (o Feed, a
        // lista de Seguindo), e o perfil de outra pessoa tambem tem Opcoes e titulo: a conta seria a dele.
        val comOpcoes = esperarAte(6_000) { r -> porRotulo(r, "options") != null && prof.nos(r, "profile_tab").any { it.isSelected } }
        comOpcoes?.let { aprender?.invoke(it, semLinha) }
        achou["options"] = comOpcoes != null
        // A conta: pelo id; na conferencia, sem o id conhecido na tela (outra versao), pela forma do titulo, e a
        // conferencia aprende o id dele aqui (a operacao le so pelo id).
        conta = comOpcoes?.let { Evidence.conta(it, prof, "profile_title") ?: if (conferindo) Evidence.contaPelaForma(it, "profile_title") else null }
        achou["profile_title"] = conta != null
        val opcoes = comOpcoes?.let { porRotulo(it, "options") }
            ?: run { passo(BroadcastEvent.UiUnknown, "menu Opções não encontrado"); return }
        if (!tela.tocar(opcoes)) { passo(BroadcastEvent.UiUnknown, "o toque em Opções foi recusado"); return }

        // Opcoes abre "Configuracoes e atividade" (Bloks). Espera ESSA tela: o perfil
        // ainda na frente tem listas que rolam, e rolar o perfil nao leva a lugar nenhum.
        val config = esperarAte(8_000) { porRotulo(it, "settings") != null || porRotulo(it, "cf_entry") != null }
        config?.let { aprender?.invoke(it, semConta) }
        achou["settings"] = config != null
        if (config == null) { passo(BroadcastEvent.UiUnknown, "Configurações e atividade não abriu"); return }

        // Amigos Proximos fica em "Quem pode ver seu conteudo", umas 3 telas abaixo.
        // Ali nada rola nem aceita clique pela acessibilidade (t01a-t01d): arrasta
        // por gesto e le de novo com a tela parada.
        var item: UiNode? = porRotulo(config, "cf_entry")
        var i = 0
        while (item == null && i++ < ROLAGENS) {
            // Sem gesto (servico sem canPerformGestures), a rolagem da acessibilidade, se houver.
            val andou = tela.arrastar() ||
                tela.ler()?.walk()?.firstOrNull(UiNode::isScrollable)?.let(tela::rolar) == true
            if (!andou) break
            item = parada()?.let { porRotulo(it, "cf_entry") }
        }
        achou["cf_entry"] = item != null
        if (item == null) { passo(BroadcastEvent.UiUnknown, "item Amigos Próximos não encontrado"); return }
        // EXCECAO documentada ao "cliques por no": esta e a UNICA acao por gesto de pixel do
        // app (arrastar acima e tocar no ponto aqui), porque a tela Bloks "Configuracoes e
        // atividade" nao rola nem aceita ACTION_CLICK pela acessibilidade (t01a-t01d; ver
        // accessibility_service_config.xml). Rele o alvo de uma tela JA PARADA logo antes do
        // gesto (bounds frescos, nao os de uma leitura anterior) e reconfere que ainda e
        // Amigos Proximos, nunca "Limpar tudo". Depois do toque, so libera se o seletor
        // (cf_search) abrir: um toque no lugar errado nao abre nada e vira parada segura.
        // Sem releitura valida, nenhum toque: o no da leitura anterior tem a posicao de antes do embalo.
        val alvo = parada()?.let { porRotulo(it, "cf_entry") }
            ?: run { passo(BroadcastEvent.UiUnknown, "Amigos Próximos saiu da tela antes do toque"); return }
        naoLimpar(alvo)
        if (!tela.tocarNoPonto(alvo) && !tela.tocar(alvo)) {
            passo(BroadcastEvent.UiUnknown, "o toque em Amigos Próximos foi recusado"); return
        }

        // A busca e a moldura sao fixas; as linhas, o cabecalho "162 pessoas / Limpar tudo" e as Sugestoes vem do
        // servidor e chegam depois (a barra de abas de Seguidores tambem chegava depois das linhas: 14e3eb8). Antes, a
        // 1a arvore com a busca era julgada e aprendida: com a lista carregando, a conferencia nunca passava. Espera a
        // tela PROVADA com linhas e so mede (e aprende) numa leitura parada.
        val pronta = esperarAte(ABRIR_MS) { naTela(it) && prof.nos(it, "cf_row").isNotEmpty() }
        val tela2 = pronta?.let { assentada(it) } ?: tela.ler()
        val aberta = tela2 != null && naTela(tela2)
        if (aberta) aprender?.invoke(tela2!!, emptySet()) // a tela certa: aprende tambem cf_row/cf_username
        achou["cf_search"] = aberta
        achou["cf_done"] = tela2?.let { concluirDe(it) != null } ?: false
        // cf_row/cf_username: a operacao usa a linha e o @ para achar a pessoa e ler a marca
        // (marcar/linhasAmigos). Sem reconhece-los aqui, toda marcacao viria NAO_ENCONTRADO
        // calada; por isso entram no gate (CF_KEYS) e sao provados na tela.
        val linhas = tela2?.let { Evidence.linhasAmigos(it, prof) }.orEmpty()
        achou["cf_row"] = tela2?.let { prof.nos(it, "cf_row").isNotEmpty() } ?: false
        achou["cf_username"] = linhas.isNotEmpty()
        // "Limpar tudo" e a acao do cabecalho dos MEMBROS ("162 pessoas"): com membros na tela, o modo so libera se o
        // app o reconhece (naoLimpar depende dele). Com a lista vazia (so Sugestoes) o botao nao existe, e exigi-lo
        // bloqueava o modo justo para quem vai montar a lista do zero. naoLimpar continua em todo toque.
        achou["cf_clear"] = tela2?.let { prof.nos(it, "cf_clear").isNotEmpty() || (aberta && semMembros(it, linhas)) } ?: false
        if (!aberta) { passo(BroadcastEvent.UiUnknown, "a tela Amigos Próximos não abriu"); return }
        passo(BroadcastEvent.PickerOpened, "fluxo fora de ordem")
    }

    /**
     * A tela e a Amigos Proximos: a busca, o "Concluir" e o aviso (ou o titulo) de Amigos Proximos. A busca sozinha
     * (search_edit_text) tambem e a da Nova mensagem e a da folha de compartilhar (t05d, t12d reais), onde cada linha
     * com caixa e um destinatario; e a caixa "Pesquisar" de Configuracoes casa a assinatura da busca.
     */
    fun naTela(r: UiNode): Boolean =
        prof.nos(r, "cf_search").isNotEmpty() && concluirDe(r) != null &&
            (prof.nos(r, "cf_disclaimer").isNotEmpty() || porRotulo(r, "cf_entry") != null)

    /** A tela Amigos Proximos provada esta na frente (depois de uma pausa). */
    suspend fun naFrente(): Boolean = esperarAte(2_000) { naTela(it) } != null

    private fun concluirDe(r: UiNode): UiNode? = prof.nos(r, "cf_done").firstOrNull() ?: porRotulo(r, "cf_done")

    /** Lista sem membros: nenhuma linha marcada e nenhum cabecalho com numero ("162 pessoas"), so "Sugestoes". */
    private fun semMembros(r: UiNode, linhas: List<Evidence.Linha>): Boolean =
        linhas.none { it.marcada } && prof.nos(r, "cf_header").none { h -> h.labels().any { l -> l.any(Char::isDigit) } }

    /** A tela como ela esta: rotulo, posicao e marca de cada no. Duas leituras iguais = parou de chegar coisa. */
    private fun retrato(r: UiNode) =
        r.walk().map { listOf(it.viewIdResourceName, it.contentDescription ?: it.text, it.bounds, it.isChecked) }.toList()

    /** Le de novo ate duas leituras seguidas iguais ([ESTAVEL_MS] entre elas), no maximo [ASSENTAR_MS]. */
    private suspend fun assentada(primeira: UiNode): UiNode {
        var r = primeira
        val fim = tela.agora() + ASSENTAR_MS
        while (tela.agora() < fim) {
            tela.esperar(ESTAVEL_MS)
            val nova = tela.ler() ?: return r
            if (retrato(nova) == retrato(r)) return nova
            r = nova
        }
        return r
    }

    /**
     * A tela depois do arraste, ja parada: dois retratos seguidos com os mesmos rotulos E posicoes. So os rotulos
     * nao bastam: com linhas de 136 px, o embalo desliza a lista com os mesmos itens na tela, e o toque no ponto
     * caia no vizinho ("Privacidade da conta", "Posts cruzados").
     */
    private suspend fun parada(): UiNode? {
        var antes: List<Any?>? = null
        val fim = tela.agora() + PARADA_MS
        while (true) {
            tela.esperar(POLL_MS.toLong())
            val r = tela.ler() ?: return null
            val agora = retrato(r)
            if (agora == antes || tela.agora() >= fim) return r
            antes = agora
        }
    }

    /**
     * "Limpar tudo" tira as 162 pessoas da lista: nenhum toque nele, nem por
     * engano. Resolve o papel cf_clear pelo motor de sempre (id aprendido -> id
     * fixo -> assinatura ao vivo, aqui sobre a subárvore do alvo): num aparelho
     * onde o id/texto fixo não bate, a assinatura ainda reconhece o botão.
     */
    private fun naoLimpar(no: UiNode) {
        if (prof.nos(no, "cf_clear").isNotEmpty()) throw ParadaSegura("o alvo do toque seria \"Limpar tudo\"")
    }

    /**
     * Uma pessoa. [adicionar] = entrar na lista; false = sair (pediu para
     * parar). Devolve null so quando a busca assentada mostrou que nao havia
     * nada a fazer na remocao (a pessoa nao esta na lista). [Desfecho.FALHA] =
     * a busca nao assentou: nenhum toque.
     *
     * [parado] (Pausar ou Parar pedidos): antes de escrever e antes de tocar, ou com a escrita/o toque recusados por
     * eles (NodeOps), lanca [Adiado] sem nada tocado; a Fila espera a pausa e refaz a MESMA pessoa (a busca le a
     * marca de novo: refazer nao desfaz nada). Antes a recusa pela pausa virava "tela mudou" e a operacao parava.
     */
    suspend fun marcar(username: String, adicionar: Boolean, parado: () -> Boolean = { false }): Desfecho? {
        passo(if (fsm.state == BroadcastState.PAUSED) BroadcastEvent.Resumed else BroadcastEvent.SearchStarted, "fluxo fora de ordem")
        fun adiar(): Nothing {
            passo(BroadcastEvent.Paused, "fluxo fora de ordem")
            throw Adiado()
        }
        if (parado()) adiar()
        // A tela PROVADA, a cada pessoa (depois de uma pausa o dono pode ter aberto a Nova mensagem ou a folha de
        // compartilhar: a mesma busca, e cada linha com caixa e um destinatario). Fora dela nada e escrito: a Fila
        // reabre Amigos Proximos pelo Perfil, na mesma conta.
        val root = esperarAte(2_000) { naTela(it) } ?: run {
            fsm.transition(BroadcastEvent.UiUnknown)
            throw ForaDaTela()
        }
        val busca = prof.nos(root, "cf_search").first()
        if (!tela.escrever(busca, username)) {
            if (parado()) adiar()
            throw ParadaSegura("não consegui escrever na busca")
        }

        val match = buscar(username) ?: run {
            passo(BroadcastEvent.MatchNotFound, "fluxo fora de ordem")
            return Desfecho.FALHA
        }
        val linha = match.row
        when {
            match.outcome == MatchOutcome.NOT_FOUND -> {
                // Tambem na remocao: quem pediu para parar e nao aparece na busca fica no relatorio (Fila.amigos).
                passo(BroadcastEvent.MatchNotFound, "fluxo fora de ordem")
                return Desfecho.NAO_ENCONTRADO
            }
            match.outcome == MatchOutcome.AMBIGUOUS -> {
                passo(BroadcastEvent.MatchAmbiguous, "fluxo fora de ordem")
                return Desfecho.AMBIGUO
            }
            linha == null -> throw ParadaSegura("linha sem no")
            linha.marcada == adicionar -> {
                // Ja esta como deveria: nenhum toque.
                passo(BroadcastEvent.AlreadySelected, "fluxo fora de ordem")
                return if (adicionar) Desfecho.JA_NA_LISTA else null
            }
        }
        val alvo = linha!!
        naoLimpar(alvo.no)
        if (parado()) adiar()
        passo(BroadcastEvent.MatchExact(username), "fluxo fora de ordem")
        if (!tela.tocar(alvo.no)) {
            if (parado()) adiar()
            passo(BroadcastEvent.SelectionFailed, "o toque na linha foi recusado")
        }
        // So conta com a marca trocada. Sem evidencia, NUNCA um segundo toque
        // (desfaria o primeiro): parada segura.
        val mudou = esperarAte(MARCA_MS) { r ->
            ContactMatcher.match(username, Evidence.linhasAmigos(r, prof)).row?.marcada == adicionar
        }
        if (mudou == null) {
            passo(
                BroadcastEvent.SelectionFailed,
                "o toque em @$username não confirmou a marca: @$username pode ter " +
                    (if (adicionar) "ficado fora de" else "continuado em") +
                    " Amigos Próximos. Confira antes de Concluir (Concluir não foi tocado)",
            )
        }
        marcados++
        passo(BroadcastEvent.SelectionVerified(marcados), "fluxo fora de ordem")
        return if (adicionar) Desfecho.ADICIONADO else Desfecho.REMOVIDO
    }

    /** As linhas da ultima busca que assentou: ainda na tela, com o campo ja trocado, sao a resposta velha. */
    private var ultimaBusca: List<String>? = null

    /**
     * Espera a busca pelo @ ASSENTAR e so entao decide. Assentada = o campo mostra o @, nenhum cabecalho da lista
     * inicial ("162 pessoas" / "Limpar tudo" / "Sugestoes"), nada carregando, e as mesmas linhas (@, marca e
     * posicao) em duas leituras com [ESTAVEL_MS] entre elas. Na lista inicial os membros vem com a caixa DESMARCADA
     * na acessibilidade, embora marcados na tela (t02a real): decidir por ela tocava e TIRAVA da lista um amigo
     * proximo. "Nao achou" ainda espera [PISO_NAO_ACHOU_MS] e nao vale com as linhas da busca anterior (a busca e no
     * servidor: a resposta chega depois). null = nao assentou em [BUSCA_MS]: nenhum toque.
     */
    private suspend fun buscar(username: String): MatchResult? {
        val inicio = tela.agora()
        var visto: List<Any?>? = null
        var desde = 0L
        while (true) {
            val r = tela.ler()
            // Sem resposta ainda: acorda na proxima mudanca da tela. Resposta nova: a confirmacao e uma leitura NOVA
            // [ESTAVEL_MS] depois, haja evento ou nao. "Nao achou" parado: so a proxima mudanca ou o piso.
            var espera = ESTAVEL_MS
            var porEvento = true
            if (r != null) {
                val linhas = Evidence.linhasAmigos(r, prof)
                val retrato = linhas.map { Triple(it.username, it.marcada, it.no.bounds) }
                if (!filtrada(r, username) || !naTela(r)) {
                    visto = null
                } else if (retrato != visto) {
                    visto = retrato
                    desde = tela.agora()
                    porEvento = false
                } else if (tela.agora() - desde >= ESTAVEL_MS) {
                    val m = ContactMatcher.match(username, linhas)
                    val nomes = linhas.map { it.username }
                    val velha = nomes.isNotEmpty() && nomes == ultimaBusca
                    if (m.outcome != MatchOutcome.NOT_FOUND || (tela.agora() - inicio >= PISO_NAO_ACHOU_MS && !velha)) {
                        ultimaBusca = nomes
                        return m
                    }
                    espera = maxOf(ESTAVEL_MS, inicio + PISO_NAO_ACHOU_MS - tela.agora())
                }
            }
            val resta = inicio + BUSCA_MS - tela.agora()
            if (resta <= 0) return null
            if (porEvento) tela.esperarEvento(minOf(espera, resta)) else tela.esperar(minOf(espera, resta))
        }
    }

    /** A lista mostra a resposta da busca pelo @: o campo com ele, sem os cabecalhos da lista inicial e nada carregando. */
    private fun filtrada(r: UiNode, username: String): Boolean =
        prof.nos(r, "cf_search").any { Evidence.norm(it.text).removePrefix("@").equals(username, ignoreCase = true) } &&
            prof.nos(r, "cf_header").isEmpty() && prof.nos(r, "cf_clear").isEmpty() &&
            r.walk().none { it.className?.endsWith("ProgressBar") == true }

    /** Todos processados: a partir daqui so com a confirmacao do dono. */
    fun prontoParaConcluir() = passo(BroadcastEvent.AllProcessed, "fluxo fora de ordem")

    /** Toca "Concluir" uma vez e confere que a tela fechou. */
    suspend fun concluir() {
        passo(BroadcastEvent.UserConfirmed, "fluxo fora de ordem")
        // Depois da confirmacao no app, a tela de Amigos Proximos precisa voltar a frente.
        val root = esperarAte(8_000) { naTela(it) }
            ?: throw ParadaSegura("a tela Amigos Próximos não voltou para a frente")
        val botao = concluirDe(root)
            ?: run { passo(BroadcastEvent.UiUnknown, "botão Concluir não encontrado"); return }
        naoLimpar(botao)
        if (!tela.tocar(botao)) throw ParadaSegura("o toque em Concluir foi recusado")
        esperarAte(6_000) { !naTela(it) }
            ?: throw ParadaSegura("a tela Amigos Próximos não fechou depois de Concluir")
        passo(BroadcastEvent.Saved, "fluxo fora de ordem")
    }

    /** Conferencia: navega ate a tela e volta sem tocar em linha nem em Concluir. */
    suspend fun conferir(): Map<String, Boolean> {
        conferindo = true
        try {
            abrir()
        } catch (_: ParadaSegura) {
            // o que faltou fica em [encontradas]
        } finally {
            for (i in 0 until 3) {
                val r = tela.ler() ?: break
                if (r.exists(id("profile_tab")) && !r.exists(id("cf_search"))) break
                tela.voltar()
                runCatching { tela.esperar(400) }
            }
        }
        return encontradas.toMap()
    }

    companion object {
        const val POLL_MS = 200
        const val BUSCA_MS = 8_000
        /** "Nao achou" so depois disto: a busca e no servidor, e no 4G fraco a resposta demora (antes 1,2 s). */
        const val PISO_NAO_ACHOU_MS = 3_000
        /** Duas leituras iguais com isto entre elas: a lista parou. */
        const val ESTAVEL_MS = 400L
        /** Amigos Proximos abrir com linhas (vem do servidor) depois do toque no item. */
        const val ABRIR_MS = 8_000
        /** A tela aberta parar de mudar antes de ser medida. */
        const val ASSENTAR_MS = 2_000L
        const val FORA_DA_TELA = "a tela Amigos Próximos não está na frente (Concluir não foi tocado)"
        const val MARCA_MS = 2_000
        const val ROLAGENS = 12
        /** A tela parar depois do arraste (sem embalo); passado isso, le como estiver. */
        const val PARADA_MS = 2_500L
        private val PARADAS = setOf(
            BroadcastState.FAILED_SAFE, BroadcastState.UI_CHANGED, BroadcastState.CANCELLED,
            BroadcastState.PERMISSION_LOST, BroadcastState.VERSION_UNSUPPORTED,
        )
    }
}
