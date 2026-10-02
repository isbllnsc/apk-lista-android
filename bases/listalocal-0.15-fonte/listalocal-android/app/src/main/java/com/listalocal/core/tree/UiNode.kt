package com.listalocal.core.tree

/**
 * Abstracao de um no de acessibilidade, independente de Android. O servico
 * adapta AccessibilityNodeInfo para esta interface; os testes usam fixtures.
 * Assim a maquina de estados e a logica de match sao testadas sem WhatsApp.
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

    /** Busca em profundidade por viewId (sufixo apos ':id/'). */
    fun findByViewIdSuffix(suffix: String): List<UiNode> {
        val out = mutableListOf<UiNode>()
        fun walk(n: UiNode) {
            val id = n.viewIdResourceName
            if (id != null && id.substringAfter(":id/", id) == suffix) out += n
            n.children.forEach(::walk)
        }
        walk(this)
        return out
    }

    fun exists(suffix: String): Boolean = findByViewIdSuffix(suffix).isNotEmpty()
}
