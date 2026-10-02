package com.listalocal.core.contacts

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/** Resultado da leitura: cada telefone foi validado uma unica vez. */
data class NormalizationResult(
    val numbers: List<NormalizedNumber>,
    /** Todos os E.164 validos de cada contato, inclusive numeros repetidos em outro contato. */
    val numbersByContactId: Map<Long, Set<String>>,
    val telefonesLidos: Int,
    val validosBrutos: Int,
    val semTelefoneValido: Int,
) {
    val duplicados: Int get() = validosBrutos - numbers.size
}

/**
 * Normaliza contatos brutos em numeros E.164, trata multiplos telefones,
 * remove duplicados e divide em lotes de ate [BATCH_LIMIT]. Puro e testavel
 * em JVM (libphonenumber e offline).
 */
class ContactNormalizer(
    private val defaultRegion: String = "BR",
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
) {

    /** Normaliza e deduplica, preservando a ordem da primeira ocorrencia. */
    fun normalize(contacts: List<RawContact>): List<NormalizedNumber> =
        normalizeWithMetrics(contacts).numbers

    /** Normaliza e calcula as contagens em uma unica passagem pelos telefones. */
    fun normalizeWithMetrics(contacts: List<RawContact>): NormalizationResult {
        val seen = LinkedHashMap<String, NormalizedNumber>()
        val byContactId = LinkedHashMap<Long, MutableSet<String>>()
        var telefonesLidos = 0
        var validosBrutos = 0
        var semTelefoneValido = 0
        for (c in contacts) {
            val numerosDoContato = byContactId.getOrPut(c.localId) { linkedSetOf() }
            var temTelefoneValido = false
            for (phone in c.phones) {
                telefonesLidos++
                val e164 = toE164(phone) ?: continue
                validosBrutos++
                temTelefoneValido = true
                numerosDoContato.add(e164)
                // Dedupe por numero: a primeira ocorrencia vence.
                seen.putIfAbsent(e164, NormalizedNumber(e164, c.localId, c.displayName))
            }
            if (!temTelefoneValido) semTelefoneValido++
        }
        return NormalizationResult(
            numbers = seen.values.toList(),
            numbersByContactId = byContactId.mapValues { (_, phones) -> phones.toSet() },
            telefonesLidos = telefonesLidos,
            validosBrutos = validosBrutos,
            semTelefoneValido = semTelefoneValido,
        )
    }

    /**
     * Divide em lotes de ate [BATCH_LIMIT]. Se sobraria uma lista de uma pessoa,
     * transfere um numero do lote anterior: o WhatsApp exige pelo menos dois.
     */
    fun batch(numbers: List<NormalizedNumber>): List<Batch> {
        val chunks = numbers.chunked(BATCH_LIMIT).toMutableList()
        if (chunks.size >= 2 && chunks.last().size == 1) {
            val previous = chunks[chunks.lastIndex - 1]
            val last = chunks.last()
            chunks[chunks.lastIndex - 1] = previous.dropLast(1)
            chunks[chunks.lastIndex] = listOf(previous.last()) + last
        }
        return chunks.mapIndexed { i, chunk -> Batch(i + 1, chunk) }
    }

    /** Retorna E.164 valido ou null (invalidos sao descartados, nao adivinhados). */
    fun toE164(raw: String): String? = try {
        val parsed = util.parse(raw.trim(), defaultRegion)
        if (util.isValidNumber(parsed)) {
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } else {
            null
        }
    } catch (_: NumberParseException) {
        null
    }

    companion object {
        const val BATCH_LIMIT = 256
    }
}
