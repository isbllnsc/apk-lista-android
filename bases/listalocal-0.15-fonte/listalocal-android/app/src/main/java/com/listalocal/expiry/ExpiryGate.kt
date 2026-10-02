package com.listalocal.expiry

import android.content.Context
import com.listalocal.BuildConfig

/**
 * Consulta a cada entrada e antes das operacoes sensiveis. O maior horario ja
 * observado impede que voltar o relogio reative uma instalacao que expirou.
 * Sem rede, nao existe maneira de autenticar um horario atrasado antes de o
 * aplicativo observar o vencimento.
 */
object ExpiryGate {
    val enabled: Boolean get() = BuildConfig.EXPIRA_04102026

    private const val PREFS = "prazo_local"
    private const val MAX_OBSERVED = "maior_horario_observado_ms"
    private const val PERSIST_INTERVAL_MS = 30_000L
    private var inMemoryMax = Long.MIN_VALUE
    private var permanentlyExpired = false

    @Synchronized
    fun isExpired(context: Context): Boolean {
        if (!enabled) return false
        if (permanentlyExpired) return true

        val now = System.currentTimeMillis()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val persisted = prefs.getLong(MAX_OBSERVED, Long.MIN_VALUE)
        val largest = maxOf(now, persisted, inMemoryMax)
        inMemoryMax = largest
        val expired = ExpiryPolicy.isExpired(now, largest)

        // Grava sempre no vencimento; antes, agrupa gravacoes para nao desgastar
        // armazenamento quando o servico consulta a arvore varias vezes por segundo.
        if (expired || persisted == Long.MIN_VALUE || largest - persisted >= PERSIST_INTERVAL_MS) {
            if (!prefs.edit().putLong(MAX_OBSERVED, largest).commit()) {
                permanentlyExpired = true // sem registro duravel, falha fechada
                return true
            }
        }
        if (expired) permanentlyExpired = true
        return expired
    }
}
