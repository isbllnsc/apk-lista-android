package com.listalocal.core.privacy

import java.security.MessageDigest

/**
 * Toda evidencia/log passa por aqui. Nunca emitimos nome ou numero completo:
 * apenas um hash curto + os 2 ultimos digitos, suficiente para correlacionar
 * sem vazar dado pessoal no Logcat.
 */
object Redaction {
    fun mask(value: String): String {
        if (value.isEmpty()) return "<>"
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }.take(8)
        val tail = if (value.length >= 2) value.takeLast(2) else ""
        return "<$hex…$tail>"
    }
}
