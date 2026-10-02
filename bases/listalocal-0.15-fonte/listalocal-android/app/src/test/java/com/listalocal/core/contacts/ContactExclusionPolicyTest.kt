package com.listalocal.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactExclusionPolicyTest {
    private fun number(value: String, owner: Long) = NormalizedNumber(value, owner, "Pessoa $owner")

    @Test fun `excluir pessoa remove todos seus numeros e o numero compartilhado`() {
        val first = "+5521999990001"
        val shared = "+5521999990002"
        val other = "+5521999990003"
        val map = mapOf(1L to setOf(first, shared), 2L to setOf(shared, other))
        val included = listOf(number(first, 1), number(shared, 1), number(other, 2))

        val excluded = ContactExclusionPolicy.excludedNumbers(map, setOf(2L))
        assertEquals(setOf(shared, other), excluded)
        assertEquals(listOf(first), ContactExclusionPolicy.keep(included, excluded).map { it.e164 })
    }

    @Test fun `reincluir contato restaura destino e fronteira de lotes`() {
        val all = (1..257).map { number("+55219%08d".format(it), it.toLong()) }
        val map = all.associate { it.contactLocalId to setOf(it.e164) }
        val excluded = ContactExclusionPolicy.excludedNumbers(map, setOf(257L))
        val filtered = ContactExclusionPolicy.keep(all, excluded)
        val normalizer = ContactNormalizer()

        assertEquals(256, filtered.size)
        assertEquals(1, normalizer.batch(filtered).size)
        assertEquals(2, normalizer.batch(ContactExclusionPolicy.keep(all, emptySet())).size)
        assertFalse(filtered.any { it.contactLocalId == 257L })
        assertTrue(all.any { it.contactLocalId == 257L })
    }
}
