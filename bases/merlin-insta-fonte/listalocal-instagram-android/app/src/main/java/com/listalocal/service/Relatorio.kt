package com.listalocal.service

/**
 * Planilha do resultado, no formato das do Merlin: BOM, `;`, celulas entre
 * aspas e protecao contra formula (celula que comeca com = + @ - ganha ').
 */
object Relatorio {

    private fun cell(value: String): String {
        var v = value
        if (Regex("^[\\s\\uFEFF]*[=+@-]").containsMatchIn(v)) v = "'$v"
        return "\"" + v.replace("\"", "\"\"") + "\""
    }

    /**
     * [mensagens]: as da operacao, para escrever em cada pessoa o texto em que
     * Enviar foi tocado ("mensagem": o numero; "texto_enviado": o texto, uma
     * por linha na sequencia).
     */
    fun csv(
        conta: String, modo: Modo, baseLegal: String, results: List<PersonResult>,
        mensagens: List<String> = emptyList(),
    ): String {
        val linhas = listOf(
            listOf("conta", "modo", "base_legal", "username", "nome", "lote", "situacao", "falha", "motivo",
                "mensagem", "texto_enviado"),
        ) + results.map {
            listOf(conta, modo.name, baseLegal, it.username, it.name, it.lote.toString(), it.desfecho.rotulo,
                if (it.desfecho.falha) "sim" else "não", it.motivo,
                it.enviadas.joinToString(","), it.enviadas.mapNotNull { n -> mensagens.getOrNull(n - 1) }.joinToString("\n"))
        }
        return "\uFEFF" + linhas.joinToString("\r\n") { l -> l.joinToString(";") { cell(it) } } + "\r\n"
    }
}
