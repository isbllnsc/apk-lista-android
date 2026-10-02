package com.listalocal.real

import com.listalocal.core.ig.Evidence
import com.listalocal.core.ig.Listas
import com.listalocal.core.match.ContactMatcher
import com.listalocal.core.match.MatchOutcome
import com.listalocal.core.selectors.RoleMatcher
import com.listalocal.core.selectors.SignatureLibrary
import com.listalocal.core.tree.ArvoreReal
import com.listalocal.real.Perfis.APARELHO
import com.listalocal.service.CloseFriendsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telas da ModalActivity (conversa, Nova mensagem, Amigos Proximos) sobre a
 * ESTRUTURA real (dump de Views da rodada 1: ids, classes, posicoes, clicavel,
 * marcado, desabilitado). O dump nao tem texto: os textos de UI documentados
 * entram marcados suposto="1" e os campos de pessoa sao "amigoNN". Por isso so
 * se afirma aqui o que depende de estrutura/estado, nao de texto lido.
 */
class ModaisReaisTest {

    // ---------------- conversa ----------------

    @Test fun `conversa aberta - cabecalho, campo e lista de mensagens pelos ids medidos`() =
        emTodas(Telas.CONVERSA, "conversa") { _, r ->
            listOfNotNull(
                "temConversa=false".takeIf { !Evidence.temConversa(r, APARELHO) },
                "sem campo".takeIf { Evidence.campo(r, APARELHO)?.id() != "row_thread_composer_edittext" },
                "sem message_list".takeIf { !Evidence.temMensagens(r, APARELHO) },
                "sem header_title".takeIf { APARELHO.nos(r, "header_title").isEmpty() },
            ).takeIf { it.isNotEmpty() }?.joinToString()
        }

    /**
     * A conversa como o SERVICO a le (sem as Views nao importantes): o direct_thread_header, layout sem clique nem
     * descricao, some, e o subtitulo (IgView de texto desenhado) tambem, no pior caso. O @ tem de vir do bloco
     * clicavel do nome (descricao "nome, @", suposta). Com o dump de Views inteiro o teste passava e o aparelho nao
     * leria o @ de ninguem pela caixa de entrada.
     */
    @Test fun `conversa como o servico le - o @ vem do bloco do nome sem o direct_thread_header`() {
        val comTexto = ArvoreReal.comRotuloNoId(
            ArvoreReal.comRotuloNoId(Telas.raiz("v_conversa"), "header_title", texto = "Pessoa 01"),
            "header_title_subtitle_container", descricao = "Pessoa 01, pessoa01",
        )
        val lida = ArvoreReal.semNaoImportantes(comTexto)
        assertTrue(lida.findByViewIdSuffix("direct_thread_header").isEmpty())
        assertTrue(lida.findByViewIdSuffix("header_subtitle").isEmpty())
        assertTrue(Evidence.temConversa(lida, APARELHO))
        assertEquals("pessoa01", Evidence.arrobaDoCabecalho(lida, APARELHO).username)
        assertEquals(Evidence.Conversa.PROVADA, Evidence.conversa(lida, APARELHO, "pessoa01"))
    }

    @Test fun `conversa como o servico le - cabecalho, campo e lista continuam pelos ids medidos`() =
        emTodas(Telas.CONVERSA, "conversa comprimida") { n, _ ->
            val r = ArvoreReal.semNaoImportantes(Telas.raiz(n))
            listOfNotNull(
                "temConversa=false".takeIf { !Evidence.temConversa(r, APARELHO) },
                "sem campo".takeIf { Evidence.campo(r, APARELHO)?.id() != "row_thread_composer_edittext" },
                "sem message_list".takeIf { !Evidence.temMensagens(r, APARELHO) },
                "sem header_title".takeIf { APARELHO.nos(r, "header_title").isEmpty() },
            ).takeIf { it.isNotEmpty() }?.joinToString()
        }

    @Test fun `Enviar so existe com texto no campo, e desabilitado em conversa nova nao vale`() {
        assertNull(Evidence.botaoEnviar(Telas.raiz("v_conversa"), APARELHO))
        assertNull(Evidence.botaoEnviar(Telas.raiz("v_conversa_temporaria"), APARELHO))
        val comTexto = Evidence.botaoEnviar(Telas.raiz("v_conversa_com_texto"), APARELHO)
        assertEquals("row_thread_composer_send_button_container", comTexto?.id())
        // Conversa nova: o Enviar ja aparece, DESABILITADO (t07h). Nao pode valer como "pronto para enviar".
        val nova = Telas.raiz("v_conversa_nova")
        assertTrue(nova.findByViewIdSuffix("row_thread_composer_send_button_container").single().isEnabled.not())
        assertNull(Evidence.botaoEnviar(nova, APARELHO))
    }

    /**
     * Sem a lista de mensagens nada e conferido (pedido de parar, nao enviada) e ninguem recebe. So "rolavel" nunca
     * chegava ao minimo: com o id renomeado toda pessoa parava em "as mensagens da conversa nao foram lidas".
     */
    // ponytail: pela forma, a lista precisa de 2 itens empilhados; conversa nova (1 item ou nenhum) so pelo id.
    @Test fun `ids diferentes - a lista de mensagens e achada pela forma`() =
        emTodas(Telas.CONVERSA - "v_conversa_nova", "message_list por assinatura") { _, r ->
            val t = ArvoreReal.comIdsTrocados(r)
            val no = APARELHO.nos(t, "message_list").firstOrNull()
            if (no?.id() == "message_list_v2" && Evidence.temMensagens(t, APARELHO)) null else "achou ${no?.descr()}"
        }

    @Test fun `ids diferentes - o Enviar sem rotulo nunca e chutado`() =
        // Sem o rotulo "Enviar" (nao lido) e sem o id conhecido: nenhum botao da barra vira Enviar.
        emTodas(Telas.CONVERSA, "send por assinatura deveria ser null") { n, _ ->
            RoleMatcher.achar(ArvoreReal.comIdsTrocados(Telas.raiz(n)), SignatureLibrary.send)?.let { "achou ${it.descr()}" }
        }

    @Test fun `ids diferentes - o campo de mensagem e achado pela forma (com e sem teclado, com e sem texto)`() =
        emTodas(Telas.CONVERSA, "composer por assinatura") { n, r ->
            val no = RoleMatcher.achar(ArvoreReal.comIdsTrocados(r), SignatureLibrary.composer)
            val campo = r.findByViewIdSuffix("row_thread_composer_edittext").single()
            if (no?.id() == "row_thread_composer_edittext_v2") null
            else "achou ${no?.descr()} (campo real ${campo.descr()} texto='${campo.text.orEmpty()}')"
        }

    // ---------------- Nova mensagem ----------------

    /**
     * Nova mensagem com 1 pilula ja abre a conversa EMBUTIDA com campo; com 2, a
     * PREVIA DE GRUPO com campo (enviar ali cria o grupo, sem confirmacao). O app
     * nunca envia por ali: a prova exige o cabecalho da conversa (direct_thread_header,
     * header_title), que essa tela nao tem.
     */
    @Test fun `Nova mensagem com pilulas tem campo mas nao tem cabecalho de conversa`() =
        emTodas(listOf("v_nova_mensagem_1_pessoa", "v_nova_mensagem_grupo"), "Nova mensagem embutida") { _, r ->
            listOfNotNull(
                "sem 'Para:'".takeIf { r.findByViewIdSuffix("direct_new_chat_to_field").isEmpty() },
                "sem campo".takeIf { Evidence.campo(r, APARELHO) == null },
                "tem cabecalho".takeIf { APARELHO.nos(r, "thread_header").isNotEmpty() || APARELHO.nos(r, "header_title").isNotEmpty() },
                "nomeProva".takeIf { Evidence.nomeProva(r, APARELHO, "Amigo 01") },
            ).takeIf { it.isNotEmpty() }?.joinToString()
        }

    @Test fun `ids diferentes - o campo de mensagem nunca e a busca do Para`() {
        for (n in listOf("v_nova_mensagem_1_pessoa", "v_nova_mensagem_grupo")) {
            val t = ArvoreReal.comIdsTrocados(Telas.raiz(n))
            assertEquals(n, "row_thread_composer_edittext_v2", RoleMatcher.achar(t, SignatureLibrary.composer)?.id())
        }
        // Nova mensagem vazia: a unica caixa de texto e a busca do topo; nao e o campo de mensagem.
        assertNull(RoleMatcher.achar(ArvoreReal.comIdsTrocados(Telas.raiz("v_nova_mensagem")), SignatureLibrary.composer))
    }

    @Test fun `conferencia DM ve a Nova mensagem pelo Para (id e forma)`() {
        for (n in Telas.NOVA_MENSAGEM) {
            val r = Telas.raiz(n)
            assertTrue(n, r.exists(APARELHO.idFor("new_chat_to")!!))
            assertEquals(n, "direct_new_chat_to_field_v2", APARELHO.noPorAssinatura(ArvoreReal.comIdsTrocados(r), "new_chat_to")?.id())
        }
    }

    // ---------------- folha de compartilhar (t12 reais) ----------------

    /**
     * A folha de compartilhar de um post tem busca, caixa por pessoa, campo "Escreva uma mensagem...", "Enviar" e
     * "Enviar para nova conversa em grupo (2)": tocar ali manda o post (e cria grupo) sem confirmacao. Nenhum portao
     * do app a toma por conversa, Nova mensagem, Amigos Proximos, caixa ou lista de seguidores, com os ids medidos ou
     * trocados (outra versao do Instagram).
     */
    @Test fun `folha de compartilhar nunca passa por conversa, Nova mensagem, Amigos Proximos, caixa ou lista`() {
        val telas = Telas.FOLHA.flatMap { listOf(it to Telas.raiz(it), "$it (ids trocados)" to ArvoreReal.comIdsTrocados(Telas.raiz(it))) }
        val erros = telas.mapNotNull { (n, r) ->
            val cf = CloseFriendsFlow(TelaRoteiro(Telas.FOLHA.first { n.startsWith(it) }), APARELHO)
            listOfNotNull(
                "temConversa".takeIf { Evidence.temConversa(r, APARELHO) },
                "conversa provada".takeIf { (1..15).any { Evidence.conversa(r, APARELHO, "amigo%02d".format(it)) == Evidence.Conversa.PROVADA } },
                "nomeProva".takeIf { (1..15).any { Evidence.nomeProva(r, APARELHO, "Amigo %02d".format(it)) } },
                "Enviar ${Evidence.botaoEnviar(r, APARELHO)?.descr()}".takeIf { Evidence.botaoEnviar(r, APARELHO) != null },
                "Para: ${APARELHO.noPorAssinatura(r, "new_chat_to")?.descr()}".takeIf { APARELHO.noPorAssinatura(r, "new_chat_to") != null },
                "Amigos Proximos".takeIf { cf.naTela(r) },
                "lista de seguidores".takeIf { Listas.naListaDeSeguidores(r, APARELHO) },
                "conversas da caixa ${Listas.conversas(r, APARELHO)}".takeIf { Listas.conversas(r, APARELHO).isNotEmpty() },
                "conta @${Evidence.conta(r, APARELHO)}".takeIf { Evidence.conta(r, APARELHO) != null },
            ).takeIf { it.isNotEmpty() }?.let { "  $n: ${it.joinToString()}" }
        }
        assertTrue("folha de compartilhar tomada por outra tela:" + erros.joinToString(""), erros.isEmpty())
    }

    // ---------------- Amigos Proximos ----------------

    @Test fun `Amigos Proximos - linhas, marcas e cabecalhos como na tela`() {
        val topo = Evidence.linhasAmigos(Telas.raiz("v_amigos_topo"), APARELHO)
        assertEquals((1..9).map { "amigo%02d".format(it) }, topo.map { it.username })
        val fim = Evidence.linhasAmigos(Telas.raiz("v_amigos_membros_fim"), APARELHO)
        assertEquals(9, fim.size)
        // Os 3 ultimos membros marcados; depois do cabecalho "Sugestoes", desmarcados.
        assertEquals(listOf(true, true, true, false, false, false, false, false, false), fim.map { it.marcada })
    }

    @Test fun `Amigos Proximos - busca pelo @ de um membro acha a linha exata ja marcada (nenhum toque)`() {
        val r = Telas.raiz("v_amigos_busca_membro")
        val arroba = r.findByViewIdSuffix("search_edit_text").single().text!!
        val m = ContactMatcher.match(arroba, Evidence.linhasAmigos(r, APARELHO))
        assertEquals(MatchOutcome.ALREADY_SELECTED, m.outcome)
        assertEquals(true, m.row?.marcada)
    }

    /**
     * Amigos Proximos rolada no meio (_cf real: sem o cabecalho "N pessoas" nem "Limpar tudo" na tela). E a tela em
     * que o dono deixa o Instagram se rolar durante uma pausa: continua provada (nenhuma parada a toa ao Continuar),
     * e nada ali e o "Limpar tudo".
     */
    @Test fun `Amigos Proximos rolada continua provada e sem Limpar tudo`() {
        val r = Telas.raiz("v_amigos_rolada")
        assertTrue(CloseFriendsFlow(TelaRoteiro("v_amigos_rolada"), APARELHO).naTela(r))
        assertTrue(Evidence.linhasAmigos(r, APARELHO).isNotEmpty())
        assertTrue(APARELHO.nos(r, "cf_clear").isEmpty())
        assertTrue(APARELHO.nos(ArvoreReal.comIdsTrocados(r), "cf_clear").isEmpty())
    }

    /** "Limpar tudo" tira as 162 pessoas: o guard naoLimpar olha a subarvore do alvo do toque. */
    @Test fun `Limpar tudo - so o botao do cabecalho, nunca dentro de uma linha ou do Concluir`() {
        for (n in Telas.AMIGOS) {
            val r = Telas.raiz(n)
            for (alvo in APARELHO.nos(r, "cf_row") + r.findByViewIdSuffix("done_button")) {
                assertTrue("$n: ${alvo.descr()}", APARELHO.nos(alvo, "cf_clear").isEmpty())
            }
        }
        val topo = Telas.raiz("v_amigos_topo")
        assertEquals(listOf("row_header_action"), APARELHO.nos(topo, "cf_clear").map { it.id() })
        // Com ids trocados (outro aparelho), a assinatura acha o mesmo botao e so ele.
        val trocada = ArvoreReal.comIdsTrocados(topo)
        assertEquals(listOf("row_header_action_v2"), APARELHO.nos(trocada, "cf_clear").map { it.id() })
        assertNotNull(Evidence.porRotulo(topo, APARELHO, "cf_done")?.takeIf { it.id() == "done_button" })
    }

    @Test fun `ids diferentes - linha e @ de Amigos Proximos pela forma`() {
        for (n in Telas.AMIGOS) {
            val r = Telas.raiz(n)
            val certos = Evidence.linhasAmigos(r, APARELHO).map { it.username to it.marcada }
            val trocados = Evidence.linhasAmigos(ArvoreReal.comIdsTrocados(r), APARELHO).map { it.username to it.marcada }
            assertEquals(n, certos, trocados)
        }
    }
}
