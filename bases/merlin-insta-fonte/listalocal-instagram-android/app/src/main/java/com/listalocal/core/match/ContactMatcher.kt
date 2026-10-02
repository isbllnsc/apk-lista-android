package com.listalocal.core.match

import com.listalocal.core.ig.Evidence

enum class MatchOutcome { EXACT, NOT_FOUND, AMBIGUOUS, ALREADY_SELECTED }

data class MatchResult(val outcome: MatchOutcome, val row: Evidence.Linha? = null)

/**
 * Decide o desfecho de uma busca pelo @. Regras herdadas do Lista Local:
 * nunca a primeira linha por padrao; so o @ EXATO casa (sem "contem", sem
 * nome de exibicao); mais de uma linha exata = ambiguo, nenhum toque.
 */
object ContactMatcher {

    fun match(username: String, rows: List<Evidence.Linha>): MatchResult {
        val alvo = username.trim().removePrefix("@").lowercase()
        val exatas = rows.filter { it.username == alvo }
        return when (exatas.size) {
            0 -> MatchResult(MatchOutcome.NOT_FOUND)
            1 -> exatas.first().let {
                MatchResult(if (it.marcada) MatchOutcome.ALREADY_SELECTED else MatchOutcome.EXACT, it)
            }
            else -> MatchResult(MatchOutcome.AMBIGUOUS)
        }
    }
}
