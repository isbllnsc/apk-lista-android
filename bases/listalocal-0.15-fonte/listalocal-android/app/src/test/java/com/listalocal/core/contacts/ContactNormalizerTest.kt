package com.listalocal.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactNormalizerTest {

    private val n = ContactNormalizer(defaultRegion = "BR")

    @Test fun `zero contatos gera zero numeros e zero lotes`() {
        val nums = n.normalize(emptyList())
        assertTrue(nums.isEmpty())
        assertTrue(n.batch(nums).isEmpty())
    }

    @Test fun `um contato brasileiro normaliza para E164`() {
        val nums = n.normalize(listOf(RawContact(1, "Alguem", listOf("21 99999-0001"))))
        assertEquals(listOf("+5521999990001"), nums.map { it.e164 })
    }

    @Test fun `numero internacional e preservado`() {
        val nums = n.normalize(listOf(RawContact(1, "Intl", listOf("+1 415 555 2671"))))
        assertEquals(listOf("+14155552671"), nums.map { it.e164 })
    }

    @Test fun `numeros duplicados sao removidos preservando ordem`() {
        val nums = n.normalize(
            listOf(
                RawContact(1, "A", listOf("+5521999990001")),
                RawContact(2, "B", listOf("21 99999-0001")), // mesmo numero, outra grafia
            )
        )
        assertEquals(1, nums.size)
        assertEquals(1L, nums.first().contactLocalId) // primeira ocorrencia vence
    }

    @Test fun `contato com multiplos telefones gera multiplos numeros`() {
        val nums = n.normalize(
            listOf(RawContact(1, "Multi", listOf("+5521999990001", "+5521999990002")))
        )
        assertEquals(2, nums.size)
    }

    @Test fun `nomes duplicados com numeros distintos nao colidem`() {
        val nums = n.normalize(
            listOf(
                RawContact(1, "Kay", listOf("+5521999990001")),
                RawContact(2, "Kay", listOf("+5521999990002")),
            )
        )
        assertEquals(2, nums.size)
    }

    @Test fun `numero invalido e descartado nao adivinhado`() {
        val nums = n.normalize(listOf(RawContact(1, "Lixo", listOf("abc", "12"))))
        assertTrue(nums.isEmpty())
    }

    @Test fun `metricas vazias incluem zero contatos sem telefone valido`() {
        val result = n.normalizeWithMetrics(emptyList())
        assertTrue(result.numbers.isEmpty())
        assertEquals(0, result.telefonesLidos)
        assertEquals(0, result.validosBrutos)
        assertEquals(0, result.semTelefoneValido)
        assertEquals(0, result.duplicados)
        assertTrue(result.numbersByContactId.isEmpty())
    }

    @Test fun `metricas contam telefones validos duplicados e contatos sem valido`() {
        val contacts = listOf(
            RawContact(1, "Primeiro", listOf("21 99999-0001", "abc", "+5521999990002")),
            RawContact(2, "Duplicado", listOf("+55 21 99999-0001", "12")),
            RawContact(3, "Sem numero", emptyList()),
            RawContact(4, "So invalido", listOf("abc")),
        )

        val result = n.normalizeWithMetrics(contacts)

        assertEquals(6, result.telefonesLidos)
        assertEquals(3, result.validosBrutos)
        assertEquals(2, result.numbers.size)
        assertEquals(1, result.duplicados)
        assertEquals(2, result.semTelefoneValido)
        assertEquals(listOf("Primeiro", "Primeiro"), result.numbers.map { it.displayName })
        assertEquals(setOf("+5521999990001", "+5521999990002"), result.numbersByContactId[1L])
        assertEquals(setOf("+5521999990001"), result.numbersByContactId[2L])
        assertEquals(emptySet<String>(), result.numbersByContactId[3L])
        assertEquals(emptySet<String>(), result.numbersByContactId[4L])
        assertEquals(n.normalize(contacts), result.numbers)
    }

    @Test fun `exatamente 256 gera um unico lote`() {
        val contacts = (1..256).map { RawContact(it.toLong(), "C$it", listOf(fakeNumber(it))) }
        val batches = n.batch(n.normalize(contacts))
        assertEquals(1, batches.size)
        assertEquals(256, batches.first().numbers.size)
        assertEquals("Transmissão 001", batches.first().label)
    }

    @Test fun `257 gera dois lotes criaveis 255 mais 2`() {
        val contacts = (1..257).map { RawContact(it.toLong(), "C$it", listOf(fakeNumber(it))) }
        val normalized = n.normalize(contacts)
        val batches = n.batch(normalized)
        assertEquals(2, batches.size)
        assertEquals(255, batches[0].numbers.size)
        assertEquals(2, batches[1].numbers.size)
        assertEquals(normalized, batches.flatMap { it.numbers })
        assertEquals("Transmissão 002", batches[1].label)
    }

    @Test fun `513 nao deixa lote com uma pessoa`() {
        val contacts = (1..513).map { RawContact(it.toLong(), "C$it", listOf(fakeNumber(it))) }
        val normalized = n.normalize(contacts)
        val batches = n.batch(normalized)
        assertEquals(listOf(256, 255, 2), batches.map { it.numbers.size })
        assertEquals(normalized, batches.flatMap { it.numbers })
    }

    @Test fun `mais de mil contatos gera lotes corretos`() {
        val contacts = (1..1000).map { RawContact(it.toLong(), "C$it", listOf(fakeNumber(it))) }
        val batches = n.batch(n.normalize(contacts))
        assertEquals(4, batches.size) // 256*3 + 232
        assertEquals(232, batches.last().numbers.size)
    }

    // Gera numeros BR validos e distintos (fixos DDD 21, faixa 9xxxx-xxxx).
    private fun fakeNumber(i: Int): String {
        val body = (985_000_000 + i).toString().padStart(9, '0').take(9)
        return "+5521$body"
    }
}
