package com.listalocal.core.followers

/**
 * O @ do Instagram normalizado: a chave de tudo (desfecho, exclusao, prova da
 * conversa). Sem arquivo: os destinatarios vem do proprio Instagram, ao vivo.
 */
object FollowerImport {

    private val RESERVED = setOf(
        "accounts", "direct", "explore", "p", "reel", "reels", "stories", "about", "help", "privacy", "terms",
    )
    private val USERNAME = Regex("^[a-z0-9._]{1,30}$")

    /** @ normalizado (sem arroba, minusculas) ou null se invalido. */
    fun username(value: String?): String? {
        var text = value?.trim().orEmpty()
        // A protecao de planilha do Merlin prefixa "'" antes de um @ literal.
        if (text.startsWith("'@")) text = text.substring(1)
        text = text.removePrefix("@").lowercase()
        return text.takeIf { USERNAME.matches(it) && it !in RESERVED }
    }
}
