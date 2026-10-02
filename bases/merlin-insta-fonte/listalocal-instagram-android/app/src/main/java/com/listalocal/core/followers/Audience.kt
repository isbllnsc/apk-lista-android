package com.listalocal.core.followers

/**
 * O que o dono digita (ou cola) na tela: listas de @, sem arquivo. E o plano
 * do modo A (Amigos Proximos), que marca so os @ de "So para estes @". O modo
 * C nao tem plano: le as pessoas ao vivo no Instagram (service.Fonte).
 */
object Audience {

    enum class Motivo(val texto: String) {
        PEDIU_PARA_PARAR("pediu para parar"),
        NAO_ENVIAR("na lista Não enviar para"),
    }

    data class Plano(
        val lotes: List<Batch>,
        val pulados: Map<Motivo, Int>,
    ) {
        val total: Int get() = lotes.sumOf { it.followers.size }
    }

    /** @ validos (sem arroba, minusculas, na ordem, sem repetir) e o que nao e @. */
    data class Arrobas(val validos: Set<String>, val invalidos: List<String>)

    /** Separados por espaco, virgula, ponto e virgula ou linha; "@" e links de perfil nao precisam. */
    fun arrobas(texto: String): Arrobas {
        val validos = LinkedHashSet<String>()
        val invalidos = mutableListOf<String>()
        texto.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }.forEach { t ->
            val u = FollowerImport.username(t.trim().removeSuffix("/").substringAfterLast("instagram.com/"))
            if (u != null) validos += u else invalidos += t
        }
        return Arrobas(validos, invalidos)
    }

    /**
     * Parser para o campo "Só para" no modo WhatsApp.
     * Aceita números em qualquer formato nacional ou E.164 e os normaliza para +55...
     * Números com menos de 8 dígitos ou que não normalizem ficam em [invalidos].
     */
    fun numeros(texto: String): Arrobas {
        val validos = LinkedHashSet<String>()
        val invalidos = mutableListOf<String>()
        texto.split(Regex("[\\s,;\\n]+")).filter { it.isNotBlank() }.forEach { t ->
            val e164 = normalizarTelefone(t.trim())
            if (e164 != null) validos += e164 else invalidos += t
        }
        return Arrobas(validos, invalidos)
    }

    /** Normaliza um número de telefone para E.164 (+55...). Retorna null se inválido. */
    fun normalizarTelefone(raw: String): String? {
        val digits = raw.replace(Regex("[^0-9+]"), "")
        val e164 = when {
            digits.startsWith("+") && digits.length in 8..16 -> digits
            digits.startsWith("00") && digits.length in 10..16 -> "+${digits.drop(2)}"
            digits.startsWith("55") && digits.length in 12..13 -> "+$digits"
            digits.length in 10..11 -> "+55$digits"
            else -> null
        }
        return e164?.takeIf { it.length >= 9 }
    }

    /** Um lote so, na ordem digitada. Cada @ sai pelo primeiro motivo que o barra (pedido de parar vence). */
    fun montar(
        pessoas: List<Follower>,
        optOut: Set<String> = emptySet(),
        naoEnviar: Set<String> = emptySet(),
    ): Plano {
        val pulados = LinkedHashMap<Motivo, Int>()
        val dentro = pessoas.filter { f ->
            val motivo = when (f.username) {
                in optOut -> Motivo.PEDIU_PARA_PARAR
                in naoEnviar -> Motivo.NAO_ENVIAR
                else -> null
            }
            if (motivo != null) pulados[motivo] = (pulados[motivo] ?: 0) + 1
            motivo == null
        }
        return Plano(if (dentro.isEmpty()) emptyList() else listOf(Batch(1, dentro)), pulados)
    }
}
