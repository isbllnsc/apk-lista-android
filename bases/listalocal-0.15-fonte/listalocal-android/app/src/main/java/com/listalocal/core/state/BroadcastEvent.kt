package com.listalocal.core.state

/** Eventos que dirigem a FSM. Emitidos pelo servico a partir da arvore de nos. */
sealed interface BroadcastEvent {
    data object Start : BroadcastEvent
    data object WhatsAppOpened : BroadcastEvent
    data object NewBroadcastPickerOpened : BroadcastEvent
    data object SearchStarted : BroadcastEvent

    /** Resultado da avaliacao de uma busca de contato. */
    data class MatchExact(val contactKey: String) : BroadcastEvent
    data object MatchNotFound : BroadcastEvent
    data object MatchAmbiguous : BroadcastEvent
    data object AlreadySelected : BroadcastEvent

    /** Selecao confirmada pelo contador/chips. */
    data class SelectionVerified(val selectedCount: Int) : BroadcastEvent
    data object SelectionFailed : BroadcastEvent

    /** Todos os contatos do lote foram processados. */
    data object AllProcessed : BroadcastEvent

    /** Confirmacao humana explicita para criar a lista. */
    data object UserConfirmed : BroadcastEvent
    data object Saved : BroadcastEvent

    // Excecoes vindas do ambiente
    data object UiUnknown : BroadcastEvent
    data object VersionUnsupported : BroadcastEvent
    data object PermissionLost : BroadcastEvent
    data object Paused : BroadcastEvent
    data object Resumed : BroadcastEvent
    data object Cancelled : BroadcastEvent
}
