package com.listalocal.service

import com.listalocal.core.followers.Follower
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.state.DmState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O envio de UMA mensagem sobre um Instagram falso. O que importa aqui e a
 * ORDEM: prova do @ antes de escrever, commit antes do toque, um toque so, e
 * nenhum envio sem prova.
 */
class DmFlowTest {

    private val ig = FakeInstagram()
    private val registro = FakeRegistro(ig.log)
    private val flow = DmFlow(ig, SelectorProfile.IG_448, registro)
    private val ana = Follower("ana.souza", "Ana")
    private val texto = "Oi, Ana! Sábado tem evento.\nResponda PARE para não receber mais."

    private val TOQUE_ENVIAR = "tocar row_thread_composer_send_button_container"
    private val ESCREVER_TEXTO = "escrever row_thread_composer_edittext '$texto'"

    private fun enviar() = kotlinx.coroutines.runBlocking { flow.enviar(ana, texto) }

    @Test fun `caminho feliz prova, escreve, grava e toca uma vez`() = runTest {
        ig.conversa("ana.souza")
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.ENVIADO, r.desfecho)
        assertEquals(DmState.ENVIADO, r.trilha.last())
        assertEquals(Desfecho.ENVIADO, registro.gravados["ana.souza"])
        val escreveu = ig.indice(ESCREVER_TEXTO)
        val commit = ig.indice("commit ana.souza")
        val toque = ig.indice(TOQUE_ENVIAR)
        assertTrue(ig.log.toString(), escreveu in 0 until commit && commit < toque)
        assertEquals(1, ig.log.count { it == TOQUE_ENVIAR })
        assertEquals(listOf(texto), ig.conversas["ana.souza"]!!.mensagens)
        assertTrue("sai da conversa no fim", ig.log.last() == "voltar")
    }

    @Test fun `sem o arroba exato na conversa nada e escrito nem enviado`() = runTest {
        ig.conversa("ana.souza") { cabecalho = "ana.souza_"; cartao = "ana.souza_" } // outra pessoa na frente
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.FALHA, r.desfecho)
        assertTrue(r.motivo.contains("@ da pessoa"))
        assertEquals(-1, ig.indice("escrever"))
        assertEquals(-1, ig.indice("commit"))
        assertEquals(-1, ig.indice(TOQUE_ENVIAR))
    }

    @Test fun `sem a lista de mensagens na leitura o pedido de parar nao foi conferido e nada e escrito`() = runTest {
        ig.conversa("ana.souza") { semLista = true; mensagens += "PARE" }
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.FALHA, r.desfecho)
        assertTrue(r.motivo, r.motivo.contains("pedido de parar"))
        assertEquals(-1, ig.indice("escrever"))
        assertEquals(-1, ig.indice("commit"))
    }

    @Test fun `cabecalho sem arroba exposto e sem cartao e falha sem escrever`() = runTest {
        ig.conversa("ana.souza") { cabecalho = null; cartao = null }
        assertEquals(Desfecho.FALHA, flow.enviar(ana, texto).desfecho)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `subtitulo em Online agora com o cartao do topo prova e envia`() = runTest {
        ig.conversa("ana.souza") { cabecalho = "Online agora" }
        assertEquals(Desfecho.ENVIADO, flow.enviar(ana, texto).desfecho)
    }

    @Test fun `conversa que diz que nao se seguem nao recebe`() = runTest {
        ig.conversa("ana.souza") { cartaoExtra += "Vocês não se seguem no Instagram" }
        assertEquals(Desfecho.NAO_SEGUE, flow.enviar(ana, texto).desfecho)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `mensagens temporarias ligadas nao recebem`() = runTest {
        ig.conversa("ana.souza") { dica = "Mensagem temporária..." }
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.FALHA, r.desfecho)
        assertTrue(r.motivo.contains("temporárias"))
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `temporarias so com a opcao explicita, e ai envia uma vez`() = runTest {
        ig.conversa("ana.souza") { dica = "Mensagem temporária..." }
        val r = flow.enviar(ana, texto, aceitarTemporaria = true)
        assertEquals(Desfecho.ENVIADO, r.desfecho)
        assertEquals(1, ig.log.count { it == TOQUE_ENVIAR })
    }

    @Test fun `enviar desabilitado nunca e tocado`() = runTest {
        ig.conversa("ana.souza") { enviarHabilitado = false }
        assertEquals(Desfecho.FALHA, flow.enviar(ana, texto).desfecho)
        assertEquals(-1, ig.indice("commit"))
        assertEquals(-1, ig.indice(TOQUE_ENVIAR))
    }

    @Test fun `arroba que nao existe nao abre nada e e nao encontrado`() = runTest {
        val r = flow.enviar(Follower("ninguem", ""), texto)
        assertEquals(Desfecho.NAO_ENCONTRADO, r.desfecho)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `pedido de parar na conversa barra o envio`() = runTest {
        ig.conversa("ana.souza") { mensagens += listOf(texto, "Pare, por favor") }
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.PEDIU_PARA_PARAR, r.desfecho)
        assertEquals(Desfecho.PEDIU_PARA_PARAR, registro.gravados["ana.souza"])
        assertEquals(-1, ig.indice("escrever"))
        assertEquals(-1, ig.indice(TOQUE_ENVIAR))
    }

    @Test fun `nosso texto anterior com a instrucao de parar nao barra`() = runTest {
        ig.conversa("ana.souza") { mensagens += texto }
        assertEquals(Desfecho.ENVIADO, flow.enviar(ana, texto).desfecho)
    }

    @Test fun `sem evidencia depois do toque e INCERTO e ficou gravado antes`() = runTest {
        ig.conversa("ana.souza")
        ig.envio = FakeInstagram.Envio.NADA
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.INCERTO, r.desfecho)
        assertTrue(Desfecho.INCERTO.bloqueiaNovoEnvio)
        assertTrue(ig.indice("commit ana.souza") < ig.indice(TOQUE_ENVIAR))
        assertEquals(1, ig.log.count { it == TOQUE_ENVIAR })
        // O texto que ficou no campo e apagado: nada de rascunho para um toque acidental.
        assertEquals(null, ig.conversas["ana.souza"]!!.campo)
    }

    @Test fun `outra conversa na frente depois do toque nao prova o envio`() = runTest {
        ig.conversa("ana.souza") { nome = "Ana Souza" }
        // Outra pessoa que ja recebeu o mesmo texto num lote anterior.
        ig.conversa("bia") { nome = "Bia Lima"; mensagens += texto }
        ig.envio = FakeInstagram.Envio.NADA
        ig.aposEnviarAbre = "bia"
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.INCERTO, r.desfecho)
        assertEquals(Desfecho.INCERTO, registro.gravados["ana.souza"])
    }

    @Test fun `mensagem marcada como nao enviada e INCERTO`() = runTest {
        ig.conversa("ana.souza")
        ig.envio = FakeInstagram.Envio.NAO_ENVIADA
        assertEquals(Desfecho.INCERTO, flow.enviar(ana, texto).desfecho)
    }

    @Test fun `restricao depois do toque e INCERTO e pede pausa`() = runTest {
        ig.conversa("ana.souza")
        ig.envio = FakeInstagram.Envio.RESTRICAO
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.INCERTO, r.desfecho)
        assertTrue(r.restricao)
    }

    @Test fun `restricao ja na tela pausa antes de escrever`() = runTest {
        ig.conversa("ana.souza") { avisos += "Tente novamente mais tarde" }
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.FALHA, r.desfecho)
        assertTrue(r.restricao)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `sem gravar o commit nao ha toque`() = runTest {
        ig.conversa("ana.souza")
        registro.commitFunciona = false
        assertEquals(Desfecho.FALHA, flow.enviar(ana, texto).desfecho)
        assertEquals(-1, ig.indice(TOQUE_ENVIAR))
        assertEquals(null, ig.conversas["ana.souza"]!!.campo)
    }

    @Test fun `texto cortado no campo e falha antes do commit`() = runTest {
        ig.conversa("ana.souza")
        ig.campoCorta = 10
        assertEquals(Desfecho.FALHA, flow.enviar(ana, texto).desfecho)
        assertEquals(-1, ig.indice("commit"))
        assertEquals(-1, ig.indice(TOQUE_ENVIAR))
        assertEquals(null, ig.conversas["ana.souza"]!!.campo)
    }

    @Test fun `mensagem nao enviada antiga na conversa pede conferencia a mao`() = runTest {
        ig.conversa("ana.souza") { avisos += "Não enviada" }
        assertEquals(Desfecho.FALHA, flow.enviar(ana, texto).desfecho)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `conta indisponivel`() = runTest {
        ig.conversa("ana.souza") { avisos += "Esta conta não pode receber mensagens" }
        assertEquals(Desfecho.INDISPONIVEL, flow.enviar(ana, texto).desfecho)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `duas pessoas seguidas saem da conversa entre uma e outra`() = runTest {
        ig.conversa("ana.souza")
        ig.conversa("bia")
        assertEquals(Desfecho.ENVIADO, flow.enviar(ana, texto).desfecho)
        assertEquals(Desfecho.ENVIADO, flow.enviar(Follower("bia", "Bia"), "Oi Bia").desfecho)
        assertTrue(ig.indice("abrir bia") > ig.log.indexOfLast { it.startsWith("gravar ana.souza") })
        assertEquals(listOf("Oi Bia"), ig.conversas["bia"]!!.mensagens)
    }

    @Test fun `parada no meio depois do commit fica INCERTO e repassa`() {
        ig.conversa("ana.souza")
        ig.envio = FakeInstagram.Envio.NADA
        ig.interromperApos = TOQUE_ENVIAR
        assertThrows(Interrompido::class.java) { enviar() }
        assertEquals(Desfecho.INCERTO, registro.gravados["ana.souza"])
    }

    @Test fun `parada antes de escrever nao grava desfecho e nada e tocado`() {
        // Nada aconteceu na conversa: sem desfecho, a retomada tenta a pessoa de novo (FALHA a pulava para sempre).
        ig.conversa("ana.souza") { cabecalho = null; cartao = null } // prende na espera da prova
        ig.interromperApos = "abrir"
        assertThrows(Interrompido::class.java) { enviar() }
        assertEquals(null, registro.gravados["ana.souza"])
        assertEquals(-1, ig.indice("escrever"))
        assertEquals(-1, ig.indice(TOQUE_ENVIAR))
    }

    // ---- Janelas, prazos e variacoes do cabecalho (INSTAGRAM-APP-REAL.md, TESTE-ENVIO-REAL.md) ----

    @Test fun `envia com a Modal em qualquer das leituras de janela medidas`() {
        for (visao in listOf(
            FakeInstagram.Visao.NORMAL, FakeInstagram.Visao.MODAL_ABAIXO,
            FakeInstagram.Visao.MODAL_FORA_DA_LISTA, FakeInstagram.Visao.SO_RESERVAS,
        )) {
            val ig = FakeInstagram().apply { this.visao = visao; conversa("ana.souza") }
            val r = kotlinx.coroutines.runBlocking { DmFlow(ig, SelectorProfile.IG_448, FakeRegistro(ig.log)).enviar(ana, texto) }
            assertEquals(visao.name, Desfecho.ENVIADO, r.desfecho)
            assertEquals(visao.name, 1, ig.log.count { it == TOQUE_ENVIAR })
        }
    }

    @Test fun `outro app na frente nada e escrito nem tocado`() = runTest {
        ig.visao = FakeInstagram.Visao.OUTRO_APP_NA_FRENTE
        ig.conversa("ana.souza")
        // Sem nada do Instagram legivel nao ha como saber se a conta existe: FALHA, nunca "Conta nao encontrada".
        assertEquals(Desfecho.FALHA, flow.enviar(ana, texto).desfecho)
        assertEquals(-1, ig.indice("escrever"))
        assertEquals(-1, ig.indice("tocar"))
    }

    @Test fun `link que nao abre em 5 s e nao encontrado mesmo com leitura lenta`() = runTest {
        ig.custoLeitura = 800 // leitura da arvore na rodada 1
        val r = flow.enviar(Follower("ninguem", ""), texto)
        assertEquals(Desfecho.NAO_ENCONTRADO, r.desfecho)
        assertTrue(r.motivo.contains("5 s"))
        // O prazo e no relogio: por contagem de voltas seriam 20 voltas de 1,05 s.
        assertTrue("relogio=${ig.relogio}", ig.relogio in 5_000L..8_000L)
    }

    @Test fun `conversa que abre em 2,7 s envia`() = runTest {
        ig.conversa("ana.souza")
        ig.atrasoAbrir = 2_700
        assertEquals(Desfecho.ENVIADO, flow.enviar(ana, texto).desfecho)
    }

    @Test fun `conversa que so abriria depois de 5 s e nao encontrado sem escrever`() = runTest {
        ig.conversa("ana.souza")
        ig.atrasoAbrir = 6_000
        assertEquals(Desfecho.NAO_ENCONTRADO, flow.enviar(ana, texto).desfecho)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `Online agora em conversa longa prova pelo nome do seguidor`() = runTest {
        ig.conversa("ana.souza") { nome = "Ana Souza"; cabecalho = "Online agora"; cartao = null }
        val r = flow.enviar(Follower("ana.souza", "Ana Souza"), texto)
        assertEquals(Desfecho.ENVIADO, r.desfecho)
        // So depois do prazo da prova do @ (o subtitulo pode voltar a mostrar o @).
        assertTrue(ig.relogio >= DmFlow.PROVA_MS)
    }

    @Test fun `Online agora com nome diferente falha sem escrever`() = runTest {
        ig.conversa("ana.souza") { nome = "Ana Souza"; cabecalho = "Online agora"; cartao = null }
        assertEquals(Desfecho.FALHA, flow.enviar(Follower("ana.souza", "Ana Sousa"), texto).desfecho)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `mesmo nome com outro arroba no subtitulo falha sem escrever`() = runTest {
        ig.conversa("ana.souza") { nome = "Ana Souza"; cabecalho = "ana.souza.oficial"; cartao = null }
        assertEquals(Desfecho.FALHA, flow.enviar(Follower("ana.souza", "Ana Souza"), texto).desfecho)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `conversa comercial prova pelo arroba no titulo`() = runTest {
        ig.conversa("loja.oficial") { nome = "loja.oficial"; cabecalho = "Conversa comercial"; cartao = null }
        assertEquals(Desfecho.ENVIADO, flow.enviar(Follower("loja.oficial", "Loja"), texto).desfecho)
    }

    @Test fun `dialogo de restricao numa janela por cima depois do toque e INCERTO e pausa`() = runTest {
        ig.conversa("ana.souza")
        ig.envio = FakeInstagram.Envio.RESTRICAO_DIALOGO
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.INCERTO, r.desfecho)
        assertTrue(r.restricao)
        assertEquals(1, ig.log.count { it == TOQUE_ENVIAR })
    }

    @Test fun `dialogo de restricao ja aberto barra antes de escrever`() = runTest {
        ig.conversa("ana.souza")
        ig.dialogo = com.listalocal.core.tree.ig(text = "Tente novamente mais tarde")
        val r = flow.enviar(ana, texto)
        assertEquals(Desfecho.FALHA, r.desfecho)
        assertTrue(r.restricao)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `leitura lenta nao estica o envio alem dos prazos`() = runTest {
        ig.conversa("ana.souza")
        ig.custoLeitura = 800
        ig.envio = FakeInstagram.Envio.NADA
        assertEquals(Desfecho.INCERTO, flow.enviar(ana, texto).desfecho)
        val teto = DmFlow.ABRIR_MS + DmFlow.PROVA_MS + DmFlow.CONFERIR_MS + DmFlow.EVIDENCIA_MS + 10_000L
        assertTrue("relogio=${ig.relogio}", ig.relogio < teto)
    }

    @Test fun `conferencia le a conta, ve Nova mensagem e volta sem escrever`() = runTest {
        val (achou, conta) = flow.conferir()
        assertEquals(
            mapOf("direct_tab" to true, "inbox_title" to true, "new_message" to true, "new_chat_to" to true),
            achou,
        )
        assertEquals("minhaconta", conta)
        assertFalse(ig.log.any { it.startsWith("abrir ") || it.startsWith("escrever") })
        assertEquals("voltar", ig.log.last())
    }
}
