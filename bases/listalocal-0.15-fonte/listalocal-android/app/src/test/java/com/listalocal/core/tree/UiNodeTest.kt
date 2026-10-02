package com.listalocal.core.tree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiNodeTest {

    @Test fun `encontra linhas por viewId suffix`() {
        val tree = pickerFixture(
            selectedNames = listOf("Arnaldo Volei"),
            rows = listOf("Arnaldo Volei" to "+5521999990071", "Dora" to "+5521999990002"),
        )
        assertEquals(2, tree.findByViewIdSuffix("row_container").size)
    }

    @Test fun `conta chips de selecionados`() {
        val tree = pickerFixture(
            selectedNames = listOf("A", "B", "C"),
            rows = emptyList(),
        )
        assertEquals(3, tree.findByViewIdSuffix("contact_name").size)
    }

    @Test fun `exists confirma presenca do campo de selecao`() {
        val tree = pickerFixture(listOf("A"), listOf("A" to null))
        assertTrue(tree.exists("selection_check"))
        assertFalse(tree.exists("next_btn")) // botao criar so aparece com >=2
    }
}
