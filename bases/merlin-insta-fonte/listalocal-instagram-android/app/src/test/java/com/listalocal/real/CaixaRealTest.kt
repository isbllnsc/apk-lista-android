package com.listalocal.real

import com.listalocal.core.ig.Evidence
import com.listalocal.core.ig.Listas
import com.listalocal.core.tree.ArvoreReal
import com.listalocal.real.Perfis.APARELHO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct > caixa de entrada sobre as telas REAIS (t04, inbox, t16_final, _ib, _in1,
 * t07d e Pedidos t15c). A linha e Compose sem id: o nome vem do texto que abre a
 * descricao-resumo "<nome>, <estado>, <tempo>" (INSTAGRAM-APP-REAL.md 1.f).
 */
class CaixaRealTest {

    /** As conversas de cada tela, lidas a olho no print/arvore (nome anonimizado). */
    private val conversas = mapOf(
        "caixa_principal" to listOf("Pessoa 14", "Pessoa 15", "Pessoa 16", "pessoa17", "pessoa19", "Pessoa 20"),
        "caixa_principal_2" to listOf("Pessoa 14", "Pessoa 15", "Pessoa 16", "pessoa17", "pessoa19", "Pessoa 20"),
        "caixa_principal_3" to listOf("Pessoa 14", "Pessoa 15", "Pessoa 16", "pessoa17", "pessoa19", "Pessoa 20"),
        "caixa_velha_perfil_na_frente" to listOf("Pessoa 14", "Pessoa 15", "Pessoa 16", "pessoa17", "pessoa19", "Pessoa 20"),
        "caixa_rolada" to listOf(
            "Pessoa 28", "pessoa29", "pessoa30", "Pessoa 32", "Pessoa 33", "Pessoa 34", "pessoa35", "Pessoa 36", "Pessoa 37", "Pessoa 38",
        ),
        "caixa_rolada_2" to listOf(
            "Pessoa 15", "Pessoa 16", "pessoa17", "pessoa19", "Pessoa 20", "pessoa39", "Pessoa 40", "Pessoa 42", "Pessoa 08", "Pessoa 44",
        ),
    )

    @Test fun `as conversas da tela, na ordem, sem busca, notas, filtros nem a previa de snaps`() =
        emTodas(Telas.CAIXA, "Listas.conversas") { n, r ->
            val lidas = Listas.conversas(r, APARELHO).map { it.nome }
            if (lidas == conversas.getValue(n)) null else "leu $lidas"
        }

    @Test fun `o no a tocar e a linha inteira (nunca o avatar, que abre story)`() =
        emTodas(Telas.CAIXA, "LinhaConversa.no") { _, r ->
            Listas.conversas(r, APARELHO).filterNot { l ->
                l.no.isClickable && l.no.bounds?.esquerda == 0 && l.no.children.first().contentDescription.orEmpty().startsWith(l.nome + ",")
            }.map { it.no.descr() }.takeIf { it.isNotEmpty() }?.let { "tocaria em $it" }
        }

    @Test fun `Pedidos marcado e recusado, Principal nao`() {
        emTodas(Telas.PEDIDOS, "emPedidos deveria ser true") { _, r -> "false".takeIf { !Listas.emPedidos(r, APARELHO) } }
        emTodas(Telas.CAIXA, "emPedidos deveria ser false") { _, r -> "true".takeIf { Listas.emPedidos(r, APARELHO) } }
        assertTrue(Listas.conversas(Telas.raiz("caixa_pedidos"), APARELHO).isEmpty())
    }

    /**
     * Os titulos de musica das notas do topo sao TextView marcados (selected: o letreiro que corre) e o texto e de
     * terceiro. Uma nota com a musica "Requests" (ou "Pedidos ...") parava a operacao com Principal marcado.
     */
    @Test fun `nota com musica Requests ou Pedidos nao e o filtro Pedidos`() =
        emTodas(Telas.CAIXA - "caixa_rolada" - "caixa_rolada_2", "emPedidos com a nota trocada") { _, r ->
            listOf("Requests", "Pedidos de Natal").mapNotNull { musica ->
                "leu Pedidos com a musica '$musica'".takeIf { Listas.emPedidos(ArvoreReal.comRotulo(r, "Pessoa 06", musica), APARELHO) }
            }.takeIf { it.isNotEmpty() }?.joinToString()
        }

    @Test fun `filtro ligado no chip Filtros e reconhecido, nas telas reais nao ha`() {
        emTodas(Telas.CAIXA + Telas.PEDIDOS, "filtroLigado deveria ser false") { _, r -> "true".takeIf { Listas.filtroLigado(r, APARELHO) } }
        // Suposto (nao medido): com "Nao lidos" ligado, o chip mostra o filtro no lugar de "Filtros".
        assertTrue(Listas.filtroLigado(ArvoreReal.comRotulo(Telas.raiz("caixa_principal"), "Filtros", "Não lidos"), APARELHO))
    }

    @Test fun `sem o id da busca, Pesquisar nunca e uma conversa`() =
        emTodas(Telas.CAIXA - "caixa_rolada" - "caixa_rolada_2", "Listas.conversas sem o id search_row") { n, r ->
            val lidas = Listas.conversas(ArvoreReal.semId(r, "search_row"), APARELHO).map { it.nome }
            if (lidas == conversas.getValue(n)) null else "leu $lidas"
        }

    @Test fun `caixa no topo so com a linha Pesquisar na tela`() {
        emTodas(Telas.CAIXA - "caixa_rolada" - "caixa_rolada_2", "caixaNoTopo deveria ser true") { _, r -> "false".takeIf { !Listas.caixaNoTopo(r, APARELHO) } }
        emTodas(listOf("caixa_rolada", "caixa_rolada_2"), "caixaNoTopo deveria ser false") { _, r -> "true".takeIf { Listas.caixaNoTopo(r, APARELHO) } }
    }

    @Test fun `a lista de conversas que rola e a da caixa`() =
        emTodas(Telas.CAIXA + Telas.PEDIDOS, "rolagemConversas") { _, r ->
            val no = Listas.rolagemConversas(r, APARELHO)
            if (no?.id() == "inbox_refreshable_thread_list_recyclerview") null else "rola ${no?.descr()}"
        }

    @Test fun `Nova mensagem (conferencia do modo DM) e o lapis do topo`() =
        emTodas(Telas.CAIXA + Telas.PEDIDOS, "porRotulo(new_message)") { _, r ->
            val no = Evidence.porRotulo(r, APARELHO, "new_message")
            if (no != null && no.contentDescription == "Nova mensagem" && no.isClickable) null else "achou ${no?.descr()}"
        }

    @Test fun `caixa de entrada nao tem conversa aberta (a janela da conversa e outra)`() =
        emTodas(Telas.CAIXA + Telas.PEDIDOS, "temConversa deveria ser false") { _, r ->
            "true".takeIf { Evidence.temConversa(r, APARELHO) }
        }

    @Test fun `nenhuma outra tela real tem conversas da caixa`() =
        emTodas(Telas.PRINCIPAIS - Telas.CAIXA.toSet(), "Listas.conversas deveria ser vazio") { _, r ->
            Listas.conversas(r, APARELHO).map { it.nome }.takeIf { it.isNotEmpty() }?.let { "leu $it" }
        }

    /**
     * Outra versao (ids trocados): a conferencia DM aprende a lista pela forma (lista vertical que rola) na caixa
     * provada, e a operacao le as conversas pelo id aprendido. So "rolavel" nunca chegava ao minimo: a conferencia
     * liberava e "Conversas do Direct" parava em "A lista nao abriu".
     */
    @Test fun `ids diferentes - le as mesmas conversas`() =
        emTodas(Telas.CAIXA, "Listas.conversas com ids trocados, depois da conferencia") { n, r ->
            val t = ArvoreReal.comIdsTrocados(r)
            val a = com.listalocal.core.selectors.Aprendiz(com.listalocal.core.selectors.SignatureLibrary.doModo(com.listalocal.core.selectors.SignatureLibrary.DM))
            a.viu(t)
            val cal = com.listalocal.core.selectors.SelectorProfile.IG_448.comAprendido(
                a.perfil("448.0.0.52.84", "pt", "DM", com.listalocal.core.selectors.SignatureLibrary.DM, 0),
            )
            val lidas = Listas.conversas(t, cal).map { it.nome }
            if (lidas == conversas.getValue(n)) null else "aprendeu #${cal.aprendidos["inbox_list"]}, leu $lidas"
        }

    /** A lista pela forma nunca vale ao vivo: a de seguidores tem a mesma forma ("Seguir de volta" virava conversa). */
    @Test fun `ids diferentes - sem conferir, nenhuma lista vira a caixa`() =
        emTodas(Telas.PRINCIPAIS, "Listas.conversas com ids trocados, sem conferir") { _, r ->
            Listas.conversas(ArvoreReal.comIdsTrocados(r), APARELHO).map { it.nome }.takeIf { it.isNotEmpty() }?.let { "leu $it" }
        }

    @Test fun `ids diferentes - Pedidos continua recusado`() =
        assertEquals(true, Listas.emPedidos(ArvoreReal.comIdsTrocados(Telas.raiz("caixa_pedidos")), APARELHO))
}
