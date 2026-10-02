package com.listalocal.data

import android.content.Context

/**
 * Estado local da operação única. Depois dos toques de criação, a operação
 * aguarda a pessoa conferir as listas no WhatsApp. Somente a confirmação dela
 * muda o estado para consumido.
 *
 * Estes dados privados sobrevivem ao fechamento e à atualização, mas são
 * removidos na desinstalação ou ao limpar os dados do app. O bloqueio após
 * reinstalação depende de um registro externo.
 */
object OneShotGate {
    private const val PREFS = "one_shot_gate"
    private const val KEY_CONSUMED = "consumed"
    private const val KEY_LISTS_CREATED = "lists_created"
    private const val KEY_PENDING = "awaiting_verification"
    private const val KEY_PENDING_LISTS = "pending_lists"

    @Volatile private var consumedInProcess = false
    @Volatile private var pendingInProcess = false
    @Volatile private var pendingCountInProcess = 0

    /** Todos os lotes tiveram o botão Criar acionado; ainda falta conferir. */
    fun shouldConsume(completed: Boolean, plannedLists: Int, createdLists: Int): Boolean =
        completed && plannedLists > 0 && createdLists == plannedLists

    @Synchronized
    fun isConsumed(context: Context): Boolean {
        if (com.listalocal.BuildConfig.DEBUG) return false
        if (consumedInProcess) return true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            prefs.getBoolean(KEY_CONSUMED, false).also { if (it) consumedInProcess = true }
        } catch (_: ClassCastException) {
            // Um registro corrompido não deve liberar outra operação.
            consumedInProcess = true
            true
        }
    }

    /**
     * Há uma operação concluída que precisa ser conferida no WhatsApp.
     */
    @Synchronized
    fun isAwaitingVerification(context: Context): Boolean {
        if (com.listalocal.BuildConfig.DEBUG) return false
        if (isConsumed(context)) return false
        if (pendingInProcess) return true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            prefs.getBoolean(KEY_PENDING, false).also { if (it) pendingInProcess = true }
        } catch (_: ClassCastException) {
            // Falha fechada: não iniciar outra criação sem resolver o registro.
            pendingInProcess = true
            true
        }
    }

    @Synchronized
    fun pendingListsCount(context: Context): Int {
        if (!isAwaitingVerification(context)) return 0
        if (pendingCountInProcess > 0) return pendingCountInProcess
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            prefs.getInt(KEY_PENDING_LISTS, 0).coerceAtLeast(0).also {
                pendingCountInProcess = it
            }
        } catch (_: ClassCastException) {
            0
        }
    }

    /**
     * Grava de forma síncrona a etapa que exige conferência humana. Mesmo se a
     * gravação falhar, bloqueia outra operação enquanto este processo existir.
     */
    @Synchronized
    fun markPending(context: Context, listsCreated: Int): Boolean {
        require(listsCreated > 0) { "listsCreated deve ser maior que zero" }
        if (isConsumed(context)) return false
        if (isAwaitingVerification(context) && pendingListsCount(context) != listsCreated) return false
        pendingInProcess = true
        pendingCountInProcess = listsCreated
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .putInt(KEY_PENDING_LISTS, listsCreated)
            .commit()
    }

    /**
     * Depois de a pessoa conferir as listas, transfere a contagem pendente para
     * o registro consumido numa única gravação. Falha mantém o estado pendente
     * para que a confirmação possa ser repetida.
     */
    @Synchronized
    fun markConsumedFromVerified(context: Context): Boolean {
        if (isConsumed(context)) return true
        if (!isAwaitingVerification(context)) return false
        val count = pendingListsCount(context)
        if (count <= 0) return false
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LISTS_CREATED, count)
            .putBoolean(KEY_CONSUMED, true)
            .remove(KEY_PENDING)
            .remove(KEY_PENDING_LISTS)
            .commit()
        if (saved) {
            consumedInProcess = true
            pendingInProcess = false
            pendingCountInProcess = 0
        }
        return saved
    }

    /**
     * A pessoa encontrou uma lista ausente. Libera novo planejamento sem
     * declarar consumo; ela deve evitar recriar listas que já existem.
     */
    @Synchronized
    fun cancelPendingAfterFailedVerification(context: Context): Boolean {
        if (isConsumed(context)) return false
        if (!isAwaitingVerification(context)) return true
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING)
            .remove(KEY_PENDING_LISTS)
            .commit()
        if (saved) {
            pendingInProcess = false
            pendingCountInProcess = 0
        }
        return saved
    }

    @Synchronized
    fun consumedListsCount(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LISTS_CREATED, 0)
}
