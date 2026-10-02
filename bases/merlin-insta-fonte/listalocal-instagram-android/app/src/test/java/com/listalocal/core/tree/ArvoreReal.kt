package com.listalocal.core.tree

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Telas REAIS do Instagram 448 pt-BR lidas no celular do dono (rodada 1,
 * INSTAGRAM-APP-REAL.md), anonimizadas por app/src/test/tools/anonimizar_arvores.py
 * em src/test/resources/real/. Cada tela vira uma arvore de [FakeNode] com os
 * mesmos ids, classes, textos de UI, estados (clicavel, marcado, selecionado,
 * rolavel, habilitado) e bounds da tela; nome e @ de pessoa sao marcadores
 * ("pessoa07", "Pessoa 12"), a conta do dono e "minhaconta".
 *
 * Limites (valem para ler os achados):
 *  - fonte "uiautomator": o dump le TODAS as Views visiveis (inclusive as "nao
 *    importantes"); o servico do app nao pede flagIncludeNotImportantViews, entao
 *    na leitura dele alguns layouts vazios somem e os filhos sobem um nivel. As
 *    partes em Compose (caixa de entrada) sao iguais nos dois.
 *  - fonte "views" (ModalActivity: conversa, Nova mensagem, Amigos Proximos): o
 *    dump de Views nao traz texto; so os textos de UI documentados entram, com
 *    suposto="1", e o "rolavel" das listas e suposto.
 */
object ArvoreReal {

    class Tela(val nome: String, val raiz: UiNode, val fonte: String, val origem: String, val altura: Int)

    private val cache = HashMap<String, Tela>()

    fun tela(nome: String): Tela = synchronized(cache) { cache.getOrPut(nome) { ler(nome) } }

    fun raiz(nome: String): UiNode = tela(nome).raiz

    /**
     * A mesma tela num Instagram de ids diferentes (outro aparelho ou versao): todo
     * id do Instagram ganha o sufixo [sufixo]. O resto (texto, forma, estado) fica.
     */
    fun comIdsTrocados(no: UiNode, sufixo: String = "_v2"): UiNode {
        if (no is UiGroup) return UiGroup(no.children.map { comIdsTrocados(it, sufixo) })
        val f = no as FakeNode
        val id = f.viewIdResourceName?.let { if (it.startsWith("com.instagram.android:id/")) it + sufixo else it }
        return f.copy(viewIdResourceName = id, children = f.children.map { comIdsTrocados(it, sufixo) })
    }

    /** A mesma tela com um rotulo trocado (outro idioma, outro numero): texto ou descricao igual a [de] vira [para]. */
    fun comRotulo(no: UiNode, de: String, para: String): UiNode {
        if (no is UiGroup) return UiGroup(no.children.map { comRotulo(it, de, para) })
        val f = no as FakeNode
        return f.copy(
            text = if (f.text == de) para else f.text,
            contentDescription = if (f.contentDescription == de) para else f.contentDescription,
            children = f.children.map { comRotulo(it, de, para) },
        )
    }

    /**
     * A mesma tela como o SERVICO a le, sem flagIncludeNotImportantViews: um layout sem clique, rolagem, texto nem
     * descricao (e que nao e TextView/EditText) some, e os filhos dele sobem um nivel. Aproxima
     * View.isImportantForAccessibility no modo AUTO; um IgView de texto desenhado sem descricao some (o pior caso).
     */
    fun semNaoImportantes(no: UiNode): UiNode {
        if (no is UiGroup) return UiGroup(no.children.map(::semNaoImportantes))
        fun importante(f: FakeNode) = f.isClickable || f.isScrollable || f.text != null || f.contentDescription != null ||
            f.className?.let { it.endsWith("TextView") || it.endsWith("EditText") } == true
        fun filhos(n: UiNode): List<UiNode> = n.children.flatMap { c ->
            val f = c as FakeNode
            val fs = filhos(f)
            if (importante(f)) listOf(f.copy(children = fs)) else fs
        }
        return (no as FakeNode).copy(children = filhos(no))
    }

    /** A mesma tela sem o id [id] (outra versao em que o testTag do Compose nao vira viewId). */
    fun semId(no: UiNode, id: String): UiNode {
        if (no is UiGroup) return UiGroup(no.children.map { semId(it, id) })
        val f = no as FakeNode
        val tira = f.viewIdResourceName?.substringAfter(":id/") == id
        return f.copy(viewIdResourceName = if (tira) null else f.viewIdResourceName, children = f.children.map { semId(it, id) })
    }

    /** A mesma tela deslocada [dy] px na vertical (a lista ainda deslizando, com os mesmos itens). */
    fun deslocada(no: UiNode, dy: Int): UiNode {
        if (no is UiGroup) return UiGroup(no.children.map { deslocada(it, dy) })
        val f = no as FakeNode
        return f.copy(bounds = f.bounds?.let { it.copy(topo = it.topo + dy, baixo = it.baixo + dy) }, children = f.children.map { deslocada(it, dy) })
    }

    /** A mesma tela sem os nos deste id (e o que ha dentro deles): a parte que ainda nao chegou ou que nao existe. */
    fun semNos(no: UiNode, id: String): UiNode {
        if (no is UiGroup) return UiGroup(no.children.map { semNos(it, id) })
        val f = no as FakeNode
        val fica = f.children.filter { it.viewIdResourceName?.substringAfter(":id/") != id }
        return f.copy(children = fica.map { semNos(it, id) })
    }

    /** A mesma tela com texto e/ou descricao nos nos deste id (o dump de Views nao traz texto: o que se supoe ler). */
    fun comRotuloNoId(no: UiNode, id: String, texto: String? = null, descricao: String? = null): UiNode {
        if (no is UiGroup) return UiGroup(no.children.map { comRotuloNoId(it, id, texto, descricao) })
        val f = no as FakeNode
        val casa = f.viewIdResourceName?.substringAfter(":id/") == id
        return f.copy(
            text = if (casa && texto != null) texto else f.text,
            contentDescription = if (casa && descricao != null) descricao else f.contentDescription,
            children = f.children.map { comRotuloNoId(it, id, texto, descricao) },
        )
    }

    private fun ler(nome: String): Tela {
        val arq = ArvoreReal::class.java.getResourceAsStream("/real/$nome.xml")
            ?: error("fixture real/$nome.xml nao existe")
        val doc = arq.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val h = doc.documentElement
        val raiz = UiGroup.juntar(h.filhos().map(::no)) ?: error("real/$nome.xml sem nos")
        return Tela(nome, raiz, h.getAttribute("fonte"), h.getAttribute("origem"), h.getAttribute("altura").toIntOrNull() ?: 2408)
    }

    private fun Element.filhos(): List<Element> =
        (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>().filter { it.tagName == "node" }

    private val BOUNDS = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

    private fun no(e: Element): UiNode {
        fun s(k: String) = e.getAttribute(k).takeIf { it.isNotEmpty() }
        fun b(k: String, padrao: Boolean = false) = s(k)?.toBoolean() ?: padrao
        val bounds = s("bounds")?.let(BOUNDS::matchEntire)?.destructured
            ?.let { (l, t, r, bx) -> Bounds(l.toInt(), t.toInt(), r.toInt(), bx.toInt()) }
            ?.takeIf { it.direita > it.esquerda && it.baixo > it.topo } // como o NodeAdapter: vazio = null
        return FakeNode(
            viewIdResourceName = s("resource-id"),
            className = s("class"),
            contentDescription = s("content-desc"),
            text = s("text"),
            isClickable = b("clickable"),
            isChecked = b("checked"),
            isSelected = b("selected"),
            children = e.filhos().map(::no),
            isScrollable = b("scrollable"),
            isEnabled = b("enabled", true),
            isVisibleToUser = true, // o dump so traz o que esta visivel
            bounds = bounds,
            isFocused = b("focused"),
        )
    }
}
