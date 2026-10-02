package com.listalocal.core.ig

import com.listalocal.core.followers.Follower
import com.listalocal.core.followers.FollowerImport
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.UiNode

/**
 * As listas do proprio Instagram, lidas uma tela por vez (destinatarios ao
 * vivo, sem extracao nem arquivo). Funcoes puras, como Evidence: o servico
 * passa a copia da arvore e os testes passam FakeNode.
 *
 * Formato medido na rodada 1 (INSTAGRAM-APP-REAL.md, 2 e 1.f; t04.xml, t10*.xml):
 *  - Seguidores: `follow_list_container` com `follow_list_username` (o @) e
 *    `follow_list_subtitle` (nome), ~8 por tela, dentro de `android:id/list`;
 *    o fim e a primeira `recommended_user_row_content_identifier`.
 *  - Caixa de entrada: linhas em Compose SEM id. A linha e um no clicavel
 *    cujo filho tem a descricao "<nome>, <estado>, <tempo>" e cujo texto e o
 *    nome. O @ NAO aparece na linha: so abrindo a conversa.
 * So nos visiveis: a pagina ao lado do ViewPager ("seguindo") pode estar na arvore.
 */
object Listas {

    /** Uma tela da lista de Seguidores: as linhas visiveis, na ordem, e se o fim ja apareceu. */
    data class TelaSeguidores(val linhas: List<Follower>, val fim: Boolean)

    // Resolve o papel (id aprendido -> id fixo -> assinatura ao vivo), so os visiveis.
    private fun id(root: UiNode, prof: SelectorProfile, key: String): List<UiNode> =
        prof.nos(root, key).filter { it.isVisibleToUser }

    /**
     * A tela e a lista de Seguidores: a aba "N seguidores" MARCADA, com linhas de seguidor (pelo id de
     * verdade) ou o fim. Linhas sem a aba nao bastam: "Seguindo" tem as mesmas linhas, e pela forma o
     * perfil, a caixa de entrada e Configuracoes pareciam a lista (telas reais, 26/09). A barra de abas
     * pode chegar depois das linhas: quem le espera (FonteSeguidores), nunca aceita sem ela.
     */
    fun naListaDeSeguidores(root: UiNode, prof: SelectorProfile): Boolean =
        abaSeguidores(root, prof) && (id(root, prof, "follow_row").isNotEmpty() || id(root, prof, "follow_end").isNotEmpty())

    /**
     * A aba marcada e a de seguidores ("388 seguidores"), nao "seguindo": as
     * duas tem as mesmas linhas. Sem aba marcada legivel = false.
     */
    fun abaSeguidores(root: UiNode, prof: SelectorProfile): Boolean {
        val rotulos = prof.descriptionsFor("followers_tab")
        return id(root, prof, "follow_tab").any { n ->
            n.isSelected && n.labels().any { l -> rotulos.any { l.contains(it, ignoreCase = true) } }
        }
    }

    /**
     * A aba "Seguindo"/"Following" esta marcada: so para dizer ao dono POR QUE parou. Quem protege e
     * [naListaDeSeguidores] (exige a aba de seguidores marcada), que vale em qualquer idioma de "seguindo".
     */
    fun abaSeguindoSelecionada(root: UiNode, prof: SelectorProfile): Boolean {
        val rotulos = prof.descriptionsFor("following_tab")
        return id(root, prof, "follow_tab").any { n ->
            n.isSelected && n.labels().any { l -> rotulos.any { l.contains(it, ignoreCase = true) } }
        }
    }

    fun seguidores(root: UiNode, prof: SelectorProfile): TelaSeguidores {
        val fim = id(root, prof, "follow_end").isNotEmpty()
        val rows = id(root, prof, "follow_row")
        // Linha e @ so pelo id de verdade (aprendido -> fixo; SelectorProfile.SO_POR_ID): sem ele na
        // tela, ninguem e lido. Pela forma, a aba "Sinalizadas" virava um seguidor.
        val user = id(root, prof, "follow_username")
            .firstNotNullOfOrNull { it.viewIdResourceName?.substringAfter(":id/") }
            ?: return TelaSeguidores(emptyList(), fim)
        val nome = prof.idFor("follow_name")
        val linhas = rows.mapNotNull { row ->
            val u = FollowerImport.username(row.findByViewIdSuffix(user).firstOrNull()?.text) ?: return@mapNotNull null
            val n = nome?.let { row.findByViewIdSuffix(it).firstOrNull()?.text }.orEmpty().trim()
            Follower(u, n.ifEmpty { u })
        }.distinctBy { it.username }
        return TelaSeguidores(linhas, fim)
    }

    /**
     * A busca da lista de seguidores tem texto digitado (nao so a dica "Pesquisar"): a lista mostra so quem
     * casa com a busca, e o Instagram mantem a busca ao voltar a lista.
     */
    fun buscaDigitada(root: UiNode, prof: SelectorProfile): Boolean {
        val dicas = prof.descriptionsFor("follow_search_hint")
        return id(root, prof, "follow_search").any { n ->
            val t = n.text?.trim().orEmpty()
            t.isNotEmpty() && dicas.none { it.equals(t, ignoreCase = true) }
        }
    }

    /** A lista que rola: o ListView das linhas. NUNCA o ViewPager de cima (rolar ele troca de aba). */
    fun rolagemSeguidores(root: UiNode, prof: SelectorProfile): UiNode? {
        val linha = prof.idFor("follow_row") ?: return null
        return id(root, prof, "follow_list").firstOrNull { it.isScrollable && it.exists(linha) }
    }

    /** Uma linha da caixa de entrada: o nome e o no a tocar (a linha, nunca o avatar: abre story). */
    data class LinhaConversa(val nome: String, val no: UiNode)

    fun conversas(root: UiNode, prof: SelectorProfile): List<LinhaConversa> {
        val lista = id(root, prof, "inbox_list").firstOrNull() ?: return emptyList()
        // Notas e a busca ("Pesquisar") tem o formato de uma linha e nao sao conversas. A busca tambem pelo rotulo:
        // sem o id (testTag) dela, "Pesquisar" virava a 1a conversa e o toque abria a busca do Direct.
        val fora = listOfNotNull(prof.idFor("inbox_notes"), prof.idFor("inbox_search")).toSet()
        val busca = prof.descriptionsFor("inbox_search")
        val out = mutableListOf<LinhaConversa>()
        fun visitar(n: UiNode) {
            if (!n.isVisibleToUser) return
            if (n.viewIdResourceName?.substringAfter(":id/") in fora) return
            val nome = if (n.isClickable) nomeDaLinha(n) else null
            if (nome != null) { if (busca.none { it.equals(nome, ignoreCase = true) }) out += LinhaConversa(nome, n) }
            else n.children.forEach(::visitar)
        }
        lista.children.forEach(::visitar)
        return out
    }

    /** O nome da linha: um texto dela com que a descricao-resumo de um filho comeca ("Ana, Enviado"). */
    private fun nomeDaLinha(row: UiNode): String? {
        val resumos = row.children.mapNotNull { c -> Evidence.norm(c.contentDescription).takeIf { it.isNotEmpty() } }
        if (resumos.isEmpty()) return null
        return row.walk().drop(1)
            .mapNotNull { n -> Evidence.norm(n.text).takeIf { it.isNotEmpty() } }
            .firstOrNull { t -> resumos.any { d -> d == t || d.startsWith("$t,") } }
    }

    fun rolagemConversas(root: UiNode, prof: SelectorProfile): UiNode? =
        id(root, prof, "inbox_list").firstOrNull { it.isScrollable }

    /** O filtro marcado da caixa de entrada e "Pedidos": ali nao ha conversa com quem segue. So na barra de filtros. */
    fun emPedidos(root: UiNode, prof: SelectorProfile): Boolean =
        filtrosDaCaixa(root, prof).any { it.isSelected && ehPedidos(it, prof) }

    /**
     * O chip "Filtros" mostra um filtro ligado (marcado, ou com outro rotulo, ex. "Nao lidos"): a caixa mostra so um
     * pedaco das conversas e o fim dele viraria "Fim da lista" da operacao. Sem o chip legivel, nao da para saber.
     */
    fun filtroLigado(root: UiNode, prof: SelectorProfile): Boolean {
        val pill = prof.idFor("inbox_filters") ?: return false
        val livre = prof.descriptionsFor("inbox_filters")
        return filtrosDaCaixa(root, prof).filter { it.viewIdResourceName?.substringAfter(":id/") == pill }.any { c ->
            c.isSelected || c.labels().none { l -> livre.any { Evidence.norm(l).equals(it, ignoreCase = true) } }
        }
    }

    private fun ehPedidos(n: UiNode, prof: SelectorProfile): Boolean {
        val rotulos = prof.descriptionsFor("requests")
        return n.labels().any { l -> rotulos.any { Evidence.norm(l).startsWith(it, ignoreCase = true) } }
    }

    /**
     * Os chips da barra de filtros da caixa (Filtros, Principal, Pedidos, Geral): os filhos clicaveis do pai do chip
     * "Filtros" (id) ou, sem ele, de um chip clicavel "Pedidos". Nunca as notas do topo: o titulo de musica de uma
     * nota e texto de terceiro, fica marcado (selected, o letreiro que corre) e pode ser "Requests" ou "Pedidos...".
     */
    private fun filtrosDaCaixa(root: UiNode, prof: SelectorProfile): List<UiNode> {
        val pill = prof.idFor("inbox_filters")
        val notas = prof.idFor("inbox_notes")
        fun semNotas(n: UiNode): Sequence<UiNode> = sequence {
            if (notas != null && n.viewIdResourceName?.substringAfter(":id/") == notas) return@sequence
            yield(n)
            n.children.forEach { yieldAll(semNotas(it)) }
        }
        val barra = semNotas(root).firstOrNull { b -> pill != null && b.children.any { it.viewIdResourceName?.substringAfter(":id/") == pill } }
            ?: semNotas(root).firstOrNull { b -> b.children.any { it.isClickable && ehPedidos(it, prof) } }
        return barra?.children?.filter { it.isClickable }.orEmpty()
    }

    /** A caixa esta no topo: a linha "Pesquisar" (a 1a da lista) aparece. Sem o id dela, nao da para saber: false. */
    fun caixaNoTopo(root: UiNode, prof: SelectorProfile): Boolean = id(root, prof, "inbox_search").isNotEmpty()
}
