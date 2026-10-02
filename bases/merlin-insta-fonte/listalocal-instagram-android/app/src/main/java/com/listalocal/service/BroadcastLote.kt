package com.listalocal.service

import com.listalocal.core.contacts.Contato

/**
 * Um lote de contatos para criar uma lista de transmissão.
 * Máximo de 256 contatos por lote (limitação do WhatsApp).
 */
data class BroadcastLote(
    /** Número do lote, 1-indexed. */
    val index: Int,
    /** Rótulo exibido na UI, ex: "Lista 1 (256 contatos)". */
    val label: String,
    /** Contatos normalizados em E.164 deste lote. */
    val contatos: List<Contato>,
) {
    companion object {
        const val MAX_POR_LOTE = 256

        /**
         * Divide uma lista de contatos em lotes de [MAX_POR_LOTE].
         */
        fun deContatos(contatos: List<Contato>): List<BroadcastLote> =
            contatos.chunked(MAX_POR_LOTE).mapIndexed { i, chunk ->
                BroadcastLote(
                    index = i + 1,
                    label = "Lista ${i + 1} (${chunk.size} ${if (chunk.size == 1) "contato" else "contatos"})",
                    contatos = chunk,
                )
            }
    }
}
