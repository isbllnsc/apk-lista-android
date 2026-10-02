package com.listalocal.core.region

import com.listalocal.core.contacts.NormalizedNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionsTest {

    private fun n(e164: String) = NormalizedNumber(e164, 0, "x")

    @Test fun `tabela cobre 27 UFs`() {
        assertEquals(27, Regions.UF_REGIAO.size)
        // toda UF com DDD tem regiao e vice-versa
        assertTrue(Regions.DDD_UF.values.toSet().all { it in Regions.UF_REGIAO })
        assertTrue(Regions.UF_REGIAO.keys.all { uf -> Regions.DDD_UF.values.contains(uf) })
    }

    @Test fun `classifica DDD conhecido`() {
        val a = Regions.areaOf("+5521999990071")
        assertEquals("21", a.ddd); assertEquals("RJ", a.uf); assertEquals("Sudeste", a.regiao)
    }

    @Test fun `DDD desconhecido nao e chutado`() {
        val a = Regions.areaOf("+5510912345678")  // DDD 10 nao existe
        assertNull(a.uf); assertTrue(a.desconhecido)
    }

    @Test fun `numero internacional`() {
        val a = Regions.areaOf("+14155552671")
        assertTrue(a.internacional); assertNull(a.uf)
    }

    @Test fun `filtro por uf`() {
        val nums = listOf(n("+5521999990071"), n("+5511987654321"), n("+5531988887777"))
        val rj = Regions.apply(nums, Regions.Filter(uf = setOf("RJ")))
        assertEquals(1, rj.size); assertEquals("+5521999990071", rj[0].e164)
    }

    @Test fun `filtro por regiao`() {
        val nums = listOf(n("+5521999990071"), n("+5571991112222"), n("+5511987654321"))
        val sudeste = Regions.apply(nums, Regions.Filter(regiao = setOf("Sudeste")))
        assertEquals(2, sudeste.size)
    }

    @Test fun `excluir ddd`() {
        val nums = listOf(n("+5511987654321"), n("+5521999990071"))
        val semSp = Regions.apply(nums, Regions.Filter(excluirDdd = setOf("11")))
        assertEquals(1, semSp.size); assertEquals("21", Regions.areaOf(semSp[0].e164).ddd)
    }

    @Test fun `internacional so entra se pedido`() {
        val nums = listOf(n("+5521999990071"), n("+14155552671"))
        assertEquals(1, Regions.apply(nums, Regions.Filter(uf = setOf("RJ"))).size)
        assertEquals(2, Regions.apply(nums, Regions.Filter(incluirInternacional = true)).size)
    }

    @Test fun `contagem por uf`() {
        val nums = listOf(n("+5521999990071"), n("+5521999990002"), n("+5511987654321"))
        val c = Regions.countByUf(nums)
        assertEquals(2, c["RJ"]); assertEquals(1, c["SP"])
    }
}
