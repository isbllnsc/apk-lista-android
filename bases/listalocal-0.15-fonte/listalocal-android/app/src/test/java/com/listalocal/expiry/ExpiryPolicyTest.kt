package com.listalocal.expiry

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiryPolicyTest {
    private val ultimoInstanteValido = Instant.parse("2026-10-05T02:59:59.999Z").toEpochMilli()
    private val primeiroInstanteBloqueado = Instant.parse("2026-10-05T03:00:00Z").toEpochMilli()

    @Test fun `dia 4 completo vale e dia 5 inicia bloqueio no horario de Sao Paulo`() {
        assertEquals(primeiroInstanteBloqueado, ExpiryPolicy.cutoffMillis)
        assertFalse(ExpiryPolicy.isExpired(ultimoInstanteValido, ultimoInstanteValido))
        assertTrue(ExpiryPolicy.isExpired(primeiroInstanteBloqueado, ultimoInstanteValido))
    }

    @Test fun `recuo do relogio apos horario observado de vencimento nao libera`() {
        assertTrue(ExpiryPolicy.isExpired(ultimoInstanteValido, primeiroInstanteBloqueado))
        assertTrue(ExpiryPolicy.isExpired(0, primeiroInstanteBloqueado + 86_400_000))
    }

    @Test fun `fuso do aparelho nao altera o corte`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(primeiroInstanteBloqueado, ExpiryPolicy.cutoffMillis)
            assertTrue(ExpiryPolicy.isExpired(primeiroInstanteBloqueado, Long.MIN_VALUE))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
