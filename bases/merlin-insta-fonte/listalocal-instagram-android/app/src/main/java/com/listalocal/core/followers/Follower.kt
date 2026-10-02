package com.listalocal.core.followers

/**
 * Um seguidor, unidade de trabalho da automacao. `username` e o @ sem arroba,
 * em minusculas: e a chave de tudo (exclusao, historico, prova da conversa).
 * Nunca serializamos o nome em log.
 */
data class Follower(
    val username: String,
    val name: String,
)

/**
 * Um bloco de conferencia de ate N pessoas, sem teto. No fim de cada bloco o
 * app pode parar e esperar o dono (como a confirmacao por lista do Lista Local).
 */
data class Batch(
    val index: Int, // 1-based
    val followers: List<Follower>,
) {
    val label: String get() = "Lote %03d".format(index)
}
