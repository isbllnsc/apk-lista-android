package com.listalocal.core.tree

/** No de fixture para testar a logica sem AccessibilityNodeInfo real. */
data class FakeNode(
    override val viewIdResourceName: String? = null,
    override val className: String? = null,
    override val contentDescription: String? = null,
    override val text: String? = null,
    override val isClickable: Boolean = false,
    override val isChecked: Boolean = false,
    override val isSelected: Boolean = false,
    override val children: List<UiNode> = emptyList(),
    override val isScrollable: Boolean = false,
    override val isEnabled: Boolean = true,
    override val isVisibleToUser: Boolean = true,
    override val bounds: Bounds? = null,
    override val isFocused: Boolean = false,
) : UiNode
