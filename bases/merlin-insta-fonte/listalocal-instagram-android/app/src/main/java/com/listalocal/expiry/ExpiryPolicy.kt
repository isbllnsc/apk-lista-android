package com.listalocal.expiry

import java.time.LocalDate
import java.time.ZoneId

/** Regra independente do fuso escolhido no aparelho e da configuracao regional. */
object ExpiryPolicy {
    val cutoffMillis: Long = LocalDate.of(2026, 10, 5)
        .atStartOfDay(ZoneId.of("America/Sao_Paulo"))
        .toInstant().toEpochMilli()

    fun isExpired(nowMillis: Long, largestObservedMillis: Long): Boolean =
        maxOf(nowMillis, largestObservedMillis) >= cutoffMillis
}
