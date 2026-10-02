package com.listalocal.service

import com.listalocal.core.selectors.SelectorProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O dono tem duas contas no mesmo Instagram (@frutacarecafc e @jvsgirao) e
 * troca entre elas. A operacao usa a conta aberta ao vivo (26/09, 08:38:
 * parou por usar a conta de outra conferencia).
 */
class ContaTest {

    private val prof = SelectorProfile.IG_448
    private val versao = "448.0.0.52.84"
    private val ig = FakeInstagram(conta = "frutacarecafc")
    private fun dm() = DmFlow(ig, prof, FakeRegistro(mutableListOf()))

    private fun campanha(conta: String, ultimo: String? = null) = Campanha(
        conta = conta, origem = Origem.SEGUIDORES, mensagens = listOf("Oi"), velocidade = Velocidade.NORMAL,
        inicio = 1L, ultimo = ultimo,
    )

    @Test fun `a conta lida ao vivo e a aberta agora, em cada uma das duas contas`() = runTest {
        assertEquals("frutacarecafc", dm().contaAberta())
        ig.conta = "jvsgirao"
        assertEquals("jvsgirao", dm().contaAberta())
        // So leitura: nada escrito, Nova mensagem nem aberta.
        assertFalse(ig.log.toString(), ig.log.any { it.startsWith("escrever") || it.startsWith("tocar Nova") })
    }

    /**
     * A conta aberta ao iniciar tem de ser a do plano ("Enviando como @"): a mensagem e os filtros foram escritos para
     * ela. Antes, o app reconferia a conta aberta sozinho, passava a operacao (e a salva) para ela e mandava a
     * mensagem comercial de @frutacarecafc como @jvsgirao, do comeco, para os seguidores pessoais.
     */
    @Test fun `conta aberta diferente da do plano para ao iniciar, sem trocar a operacao de conta`() = runTest {
        assertNull(Conta.naOperacao("frutacarecafc", dm().contaAberta()!!))
        ig.conta = "jvsgirao" // o dono trocou de conta no Instagram depois de conferir
        val msg = Conta.naOperacao("frutacarecafc", dm().contaAberta()!!)!!
        assertTrue(msg, msg.contains("@jvsgirao") && msg.contains("@frutacarecafc") && msg.contains("Nada foi enviado"))
        assertTrue(msg, msg.contains("Trocar de conta"))
        // A conferencia continua valendo em cada conta (o dono confere a outra se quiser enviar como ela).
        val (achou, conta) = dm().conferir()
        assertTrue(CompatCheck(Modo.DM, versao, conta = conta, found = achou).ok)
        assertEquals("jvsgirao", conta)
    }

    @Test fun `pausa enquanto a conta e lida ao iniciar espera o Continuar e le de novo`() = runTest {
        // O toque no Direct e recusado pela pausa: e a pausa, nao "nao consegui ver qual conta".
        AutomationController.update { RunState() }
        ig.bloqueado = { AutomationController.pauseRequested }
        AutomationController.pause()
        val fases = mutableListOf<RunPhase>()
        ig.aoEsperar = { ms ->
            if (ms == 400L && AutomationController.pauseRequested) { fases += AutomationController.state.value.phase; AutomationController.resume() }
        }
        assertEquals("frutacarecafc", Conta.lerAoIniciar(ig) { dm().contaAberta() })
        assertEquals(listOf(RunPhase.PAUSED), fases)
        assertTrue(ig.log.contains("recusado"))
    }

    @Test fun `retomar com outra conta aberta nao converte a salva nem a apaga`() {
        // A salva e de @jvsgirao; o Instagram esta em @frutacarecafc: para, e a salva continua para a conta dela.
        val salva = campanha("jvsgirao", ultimo = "ana")
        assertTrue(Conta.naOperacao(salva.conta, "frutacarecafc")!!.contains("@jvsgirao"))
        assertNull(Conta.naOperacao(salva.conta, "jvsgirao"))
    }

    @Test fun `trocar de conta abre o seletor do Instagram e espera a escolha do dono`() = runTest {
        var esperas = 0
        ig.aoEsperar = { if (ig.seletorAberto && ++esperas == 5) ig.conta = "jvsgirao" }
        assertNotNull(Conta.abrirSeletor(ig, prof))
        assertTrue(ig.log.toString(), "tocarLongo profile_tab" in ig.log)
        assertEquals("jvsgirao", Conta.esperarOutra(ig, prof, antes = "frutacarecafc"))
        // Nada escrito: a senha nunca passa pelo app.
        assertFalse(ig.log.any { it.startsWith("escrever") })
    }

    @Test fun `sem escolha no prazo, nao troca`() = runTest {
        assertNotNull(Conta.abrirSeletor(ig, prof))
        assertNull(Conta.esperarOutra(ig, prof, antes = "frutacarecafc", ms = 3_000))
    }

    @Test fun `mensagens em portugues simples dizem o que fazer`() {
        assertTrue(Conta.SEM_CONTA.contains("Iniciar de novo"))
        assertTrue(Conta.naoConferida("jvsgirao").contains("Conferir o Instagram"))
        assertTrue(Conta.naoConferida("jvsgirao").contains("@jvsgirao"))
    }

    @Test fun `modo A segue na conta conferida, ou noutra conferida na abertura`() {
        assertNull(Conta.amigosNaConta("minhaconta", "minhaconta", conferidaAgora = false))
        assertEquals(Conta.SEM_CONTA, Conta.amigosNaConta("minhaconta", null, conferidaAgora = true))
        assertEquals(Conta.naoConferida("outra"), Conta.amigosNaConta("minhaconta", "outra", conferidaAgora = false))
        assertEquals(Conta.naoConferida("outra"), Conta.amigosNaConta(null, "outra", conferidaAgora = false))
        assertNull(Conta.amigosNaConta("minhaconta", "outra", conferidaAgora = true))
    }
}
