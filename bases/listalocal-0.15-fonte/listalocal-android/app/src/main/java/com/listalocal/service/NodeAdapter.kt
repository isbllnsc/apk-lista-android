package com.listalocal.service

import android.view.accessibility.AccessibilityNodeInfo
import com.listalocal.core.tree.UiNode

/**
 * Adapta AccessibilityNodeInfo (Android) para [UiNode] (puro). Faz uma copia
 * imutavel da subarvore para que a logica de match nunca dependa de handles
 * nativos que podem ser reciclados.
 */
class NodeAdapter private constructor(
    override val viewIdResourceName: String?,
    override val className: String?,
    override val contentDescription: String?,
    override val text: String?,
    override val isClickable: Boolean,
    override val isChecked: Boolean,
    override val isSelected: Boolean,
    override val children: List<UiNode>,
) : UiNode {
    companion object {
        fun from(node: AccessibilityNodeInfo?): NodeAdapter? {
            if (node == null) return null
            val kids = ArrayList<UiNode>(node.childCount)
            for (i in 0 until node.childCount) {
                from(node.getChild(i))?.let { kids += it }
            }
            return NodeAdapter(
                viewIdResourceName = node.viewIdResourceName,
                className = node.className?.toString(),
                contentDescription = node.contentDescription?.toString(),
                text = node.text?.toString(),
                isClickable = node.isClickable,
                isChecked = node.isChecked,
                isSelected = node.isSelected,
                children = kids,
            )
        }
    }
}
