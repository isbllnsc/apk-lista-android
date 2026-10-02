package com.listalocal.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorioTest {

    @Test fun `planilha no formato do Merlin com protecao contra formula`() {
        val csv = Relatorio.csv(
            "minhaconta", Modo.DM, "Convite a contatos/seguidores — legítimo interesse",
            listOf(
                PersonResult("ana", "=SOMA(A1)", 1, Desfecho.ENVIADO, ""),
                PersonResult("bia", "Bia \"B\"", 2, Desfecho.INCERTO, "sem evidência de envio no prazo"),
            ),
        )
        assertTrue(csv.startsWith("\uFEFF\"conta\";\"modo\";\"base_legal\";\"username\""))
        assertTrue(csv.contains("\"legítimo interesse\"") || csv.contains("legítimo interesse"))
        val linhas = csv.removePrefix("\uFEFF").trimEnd().split("\r\n")
        assertEquals(3, linhas.size)
        assertTrue(linhas[1], linhas[1].contains("\"'=SOMA(A1)\""))
        assertTrue(linhas[2], linhas[2].contains("\"Bia \"\"B\"\"\""))
        assertTrue(linhas[2], linhas[2].endsWith("\"sim\";\"sem evidência de envio no prazo\";\"\";\"\""))
    }

    @Test fun `planilha traz a mensagem enviada a cada pessoa`() {
        val msgs = listOf("Oi!", "Sábado tem evento.", "Responda PARE para sair.")
        val csv = Relatorio.csv(
            "outraconta", Modo.DM, "base",
            listOf(
                PersonResult("ana", "Ana", 0, Desfecho.ENVIADO, "", listOf(2)),
                PersonResult("bia", "Bia", 0, Desfecho.ENVIADO, "1 de 3 mensagens; parou: x", listOf(1)),
                PersonResult("caio", "Caio", 0, Desfecho.ENVIADO, "", listOf(1, 2, 3)),
                PersonResult("duda", "Duda", 0, Desfecho.PULADO, "já recebeu antes"),
            ),
            msgs,
        )
        val linhas = csv.removePrefix("\uFEFF").trimEnd().split("\r\n")
        assertTrue(linhas[0], linhas[0].endsWith("\"mensagem\";\"texto_enviado\""))
        assertTrue(linhas[1], linhas[1].startsWith("\"outraconta\"") && linhas[1].endsWith("\"2\";\"Sábado tem evento.\""))
        assertTrue(linhas[2], linhas[2].endsWith("\"1\";\"Oi!\""))
        assertTrue(csv, csv.contains("\"1,2,3\";\"Oi!\nSábado tem evento.\nResponda PARE para sair.\""))
        assertTrue(linhas.last(), linhas.last().endsWith("\"\";\"\""))
    }
}
