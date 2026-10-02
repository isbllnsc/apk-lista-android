package com.listalocal.core.ig

import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.seguidoresFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Teste real 26/09 17:04: na lista de Seguidores o app leu o @ de um SEGUIDOR (@pessoa01) como a
 * conta aberta e travou com "A conta aberta no Instagram (@pessoa01) não é a conferida". A conta
 * vem so do titulo por id exato, nunca da assinatura "texto parecido com @".
 */
class ContaAbertaRealTest {
    private val comAssinatura = SelectorProfile.IG_448.copy(assinaturaAoVivo = true)

    @Test fun `na lista de seguidores a conta e o titulo, nao o arroba de um seguidor`() {
        val tela = seguidoresFixture("frutacarecafc", listOf("pessoa01" to "Pessoa 01", "pessoa02" to "Pessoa 02"))
        assertEquals("frutacarecafc", Evidence.conta(tela, comAssinatura, "profile_title"))
    }

    @Test fun `id aprendido errado para o titulo nao passa na frente do id fixo`() {
        // Calibracao que aprendeu o id das linhas de seguidor como "titulo da conta".
        val aprendeuErrado = comAssinatura.copy(aprendidos = mapOf("profile_title" to "follow_list_username"))
        val tela = seguidoresFixture("frutacarecafc", listOf("pessoa01" to "Pessoa 01"))
        assertEquals("frutacarecafc", Evidence.conta(tela, aprendeuErrado, "profile_title"))
    }

    @Test fun `sem o id do titulo a conta fica desconhecida, nunca vira um seguidor`() {
        val semTitulo = comAssinatura.copy(ids = comAssinatura.ids - "profile_title")
        val tela = seguidoresFixture("frutacarecafc", listOf("pessoa01" to "Pessoa 01"))
        assertNull(Evidence.conta(tela, semTitulo, "profile_title"))
    }
}
