package com.listalocal.service

import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.state.BroadcastState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modo A sobre um Instagram falso: a maquina do Lista Local dirige cada passo. */
class CloseFriendsFlowTest {

    private val ig = FakeInstagram().apply {
        amigos["ana"] = true
        amigos["bia"] = false
        amigos["caio"] = false
        amigos["zeca"] = true
    }
    private val flow = CloseFriendsFlow(ig, SelectorProfile.IG_448)
    private fun toques() = ig.log.filter { it == "tocar row_user_container" }.size

    @Test fun `abre pelo caminho real - Perfil, Opcoes, Configuracoes e atividade arrastando`() = runTest {
        flow.abrir()
        assertEquals(BroadcastState.OPENING_PICKER, flow.fsm.state)
        // Configuracoes e atividade (Bloks) nao rola pela acessibilidade: arrasta por gesto.
        assertEquals(2, ig.log.count { it == "arrastar" })
        assertEquals(0, ig.log.count { it.startsWith("rolar") })
        // O item nao aceita clique pela acessibilidade: toque no ponto dele.
        assertTrue(ig.log.toString(), "ponto Amigos Próximos, 2" in ig.log)
        assertTrue(flow.encontradas.toString(), flow.encontradas.values.all { it } && "settings" in flow.encontradas)
    }

    @Test fun `Limpar tudo nunca recebe toque`() = runTest {
        flow.abrir()
        flow.marcar("bia", adicionar = true)
        flow.marcar("zeca", adicionar = false)
        flow.prontoParaConcluir()
        flow.concluir()
        assertFalse(ig.log.toString(), ig.log.any { "row_header_action" in it || "Limpar" in it })
        assertEquals(mapOf("ana" to true, "bia" to true, "caio" to false, "zeca" to false), ig.amigos.toMap())
    }

    @Test fun `tela real - cabecalhos, Limpar tudo e Sugestoes nao viram linha`() {
        val tela = com.listalocal.core.tree.amigosFixture(listOf("ana" to true, "bia" to false, "zeca" to true))
        val linhas = com.listalocal.core.ig.Evidence.linhasAmigos(tela, SelectorProfile.IG_448)
        assertEquals(listOf("ana", "zeca", "bia"), linhas.map { it.username })
        assertEquals(listOf(true, true, false), linhas.map { it.marcada })
    }

    @Test fun `conferencia so leitura na tela real libera Amigos Proximos`() = runTest {
        val achou = flow.conferir()
        val check = CompatCheck(Modo.AMIGOS_PROXIMOS, "448.0.0.52.84", conta = flow.conta, found = achou)
        assertTrue(check.missing.toString(), check.ok)
        assertEquals(0, toques())
        assertFalse(ig.log.contains("tocar done_button"))
    }

    @Test fun `sem gesto nem rolagem, a conferencia diz o que faltou e nao libera`() = runTest {
        ig.gestos = false
        val achou = flow.conferir()
        val check = CompatCheck(Modo.AMIGOS_PROXIMOS, "448.0.0.52.84", found = achou)
        assertFalse(check.ok)
        assertEquals(listOf("cf_entry", "cf_search", "cf_done", "cf_clear", "cf_row", "cf_username"), check.missing)
        assertEquals(true, achou["settings"])
    }

    @Test fun `adiciona quem nao esta e confere a marca`() = runTest {
        flow.abrir()
        assertEquals(Desfecho.ADICIONADO, flow.marcar("bia", adicionar = true))
        assertEquals(true, ig.amigos["bia"])
        assertEquals(1, toques())
    }

    @Test fun `quem ja esta na lista nao recebe toque`() = runTest {
        flow.abrir()
        assertEquals(Desfecho.JA_NA_LISTA, flow.marcar("ana", adicionar = true))
        assertEquals(0, toques())
    }

    @Test fun `arroba que nao aparece na busca nao recebe toque`() = runTest {
        flow.abrir()
        assertEquals(Desfecho.NAO_ENCONTRADO, flow.marcar("bi", adicionar = true))
        assertEquals(0, toques())
    }

    /**
     * A busca e no servidor: por um tempo, com o @ novo ja no campo, a lista ainda mostra a resposta da busca
     * anterior. Antes, 1,2 s de lista "parada" (os mesmos @) davam "nao encontrado" calado para quem existe.
     */
    @Test fun `resposta da busca atrasada - as linhas da busca anterior nao viram nao encontrado`() = runTest {
        ig.atrasoBusca = 2_000
        flow.abrir()
        assertEquals(Desfecho.ADICIONADO, flow.marcar("bia", adicionar = true))
        ig.esperar(3_000) // a resposta da busca por bia ja chegou: a lista mostra so bia
        assertEquals(Desfecho.ADICIONADO, flow.marcar("caio", adicionar = true))
        assertEquals(true, ig.amigos["caio"])
        assertEquals(2, toques())
    }

    @Test fun `toque sem mudanca para sem segundo toque`() {
        ig.toqueSemEfeito = true
        runBlocking { flow.abrir() }
        assertThrows(CloseFriendsFlow.ParadaSegura::class.java) { runBlocking { flow.marcar("caio", adicionar = true) } }
        assertEquals(1, toques())
        assertEquals(BroadcastState.UI_CHANGED, flow.fsm.state)
    }

    @Test fun `quem pediu para parar sai da lista e quem nao esta fica como esta`() = runTest {
        flow.abrir()
        assertEquals(Desfecho.REMOVIDO, flow.marcar("zeca", adicionar = false))
        assertEquals(false, ig.amigos["zeca"])
        assertNull(flow.marcar("caio", adicionar = false))
        assertEquals(1, toques())
    }

    @Test fun `concluir so depois de todos e confere que a tela fechou`() = runTest {
        flow.abrir()
        flow.marcar("bia", adicionar = true)
        flow.prontoParaConcluir()
        assertEquals(BroadcastState.WAITING_CONFIRMATION, flow.fsm.state)
        flow.concluir()
        assertEquals(BroadcastState.COMPLETED, flow.fsm.state)
        assertEquals(1, ig.log.count { it == "tocar done_button" })
    }

    @Test fun `marcar fora de ordem e parada segura`() {
        assertThrows(CloseFriendsFlow.ParadaSegura::class.java) { runBlocking { flow.marcar("bia", true) } }
        assertEquals(0, toques())
    }

    @Test fun `sem o item na tela a abertura para sem tocar em linha`() {
        ig.rolagensAteItem = 99
        assertThrows(CloseFriendsFlow.ParadaSegura::class.java) { runBlocking { flow.abrir() } }
        assertEquals(false, flow.encontradas["cf_entry"])
        assertEquals(0, toques())
    }

    @Test fun `conferencia navega e volta sem tocar em linha nem em Concluir`() = runTest {
        val achou = flow.conferir()
        assertTrue(achou.toString(), achou.values.all { it } && "cf_search" in achou)
        assertEquals("minhaconta", flow.conta)
        assertEquals(0, toques())
        assertFalse(ig.log.contains("tocar done_button"))
    }
}
