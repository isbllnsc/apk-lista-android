package com.listalocal.service

import com.listalocal.core.ig.Evidence
import com.listalocal.core.ig.Evidence.Conversa
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.ArvoreReal
import com.listalocal.core.tree.UiGroup
import com.listalocal.core.tree.UiNode
import com.listalocal.core.tree.amigosFixture
import com.listalocal.core.tree.ig
import com.listalocal.core.tree.inboxFixture
import com.listalocal.core.tree.threadFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qual janela o servico le, com as janelas que a calibracao viu no celular
 * (INSTAGRAM-APP-REAL.md, 0.1): a atividade principal (caixa de entrada, com
 * a "janela ativa" presa nela) e a ModalActivity da conversa por cima, mais
 * teclado e o painel Pausar/Parar, que nao sao janelas de aplicativo.
 */
class JanelasTest {

    private val prof = SelectorProfile.IG_448
    private val igPkg = "com.instagram.android"

    private val principal = inboxFixture("minhaconta")
    private val conversa = threadFixture("ana.souza", nome = "Ana Souza")
    private val teclado = Janelas.Janela<UiNode>(ig(text = "q w e"), "com.samsung.android.honeyboard", app = false, camada = 8)
    private val painel = Janelas.Janela<UiNode>(ig(text = "Pausar"), "com.listalocal.instagram.claude", app = false, camada = 9)

    private fun janela(raiz: UiNode?, camada: Int, pacote: String? = raiz?.let { igPkg }) =
        Janelas.Janela(raiz, pacote, app = true, camada = camada)

    private fun escolher(janelas: List<Janelas.Janela<UiNode>>, vararg reservas: Pair<UiNode, String>) =
        escolherComJanela(janelas, *reservas.map { (r, p) -> Janelas.Reserva(r, p) }.toTypedArray())

    private fun escolherComJanela(janelas: List<Janelas.Janela<UiNode>>, vararg reservas: Janelas.Reserva<UiNode>) =
        Janelas.escolher(
            janelas, igPkg,
            temConversa = { Evidence.temConversa(it, prof) },
            ehPrincipal = { r -> prof.abasIds.any(r::exists) },
        ) { reservas.toList() }

    @Test fun `conversa na Modal por cima da principal le a Modal e nao a caixa de entrada velha`() {
        val lidas = escolher(listOf(janela(principal, 1), janela(conversa, 2), teclado, painel))
        assertEquals(1, lidas.size)
        assertSame(conversa, lidas[0])
    }

    @Test fun `camadas trocadas acham a conversa abaixo e deixam a principal velha de fora`() {
        val lidas = escolher(listOf(janela(principal, 3), janela(conversa, 2)))
        assertEquals(listOf<UiNode>(conversa), lidas)
        // A linha "Usuario do Instagram" da caixa de entrada velha nao vira conta indisponivel.
        assertEquals(Conversa.PROVADA, Evidence.conversa(UiGroup.juntar(lidas)!!, prof, "ana.souza"))
    }

    @Test fun `dialogo do Instagram por cima da conversa e lido junto`() {
        val dialogo = ig(id = "dialog_container", children = listOf(ig(text = "Tente novamente mais tarde")))
        val lidas = escolher(listOf(janela(principal, 1), janela(conversa, 2), janela(dialogo, 3)))
        assertEquals(listOf(conversa, dialogo), lidas)
        val arvore = UiGroup.juntar(lidas)!!
        assertTrue(Evidence.restricao(arvore, prof))
        assertEquals(Conversa.PROVADA, Evidence.conversa(arvore, prof, "ana.souza"))
    }

    @Test fun `outro app na frente nada e lido`() {
        val app = janela(ig(text = "Lista Local"), 5, "com.listalocal.instagram.claude")
        assertTrue(escolher(listOf(janela(principal, 1), janela(conversa, 2), app)).isEmpty())
        // Nem a reserva: o dono esta usando outro app.
        assertTrue(escolher(listOf(app), conversa to igPkg).isEmpty())
    }

    @Test fun `janela de cima ilegivel e tratada como outro app`() {
        assertTrue(escolher(listOf(janela(principal, 1), janela(null, 2, pacote = null))).isEmpty())
    }

    @Test fun `teclado e painel por cima nao contam como outro app`() {
        assertEquals(listOf<UiNode>(conversa), escolher(listOf(teclado, painel, janela(conversa, 2))))
    }

    @Test fun `sem conversa le a janela do Instagram de cima`() {
        val amigos = amigosFixture(listOf("ana" to true))
        assertEquals(listOf<UiNode>(amigos), escolher(listOf(janela(principal, 1), janela(amigos, 2))))
        assertEquals(listOf<UiNode>(principal), escolher(listOf(janela(principal, 1), teclado)))
    }

    @Test fun `Modal fora da lista de janelas vem pela fonte do evento`() {
        val lidas = escolher(listOf(janela(principal, 1)), principal to igPkg, conversa to igPkg)
        assertEquals(listOf<UiNode>(conversa), lidas)
    }

    @Test fun `sem service windows a reserva com a conversa vem antes da janela ativa velha`() {
        assertEquals(listOf<UiNode>(conversa), escolher(emptyList(), principal to igPkg, conversa to igPkg))
        assertEquals(listOf<UiNode>(principal), escolher(emptyList(), principal to igPkg))
    }

    @Test fun `reserva de outro pacote nunca e lida`() {
        assertTrue(escolher(emptyList(), conversa to "com.whatsapp").isEmpty())
        assertTrue(escolher(emptyList()).isEmpty())
    }

    /**
     * O teste real (b), 26/09: neste A14 a ModalActivity vem com a raiz nula (o "null root node" do uiautomator,
     * INSTAGRAM-APP-REAL.md 0.1), no topo, com a janela ativa presa na principal. Nada era lido: a conversa aberta
     * pela linha "nao abria" 3 vezes e a fila parava. A arvore dela vem pela fonte do evento DA MESMA janela.
     */
    @Test fun `Modal sem raiz no topo vem pelo evento da mesma janela, nunca pela caixa velha (telas reais)`() {
        val caixa = ArvoreReal.raiz("caixa_principal")
        val conversaReal = ArvoreReal.raiz("v_conversa")
        val janelas = listOf(
            Janelas.Janela<UiNode>(caixa, igPkg, app = true, camada = 1, id = 3),
            Janelas.Janela<UiNode>(null, null, app = true, camada = 2, id = 7),
            teclado, painel,
        )
        val lidas = escolherComJanela(janelas, Janelas.Reserva(caixa, igPkg, 3), Janelas.Reserva(conversaReal, igPkg, 7))
        assertEquals(listOf(conversaReal), lidas)
        assertTrue(Evidence.temConversa(UiGroup.juntar(lidas)!!, prof))
    }

    @Test fun `sem raiz no topo e sem evento dessa janela nada e lido, nem a caixa velha nem a conversa de outra janela`() {
        val caixa = ArvoreReal.raiz("caixa_principal")
        val janelas = listOf(
            Janelas.Janela<UiNode>(caixa, igPkg, app = true, camada = 1, id = 3),
            Janelas.Janela<UiNode>(null, null, app = true, camada = 2, id = 7),
        )
        assertTrue(escolherComJanela(janelas, Janelas.Reserva(caixa, igPkg, 3), Janelas.Reserva(conversa, igPkg, 9)).isEmpty())
        assertTrue(escolherComJanela(janelas, Janelas.Reserva(caixa, igPkg, 3), Janelas.Reserva(conversa, igPkg)).isEmpty())
    }

    @Test fun `campo da conversa sem cabecalho legivel ainda marca a janela da conversa`() {
        val soCampo = ig(children = listOf(ig(id = "row_thread_composer_edittext", text = "Mensagem...")))
        assertEquals(listOf(soCampo), escolher(listOf(janela(soCampo, 1), janela(principal, 2))))
        assertFalse(Evidence.temConversa(principal, prof))
    }
}
