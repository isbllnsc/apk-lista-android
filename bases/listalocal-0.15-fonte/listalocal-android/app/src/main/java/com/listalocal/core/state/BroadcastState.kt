package com.listalocal.core.state

/**
 * Estados explicitos da automacao assistida. Espelham a maquina validada no
 * Spike A. Toda transicao passa por [BroadcastStateMachine.transition]; uma
 * transicao invalida leva sempre a [FAILED_SAFE], nunca a um clique cego.
 */
enum class BroadcastState {
    IDLE,
    PREPARING,
    OPENING_WHATSAPP,
    OPENING_NEW_BROADCAST,
    SEARCHING,
    MATCHING,
    SELECTING,
    VERIFYING,
    WAITING_CONFIRMATION,
    SAVING,
    COMPLETED,

    // Excecao
    PAUSED,
    CONTACT_NOT_FOUND,
    AMBIGUOUS_CONTACT,
    ALREADY_SELECTED,
    UI_CHANGED,
    WHATSAPP_VERSION_UNSUPPORTED,
    PERMISSION_LOST,
    CANCELLED,
    FAILED_SAFE;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED || this == FAILED_SAFE

    val isException: Boolean
        get() = this in EXCEPTION_STATES

    companion object {
        val EXCEPTION_STATES = setOf(
            PAUSED, CONTACT_NOT_FOUND, AMBIGUOUS_CONTACT, ALREADY_SELECTED,
            UI_CHANGED, WHATSAPP_VERSION_UNSUPPORTED, PERMISSION_LOST,
            CANCELLED, FAILED_SAFE,
        )
    }
}
