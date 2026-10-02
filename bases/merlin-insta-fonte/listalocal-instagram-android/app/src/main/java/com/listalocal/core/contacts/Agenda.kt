package com.listalocal.core.contacts

object Agenda {
    data class OpcoesAgenda(
        val soPara: Set<String> = emptySet(),
        val naoEnviar: Set<String> = emptySet(),
        val propriaConta: String? = null,
    )
    data class Plano(val fila: List<Contato>, val fora: List<Pair<String, String>>)

    fun plano(rawContacts: List<RawContactEntry>, opcoes: OpcoesAgenda = OpcoesAgenda()): Plano {
        val fila = mutableListOf<Contato>()
        val fora = mutableListOf<Pair<String, String>>()
        val vistos = mutableMapOf<String, Contato>()
        for (raw in rawContacts) {
            val numeros = raw.phones.mapNotNull { normalizar(it) }
            if (numeros.isEmpty()) { fora += raw.displayName to "sem número válido"; continue }
            val escolhido = numeros.firstOrNull { isCelularBr(it) } ?: numeros.first()
            if (vistos.containsKey(escolhido)) { fora += raw.displayName to "duplicado ($escolhido)"; continue }
            if (escolhido == opcoes.propriaConta) { fora += raw.displayName to "própria conta"; continue }
            if (escolhido in opcoes.naoEnviar) { fora += raw.displayName to "na lista Não enviar para"; continue }
            val c = Contato(escolhido, raw.displayName, raw.localId, setOf(raw.displayName))
            vistos[escolhido] = c; fila += c
        }
        val filaFinal = if (opcoes.soPara.isEmpty()) fila else fila.filter { it.e164 in opcoes.soPara }
        return Plano(filaFinal, fora)
    }

    internal fun normalizar(raw: String): String? {
        val digits = raw.replace(Regex("[^0-9+]"), "")
        return when {
            digits.startsWith("+") && digits.length in 8..16 -> digits
            digits.startsWith("00") && digits.length in 10..16 -> "+${digits.drop(2)}"
            digits.startsWith("55") && digits.length in 12..13 -> "+$digits"
            digits.length in 10..11 -> "+55$digits"
            else -> null
        }.takeIf { it != null && Contato.E164_RE.matches(it!!) }
    }

    private fun isCelularBr(e164: String): Boolean =
        e164.startsWith("+55") && e164.length == 14 && e164[5] == '9'
}
