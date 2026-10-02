package com.listalocal.core.contacts

/** Contato bruto lido do aparelho (ContactsContract). Pode ter varios telefones. */
data class RawContact(
    val localId: Long,
    val displayName: String,
    val phones: List<String>,
)

/**
 * Numero normalizado, unidade de trabalho da automacao. `key` e o E.164; o
 * `contactLocalId` amarra a origem. Nunca serializamos displayName em log.
 */
data class NormalizedNumber(
    val e164: String,
    val contactLocalId: Long,
    val displayName: String,
) {
    /** Chave estavel para dedupe e para casar com o resultado da busca. */
    val key: String get() = e164
}

/** Um lote de ate 256 numeros que virara uma "Transmissão NNN". */
data class Batch(
    val index: Int,           // 1-based: 1 -> "Transmissão 001"
    val numbers: List<NormalizedNumber>,
) {
    val label: String get() = "Transmissão %03d".format(index)
}
