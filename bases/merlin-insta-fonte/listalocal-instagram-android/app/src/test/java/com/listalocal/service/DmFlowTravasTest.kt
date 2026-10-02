package com.listalocal.service

import com.listalocal.core.followers.Follower
import com.listalocal.core.selectors.SelectorProfile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Travas do envio numa situacao VALIDA (falso-bloqueio) e travas frouxas, achadas antes do dono (caca de 26/09).
 * Cada teste falha sem a correcao.
 */
class DmFlowTravasTest {

    private val ig = FakeInstagram()
    private val registro = FakeRegistro(ig.log)
    private val flow = DmFlow(ig, SelectorProfile.IG_448, registro)
    private val ana = Follower("ana.souza", "Ana")
    private val TOQUE_ENVIAR = "tocar row_thread_composer_send_button_container"

    // ---------------- avisos x texto das bolhas ----------------

    @Test fun `bolha do seguidor com tente novamente mais tarde nao pausa a fila como restricao`() = runTest {
        ig.conversa("ana.souza") { mensagens += "o site caiu, tente novamente mais tarde" }
        val r = flow.enviar(ana, "Oi, Ana! Sábado tem evento.")
        assertEquals(r.motivo, Desfecho.ENVIADO, r.desfecho)
        assertFalse(r.restricao)
    }

    @Test fun `nosso texto com as palavras de um aviso envia, confirma e nao pausa`() = runTest {
        ig.conversa("ana.souza")
        val texto = "Se o link não abrir, tente novamente mais tarde. Mensagem não enviada? Responda aqui."
        val r = flow.enviar(ana, texto)
        assertEquals(r.motivo, Desfecho.ENVIADO, r.desfecho)
        assertFalse(r.restricao)
        assertEquals(1, ig.log.count { it == TOQUE_ENVIAR })
    }

    private val TEXTO = "Oi, Ana! Sábado tem evento."

    // ---------------- pedido de parar no historico (so as bolhas da tela estao na arvore) ----------------

    @Test fun `pedido de parar mais antigo que a tela barra o envio`() = runTest {
        ig.conversa("ana.souza") {
            antigas += listOf("oi, tudo bem?", "para de me mandar mensagem")
            mensagens += listOf("bom dia", "tudo certo")
        }
        val r = flow.enviar(ana, TEXTO)
        assertEquals(r.motivo, Desfecho.PEDIU_PARA_PARAR, r.desfecho)
        assertEquals(-1, ig.indice("escrever"))
        assertEquals(-1, ig.indice(TOQUE_ENVIAR))
    }

    @Test fun `historico sem pedido de parar volta ao fim da conversa antes de escrever e envia`() = runTest {
        ig.conversa("ana.souza") { antigas += "oi, tudo bem?"; mensagens += "bom dia" }
        val r = flow.enviar(ana, TEXTO)
        assertEquals(r.motivo, Desfecho.ENVIADO, r.desfecho)
        val tras = ig.indice("rolar para tras message_list")
        val frente = ig.indice("rolar message_list")
        assertTrue(ig.log.toString(), tras in 0 until frente && frente < ig.indice("escrever row_thread_composer_edittext 'Oi"))
        assertEquals(listOf("bom dia", TEXTO), ig.conversas.getValue("ana.souza").mensagens)
    }

    @Test fun `Pausar no meio da conferencia do historico adia a pessoa sem desfecho e sem escrever`() {
        ig.conversa("ana.souza") { antigas += "oi, tudo bem?"; mensagens += "bom dia" }
        // Como o NodeOps: com a pausa pedida, a rolagem e recusada (lanca).
        ig.bloqueado = { ig.log.lastOrNull()?.startsWith("rolar para tras") == true }
        val vez: suspend () -> Boolean = { if ("recusado" in ig.log) throw Adiado(); false }
        assertThrows(Adiado::class.java) { runBlocking { flow.enviar(ana, TEXTO, vez = vez) } }
        assertEquals(null, registro.gravados["ana.souza"])
        assertEquals(-1, ig.indice("escrever"))
    }

    // ---------------- uma leitura so, no instante errado ----------------

    @Test fun `leitura vazia logo depois do intervalo nao vira a conversa saiu da frente`() = runTest {
        ig.conversa("ana.souza")
        var nula = false
        ig.leituraNula = { nula.also { nula = false } }
        val r = flow.enviar(ana, TEXTO, vez = { nula = true; true })
        assertEquals(r.motivo, Desfecho.ENVIADO, r.desfecho)
    }

    @Test fun `leituras vazias ate o fim da prova pelo nome nao viram a conversa fechou`() = runTest {
        ig.conversa("ana.souza") { nome = "Ana Souza"; cabecalho = "Online agora"; cartao = null }
        var t0: Long? = null
        ig.leituraNula = {
            ig.telaAtual == "conversa" && (t0 ?: ig.relogio.also { t0 = it }).let { t -> ig.relogio > t && ig.relogio <= t + DmFlow.PROVA_MS }
        }
        val r = flow.enviar(Follower("ana.souza", "Ana Souza"), TEXTO)
        assertEquals(r.motivo, Desfecho.ENVIADO, r.desfecho)
    }

    // ---------------- conversa que abriu e nao foi lida ----------------

    @Test fun `conversa aberta numa janela que o app nao le nao e conta nao encontrada`() = runTest {
        ig.visao = FakeInstagram.Visao.MODAL_ILEGIVEL
        ig.conversa("ana.souza")
        val r = flow.enviar(ana, TEXTO)
        assertEquals(Desfecho.FALHA, r.desfecho)
        assertEquals(DmFlow.NAO_LIDA, r.motivo)
        assertEquals(-1, ig.indice("escrever"))
    }

    // ---------------- Parar antes de escrever ----------------

    // (Parar enquanto a conversa abre, sem desfecho: DmFlowTest "parada antes de escrever nao grava desfecho".)

    @Test fun `Parar com o texto ja escrito e antes do commit fica FALHA e o campo e limpo`() {
        ig.conversa("ana.souza") { enviarHabilitado = false } // prende na conferencia do texto
        ig.interromperApos = "escrever row_thread_composer_edittext 'Oi"
        assertThrows(Interrompido::class.java) { runBlocking { flow.enviar(ana, TEXTO) } }
        assertEquals(Desfecho.FALHA, registro.gravados["ana.souza"])
        assertEquals(-1, ig.indice("commit"))
        assertEquals(null, ig.conversas.getValue("ana.souza").campo)
    }

    // ---------------- recusas por regra (a conversa foi lida e conferida) ----------------

    @Test fun `mensagens temporarias em espanhol tambem nao recebem`() = runTest {
        ig.conversa("ana.souza") { dica = "Mensaje temporal..." }
        val r = flow.enviar(ana, TEXTO)
        assertEquals(Desfecho.FALHA, r.desfecho)
        assertTrue(r.recusada)
        assertEquals(-1, ig.indice("escrever"))
    }

    @Test fun `rascunho do dono no campo nao e apagado nem enviado`() = runTest {
        ig.conversa("ana.souza") { campo = "rascunho do dono" }
        val r = flow.enviar(ana, TEXTO)
        assertEquals(r.motivo, Desfecho.FALHA, r.desfecho)
        assertTrue(r.recusada)
        assertEquals("rascunho do dono", ig.conversas.getValue("ana.souza").campo)
        assertEquals(-1, ig.indice("escrever"))
        assertEquals(-1, ig.indice(TOQUE_ENVIAR))
    }

    @Test fun `temporarias e mensagem nao enviada antiga sao recusas por regra, tela nao conferida nao`() = runTest {
        ig.conversa("ana.souza") { dica = "Mensagem temporária..." }
        assertTrue(flow.enviar(ana, TEXTO).recusada)
        ig.conversa("bia") { avisos += "Não enviada" }
        assertTrue(flow.enviar(Follower("bia", "Bia"), TEXTO).recusada)
        ig.conversa("caio") { cabecalho = null; cartao = null }
        assertFalse(flow.enviar(Follower("caio", "Caio"), TEXTO).recusada)
    }

    // ---------------- toque em Enviar recusado ----------------

    @Test fun `toque em Enviar recusado com o texto ainda no campo e FALHA, nao INCERTO`() = runTest {
        ig.conversa("ana.souza")
        ig.recusarEnviar = true
        val r = flow.enviar(ana, TEXTO)
        assertEquals(r.motivo, Desfecho.FALHA, r.desfecho)
        assertEquals(Desfecho.FALHA, registro.gravados["ana.souza"])
        assertTrue(r.enviadas.isEmpty())
        assertTrue(ig.conversas.getValue("ana.souza").mensagens.isEmpty())
        assertEquals(null, ig.conversas.getValue("ana.souza").campo) // o texto nao fica no campo
    }

    @Test fun `toque em Enviar sem evidencia continua INCERTO`() = runTest {
        ig.conversa("ana.souza")
        ig.envio = FakeInstagram.Envio.NADA
        assertEquals(Desfecho.INCERTO, flow.enviar(ana, TEXTO).desfecho)
    }
}
