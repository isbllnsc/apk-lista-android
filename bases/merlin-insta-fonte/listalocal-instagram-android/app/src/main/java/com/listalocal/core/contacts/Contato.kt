package com.listalocal.core.contacts

data class Contato(
    val e164: String,
    val nome: String,
    val contactId: Long = 0L,
    val nomesAceitos: Set<String> = setOf(nome),
) {
    companion object {
        val E164_RE = Regex("""\+\d{8,15}""")
    }
}
