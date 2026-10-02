package com.listalocal.core.state

import com.listalocal.core.state.BroadcastState.*

/**
 * Maquina de estados pura (sem Android), dirigida por eventos. Regra de ouro:
 * qualquer par (estado, evento) nao previsto resulta em [FAILED_SAFE] — nunca
 * uma acao no aplicativo. Estados de excecao recuperaveis retornam ao fluxo por
 * eventos explicitos; excecoes graves sao terminais.
 */
class BroadcastStateMachine(initial: BroadcastState = IDLE) {

    var state: BroadcastState = initial
        private set

    /** Historico de transicoes para evidencia/depuracao (sem dados pessoais). */
    private val _trail = mutableListOf(initial)
    val trail: List<BroadcastState> get() = _trail.toList()

    /** Contatos confirmados como selecionados neste lote. */
    var selectedCount: Int = 0
        private set

    fun transition(event: BroadcastEvent): BroadcastState {
        val next = compute(state, event)
        state = next
        _trail += next
        return next
    }

    private fun compute(current: BroadcastState, event: BroadcastEvent): BroadcastState {
        // Excecoes globais tem prioridade a partir de qualquer estado ativo.
        when (event) {
            is BroadcastEvent.PermissionLost -> return PERMISSION_LOST
            is BroadcastEvent.VersionUnsupported -> return VERSION_UNSUPPORTED
            is BroadcastEvent.UiUnknown -> return UI_CHANGED
            is BroadcastEvent.Cancelled -> return CANCELLED
            is BroadcastEvent.Paused -> return if (current.isTerminal) current else PAUSED
            else -> Unit
        }

        if (current == PAUSED && event is BroadcastEvent.Resumed) return SEARCHING
        if (current.isTerminal) return FAILED_SAFE

        return when (current) {
            IDLE -> when (event) {
                is BroadcastEvent.Start -> PREPARING
                else -> FAILED_SAFE
            }
            PREPARING -> when (event) {
                is BroadcastEvent.AppOpened -> OPENING_APP
                else -> FAILED_SAFE
            }
            OPENING_APP -> when (event) {
                is BroadcastEvent.PickerOpened -> OPENING_PICKER
                else -> FAILED_SAFE
            }
            OPENING_PICKER -> when (event) {
                is BroadcastEvent.SearchStarted -> SEARCHING
                // Ninguem a buscar (lista sem pessoas): direto a confirmacao, nao "fluxo fora de ordem".
                is BroadcastEvent.AllProcessed -> WAITING_CONFIRMATION
                else -> FAILED_SAFE
            }
            SEARCHING -> when (event) {
                is BroadcastEvent.MatchExact -> MATCHING
                is BroadcastEvent.MatchNotFound -> CONTACT_NOT_FOUND
                is BroadcastEvent.MatchAmbiguous -> AMBIGUOUS_CONTACT
                is BroadcastEvent.AlreadySelected -> ALREADY_SELECTED
                is BroadcastEvent.AllProcessed -> WAITING_CONFIRMATION
                else -> FAILED_SAFE
            }
            MATCHING -> when (event) {
                is BroadcastEvent.SelectionVerified -> VERIFYING.also { selectedCount = event.selectedCount }
                is BroadcastEvent.SelectionFailed -> UI_CHANGED
                else -> FAILED_SAFE
            }
            VERIFYING -> when (event) {
                is BroadcastEvent.SearchStarted -> SEARCHING
                is BroadcastEvent.AllProcessed -> WAITING_CONFIRMATION
                else -> FAILED_SAFE
            }
            // Excecoes recuperaveis por contato: voltam a buscar o proximo.
            CONTACT_NOT_FOUND, AMBIGUOUS_CONTACT, ALREADY_SELECTED -> when (event) {
                is BroadcastEvent.SearchStarted -> SEARCHING
                is BroadcastEvent.AllProcessed -> WAITING_CONFIRMATION
                else -> FAILED_SAFE
            }
            WAITING_CONFIRMATION -> when (event) {
                is BroadcastEvent.UserConfirmed -> SAVING
                else -> FAILED_SAFE
            }
            SAVING -> when (event) {
                is BroadcastEvent.Saved -> COMPLETED
                else -> FAILED_SAFE
            }
            PERMISSION_LOST -> when (event) {
                is BroadcastEvent.Resumed -> PREPARING
                else -> FAILED_SAFE
            }
            // Demais estados sao terminais ou nao aceitam mais eventos.
            SELECTING -> FAILED_SAFE
            else -> FAILED_SAFE
        }
    }
}
