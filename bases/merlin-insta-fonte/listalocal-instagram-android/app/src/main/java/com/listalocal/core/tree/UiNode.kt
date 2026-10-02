package com.listalocal.core.tree

/**
 * Retangulo na tela, em pixels. So para leitura da posicao (assinatura de
 * papel); nunca vira coordenada de toque.
 */
data class Bounds(val esquerda: Int, val topo: Int, val direita: Int, val baixo: Int) {
    val centroY: Int get() = (topo + baixo) / 2
    val altura: Int get() = baixo - topo
}

/**
 * Abstracao de um no de acessibilidade, independente de Android. O servico
 * adapta AccessibilityNodeInfo para esta interface; os testes usam fixtures.
 * Assim a maquina de estados e as provas sao testadas sem o Instagram.
 */
interface UiNode {
    val viewIdResourceName: String?
    val className: String?
    val contentDescription: String?
    val text: String?
    val isClickable: Boolean
    val isChecked: Boolean
    val isSelected: Boolean
    val children: List<UiNode>
    val isScrollable: Boolean get() = false
    val isEnabled: Boolean get() = true
    /** Na tela de verdade. A pagina ao lado de um ViewPager pode estar na arvore sem aparecer. */
    val isVisibleToUser: Boolean get() = true
    /** Tem o foco de entrada (o campo com o teclado aberto nele). */
    val isFocused: Boolean get() = false

    /**
     * Retangulo do no na tela, em pixels (getBoundsInScreen). So para LER a
     * assinatura (rodape para o campo, topo para o cabecalho): a auto-calibracao
     * e a resolucao por papel usam a posicao como mais um sinal. O toque continua
     * por no (performAction), nunca por coordenada. null quando nao ha medida.
     */
    val bounds: Bounds? get() = null

    /** Este no e todos os descendentes, em profundidade. */
    fun walk(): Sequence<UiNode> = sequence {
        yield(this@UiNode)
        children.forEach { yieldAll(it.walk()) }
    }

    /** Busca em profundidade por viewId (sufixo apos ':id/'). */
    fun findByViewIdSuffix(suffix: String): List<UiNode> =
        walk().filter { n -> n.viewIdResourceName?.let { it.substringAfter(":id/", it) == suffix } == true }
            .toList()

    fun exists(suffix: String): Boolean = findByViewIdSuffix(suffix).isNotEmpty()

    /** Texto e descricao do no, sem vazios. */
    fun labels(): List<String> = listOfNotNull(text, contentDescription).filter { it.isNotBlank() }
}

/**
 * Varias janelas lidas como uma arvore so: a principal primeiro, depois as que
 * estao por cima dela (um dialogo de aviso, por exemplo). No sintetico, sem id,
 * texto nem acao.
 */
class UiGroup(override val children: List<UiNode>) : UiNode {
    override val viewIdResourceName: String? get() = null
    override val className: String? get() = null
    override val contentDescription: String? get() = null
    override val text: String? get() = null
    override val isClickable: Boolean get() = false
    override val isChecked: Boolean get() = false
    override val isSelected: Boolean get() = false

    companion object {
        /** Nenhuma raiz = null; uma = ela mesma; varias = o grupo. */
        fun juntar(raizes: List<UiNode>): UiNode? = raizes.singleOrNull() ?: raizes.takeIf { it.isNotEmpty() }?.let(::UiGroup)
    }
}
