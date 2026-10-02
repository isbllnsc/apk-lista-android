package com.listalocal.core.match

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactMatcherTest {

    // normalizador de teste: mantem digitos e prefixo +, senao null
    private val matcher = ContactMatcher { raw ->
        val digits = raw.filter { it.isDigit() }
        if (digits.length >= 10) "+$digits" else null
    }

    @Test fun `sem resultados e NOT_FOUND`() {
        val r = matcher.match("+5521999990071", "Alguem", emptyList())
        assertEquals(MatchOutcome.NOT_FOUND, r.outcome)
    }

    @Test fun `match exato por numero`() {
        val rows = listOf(ResultRow("Arnaldo Volei", "+55 21 99999-0071", false))
        val r = matcher.match("+5521999990071", null, rows)
        assertEquals(MatchOutcome.EXACT, r.outcome)
        assertEquals(0, r.rowIndex)
    }

    @Test fun `dois numeros iguais sao ambiguos`() {
        val rows = listOf(
            ResultRow("Arnaldo", "+55 21 99999-0071", false),
            ResultRow("Arnaldo casa", "+55 21 99999-0071", false),
        )
        val r = matcher.match("+5521999990071", null, rows)
        assertEquals(MatchOutcome.AMBIGUOUS, r.outcome)
    }

    @Test fun `resultado sem match exato de nome e NOT_FOUND`() {
        val rows = listOf(ResultRow("Matheusin", null, false))
        val r = matcher.match(null, "Matheus", rows)
        assertEquals(MatchOutcome.NOT_FOUND, r.outcome)
    }

    @Test fun `nome exato casa quando nao ha numero`() {
        val rows = listOf(ResultRow("Dora Matias", null, false))
        val r = matcher.match(null, "Dora Matias", rows)
        assertEquals(MatchOutcome.EXACT, r.outcome)
    }

    @Test fun `linha ja selecionada retorna ALREADY_SELECTED`() {
        val rows = listOf(ResultRow("Arnaldo Volei", "+55 21 99999-0071", true))
        val r = matcher.match("+5521999990071", null, rows)
        assertEquals(MatchOutcome.ALREADY_SELECTED, r.outcome)
    }

    @Test fun `nunca seleciona primeiro resultado sem match inequivoco`() {
        val rows = listOf(
            ResultRow("Outro", "+55 21 90000-0000", false),
            ResultRow("Mais outro", "+55 21 91111-1111", false),
        )
        val r = matcher.match("+5521999990071", "Alguem", rows)
        assertEquals(MatchOutcome.NOT_FOUND, r.outcome)
    }
}
