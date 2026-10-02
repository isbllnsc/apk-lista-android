package com.listalocal.core.contacts

/** Exclui destinos pelo numero, inclusive quando ele aparece em dois contatos. */
object ContactExclusionPolicy {
    fun excludedNumbers(
        numbersByContactId: Map<Long, Set<String>>,
        excludedContactIds: Set<Long>,
    ): Set<String> = excludedContactIds.flatMapTo(linkedSetOf()) {
        numbersByContactId[it].orEmpty()
    }

    fun keep(numbers: List<NormalizedNumber>, excludedNumbers: Set<String>): List<NormalizedNumber> =
        if (excludedNumbers.isEmpty()) numbers
        else numbers.filterNot { it.e164 in excludedNumbers }
}
