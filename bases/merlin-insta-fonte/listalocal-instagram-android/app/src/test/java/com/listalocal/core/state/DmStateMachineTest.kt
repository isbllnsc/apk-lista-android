package com.listalocal.core.state

import com.listalocal.core.state.DmState.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DmStateMachineTest {

    private fun ate(vararg eventos: DmEvent) = DmStateMachine().also { m -> eventos.forEach { m.transition(it) } }

    @Test fun `caminho feliz ate ENVIADO`() {
        val m = ate(DmEvent.Abrir, DmEvent.ConversaProvada, DmEvent.TextoConferido, DmEvent.Commit, DmEvent.Enviado)
        assertEquals(ENVIADO, m.state)
        assertEquals(listOf(PRONTO, ABRINDO, CONVERSA_PROVADA, TEXTO_CONFERIDO, COMMIT, ENVIADO), m.trail)
    }

    @Test fun `imprevisto antes do commit e FALHA`() {
        assertEquals(FALHA, ate(DmEvent.Abrir, DmEvent.Inesperado).state)
        assertEquals(FALHA, ate(DmEvent.Abrir, DmEvent.ConversaProvada, DmEvent.Inesperado).state)
        assertEquals(FALHA, ate(DmEvent.Abrir, DmEvent.ConversaProvada, DmEvent.TextoConferido, DmEvent.Inesperado).state)
    }

    @Test fun `sem evidencia depois do commit e INCERTO`() {
        val m = ate(DmEvent.Abrir, DmEvent.ConversaProvada, DmEvent.TextoConferido, DmEvent.Commit, DmEvent.Inesperado)
        assertEquals(INCERTO, m.state)
    }

    @Test fun `nao se pula a prova da conversa`() {
        // Texto conferido sem conversa provada: nunca chega ao commit.
        assertEquals(FALHA, ate(DmEvent.Abrir, DmEvent.TextoConferido).state)
        assertEquals(FALHA, ate(DmEvent.Abrir, DmEvent.Commit).state)
        assertEquals(FALHA, ate(DmEvent.Commit).state)
    }

    @Test fun `nao se pula a conferencia do texto`() {
        assertEquals(FALHA, ate(DmEvent.Abrir, DmEvent.ConversaProvada, DmEvent.Commit).state)
    }

    @Test fun `saidas por pessoa`() {
        assertEquals(NAO_ENCONTRADO, ate(DmEvent.Abrir, DmEvent.NaoEncontrado).state)
        assertEquals(AMBIGUO, ate(DmEvent.Abrir, DmEvent.Ambiguo).state)
        assertEquals(INDISPONIVEL, ate(DmEvent.Abrir, DmEvent.Indisponivel).state)
        assertEquals(PEDIU_PARA_PARAR, ate(DmEvent.Abrir, DmEvent.ConversaProvada, DmEvent.PediuParaParar).state)
        assertEquals(NAO_SEGUE, ate(DmEvent.Abrir, DmEvent.ConversaProvada, DmEvent.NaoSegue).state)
    }

    @Test fun `pedido de parar depois do texto conferido nao e aceito`() {
        // A leitura do pedido acontece antes de escrever; depois disso e imprevisto.
        val m = ate(DmEvent.Abrir, DmEvent.ConversaProvada, DmEvent.TextoConferido, DmEvent.PediuParaParar)
        assertEquals(FALHA, m.state)
    }

    @Test fun `estado final nao reativa`() {
        val enviado = ate(DmEvent.Abrir, DmEvent.ConversaProvada, DmEvent.TextoConferido, DmEvent.Commit, DmEvent.Enviado)
        assertEquals(ENVIADO, enviado.transition(DmEvent.Inesperado))
        assertEquals(ENVIADO, enviado.transition(DmEvent.Abrir))
        val falha = ate(DmEvent.Inesperado)
        assertEquals(FALHA, falha.transition(DmEvent.Abrir))
        assertEquals(FALHA, falha.transition(DmEvent.Commit))
    }

    @Test fun `trilha nao guarda arroba nem texto`() {
        val m = ate(DmEvent.Abrir, DmEvent.ConversaProvada)
        assertFalse(m.trail.toString().contains("@"))
        assertEquals(3, m.trail.size)
    }
}
