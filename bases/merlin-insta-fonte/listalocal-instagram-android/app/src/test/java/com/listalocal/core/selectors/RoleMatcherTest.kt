package com.listalocal.core.selectors

import com.listalocal.core.ig.Listas
import com.listalocal.core.tree.Bounds
import com.listalocal.core.tree.UiNode
import com.listalocal.core.tree.abasDeBaixo
import com.listalocal.core.tree.ig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prova que a ASSINATURA acha o controle certo e recusa o errado em VÁRIAS
 * variações: ids diferentes, textos es/pt/en, um controle sem id, Enviar só
 * depois do texto, lista de seguidores com outro id. É a rede de segurança da
 * auto-calibração: o motor casa por papel, não por id fixo.
 */
class RoleMatcherTest {

    private fun idDe(n: UiNode?) = n?.viewIdResourceName?.substringAfter(":id/")

    // Uma conversa: cabeçalho no topo, lista de mensagens no meio, barra do campo no rodapé.
    private fun conversa(
        idCampo: String? = "row_thread_composer_edittext",
        textoCampo: String = "Mensagem...",
        comEnviar: Boolean = false,
        idEnviar: String? = "row_thread_composer_send_button_container",
        descEnviar: String? = "Enviar",
        comIsca: Boolean = false,
        comBusca: Boolean = false,
        buscaTexto: String = "Pesquisar",
    ): UiNode {
        val campo = ig(id = idCampo, text = textoCampo, cls = "android.widget.EditText", clickable = true, bounds = Bounds(0, 2150, 1000, 2250))
        val enviar = if (comEnviar) ig(id = idEnviar, desc = descEnviar, clickable = true, bounds = Bounds(900, 2150, 1000, 2250)) else null
        val sticker = if (comEnviar) ig(id = "row_thread_composer_button_sticker_shortcut", clickable = true, bounds = Bounds(0, 2150, 100, 2250)) else null
        val semTexto = if (!comEnviar) listOf(
            ig(id = "row_thread_composer_button_camera", clickable = true, bounds = Bounds(0, 2150, 100, 2250)),
            ig(id = "row_thread_composer_button_gallery", clickable = true, bounds = Bounds(120, 2150, 220, 2250)),
        ) else emptyList()
        val barra = ig(id = "message_composer_bar", bounds = Bounds(0, 2100, 1000, 2260), children = listOf(
            ig(id = "row_thread_composer_container", children = listOfNotNull(campo, sticker, enviar) + semTexto),
        ))
        val isca = if (comIsca) listOf(ig(children = listOf(
            ig(text = "comentou: olha isso"),
            ig(id = "floating_send_container", clickable = true, children = listOf(
                ig(id = "send_button_pill_container", children = listOf(ig(id = "send_label", text = "Enviar"))),
            )),
        ))) else emptyList()
        val lista = ig(id = "message_list", scrollable = true, cls = "CustomFadingEdgeRecyclerView", bounds = Bounds(0, 300, 1000, 2090), children = isca)
        val header = ig(id = "direct_thread_header", bounds = Bounds(0, 60, 1000, 220), children = listOf(
            ig(id = "header_title", text = "Pessoa", bounds = Bounds(160, 90, 800, 150)),
        ))
        val busca = if (comBusca) ig(id = "row_search_edit_text", text = buscaTexto, cls = "android.widget.EditText", clickable = true, bounds = Bounds(0, 100, 1000, 180)) else null
        return ig(bounds = Bounds(0, 0, 1000, 2400), children = listOfNotNull(header, busca, lista, barra))
    }

    // ---- campo de mensagem (composer) ----

    @Test fun campoAchadoComIdDiferente() {
        val d = RoleMatcher.descobrir(conversa(idCampo = "chat_input_v2"), SignatureLibrary.composer)
        assertEquals("chat_input_v2", d?.id)
    }

    @Test fun campoAchadoSemIdNenhum() {
        // Controle sem id: acha pela forma (EditText, rodapé, dica).
        val no = RoleMatcher.achar(conversa(idCampo = null), SignatureLibrary.composer)
        assertNotNull(no)
        assertTrue(RoleMatcher.ehEditText(no!!))
        assertNull(idDe(no))
    }

    @Test fun campoRecusaBuscaDoTopo() {
        // Duas EditText: a busca no topo e o campo no rodapé. A assinatura pega o campo.
        val no = RoleMatcher.achar(conversa(comBusca = true), SignatureLibrary.composer)
        assertEquals("row_thread_composer_edittext", idDe(no))
    }

    @Test fun campoIdiomaEspanhol() {
        assertNotNull(RoleMatcher.achar(conversa(textoCampo = "Mensaje..."), SignatureLibrary.composer))
    }

    @Test fun campoIdiomaNaoPrevistoPelaForma() {
        // Sem dica reconhecível (idioma novo) e uma busca no topo: a região (rodapé) decide.
        val no = RoleMatcher.achar(conversa(idCampo = "campo_x", textoCampo = "Habari...", comBusca = true, buscaTexto = "Tafuta"), SignatureLibrary.composer)
        assertEquals("campo_x", idDe(no))
    }

    @Test fun campoRecusaEditTextDoMeioSemSinal() {
        // Um 2º EditText clicável no meio da tela (busca/comentário), sem "mensagem",
        // sem id conhecido e fora do rodapé: editável+clicável (2) não basta (mínimo 3).
        val raiz = ig(bounds = Bounds(0, 0, 1000, 2400), children = listOf(
            ig(id = "search_x", text = "Buscar", cls = "android.widget.EditText", clickable = true, bounds = Bounds(0, 1000, 1000, 1100)),
        ))
        assertNull(RoleMatcher.achar(raiz, SignatureLibrary.composer))
    }

    // ---- Enviar ----

    @Test fun enviarSoComTexto() {
        val d = RoleMatcher.descobrir(conversa(comEnviar = true, idEnviar = "btn_send_novo"), SignatureLibrary.send)
        assertEquals("btn_send_novo", d?.id)
    }

    @Test fun enviarAusenteSemTexto() {
        // Sem texto o botão Enviar não existe; câmera/galeria NÃO viram Enviar.
        assertNull(RoleMatcher.achar(conversa(comEnviar = false), SignatureLibrary.send))
    }

    @Test fun enviarRecusaIscaDaMensagem() {
        // "Enviar" (send_label) dentro de uma mensagem NÃO é o botão Enviar.
        val no = RoleMatcher.achar(conversa(comEnviar = true, idEnviar = "btn_send", comIsca = true), SignatureLibrary.send)
        assertEquals("btn_send", idDe(no))
    }

    @Test fun enviarInglesPeloRotulo() {
        val no = RoleMatcher.achar(conversa(comEnviar = true, idEnviar = "no_id_send", descEnviar = "Send"), SignatureLibrary.send)
        assertEquals("no_id_send", idDe(no))
    }

    // ---- "Para:"/"To:" (new_chat_to): campo da Nova mensagem ----

    @Test fun paraRecusaRotuloComDoisPontosNoMeioDaPalavra() {
        // "Categoria:"/"Conta:"/"Data:" no topo NÃO são o campo "Para:": o "a:" ancorado
        // não casa ":" no meio de palavra. Sem texto casado, só a região (2) < mínimo (3).
        for (t in listOf("Categoria:", "Conta:", "Data:")) {
            val raiz = ig(bounds = Bounds(0, 0, 1080, 2400), children = listOf(
                ig(id = "decoy", text = t, cls = "android.widget.TextView", bounds = Bounds(40, 60, 1040, 150)),
            ))
            assertNull("virou new_chat_to: '$t'", RoleMatcher.achar(raiz, SignatureLibrary.newChatTo))
        }
    }

    @Test fun paraAceitaAMaiusculoNoInicioComRegiao() {
        // "A:" (es/it) no topo continua valendo: texto (2) + região topo (2) = 4 >= 3.
        val raiz = ig(bounds = Bounds(0, 0, 1080, 2400), children = listOf(
            ig(id = "campo_para", text = "A:", cls = "android.widget.TextView", bounds = Bounds(40, 180, 200, 260)),
        ))
        assertNotNull(RoleMatcher.achar(raiz, SignatureLibrary.newChatTo))
    }

    @Test fun paraRecusaPreposicaoNoMeioDeFraseNoTopo() {
        // Ancorado em ^: "para"/"to" no MEIO de uma frase do topo não vira o campo "Para:".
        // Sem texto casado, só a região (2) fica abaixo do mínimo (3).
        for (t in listOf("Buscar pessoas para adicionar", "Add people to a chat", "Envie para todos")) {
            val raiz = ig(bounds = Bounds(0, 0, 1080, 2400), children = listOf(
                ig(id = "decoy", text = t, cls = "android.widget.TextView", bounds = Bounds(40, 180, 1040, 260)),
            ))
            assertNull("virou new_chat_to: '$t'", RoleMatcher.achar(raiz, SignatureLibrary.newChatTo))
        }
    }

    @Test fun paraAceitaORotuloDoCampoNoInicio() {
        for (t in listOf("Para:", "To:", "A:", "An:", "Para", "To")) {
            val raiz = ig(bounds = Bounds(0, 0, 1080, 2400), children = listOf(
                ig(id = "campo", text = t, cls = "android.widget.TextView", bounds = Bounds(40, 180, 240, 260)),
            ))
            assertNotNull("não achou '$t'", RoleMatcher.achar(raiz, SignatureLibrary.newChatTo))
        }
    }

    // ---- Perfil > Seguidores com OUTRO id: aba Perfil, contagem, aba Seguidores (findings 3/4) ----

    @Test fun numeroDeSeguidoresNuncaEOdeSeguindo() {
        // A abreviacao ("mil", "K") entre o numero e a palavra nao abre a porta para "seguindo".
        for (t in listOf("413 seguindo", "12,3 mil seguindo", "1.2K following", "Seguir", "388")) {
            val raiz = ig(bounds = Bounds(0, 0, 1080, 2400), children = listOf(
                ig(id = "x", desc = t, clickable = true, bounds = Bounds(200, 300, 520, 380)),
            ))
            assertNull("casou '$t'", RoleMatcher.achar(raiz, SignatureLibrary.profileFollowers))
        }
    }

    @Test fun abaPerfilContagemEAbaSeguidoresAchadasComOutroId() {
        val perfil = ig(bounds = Bounds(0, 0, 1080, 2400), children = listOf(
            ig(id = "count_v2", desc = "388 followers", clickable = true, bounds = Bounds(200, 300, 520, 380)),
            abasDeBaixo(direct = "Direct", perfil = "Profile", idDirect = "tabbar_direct_v2", idPerfil = "tabbar_profile_v2"),
        ))
        assertEquals("tabbar_profile_v2", idDe(RoleMatcher.achar(perfil, SignatureLibrary.profileTab)))
        assertEquals("count_v2", idDe(RoleMatcher.achar(perfil, SignatureLibrary.profileFollowers)))
        // As duas abas casam ("413 following" tambem, para a recusa); a MARCADA decide (Listas).
        val abas = ig(bounds = Bounds(0, 0, 1080, 2400), children = listOf(
            ig(id = "tab_a", text = "388 followers", clickable = true, selected = true, bounds = Bounds(0, 120, 360, 200)),
            ig(id = "tab_b", text = "413 following", clickable = true, bounds = Bounds(360, 120, 720, 200)),
        ))
        assertEquals(listOf("tab_a", "tab_b"), RoleMatcher.todos(abas, SignatureLibrary.followTab).map(::idDe))
    }

    @Test fun seguidoresComOutroIdNaoSaoLidosPelaForma() {
        // Linha e @ de seguidor so pelo id (SO_POR_ID): pela forma, a aba "Sinalizadas" e as linhas de
        // "Sugestoes para voce" (estranhos) passavam por seguidor. Sem o id, ninguem e lido.
        val raiz = seguidoresOutroId(listOf("ana.souza" to "Ana Souza", "bia_lima" to "Bia Lima"))
        val aoVivo = SelectorProfile.IG_448.copy(assinaturaAoVivo = true)
        assertEquals(emptyList<String>(), Listas.seguidores(raiz, aoVivo).linhas.map { it.username })
    }

    // ---- rótulos multi-idioma ----

    private fun botao(id: String, texto: String) = ig(id = id, text = texto, cls = "android.widget.Button", clickable = true)

    @Test fun novaMensagemPtEnEs() {
        for (t in listOf("Nova mensagem", "New message", "Nuevo mensaje")) {
            val raiz = ig(children = listOf(botao("x", "Feed"), ig(desc = t, clickable = true)))
            assertNotNull("não achou '$t'", RoleMatcher.achar(raiz, SignatureLibrary.newMessage))
        }
    }

    @Test fun concluirEClearNaoSeConfundem() {
        // Segurança: "Concluir" e "Limpar tudo" na mesma tela têm de ser papéis distintos.
        val raiz = ig(children = listOf(
            ig(id = "cabecalho", text = "20 pessoas"),
            ig(id = "acao_limpar", text = "Limpar tudo", clickable = true),
            ig(id = "botao_ok", text = "Concluir", cls = "IgdsButton", clickable = true),
        ))
        assertEquals("botao_ok", idDe(RoleMatcher.achar(raiz, SignatureLibrary.cfDone)))
        assertEquals("acao_limpar", idDe(RoleMatcher.achar(raiz, SignatureLibrary.cfClear)))
    }

    @Test fun clearAllEmVariosIdiomas() {
        for (t in listOf("Limpar tudo", "Clear all", "Borrar todo")) {
            val raiz = ig(children = listOf(ig(id = "z", text = t, clickable = true)))
            assertNotNull("não achou '$t'", RoleMatcher.achar(raiz, SignatureLibrary.cfClear))
        }
    }

    // ---- seguidores com OUTRO id: calibra e opera ----

    private fun seguidoresOutroId(linhas: List<Pair<String, String>>) = ig(bounds = Bounds(0, 0, 1000, 2400), children = listOf(
        ig(id = "action_bar_title", text = "minhaconta", clickable = true, bounds = Bounds(0, 60, 1000, 140)),
        ig(id = "lista_custom", scrollable = true, bounds = Bounds(0, 200, 1000, 2300), children = linhas.map { (u, n) ->
            ig(id = "user_row_v2", clickable = true, children = listOf(
                ig(id = "uname_v2", text = u),
                ig(id = "subtitle_v2", text = n),
            ))
        }),
    ))

    @Test fun idsAprendidosDeSeguidoresAlimentamAOperacao() {
        val raiz = seguidoresOutroId(listOf("ana.souza" to "Ana Souza", "bia_lima" to "Bia Lima"))
        val p = PerfilAprendido("500.0", "pt", "DM", 0L, ids = mapOf("follow_row" to "user_row_v2", "follow_username" to "uname_v2"))

        // Com o id aprendido (de verdade, nao pela forma) por cima, o leitor de seguidores (Listas) lê as linhas.
        val aprendido = SelectorProfile.IG_448.comAprendido(p)
        val lidos = Listas.seguidores(raiz, aprendido).linhas.map { it.username }
        assertEquals(listOf("ana.souza", "bia_lima"), lidos)
    }
}
