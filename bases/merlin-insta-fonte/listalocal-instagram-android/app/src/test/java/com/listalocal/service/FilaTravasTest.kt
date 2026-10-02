package com.listalocal.service

import com.listalocal.core.selectors.SelectorProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fila do modo DM nas travas achadas na caca de 26/09 (falso-bloqueio e trava frouxa). Cada teste falha sem a correcao. */
class FilaTravasTest {

    private val ig = FakeInstagram()
    private val prof = SelectorProfile.IG_448
    private val flow = DmFlow(ig, prof, FakeRegistro(ig.log))
    private val fila = Fila(ig) { true }
    private val st get() = AutomationController.state.value

    private fun campanha(origem: Origem) = Campanha(
        conta = "minhaconta", origem = origem, mensagens = listOf("Oi! Sábado tem evento."),
        velocidade = Velocidade.MUITO_RAPIDO, inicio = 0,
    )

    private fun rodar(c: Campanha, anteriores: List<PersonResult> = emptyList()) = runBlocking {
        AutomationController.startCampanha(c, anteriores)
        val fonte = if (c.origem == Origem.SEGUIDORES) FonteSeguidores(ig, prof) else FonteConversas(ig, prof)
        fila.aoVivo(fonte, flow, Fila.Filtro("minhaconta")) {}
    }

    private fun seguidores(vararg us: String, montar: FakeInstagram.Conversa.() -> Unit = {}) = us.forEach { u ->
        ig.seguidores += u to "Nome $u"
        ig.conversa(u) { nome = "Nome $u"; montar() }
    }

    private fun enviados() = st.results.filter { it.desfecho == Desfecho.ENVIADO }.map { it.username }

    @Test fun `tres conversas com mensagens temporarias seguidas nao param a fila como tela nao conferida`() {
        seguidores("t1", "t2", "t3") { dica = "Mensagem temporária..." }
        seguidores("ana")
        rodar(campanha(Origem.SEGUIDORES))
        assertEquals(RunPhase.COMPLETED, st.phase)
        assertEquals(listOf("ana"), enviados())
        assertEquals(3, st.results.count { it.desfecho == Desfecho.FALHA })
    }

    @Test fun `retomada tenta de novo quem ficou com a conversa aberta e nao lida`() {
        seguidores("ana", "bia")
        val antes = listOf(PersonResult("ana", "ana", 0, Desfecho.FALHA, DmFlow.NAO_LIDA))
        rodar(campanha(Origem.SEGUIDORES), anteriores = antes)
        assertEquals(listOf("ana", "bia"), enviados())
        assertEquals("uma linha por pessoa no relatorio", 1, st.results.count { it.username == "ana" })
    }

    @Test fun `comecar a partir de - conversas sem arroba legivel antes do ponto de partida contam para a parada de 3`() {
        // A acessibilidade abre as conversas, mas nao le o @ de nenhuma: sem a parada, o app abria (e marcava como
        // lida) a caixa de entrada inteira procurando o @ de partida.
        (1..6).forEach { i -> ig.conversa("o$i") { nome = "Online $i"; cabecalho = "Online agora"; cartao = null } }
        ig.caixa += (1..6).map { "o$it" }
        val e = assertThrows(Fila.FailedSafe::class.java) { rodar(campanha(Origem.CONVERSAS).copy(aPartirDe = "zeca")) }
        assertTrue(e.message, e.message!!.startsWith("${Fila.MAX_SEM_TELA} pessoas seguidas"))
        assertEquals(Fila.MAX_SEM_TELA, ig.log.count { it.startsWith("tocar linha") })
        assertEquals(-1, ig.indice("abrir "))
    }
}
