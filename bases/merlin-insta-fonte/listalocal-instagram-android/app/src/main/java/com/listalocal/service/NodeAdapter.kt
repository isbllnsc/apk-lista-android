package com.listalocal.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.listalocal.core.tree.Bounds
import com.listalocal.core.tree.UiNode

/**
 * Adapta AccessibilityNodeInfo (Android) para [UiNode] (puro). Faz uma copia
 * imutavel da subarvore para que as provas decidam sobre um retrato estavel
 * (a MESMA funcao pura dos testes). [info] guarda o no nativo so para agir:
 * NodeOps reconfere janela, prazo, pausa e cancelamento antes de cada toque.
 */
class NodeAdapter private constructor(
    override val viewIdResourceName: String?,
    override val className: String?,
    override val contentDescription: String?,
    override val text: String?,
    override val isClickable: Boolean,
    override val isChecked: Boolean,
    override val isSelected: Boolean,
    override val isScrollable: Boolean,
    override val isEnabled: Boolean,
    override val isVisibleToUser: Boolean,
    override val children: List<UiNode>,
    override val bounds: Bounds?,
    override val isFocused: Boolean,
    internal val info: AccessibilityNodeInfo,
) : UiNode {
    companion object {
        fun from(node: AccessibilityNodeInfo?): NodeAdapter? {
            if (node == null) return null
            val kids = ArrayList<UiNode>(node.childCount)
            for (i in 0 until node.childCount) {
                from(node.getChild(i))?.let { kids += it }
            }
            val r = Rect().also(node::getBoundsInScreen)
            return NodeAdapter(
                viewIdResourceName = node.viewIdResourceName,
                className = node.className?.toString(),
                contentDescription = node.contentDescription?.toString(),
                text = node.text?.toString(),
                isClickable = node.isClickable,
                isChecked = node.isChecked,
                isSelected = node.isSelected,
                isScrollable = node.isScrollable,
                isEnabled = node.isEnabled,
                isVisibleToUser = node.isVisibleToUser,
                children = kids,
                bounds = if (r.isEmpty) null else Bounds(r.left, r.top, r.right, r.bottom),
                isFocused = node.isFocused,
                info = node,
            )
        }
    }
}
