package com.listalocal.core.ig

import java.text.Normalizer

/**
 * Pedido de parar ("pare", "sair", "nao quero mais receber") numa mensagem.
 * Adaptado do reply-analysis.js do Merlin 3.0.5, com os mesmos cuidados:
 *  - "nao pare" / "nao quero sair" nao e pedido (so o trecho negado: outra
 *    frase da mesma mensagem que pede para parar continua valendo);
 *  - a instrucao escrita pelo dono ("responda PARE para sair") nao e pedido,
 *    entao ela e apagada do texto antes de julgar;
 *  - "para" sozinho so conta quando e a mensagem inteira ("Para quando e?" nao).
 * Errar para o lado de parar e o erro seguro: a pessoa so deixa de receber.
 */
object OptOut {

    private fun norm(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private const val VERBO_ENVIO = "(?:mande|manda|mandem|envie|envia|enviem|enviar|mandar)"

    private val instrucao = Regex(
        "(?:\\b(?:para|pra|caso|se)\\s+(?:nao\\s+(?:quiser\\s+|queira\\s+)?(?:mais\\s+)?receber|parar\\s+de\\s+receber)" +
            "[^,.!?;:\\n]*[,:]\\s*)?" +
            "\\b(?:responda|responde|respondam|digite|digita|digitem|envie|envia|enviem|mande|manda|mandem|escreva|escreve)" +
            "\\s+(?:com\\s+)?(?:(?:a\\s+)?palavra\\s+)?[\"'“”]?(?:pare|sair|stop)[\"'“”]?[^,.!?;:\\n]*",
    )

    private val contrario = Regex(
        "\\bnao\\s+(?:me\\s+)?(?:pare|para|parem|remova|retire|exclua|tire|descadastre)\\b" +
            "|\\bnao\\s+quero\\s+parar\\s+de\\s+receber\\b" +
            "|\\bnao\\s+quer(?:o|emos)\\s+(?:sair|cancelar|parar)\\b",
    )

    private val pedido = listOf(
        // A mensagem inteira e a palavra ("Pare", "SAIR!", "pare, por favor").
        Regex("^[\"'“”]*(?:por favor[, ]*)?(?:pare|parar|para|parem|chega|sair|stop)[\"'“”]*(?:[, .]*(?:por favor|pfv?))?[!. \"'“”]*$"),
        Regex("\\bnao\\s+(?:me\\s+)?$VERBO_ENVIO\\s+(?:(?:mais|novas?|outras?)\\s+)?(?:mensagens?|msg|dms?|nada)\\b"),
        Regex("\\bnao\\s+(?:me\\s+)?$VERBO_ENVIO\\s+mais\\b"),
        Regex("\\bnao\\s+quero\\s+(?:mais\\s+)?receber\\b"),
        Regex("\\bnao\\s+quero\\s+mais\\s+(?:mensage(?:m|ns)|msg)\\b"),
        Regex("\\b(?:pare|para|parem)\\s+(?:de\\s+(?:me\\s+)?(?:mandar|enviar|incomodar|perturbar)|com\\s+(?:as\\s+)?mensagens)\\b"),
        Regex("\\b(?:me\\s+)?(?:remova|remove|retire|retira|exclua|tire|tira)\\b[^.!?\\n]{0,35}\\b(?:lista|contatos?|cadastro)\\b"),
        Regex("\\bsai(?:a|r)?\\s+(?:da|dessa|desta)\\s+lista\\b(?!\\s+de\\s+espera)"),
        Regex("\\bquer(?:o|emos)\\s+(?:sair|parar\\s+de\\s+receber|ser\\s+(?:removid|excluid|retirad|descadastrad)[oa]s?)\\b"),
        Regex("\\bdescadastr\\w*"),
        Regex("\\bsem\\s+mais\\s+mensagens\\b"),
        Regex("\\bme\\s+deix(?:a|e|em)\\s+em\\s+paz\\b"),
        Regex("\\bnao\\s+(?:me\\s+)?(?:contate|contatem)\\b|\\bnao\\s+entre\\s+em\\s+contato\\b"),
        Regex("\\bunsubscribe\\b|^stop\\b"),
    )

    fun pediuParaParar(texto: String): Boolean {
        val limpo = norm(texto).replace(instrucao, " ").replace(Regex("\\s+"), " ").trim()
        if (limpo.isEmpty()) return false
        // A negacao so desfaz o pedido que ela mesma nega ("nao pare de mandar"): um
        // "nao pare de me seguir" em outra frase nao cala "pare de me mandar mensagens".
        val negados = contrario.findAll(limpo).map { it.range }.toList()
        return pedido.any { p ->
            p.findAll(limpo).any { m -> negados.none { it.first <= m.range.last && m.range.first <= it.last } }
        }
    }
}
