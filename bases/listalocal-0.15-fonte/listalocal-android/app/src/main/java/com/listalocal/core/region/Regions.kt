package com.listalocal.core.region

import com.listalocal.core.contacts.NormalizedNumber

/**
 * DDD -> UF -> regiao do Brasil e filtros por area. Puro e testavel em JVM.
 * Codigos inexistentes NAO estao no mapa: um numero com DDD desconhecido e
 * sinalizado (uf == null), nunca "chutado" para um estado.
 */
object Regions {

    val DDD_UF: Map<String, String> = mapOf(
        // Sudeste
        "11" to "SP", "12" to "SP", "13" to "SP", "14" to "SP", "15" to "SP",
        "16" to "SP", "17" to "SP", "18" to "SP", "19" to "SP",
        "21" to "RJ", "22" to "RJ", "24" to "RJ",
        "27" to "ES", "28" to "ES",
        "31" to "MG", "32" to "MG", "33" to "MG", "34" to "MG", "35" to "MG",
        "37" to "MG", "38" to "MG",
        // Sul
        "41" to "PR", "42" to "PR", "43" to "PR", "44" to "PR", "45" to "PR", "46" to "PR",
        "47" to "SC", "48" to "SC", "49" to "SC",
        "51" to "RS", "53" to "RS", "54" to "RS", "55" to "RS",
        // Centro-Oeste
        "61" to "DF", "62" to "GO", "64" to "GO", "65" to "MT", "66" to "MT", "67" to "MS",
        // Norte
        "63" to "TO", "68" to "AC", "69" to "RO",
        "91" to "PA", "93" to "PA", "94" to "PA", "92" to "AM", "97" to "AM",
        "95" to "RR", "96" to "AP",
        // Nordeste
        "71" to "BA", "73" to "BA", "74" to "BA", "75" to "BA", "77" to "BA",
        "79" to "SE", "81" to "PE", "87" to "PE", "82" to "AL", "83" to "PB",
        "84" to "RN", "85" to "CE", "88" to "CE", "86" to "PI", "89" to "PI",
        "98" to "MA", "99" to "MA",
    )

    val UF_REGIAO: Map<String, String> = mapOf(
        "AC" to "Norte", "AP" to "Norte", "AM" to "Norte", "PA" to "Norte",
        "RO" to "Norte", "RR" to "Norte", "TO" to "Norte",
        "AL" to "Nordeste", "BA" to "Nordeste", "CE" to "Nordeste", "MA" to "Nordeste",
        "PB" to "Nordeste", "PE" to "Nordeste", "PI" to "Nordeste", "RN" to "Nordeste",
        "SE" to "Nordeste",
        "DF" to "Centro-Oeste", "GO" to "Centro-Oeste", "MT" to "Centro-Oeste",
        "MS" to "Centro-Oeste",
        "ES" to "Sudeste", "MG" to "Sudeste", "RJ" to "Sudeste", "SP" to "Sudeste",
        "PR" to "Sul", "RS" to "Sul", "SC" to "Sul",
    )

    val REGIOES = listOf("Norte", "Nordeste", "Centro-Oeste", "Sudeste", "Sul")

    data class Area(
        val ddd: String?,
        val uf: String?,
        val regiao: String?,
        val internacional: Boolean = false,
    ) {
        val desconhecido: Boolean get() = !internacional && uf == null
    }

    fun areaOf(e164: String): Area {
        if (!e164.startsWith("+55")) return Area(null, null, null, internacional = true)
        val resto = e164.substring(3)
        if (resto.length < 2) return Area(null, null, null)
        val ddd = resto.substring(0, 2)
        val uf = DDD_UF[ddd] ?: return Area(ddd, null, null)
        return Area(ddd, uf, UF_REGIAO[uf])
    }

    /** Criterios de filtro. Todos opcionais, combinam com E logico. */
    data class Filter(
        val ddd: Set<String> = emptySet(),
        val uf: Set<String> = emptySet(),
        val regiao: Set<String> = emptySet(),
        val excluirDdd: Set<String> = emptySet(),
        val excluirUf: Set<String> = emptySet(),
        val incluirInternacional: Boolean = false,
        val incluirDesconhecido: Boolean = false,
    ) {
        val vazio: Boolean
            get() = ddd.isEmpty() && uf.isEmpty() && regiao.isEmpty() &&
                excluirDdd.isEmpty() && excluirUf.isEmpty()
    }

    fun apply(numbers: List<NormalizedNumber>, f: Filter): List<NormalizedNumber> {
        if (f.vazio && !f.incluirInternacional && !f.incluirDesconhecido && f == Filter()) {
            return numbers
        }
        return numbers.filter { n ->
            val a = areaOf(n.e164)
            when {
                a.internacional -> f.incluirInternacional
                a.desconhecido -> f.incluirDesconhecido
                a.ddd in f.excluirDdd || (a.uf ?: "") in f.excluirUf -> false
                f.ddd.isNotEmpty() && a.ddd !in f.ddd -> false
                f.uf.isNotEmpty() && a.uf !in f.uf -> false
                f.regiao.isNotEmpty() && (a.regiao ?: "") !in f.regiao -> false
                else -> true
            }
        }
    }

    /** Distribuicao por UF (para a UI mostrar quantos de cada estado). */
    fun countByUf(numbers: List<NormalizedNumber>): Map<String, Int> {
        val out = linkedMapOf<String, Int>()
        for (n in numbers) {
            val a = areaOf(n.e164)
            val k = when {
                a.internacional -> "INTL"
                a.desconhecido -> "??"
                else -> a.uf ?: "??"
            }
            out[k] = (out[k] ?: 0) + 1
        }
        return out.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    fun countByRegiao(numbers: List<NormalizedNumber>): Map<String, Int> {
        val out = linkedMapOf<String, Int>()
        for (n in numbers) {
            val a = areaOf(n.e164)
            val k = when {
                a.internacional -> "Internacional"
                a.desconhecido -> "Outros"
                else -> a.regiao ?: "Outros"
            }
            out[k] = (out[k] ?: 0) + 1
        }
        return out
    }
}
