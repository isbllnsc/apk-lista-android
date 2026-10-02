package com.listalocal.core.ig

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptOutTest {

    private fun sim(vararg textos: String) = textos.forEach { assertTrue(it, OptOut.pediuParaParar(it)) }
    private fun nao(vararg textos: String) = textos.forEach { assertFalse(it, OptOut.pediuParaParar(it)) }

    @Test fun `palavra sozinha e pedido`() {
        sim("pare", "PARE!", "Pare, por favor", "sair", "Sair.", "chega", "stop", "\"Pare\"")
    }

    @Test fun `frases de pedido`() {
        sim(
            "Não quero mais receber essas mensagens",
            "nao quero receber",
            "Por favor não me mande mais mensagens",
            "Parem de me mandar isso",
            "me tira dessa lista",
            "quero sair da lista",
            "Quero ser removida",
            "descadastrar",
            "me deixa em paz",
        )
    }

    @Test fun `negacao e perguntas nao sao pedido`() {
        nao(
            "Não pare, adorei!",
            "não quero sair",
            "Para quando é o evento?",
            "Vou sair hoje à noite",
            "Sair que horas?",
            "Tenho interesse",
            "sai da lista de espera?",
            "",
        )
    }

    @Test fun `instrucao do dono nao e pedido`() {
        nao(
            "Se não quiser receber mais mensagens nossas, responda \"pare\".",
            "Para não receber mais, responda SAIR",
            "Evento sábado! Responda PARE para sair",
        )
    }

    @Test fun `negacao em outra frase nao cala o pedido`() {
        sim(
            "Para de me mandar mensagens, cansei disso. Mas nao pare de me seguir, hein",
            "Nao quero mais receber isso. Mas nao pare com as piadas",
            "Não quero cancelar a compra, mas não quero mais receber mensagens",
        )
        // A negacao do proprio pedido continua desfazendo.
        nao(
            "Não pare de me mandar, adoro!",
            "Não quero sair da lista",
            "não me tire da lista, por favor",
            "Não quero parar de receber",
        )
    }
}
