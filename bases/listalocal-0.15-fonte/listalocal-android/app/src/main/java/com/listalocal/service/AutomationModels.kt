package com.listalocal.service

import com.listalocal.core.contacts.Batch
import com.listalocal.core.selectors.TargetApp

/** Desfecho de um contato dentro de uma lista. */
enum class Outcome { SELECTED, NOT_FOUND, AMBIGUOUS, ALREADY_SELECTED }

/** Causa observada durante a verificacao, separada do texto mostrado na tela. */
enum class CompatIssue {
    APP_NOT_INSTALLED,
    PROFILE_NOT_AVAILABLE,
    BROADCAST_ENTRY_NOT_FOUND,
    PICKER_NOT_OPENED,
    SELECTORS_MISSING,
    CHECK_FAILED,
}

/** Proximos passos que realmente podem mudar o resultado da verificacao. */
enum class RecoveryAction {
    CHECK_BUSINESS_PLATFORM,
    RETRY_COMPATIBILITY,
    TRY_ANOTHER_BUSINESS_ACCOUNT,
    USE_STANDARD_WHATSAPP,
}

/** Fase corrente para a UI (espelha, em alto nivel, a maquina de estados). */
enum class RunPhase {
    IDLE, PREPARING, OPENING_WHATSAPP, OPENING_BROADCAST, SELECTING,
    WAITING_CONFIRMATION, SAVING, COMPLETED, PAUSED, CANCELLED,
    UI_CHANGED, VERSION_UNSUPPORTED, PERMISSION_LOST, FAILED_SAFE
}

/** Progresso de uma unica lista (Transmissão NNN). */
data class ListProgress(
    val index: Int,
    val label: String,
    val total: Int,
    val selected: Int = 0,
    val notFound: Int = 0,
    val ambiguous: Int = 0,
    val alreadySelected: Int = 0,
    val blocked: Int = 0,
    /** Contas comerciais: o WhatsApp nao permite adiciona-las a uma transmissao. */
    val business: Int = 0,
    /** Toques em que o WhatsApp nao deu evidencia de marcacao no prazo. */
    val unconfirmed: Int = 0,
    val created: Boolean = false,
)

/**
 * Resultado da verificacao de compatibilidade: o que o app achou de verdade na
 * tela daquele aplicativo, naquela versao, naquele aparelho.
 *
 * E o que autoriza operar num app ainda nao validado em laboratorio: em vez de
 * confiar que "deve ser igual", o proprio aparelho responde.
 */
data class CompatCheck(
    val app: TargetApp,
    val version: String,
    /** chave do seletor -> encontrado na tela. */
    val found: Map<String, Boolean> = emptyMap(),
    val reachedPicker: Boolean = false,
    val failure: String? = null,
    val issue: CompatIssue? = null,
    /** Lida na tela "Listas de transmissao" quando o caminho passa por ela. */
    val quota: QuotaMensal? = null,
) {
    val missing: List<String> get() {
        val alternatives = listOf(
            setOf("home_fab", "menu_overflow", "tools_tab") to "entrada da transmissão",
            setOf("picker_search", "picker_search_input") to "busca de contatos",
        )
        val groupedKeys = alternatives.flatMap { it.first }.toSet()
        val result = found
            .filter { (key, value) -> key !in groupedKeys && !value }
            .keys
            .toMutableList()

        alternatives.forEach { (keys, label) ->
            val observed = found.filterKeys { it in keys }
            if (observed.isNotEmpty() && observed.values.none { it }) result += label
        }
        return result
    }
    val ok: Boolean get() = failure == null && reachedPicker && found.isNotEmpty() && missing.isEmpty()

    val recommendedActions: List<RecoveryAction> get() =
        if (app == TargetApp.WHATSAPP_BUSINESS &&
            issue == CompatIssue.BROADCAST_ENTRY_NOT_FOUND
        ) {
            listOf(
                RecoveryAction.CHECK_BUSINESS_PLATFORM,
                RecoveryAction.RETRY_COMPATIBILITY,
                RecoveryAction.TRY_ANOTHER_BUSINESS_ACCOUNT,
                RecoveryAction.USE_STANDARD_WHATSAPP,
            )
        } else {
            emptyList()
        }
}

/** Cota mensal de transmissoes que o WhatsApp mostra na tela de listas. */
data class QuotaMensal(val enviadas: Int?, val restantes: Int?, val periodo: String?)

/** Estado completo exposto para a UI observar. */
data class RunState(
    val phase: RunPhase = RunPhase.IDLE,
    val plan: List<Batch> = emptyList(),
    val currentListIndex: Int = 0,   // 1-based; 0 = nenhuma
    val lists: List<ListProgress> = emptyList(),
    val message: String = "",
    val needsConfirmation: Boolean = false,
    val whatsAppVersion: String = "",
    val running: Boolean = false,
    /** App sobre o qual a automacao opera. */
    val targetApp: TargetApp = TargetApp.WHATSAPP,
    /** Ultima verificacao de compatibilidade feita neste aparelho. */
    val compat: CompatCheck? = null,
    val checkingCompat: Boolean = false,
) {
    val totalLists: Int get() = plan.size
    val listsCreated: Int get() = lists.count { it.created }
}
