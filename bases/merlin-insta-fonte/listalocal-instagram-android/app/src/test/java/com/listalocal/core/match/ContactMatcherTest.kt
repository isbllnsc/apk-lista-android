package com.listalocal.core.match

import com.listalocal.core.ig.Evidence
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.amigosFixture
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactMatcherTest {

    private fun linhas(vararg rows: Pair<String, Boolean>) =
        Evidence.linhasAmigos(amigosFixture(rows.toList()), SelectorProfile.IG_448)

    @Test fun `arroba exato e unico casa`() {
        val r = ContactMatcher.match("@Joao.Silva", linhas("joao.silva" to false, "joao.silva_" to false))
        assertEquals(MatchOutcome.EXACT, r.outcome)
        assertEquals("joao.silva", r.row?.username)
    }

    @Test fun `arroba parecido nao casa`() {
        assertEquals(MatchOutcome.NOT_FOUND, ContactMatcher.match("joao.silva", linhas("joao.silva_" to false)).outcome)
        assertEquals(MatchOutcome.NOT_FOUND, ContactMatcher.match("joao", linhas("joao.silva" to false)).outcome)
    }

    @Test fun `nunca escolhe a primeira linha por padrao`() {
        assertEquals(MatchOutcome.NOT_FOUND, ContactMatcher.match("zeca", linhas("ana" to false, "bia" to false)).outcome)
        assertEquals(MatchOutcome.NOT_FOUND, ContactMatcher.match("zeca", emptyList()).outcome)
    }

    @Test fun `duas linhas exatas e ambiguo`() {
        assertEquals(MatchOutcome.AMBIGUOUS, ContactMatcher.match("ana", linhas("ana" to false, "ana" to true)).outcome)
    }

    @Test fun `linha ja marcada nao recebe toque`() {
        assertEquals(MatchOutcome.ALREADY_SELECTED, ContactMatcher.match("ana", linhas("ana" to true)).outcome)
    }
}
