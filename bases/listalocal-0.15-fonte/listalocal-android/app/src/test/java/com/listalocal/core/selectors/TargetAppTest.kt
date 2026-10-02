package com.listalocal.core.selectors

import com.listalocal.service.CompatCheck
import com.listalocal.service.CompatIssue
import com.listalocal.service.RecoveryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O app opera sobre dois pacotes diferentes. Estes testes fixam as regras que
 * impedem o motor de tratar um pelo outro — o erro que faria o app procurar
 * `com.whatsapp:id/...` dentro do WhatsApp Business.
 */
class TargetAppTest {

    @Test
    fun `pacotes dos dois apps sao distintos e conhecidos`() {
        assertEquals("com.whatsapp", TargetApp.WHATSAPP.packageName)
        assertEquals("com.whatsapp.w4b", TargetApp.WHATSAPP_BUSINESS.packageName)
        assertEquals(2, TargetApp.PACKAGES.distinct().size)
    }

    @Test
    fun `resolve app pelo pacote e ignora desconhecidos`() {
        assertEquals(TargetApp.WHATSAPP, TargetApp.fromPackage("com.whatsapp"))
        assertEquals(TargetApp.WHATSAPP_BUSINESS, TargetApp.fromPackage("com.whatsapp.w4b"))
        assertNull(TargetApp.fromPackage("com.outro.app"))
        assertNull(TargetApp.fromPackage(null))
    }

    @Test
    fun `perfil casa pela versao mas so e validado no WhatsApp comum`() {
        val prof = SelectorProfile.forApp(TargetApp.WHATSAPP_BUSINESS, "2.26.34.75")
        assertTrue("a versao 2.26 deve encontrar um perfil candidato", prof != null)
        assertTrue(prof!!.isValidatedFor(TargetApp.WHATSAPP))
        assertFalse(
            "sem validacao em aparelho, o Business nao pode ser dado como suportado",
            prof.isValidatedFor(TargetApp.WHATSAPP_BUSINESS),
        )
    }

    @Test
    fun `versao fora das validadas nao tem perfil`() {
        assertNull(SelectorProfile.forApp(TargetApp.WHATSAPP, "2.19.0"))
        assertNull(SelectorProfile.forVersion(""))
    }

    @Test
    fun `chaves exigidas na verificacao existem no perfil`() {
        val prof = SelectorProfile.WA_2_26
        val exigidas = prof.requiredIdKeys()
        assertTrue(exigidas.isNotEmpty())
        exigidas.forEach { assertTrue("faltou id para $it", prof.idFor(it) != null) }
    }

    @Test
    fun `perfil reconhece a entrada oficial pela aba ferramentas do Business`() {
        val prof = SelectorProfile.WA_2_26

        assertTrue("faltou a aba Ferramentas", "Ferramentas" in prof.descriptionsFor("tools_tab"))
        assertTrue(
            "faltou Transmissões comerciais",
            "Transmissões comerciais" in prof.descriptionsFor("tools_business_broadcasts"),
        )
    }
}

/** A verificacao so pode dizer "pode operar" com prova completa. */
class CompatCheckTest {

    @Test
    fun `Business sem entrada recomenda recuperar o recurso antes de repetir`() {
        val bloqueadoNaConta = CompatCheck(
            app = TargetApp.WHATSAPP_BUSINESS,
            version = "2.26.34.75",
            failure = "a transmissão não aparece nesta conta",
            issue = CompatIssue.BROADCAST_ENTRY_NOT_FOUND,
        )

        assertEquals(
            listOf(
                RecoveryAction.CHECK_BUSINESS_PLATFORM,
                RecoveryAction.RETRY_COMPATIBILITY,
                RecoveryAction.TRY_ANOTHER_BUSINESS_ACCOUNT,
                RecoveryAction.USE_STANDARD_WHATSAPP,
            ),
            bloqueadoNaConta.recommendedActions,
        )
    }

    @Test
    fun `falha de seletor nao e apresentada como bloqueio da conta`() {
        val interfaceMudou = CompatCheck(
            app = TargetApp.WHATSAPP_BUSINESS,
            version = "2.26.34.75",
            failure = "botão de criar não encontrado",
            issue = CompatIssue.SELECTORS_MISSING,
        )

        assertTrue(interfaceMudou.recommendedActions.isEmpty())
    }

    @Test
    fun `caminho pela nova conversa nao exige menu de opcoes`() {
        val viaNovaConversa = CompatCheck(
            app = TargetApp.WHATSAPP_BUSINESS,
            version = "2.26.34.75",
            found = mapOf(
                "home_fab" to true,
                "menu_overflow" to false,
                "picker_search" to true,
                "picker_search_input" to false,
                "row_name" to true,
                "create_button" to true,
            ),
            reachedPicker = true,
        )

        assertTrue(viaNovaConversa.ok)
        assertTrue(viaNovaConversa.missing.isEmpty())
    }

    @Test
    fun `sem nenhum caminho de entrada a verificacao continua bloqueada`() {
        val semEntrada = CompatCheck(
            app = TargetApp.WHATSAPP_BUSINESS,
            version = "2.26.34.75",
            found = mapOf(
                "home_fab" to false,
                "menu_overflow" to false,
                "picker_search" to true,
                "picker_search_input" to false,
                "row_name" to true,
                "create_button" to true,
            ),
            reachedPicker = true,
        )

        assertFalse(semEntrada.ok)
        assertEquals(listOf("entrada da transmissão"), semEntrada.missing)
    }

    @Test
    fun `ok exige chegar ao seletor e achar tudo`() {
        val completo = CompatCheck(
            app = TargetApp.WHATSAPP_BUSINESS,
            version = "2.26.34.75",
            found = mapOf("home_fab" to true, "picker_search" to true),
            reachedPicker = true,
        )
        assertTrue(completo.ok)
        assertTrue(completo.missing.isEmpty())
    }

    @Test
    fun `faltando um identificador nao e compativel`() {
        val parcial = CompatCheck(
            app = TargetApp.WHATSAPP_BUSINESS,
            version = "2.26.34.75",
            found = mapOf("home_fab" to true, "create_button" to false),
            reachedPicker = true,
        )
        assertFalse(parcial.ok)
        assertEquals(listOf("create_button"), parcial.missing)
    }

    @Test
    fun `sem chegar ao seletor nunca e compativel`() {
        val semPicker = CompatCheck(
            app = TargetApp.WHATSAPP_BUSINESS,
            version = "2.26.34.75",
            found = mapOf("home_fab" to true),
            reachedPicker = false,
            failure = "nao achei 'Nova transmissao'",
        )
        assertFalse(semPicker.ok)
    }

    @Test
    fun `verificacao vazia nao autoriza nada`() {
        assertFalse(CompatCheck(TargetApp.WHATSAPP_BUSINESS, "2.26.34.75").ok)
    }
}
