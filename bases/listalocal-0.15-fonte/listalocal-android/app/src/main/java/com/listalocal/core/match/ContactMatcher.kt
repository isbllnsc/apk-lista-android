package com.listalocal.core.match

/** Uma linha de resultado da busca do WhatsApp, ja projetada em campos simples. */
data class ResultRow(
    val primaryText: String?,     // chat_able_contacts_row_name
    val secondaryText: String?,   // chat_able_contacts_row_status (numero/status)
    val alreadySelected: Boolean, // via chip/estado
)

enum class MatchOutcome { EXACT, NOT_FOUND, AMBIGUOUS, ALREADY_SELECTED }

data class MatchResult(val outcome: MatchOutcome, val rowIndex: Int? = null)

/**
 * Decide o desfecho da busca. Preferimos casar pelo NUMERO E.164; se o numero
 * nao vier na linha, exigimos igualdade EXATA de nome. Regras herdadas do Spike A:
 * nunca selecionar o primeiro resultado por padrao; >1 match inequivoco = ambiguo.
 */
class ContactMatcher(private val normalizeNumber: (String) -> String?) {

    fun match(queryE164: String?, queryName: String?, rows: List<ResultRow>): MatchResult {
        if (rows.isEmpty()) return MatchResult(MatchOutcome.NOT_FOUND)

        val matches = rows.withIndex().filter { (_, row) ->
            rowMatches(row, queryE164, queryName)
        }

        return when (matches.size) {
            0 -> MatchResult(MatchOutcome.NOT_FOUND)
            1 -> {
                val (idx, row) = matches.first()
                if (row.alreadySelected) MatchResult(MatchOutcome.ALREADY_SELECTED, idx)
                else MatchResult(MatchOutcome.EXACT, idx)
            }
            else -> MatchResult(MatchOutcome.AMBIGUOUS)
        }
    }

    private fun rowMatches(row: ResultRow, queryE164: String?, queryName: String?): Boolean {
        if (queryE164 != null) {
            val rowNumber = row.secondaryText?.let(normalizeNumber)
                ?: row.primaryText?.let(normalizeNumber)
            if (rowNumber != null) return rowNumber == queryE164
            // Sem numero na linha: so casa se o nome bater exatamente.
            return queryName != null && row.primaryText?.trim() == queryName.trim()
        }
        return queryName != null && row.primaryText?.trim() == queryName.trim()
    }
}
