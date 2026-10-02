package com.listalocal.service

import android.util.Log
import com.listalocal.core.followers.Follower
import com.listalocal.core.ig.Evidence
import com.listalocal.core.ig.Listas
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.Bounds
import com.listalocal.core.tree.UiNode

/**
 * Uma pessoa achada ao vivo. [username] null = conversa sem @ legivel: nunca
 * recebe. [motivo] nao vazio = fica de fora por isso.
 */
data class Achado(
    val username: String?,
    val nome: String,
    /** Subtitulo "Conversa comercial" (so na caixa de entrada). */
    val comercial: Boolean = false,
    /** Veio da caixa de entrada: a conversa aberta pelo @ tem de ter este mesmo nome no cabecalho. */
    val exigirNome: Boolean = false,
    val motivo: String = "",
    /**
     * A linha tocada abriu OUTRA conversa. Conta sempre para a parada de
     * seguranca. (Linha que nao abre conversa legivel para a fila na hora:
     * FonteConversas.naoAbriu.)
     */
    val ilegivel: Boolean = false,
    /**
     * A conversa da linha abriu, mas nem o cabecalho nem o cartao do topo
     * mostraram @, e o subtitulo nao se deixou ler (sem texto, ou so "Online
     * agora"). Conta para a parada so enquanto nenhum @ foi lido nesta
     * operacao; depois, a acessibilidade ja provou que le o cabecalho: pulado
     * sem contar. Grupo com o subtitulo legivel ("Ana, Bia e mais 2") nunca
     * conta (grupos fixados no topo nao travam a fila).
     */
    val semArroba: Boolean = false,
)

/**
 * De onde vem cada pessoa: a propria lista do Instagram, lida ao vivo, uma
 * tela por vez. Nada e extraido nem importado: a memoria guarda a tela atual;
 * no aparelho ficam so quem recebeu, quem falhou e o ponto de parada
 * (Outcomes, Campanhas).
 *
 * "Continuar do ultimo @ processado" sai da regra [proximas]: a fila marca
 * cada @ feito, e a fonte devolve so os da tela que ainda nao foram. Voltando
 * de uma conversa, a lista esta onde estava; se o Instagram a recriou no topo,
 * a fonte rola passando pelos feitos ate o proximo nao feito. Se a lista mudou
 * (alguem saiu ou entrou), segue pelo proximo nao feito.
 */
interface Fonte {
    /** Chega a lista e devolve o @ da conta aberta, lido na tela; null = a lista nao abriu. */
    suspend fun abrir(): String?

    /**
     * As proximas pessoas da tela, na ordem, sem as ja feitas ([feita]). Rola
     * quando a tela toda ja foi feita. null = fim da lista.
     */
    suspend fun proximas(feita: (String) -> Boolean): List<Achado>?

    /**
     * A conta aberta no Instagram agora, lida no titulo da lista (voltando a
     * ela se preciso). A fila confere antes de cada pessoa: o dono pode trocar
     * de conta no meio da operacao. null = nao deu para ler. [firme]: sem
     * atalho, pelo caminho que prova a conta LOGADA (a fila pede depois de
     * qualquer pausa: nela o dono pode ter aberto a lista de outra pessoa).
     */
    suspend fun contaAgora(firme: Boolean = false): String?
}

private const val POLL_MS = 250

/** Rolagens no maximo por chamada (8 linhas por tela: ~16 mil linhas). */
private const val MAX_ROLAGENS = 2_000

/**
 * Rolou e a tela nao mudou: da ao Instagram este tanto para carregar mais
 * (conferindo a cada mudanca) antes de contar uma parada. Um evento qualquer
 * (o circulo de carregando) nao encurta a paciencia.
 */
private const val CARREGAR_MS = 2_000L

/**
 * A lista de Seguidores e paginada: em rede lenta a proxima pagina passa de 2 s, e rolar() devolve
 * false tambem com o circulo de carregando no fundo. Sem o fim ("Sugestoes para voce"), so duas
 * paradas com esta paciencia cada terminam a lista.
 */
private const val CARREGAR_SEGUIDORES_MS = 6_000L

/**
 * Perfil > Seguidores. Le as linhas visiveis (o @ de cada uma, ~8 por tela) e
 * so rola a propria lista (android:id/list), nunca o ViewPager (trocaria para
 * "seguindo"). O fim e a primeira linha de "Sugestoes para voce".
 *
 * Auto-calibracao: este caminho NAO e navegado por "Conferir o Instagram" (que
 * so ve Direct/Amigos Proximos), entao a navegacao (profile_tab,
 * profile_followers, as abas da lista) e resolvida AO VIVO pela assinatura. As
 * linhas e o @ nao: so pelo id de verdade (SelectorProfile.SO_POR_ID). Sem ele,
 * para com aviso claro ([SEM_LINHAS]), nunca le ninguem pela forma.
 */
class FonteSeguidores(private val tela: Tela, private val prof: SelectorProfile) : Fonte {

    private fun id(key: String) = prof.idFor(key).orEmpty()

    /** No do papel por id (aprendido -> fixo) ou, sem id na tela, pela assinatura/rotulo ao vivo. */
    private fun no(root: UiNode, key: String) = prof.nos(root, key).firstOrNull() ?: Evidence.porRotulo(root, prof, key)

    /**
     * Sempre pelo caminho do app, nunca aceitando a lista que ja estava na frente: o titulo de uma lista
     * de seguidores e o DONO da lista, nao a conta logada (a lista de seguidores de outra pessoa tem o @
     * dela, e as duas contas do dono se seguem).
     *  1. uma tela com a barra de abas (a lista de seguidores nao tem: volta ate ela);
     *  2. Direct: o titulo da caixa de entrada e a conta LOGADA (como DmFlow.contaAberta);
     *  3. Perfil: o perfil com o titulo = essa conta. A aba pode mostrar outro perfil da pilha dela (o dono
     *     abriu alguem na pausa): tocar de novo volta a raiz, o proprio perfil;
     *  4. "N seguidores" (id conhecido ou assinatura ao vivo): a lista com a aba Seguidores marcada.
     */
    override suspend fun abrir(): String? {
        // Recusado (pausado: tela bloqueada, ligacao): nada de Voltar na tela de bloqueio nem Instagram por cima da chamada.
        if (!tela.abrirInstagram()) return null
        var root: UiNode? = null
        for (i in 0 until 4) {
            root = tela.esperarAte(4_000) { no(it, "direct_tab") != null }
            if (root != null) break
            tela.voltar()
        }
        val direct = root?.let { no(it, "direct_tab") } ?: return null
        if (!tela.tocar(direct)) return null
        val caixa = tela.esperarAte(8_000) { Evidence.conta(it, prof) != null } ?: return null
        val conta = Evidence.conta(caixa, prof) ?: return null
        var aba = no(caixa, "profile_tab") ?: return null
        var perfil: UiNode? = null
        for (i in 0 until 2) {
            if (!tela.tocar(aba)) return null
            perfil = tela.esperarAte(6_000) { Evidence.conta(it, prof, "profile_title") == conta && no(it, "profile_followers") != null }
            if (perfil != null) break
            aba = tela.ler()?.let { no(it, "profile_tab") } ?: return null
        }
        val seguidores = perfil?.let { no(it, "profile_followers") } ?: return null
        if (!tela.tocar(seguidores)) return null
        // A barra de abas pode chegar depois das linhas: espera a aba Seguidores marcada (naLista). Esgotado
        // o prazo, decide sobre uma leitura NOVA, nunca sobre a de antes da espera.
        tela.esperarAte(8_000) { Listas.naListaDeSeguidores(it, prof) }?.let { recusar(it); return conta }
        tela.ler()?.let { r ->
            recusar(r)
            if (Listas.abaSeguidores(r, prof)) throw Fila.FailedSafe(SEM_LINHAS)
        }
        return null
    }

    /** A tela e a lista, mas nao pode ser lida: diz ao dono por que parou. */
    private fun recusar(r: UiNode) {
        if (Listas.abaSeguindoSelecionada(r, prof)) throw Fila.FailedSafe(SEGUINDO)
        // Com uma busca digitada a lista mostra 1 a 3 linhas: lida como a lista inteira, dava "Fim da lista".
        if (Listas.abaSeguidores(r, prof) && Listas.buscaDigitada(r, prof)) throw Fila.FailedSafe(BUSCA)
    }

    override suspend fun contaAgora(firme: Boolean): String? {
        if (!firme) {
            // De volta da conversa, a lista que o proprio app abriu ja esta na frente: duas buscas diretas,
            // sem ler a arvore. O titulo e o DONO da lista: so vale sem pausa desde o [abrir] (a Fila pede firme).
            if (tela.existe(listOf(id("follow_row"), id("follow_end")))) {
                Evidence.contaDe(tela.textosPorId(id("profile_title")))?.let { return it }
            }
            tela.esperarAte(3_000) { Listas.naListaDeSeguidores(it, prof) }?.let { Evidence.conta(it, prof, "profile_title") }?.let { return it }
        }
        return abrir()
    }

    override suspend fun proximas(feita: (String) -> Boolean): List<Achado>? {
        var root = naLista() ?: abrir()?.let { naLista() } ?: throw Fila.FailedSafe("a lista de seguidores não abriu")
        var parada = 0
        repeat(MAX_ROLAGENS) {
            val t = Listas.seguidores(root, prof)
            val novas = t.linhas.filterNot { feita(it.username) }
            if (novas.isNotEmpty()) return novas.map { Achado(it.username, it.name) }
            if (t.fim) return null
            val lista = Listas.rolagemSeguidores(root, prof) ?: return null // cabe numa tela
            // Recusada (o Instagram fora da frente, pausa) lanca FailedSafe: nunca e o fim.
            val rolou = tela.rolar(lista)
            if (rolou) tela.esperarEvento(POLL_MS.toLong()) // a rolagem comecar
            var depois = naLista() ?: throw Fila.FailedSafe("a lista de seguidores sumiu ao rolar")
            if (Listas.seguidores(depois, prof).linhas == t.linhas) {
                // Nao andou: o fim, ou o circulo de carregando no fundo (rolar() da false nos dois). Paciencia
                // antes do fim, mesmo sem rolar: um "Fim da lista" cedo encerra a operacao no meio.
                val ate = tela.agora() + CARREGAR_SEGUIDORES_MS
                while (tela.agora() < ate && Listas.seguidores(depois, prof).linhas == t.linhas) {
                    tela.esperarEvento(ate - tela.agora())
                    depois = naLista() ?: depois
                }
                parada = if (Listas.seguidores(depois, prof).linhas == t.linhas) parada + 1 else 0
                if (parada >= 2) return null
            } else {
                parada = 0
            }
            root = depois
        }
        throw Fila.FailedSafe("a lista de seguidores não terminou em $MAX_ROLAGENS rolagens")
    }

    companion object {
        const val SEGUINDO = "a lista aberta é \"Seguindo\", não \"Seguidores\". Abra a aba Seguidores."
        const val BUSCA = "há uma busca digitada na lista de Seguidores. Apague a busca e toque em \"Retomar de onde parou\"."
        const val SEM_LINHAS = "a lista de Seguidores abriu, mas sem linhas que o app reconheça (ainda carregando, " +
            "ou outra versão do Instagram). Ninguém dela foi lido."
    }

    /**
     * A lista na tela, depois de assentar: lida no meio da rolagem, pularia
     * linhas. Parada = uma leitura seguida de [POLL_MS] sem nenhum evento (a
     * arvore e a lida), ou duas leituras iguais. Cada leitura confere a aba:
     * virou "Seguindo" (o dono tocou nela na pausa) = parada segura.
     */
    private suspend fun naLista(): UiNode? {
        var antes: List<Follower>? = null
        var ultima: UiNode? = null
        val fim = tela.agora() + 3_000
        while (true) {
            val r = tela.ler()?.also(::recusar)?.takeIf { Listas.naListaDeSeguidores(it, prof) }
            val linhas = r?.let { Listas.seguidores(it, prof).linhas }
            if (r != null && linhas == antes) return r
            antes = linhas
            ultima = r
            if (tela.agora() >= fim) return ultima
            if (!tela.esperarEvento(POLL_MS.toLong()) && r != null) return r
        }
    }
}

/**
 * Direct > caixa de entrada: as conversas 1:1 que o dono ja tem. A linha nao
 * mostra o @, entao cada conversa e aberta pela linha (nunca pelo avatar: abre
 * story), o @ e lido no cabecalho e a conversa fecha. O envio e sempre pelo
 * link do @ (DmFlow), 1:1 por construcao: um grupo nunca recebe. Grupo ou @
 * ilegivel = pessoa sem @, pulada. Pedidos ficam de fora (outro filtro).
 *
 * Abrir a conversa marca como lidas as mensagens dela (como abrir a mao).
 *
 * ponytail: a linha e lembrada pelo NOME (a linha nao tem @ nem id): duas
 * conversas com o mesmo nome de exibicao, so a primeira e aberta nesta
 * execucao. Na mesma tela a outra entra no relatorio ([MESMO_NOME]); em telas
 * diferentes nao ha como saber. Se o probe mostrar outro identificador estavel
 * da linha, usar.
 */
class FonteConversas(
    private val tela: Tela,
    private val prof: SelectorProfile,
    /**
     * A conta da operacao. A caixa de OUTRA conta (o dono trocou de conta no Instagram) nunca tem conversa aberta:
     * abrir marca como lida a conversa de um terceiro daquela conta. null = sem conferir (testes da leitura).
     */
    private val conta: String? = null,
    /**
     * As linhas ja abertas nesta operacao: o nome da linha -> o @ lido nela ("" = sem @). So na memoria. O servico
     * passa o da operacao (AutomationController.linhasAbertas): no Retomar e com o servico religado, a linha de quem
     * ja tem desfecho nao e aberta de novo (antes, a retomada vinha do disco so com o @, que nao bate com o nome da
     * linha, e reabria desde o topo todas as conversas ja feitas, marcando como lidas); a de quem volta ([Fila.refazer])
     * e aberta de novo. Sem ele, o que a operacao ja tem em state.results.
     */
    private val abertas: MutableMap<String, String> =
        AutomationController.state.value.results.associateTo(HashMap()) { it.name to it.username },
) : Fonte {

    /** A linha ja foi aberta e nao volta: sem @, ou com o @ ja feito pela fila. */
    private fun jaAberta(l: Listas.LinhaConversa, feita: (String) -> Boolean) =
        abertas[l.nome]?.let { u -> u.isEmpty() || feita(u) } == true

    private fun id(key: String) = prof.idFor(key).orEmpty()

    private fun naCaixa(r: UiNode) =
        r.exists(id("inbox_list")) && Evidence.conta(r, prof) != null && !Evidence.temConversa(r, prof)

    override suspend fun abrir(): String? {
        // Recusado (pausado: tela bloqueada, ligacao): nada de Voltar na tela de bloqueio nem Instagram por cima da chamada.
        if (!tela.abrirInstagram()) return null
        var root: UiNode? = null
        for (i in 0 until 4) {
            root = tela.esperarAte(4_000) { naCaixa(it) || prof.nos(it, "direct_tab").isNotEmpty() }
            if (root != null) break
            tela.voltar()
        }
        if (root == null) return null
        if (!naCaixa(root)) {
            val direct = prof.nos(root, "direct_tab").firstOrNull() ?: return null
            if (!tela.tocar(direct)) return null
            root = tela.esperarAte(8_000) { naCaixa(it) } ?: return null
        }
        var r: UiNode = root
        recusar(r)
        // Caixa rolada (o dono a deixou assim): as conversas de cima ficariam de fora sem aviso. Volta ao topo; a
        // rolagem que nao anda (ja no topo, ou sem como rolar) encerra a volta.
        for (i in 0 until MAX_VOLTAS_AO_TOPO) {
            if (Listas.caixaNoTopo(r, prof)) break
            val lista = Listas.rolagemConversas(r, prof) ?: break
            if (!tela.rolarParaTras(lista)) break
            tela.esperarEvento(POLL_MS.toLong())
            r = caixa() ?: return null
        }
        return Evidence.conta(r, prof)
    }

    /**
     * A caixa na tela, mas noutro filtro: parada segura em QUALQUER leitura (o dono pode trocar na pausa). Em
     * Pedidos as linhas sao de desconhecidos; com um filtro ligado, o fim do pedaco viraria "Fim da lista".
     */
    private fun recusar(r: UiNode) {
        if (Listas.emPedidos(r, prof)) throw Fila.FailedSafe(EM_PEDIDOS)
        if (Listas.filtroLigado(r, prof)) throw Fila.FailedSafe(FILTRO_LIGADO)
    }

    /**
     * A caixa na tela, depois de assentar (como a lista de seguidores): lida no meio de uma mudanca (a linha
     * enviada subindo, uma mensagem nova chegando), o no tocado em seguida e de outra linha (a mesma View de
     * Compose passa a mostrar outra conversa) ou ja nao existe. Parada = uma leitura seguida de [POLL_MS] sem
     * evento, ou duas leituras seguidas com as mesmas linhas (nome e posicao). null = sem caixa em 3 s.
     */
    private suspend fun caixa(): UiNode? {
        var antes: List<Pair<String, Bounds?>>? = null
        var ultima: UiNode? = null
        val fim = tela.agora() + 3_000
        while (true) {
            val r = tela.ler()?.takeIf(::naCaixa)?.also(::recusar)
            val linhas = r?.let { Listas.conversas(it, prof).map { l -> l.nome to l.no.bounds } }
            if (r != null && linhas == antes) return r
            antes = linhas
            ultima = r
            if (tela.agora() >= fim) return ultima
            if (!tela.esperarEvento(POLL_MS.toLong()) && r != null) return r
        }
    }

    // O titulo da caixa de entrada e sempre a conta logada: firme ou nao, a mesma leitura.
    override suspend fun contaAgora(firme: Boolean): String? {
        if (tela.existe(listOf(id("inbox_list"))) && !tela.existe(prof.conversaIds)) {
            Evidence.contaDe(tela.textosPorId(id("inbox_title")))?.let { return it }
        }
        return caixa()?.let { Evidence.conta(it, prof) } ?: abrir()
    }

    override suspend fun proximas(feita: (String) -> Boolean): List<Achado>? {
        var root = caixa() ?: abrir()?.let { caixa() } ?: throw Fila.FailedSafe("a caixa de entrada não abriu")
        var parada = 0
        repeat(MAX_ROLAGENS) {
            // A caixa de outra conta: nenhuma linha e aberta. Lote vazio: a fila le a conta, pausa com o aviso e, de
            // volta a conta da operacao, pede a caixa de novo.
            if (conta != null && Evidence.conta(root, prof) != conta) return emptyList()
            val linhas = Listas.conversas(root, prof)
            linhas.firstOrNull { !jaAberta(it, feita) }?.let { l ->
                val achado = ler(l)
                // Outra linha com o mesmo nome nesta tela nao sera aberta (a linha e
                // lembrada pelo nome): fica no relatorio, em vez de sumir calada.
                val gemeas = linhas.count { it.nome == l.nome } - 1
                return listOf(achado) + List(gemeas) { Achado(null, l.nome, motivo = MESMO_NOME) }
            }
            val lista = Listas.rolagemConversas(root, prof) ?: return null
            // Recusada (o Instagram fora da frente, pausa) lanca FailedSafe: nunca e o fim.
            val rolou = tela.rolar(lista)
            if (rolou) tela.esperarEvento(POLL_MS.toLong())
            var depois = caixa() ?: throw Fila.FailedSafe("a caixa de entrada sumiu ao rolar")
            val nomes = linhas.map { it.nome }
            if (Listas.conversas(depois, prof).map { it.nome } == nomes) {
                val ate = tela.agora() + CARREGAR_MS
                while (tela.agora() < ate && Listas.conversas(depois, prof).map { it.nome } == nomes) {
                    tela.esperarEvento(ate - tela.agora())
                    depois = caixa() ?: depois
                }
                parada = if (Listas.conversas(depois, prof).map { it.nome } == nomes) parada + 1 else 0
                if (parada >= 2 || (!rolou && parada > 0)) return null
            } else {
                parada = 0
            }
            root = depois
        }
        throw Fila.FailedSafe("a caixa de entrada não terminou em $MAX_ROLAGENS rolagens")
    }

    /** Abre a conversa pela linha, le o @ do cabecalho (o subtitulo alterna com "Online agora") e volta. */
    private suspend fun ler(linha: Listas.LinhaConversa): Achado {
        val l = tocarLinha(linha)
        // Sem conversa legivel a fila para na hora: se a acessibilidade nao enxerga a conversa, o app nao sai
        // tocando (e marcando como lida) a caixa de entrada inteira.
        var r = tela.esperarIds(DmFlow.ABRIR_MS, prof.conversaIds) ?: naoAbriu(l)
        abertas[l.nome] = ""
        var cab = Evidence.arrobaDoCabecalho(r, prof)
        val fim = tela.agora() + DmFlow.PROVA_MS
        while (cab.username == null && tela.agora() < fim) {
            tela.esperarMudanca(fim)
            // Uma leitura sem a conversa (janela em transicao, teclado) nao encerra a espera: segue ate o prazo
            // com a ultima leitura boa, como o DmFlow.
            r = tela.ler()?.takeIf { Evidence.temConversa(it, prof) } ?: continue
            cab = Evidence.arrobaDoCabecalho(r, prof)
        }
        val titulo = Evidence.tituloDaConversa(r, prof) ?: l.nome
        // A conversa na tela tem de ser a da linha tocada (uma arvore velha traria outra). Na "Conversa comercial" o
        // titulo e o @ e a linha mostra o nome da loja: vale o @ lido no cabecalho dela (antes do toque, nenhuma
        // conversa estava na frente: naCaixa).
        val daLinha = Evidence.mesmoNome(titulo, l.nome) || (cab.comercial && cab.username != null)
        val motivo = when {
            !daLinha -> "a conversa aberta não é a da linha tocada"
            Evidence.indisponivel(r, prof) -> "a conta não recebe mensagens"
            Evidence.conversaNova(r, prof) -> "conversa sem mensagens (não é uma conversa sua)"
            cab.username == null -> "sem @ legível na conversa (grupo, ou o @ não apareceu)"
            else -> ""
        }
        // Sem @, mas com o subtitulo legivel mostrando outra coisa ("Ana, Bia e mais 2"): o cabecalho foi lido, e um
        // grupo. So conta para a parada o que nao da para saber (subtitulo sem texto, ou so "Online agora").
        val grupo = daLinha && cab.username == null && Evidence.subtituloDeGrupo(r, prof)
        if (!tela.sairDaConversa(prof)) throw Fila.FailedSafe("não consegui voltar à caixa de entrada")
        cab.username?.takeIf { daLinha }?.let { abertas[l.nome] = it }
        return Achado(
            cab.username.takeIf { daLinha }, titulo, cab.comercial, exigirNome = true, motivo = motivo,
            ilegivel = !daLinha, semArroba = daLinha && cab.username == null && !grupo && motivo.startsWith("sem @"),
        )
    }

    /** Toca na linha. Recusado (o no ficou velho: a caixa mudou), le a caixa assentada e toca uma vez na linha do mesmo nome. */
    private suspend fun tocarLinha(l: Listas.LinhaConversa): Listas.LinhaConversa {
        if (tela.tocar(l.no)) return l
        val nova = caixa()?.let { r -> Listas.conversas(r, prof).firstOrNull { it.nome == l.nome } }
        if (nova != null && tela.tocar(nova.no)) return nova
        throw Fila.FailedSafe("o toque na conversa foi recusado")
    }

    /**
     * A conversa da linha nao apareceu no prazo. Nunca segue para a proxima linha (cada toque abre, e marca como
     * lida, a conversa de um terceiro): para ja, dizendo qual caso foi; o log leva as janelas, sem dado de pessoa.
     *  - a caixa continua na frente com a linha: o toque nao abriu nada;
     *  - senao, o Instagram mostra uma janela que o app nao le (a conversa na ModalActivity): um Voltar a fecha, e
     *    a caixa tem de voltar.
     * A linha nao entra em [abertas]: depois de conferir o Instagram, a retomada tenta a mesma de novo.
     */
    private suspend fun naoAbriu(l: Listas.LinhaConversa): Nothing {
        Log.w(TAG, "a conversa da linha nao apareceu: ${tela.diagnostico()}")
        val r = tela.ler()
        if (r != null && naCaixa(r) && Listas.conversas(r, prof).any { it.nome == l.nome }) throw Fila.FailedSafe(TOQUE_NAO_ABRIU)
        tela.voltar()
        val voltou = tela.esperarAte(SAIR_MS.toInt()) { naCaixa(it) } != null
        throw Fila.FailedSafe(if (voltou) CONVERSA_ILEGIVEL else "$CONVERSA_ILEGIVEL A caixa de entrada não voltou depois de um Voltar.")
    }

    companion object {
        const val MESMO_NOME = "outra conversa com o mesmo nome: não foi aberta"
        const val EM_PEDIDOS = "a caixa de entrada está em Pedidos; deixe em Principal e toque em \"Retomar de onde parou\"."
        const val FILTRO_LIGADO = "há um filtro ligado na caixa de entrada (Filtros): ela mostra só parte das conversas. " +
            "Desligue o filtro e toque em \"Retomar de onde parou\"."
        /** Rolagens para tras, no maximo, para a caixa voltar ao topo (~8 conversas por tela). */
        private const val MAX_VOLTAS_AO_TOPO = 200
        const val TOQUE_NAO_ABRIU = "o toque na conversa não abriu nada no Instagram (a caixa de entrada continuou na frente). " +
            "Nada foi enviado. Confira o Instagram de novo antes de continuar."
        const val CONVERSA_ILEGIVEL = "a conversa abriu numa janela que o app não consegue ler neste aparelho. " +
            "Nada foi enviado. Confira o Instagram de novo antes de continuar."
        private const val TAG = "ListaLocalIG"
    }
}
