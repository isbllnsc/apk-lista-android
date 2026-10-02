package com.listalocal.ui

import com.listalocal.service.ModoMensagens
import com.listalocal.service.Velocidade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O que o dono escreve no Plano vira a operacao: mensagens, limite, partida e hora. */
class UiStateTest {

    private val base = UiState(contaInstagram = "qualquerconta", mensagem = "Oi")

    @Test fun `mensagens vazias ficam de fora e a linha de confirmacao resume tudo`() {
        val ui = base.copy(
            mensagem2 = "  ", mensagem3 = "Tchau", modoMensagens = ModoMensagens.SEQUENCIA,
            limiteTexto = "50", velocidade = Velocidade.NORMAL,
        )
        assertEquals(listOf("Oi", "Tchau"), ui.mensagens)
        assertEquals("Enviar como @qualquerconta · até 50 pessoas · 2 mensagens em sequência · Normal", ui.linhaDoEnvio)
        assertEquals("Enviar como @qualquerconta · sem limite de pessoas · 1 mensagem · Normal", base.linhaDoEnvio)
    }

    @Test fun `limite, comecar a partir de e parar as - vazio vale, invalido barra`() {
        assertTrue(base.opcoesOk)
        assertNull(base.limite)
        val ok = base.copy(limiteTexto = "3", aPartirDeTexto = "@Ana.Souza", pararAsTexto = "18:30")
        assertEquals(3, ok.limite)
        assertEquals("ana.souza", ok.aPartirDe)
        assertEquals(18 * 60 + 30, ok.pararAs)
        assertTrue(ok.opcoesOk)
        assertFalse(base.copy(limiteTexto = "0").opcoesOk)
        assertFalse(base.copy(pararAsTexto = "25:00").opcoesOk)
        assertFalse(base.copy(aPartirDeTexto = "não é @").opcoesOk)
        assertEquals(9 * 60 + 5, base.copy(pararAsTexto = "9h05").pararAs)
    }
}
