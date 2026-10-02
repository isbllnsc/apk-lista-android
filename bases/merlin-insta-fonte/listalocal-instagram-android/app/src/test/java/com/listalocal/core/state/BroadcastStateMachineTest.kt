package com.listalocal.core.state

import com.listalocal.core.state.BroadcastState.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BroadcastStateMachineTest {

    private fun sm() = BroadcastStateMachine()

    @Test fun `fluxo feliz ate COMPLETED`() {
        val m = sm()
        m.transition(BroadcastEvent.Start)
        m.transition(BroadcastEvent.AppOpened)
        m.transition(BroadcastEvent.PickerOpened)
        m.transition(BroadcastEvent.SearchStarted)
        m.transition(BroadcastEvent.MatchExact("k"))
        m.transition(BroadcastEvent.SelectionVerified(1))
        m.transition(BroadcastEvent.SearchStarted)
        m.transition(BroadcastEvent.MatchExact("k2"))
        m.transition(BroadcastEvent.SelectionVerified(2))
        m.transition(BroadcastEvent.AllProcessed)
        assertEquals(WAITING_CONFIRMATION, m.state)
        m.transition(BroadcastEvent.UserConfirmed)
        m.transition(BroadcastEvent.Saved)
        assertEquals(COMPLETED, m.state)
        assertEquals(2, m.selectedCount)
    }

    @Test fun `evento inesperado leva a FAILED_SAFE`() {
        val m = sm()
        // Sem Start, um evento de selecao nao pode ocorrer.
        assertEquals(FAILED_SAFE, m.transition(BroadcastEvent.SelectionVerified(1)))
    }

    @Test fun `contato nao encontrado nao seleciona e segue para o proximo`() {
        val m = sm()
        drive(m, BroadcastEvent.Start, BroadcastEvent.AppOpened,
            BroadcastEvent.PickerOpened, BroadcastEvent.SearchStarted)
        assertEquals(CONTACT_NOT_FOUND, m.transition(BroadcastEvent.MatchNotFound))
        assertEquals(SEARCHING, m.transition(BroadcastEvent.SearchStarted))
    }

    @Test fun `resultado ambiguo nao seleciona`() {
        val m = sm()
        drive(m, BroadcastEvent.Start, BroadcastEvent.AppOpened,
            BroadcastEvent.PickerOpened, BroadcastEvent.SearchStarted)
        assertEquals(AMBIGUOUS_CONTACT, m.transition(BroadcastEvent.MatchAmbiguous))
    }

    @Test fun `ja selecionado nao clica de novo`() {
        val m = sm()
        drive(m, BroadcastEvent.Start, BroadcastEvent.AppOpened,
            BroadcastEvent.PickerOpened, BroadcastEvent.SearchStarted)
        assertEquals(ALREADY_SELECTED, m.transition(BroadcastEvent.AlreadySelected))
    }

    @Test fun `perda de permissao interrompe de qualquer estado`() {
        val m = sm()
        drive(m, BroadcastEvent.Start, BroadcastEvent.AppOpened)
        assertEquals(PERMISSION_LOST, m.transition(BroadcastEvent.PermissionLost))
    }

    @Test fun `versao nao suportada interrompe sem clicar`() {
        val m = sm()
        m.transition(BroadcastEvent.Start)
        assertEquals(VERSION_UNSUPPORTED, m.transition(BroadcastEvent.VersionUnsupported))
    }

    @Test fun `ui desconhecida vira UI_CHANGED`() {
        val m = sm()
        drive(m, BroadcastEvent.Start, BroadcastEvent.AppOpened)
        assertEquals(UI_CHANGED, m.transition(BroadcastEvent.UiUnknown))
    }

    @Test fun `pausa e retomada preservam o fluxo de busca`() {
        val m = sm()
        drive(m, BroadcastEvent.Start, BroadcastEvent.AppOpened,
            BroadcastEvent.PickerOpened, BroadcastEvent.SearchStarted)
        assertEquals(PAUSED, m.transition(BroadcastEvent.Paused))
        assertEquals(SEARCHING, m.transition(BroadcastEvent.Resumed))
    }

    @Test fun `selecao que nao confirma vira UI_CHANGED`() {
        val m = sm()
        drive(m, BroadcastEvent.Start, BroadcastEvent.AppOpened,
            BroadcastEvent.PickerOpened, BroadcastEvent.SearchStarted,
            BroadcastEvent.MatchExact("k"))
        assertEquals(UI_CHANGED, m.transition(BroadcastEvent.SelectionFailed))
    }

    @Test fun `nenhum evento reativa um estado terminal`() {
        val m = sm()
        drive(m, BroadcastEvent.Start, BroadcastEvent.AppOpened,
            BroadcastEvent.PickerOpened, BroadcastEvent.SearchStarted,
            BroadcastEvent.MatchExact("k"), BroadcastEvent.SelectionVerified(1),
            BroadcastEvent.AllProcessed, BroadcastEvent.UserConfirmed, BroadcastEvent.Saved)
        assertEquals(COMPLETED, m.state)
        assertEquals(FAILED_SAFE, m.transition(BroadcastEvent.Start))
    }

    /** Retomada com todos ja processados (ou lista sem ninguem a buscar): a confirmacao, nao "fluxo fora de ordem". */
    @Test fun `seletor aberto sem ninguem a buscar vai direto a confirmacao`() {
        val m = sm()
        drive(m, BroadcastEvent.Start, BroadcastEvent.AppOpened, BroadcastEvent.PickerOpened)
        assertEquals(WAITING_CONFIRMATION, m.transition(BroadcastEvent.AllProcessed))
    }

    private fun drive(m: BroadcastStateMachine, vararg events: BroadcastEvent) {
        events.forEach { m.transition(it) }
    }
}
