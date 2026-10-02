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
) : UiNode

/** Constroi uma arvore parecida com o picker real do WhatsApp 2.26. */
fun pickerFixture(selectedNames: List<String>, rows: List<Pair<String, String?>>): FakeNode {
    val rowNodes = rows.map { (name, number) ->
        FakeNode(
            viewIdResourceName = "com.whatsapp:id/row_container",
            className = "android.widget.CheckBox",
            children = listOf(
                FakeNode(viewIdResourceName = "com.whatsapp:id/chat_able_contacts_row_name", text = name),
                FakeNode(viewIdResourceName = "com.whatsapp:id/chat_able_contacts_row_status", text = number),
                FakeNode(viewIdResourceName = "com.whatsapp:id/selection_check", className = "android.widget.FrameLayout"),
            ),
        )
    }
    val chips = selectedNames.map {
        FakeNode(viewIdResourceName = "com.whatsapp:id/contact_name", text = it)
    }
    return FakeNode(
        viewIdResourceName = "com.whatsapp:id/multiple_contact_picker_content",
        children = rowNodes + FakeNode(
            viewIdResourceName = "com.whatsapp:id/selected_list",
            children = chips,
        ),
    )
}
