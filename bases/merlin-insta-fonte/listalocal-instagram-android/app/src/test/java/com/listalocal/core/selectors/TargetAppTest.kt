package com.listalocal.core.selectors

import com.listalocal.service.CompatCheck
import com.listalocal.service.Modo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O app opera sobre um pacote so. Estes testes fixam isso e o perfil medido. */
class TargetAppTest {

    @Test
    fun `so o Instagram e alvo`() {
        assertEquals(listOf("com.instagram.android"), TargetApp.PACKAGES)
        assertEquals(TargetApp.INSTAGRAM, TargetApp.fromPackage("com.instagram.android"))
        assertNull(TargetApp.fromPackage("com.instagram.lite"))
        assertNull(TargetApp.fromPackage("com.whatsapp"))
        assertNull(TargetApp.fromPackage(null))
    }

    @Test
    fun `chaves exigidas por modo existem no perfil`() {
        val prof = SelectorProfile.IG_448
        (SelectorProfile.DM_KEYS + SelectorProfile.CF_KEYS).forEach { key ->
            assertTrue("faltou $key", prof.idFor(key) != null || prof.descriptionsFor(key).isNotEmpty())
        }
    }

    @Test
    fun `o Enviar e procurado pelo id exato, nunca por pedaco de send`() {
        val prof = SelectorProfile.IG_448
        assertEquals("row_thread_composer_send_button_container", prof.idFor("send"))
        // Existem floating_send_container, send_button_pill_container e send_label dentro das mensagens.
        assertFalse(prof.ids.values.any { it == "send" || it == "send_label" })
    }

    @Test
    fun `o que nao foi lido pela acessibilidade esta marcado para calibrar`() {
        val prof = SelectorProfile.IG_448
        listOf("thread_header", "header_subtitle", "composer", "send", "cf_row", "not_sent", "restriction")
            .forEach { assertTrue("sem marca CALIBRAR: $it", it in prof.calibrar) }
        // Lidos na arvore de acessibilidade real (atividade principal).
        listOf("direct_tab", "profile_tab", "inbox_title", "new_message", "options", "cf_entry")
            .forEach { assertFalse("marcado a toa: $it", it in prof.calibrar) }
    }

    @Test
    fun `sem versao nao ha perfil`() {
        assertNull(SelectorProfile.forVersion(""))
        assertEquals(SelectorProfile.IG_448, SelectorProfile.forVersion("449.0.0.1.2"))
    }
}

/** A conferencia so pode dizer "pode operar" com prova completa, nesta versao. */
class CompatCheckTest {

    private val tudoDm = SelectorProfile.DM_KEYS.associateWith { true }

    @Test
    fun `conferencia completa libera o modo so na mesma versao`() {
        val c = CompatCheck(Modo.DM, "448.0.0.52.84", conta = "minhaconta", found = tudoDm)
        assertTrue(c.ok)
        assertTrue(c.valeParaVersao("448.0.0.52.84"))
        assertFalse("o Instagram atualizou: confere de novo", c.valeParaVersao("449.0.0.1.2"))
        assertFalse(c.valeParaVersao(null))
    }

    @Test
    fun `faltando uma chave nao libera`() {
        val c = CompatCheck(Modo.DM, "448", found = tudoDm + ("new_chat_to" to false))
        assertFalse(c.ok)
        assertEquals(listOf("new_chat_to"), c.missing)
    }

    @Test
    fun `conferencia vazia ou com falha nao autoriza nada`() {
        assertFalse(CompatCheck(Modo.DM, "448").ok)
        assertFalse(CompatCheck(Modo.AMIGOS_PROXIMOS, "448", failure = "o Instagram não está instalado").ok)
    }

    @Test
    fun `chaves de um modo nao liberam o outro`() {
        val c = CompatCheck(Modo.AMIGOS_PROXIMOS, "448", found = tudoDm)
        assertFalse(c.ok)
    }
}
