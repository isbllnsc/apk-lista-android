package com.listalocal.core.selectors

import com.listalocal.core.tree.Bounds
import com.listalocal.core.tree.UiNode
import com.listalocal.core.tree.abasDeBaixo
import com.listalocal.core.tree.ig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verificacao do OBJETIVO da auto-calibracao: num alvo com OUTRA versao, OUTRO
 * idioma (ingles) e IDS DIFERENTES dos do IG_448, "Conferir o Instagram" deve
 * APRENDER os papeis pela assinatura e LIBERAR o modo; e deve BLOQUEAR (dizendo
 * qual papel faltou) quando um papel obrigatorio nao esta na tela.
 *
 * Reproduz o caminho do servico (InstagramAccessibilityService.conferir): os
 * mesmos [alvos] e as mesmas chaves obrigatorias por modo, alimentando o
 * Aprendiz com cada tela navegada (so leitura). Arvore simulada (FakeNode) com
 * bounds, como a acessibilidade traz na tela real (getBoundsInScreen).
 */
class AutoCalibracaoAlvoDiferenteTest {

    private val H = 2400

    /** Caixa de entrada em ingles, ids diferentes do IG_448. */
    private fun inboxEn(comConversas: Boolean = true) = ig(bounds = Bounds(0, 0, 1080, H), children = listOfNotNull(
        ig(id = "ab_title_v2", text = "@my.account", cls = "android.widget.Button", clickable = true, bounds = Bounds(40, 60, 600, 150)),
        ig(desc = "New message", cls = "android.widget.ImageView", clickable = true, bounds = Bounds(950, 60, 1040, 150)),
        ig(id = "thread_list_rv_v2", scrollable = true, bounds = Bounds(0, 200, 1080, 2200), children = if (comConversas) listOf(
            linha("Ana Souza", "Sent"), linha("Bia Lima", "2 new messages"),
        ) else emptyList()),
        abasDeBaixo("direct", direct = "Direct", perfil = "Profile", idDirect = "tab_inbox_v2", idPerfil = "tab_profile_v2"),
    ))

    private fun linha(nome: String, estado: String) = ig(clickable = true, bounds = Bounds(0, 300, 1080, 460), children = listOf(
        ig(desc = "$nome, $estado", bounds = Bounds(120, 320, 900, 440)),
        ig(text = nome, cls = "android.widget.TextView", bounds = Bounds(120, 320, 700, 380)),
    ))

    /** Tela "New message" em ingles, ids diferentes; [comPara] controla o rotulo "To:". */
    private fun novaMsgEn(comPara: Boolean = true) = ig(bounds = Bounds(0, 0, 1080, H), children = listOfNotNull(
        ig(id = "ab_title_new_v2", text = "New message", cls = "android.widget.TextView", bounds = Bounds(40, 60, 1040, 150)),
        if (comPara) ig(id = "new_chat_to_v2", text = "To:", cls = "android.widget.TextView", bounds = Bounds(40, 180, 200, 260)) else null,
        ig(id = "search_field_v2", text = "Search", cls = "android.widget.EditText", clickable = true, bounds = Bounds(210, 180, 1040, 260)),
        ig(id = "recipients_v2", scrollable = true, bounds = Bounds(0, 300, 1080, 2200)),
    ))

    private fun aprenderDm(vararg telas: UiNode): PerfilAprendido {
        val aprendiz = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.DM))
        telas.forEach { aprendiz.viu(it) }
        return aprendiz.perfil("999.0.0", "en", "DM", SelectorProfile.DM_KEYS, 0L)
    }

    @Test fun aprendeELiberaNoAlvoDiferente() {
        val p = aprenderDm(inboxEn(), novaMsgEn(comPara = true))
        // Todas as chaves obrigatorias do DM aprendidas -> LIBERA.
        assertTrue("faltou: ${p.faltando}", p.completo)
        // Aprendeu os ids DE VERDADE do aparelho, nao os do IG_448.
        assertEquals("ab_title_v2", p.ids["inbox_title"])
        assertEquals("tab_inbox_v2", p.ids["direct_tab"])
        assertEquals("new_chat_to_v2", p.ids["new_chat_to"])
        // Rotulos em ingles gravados (idioma do aparelho).
        assertTrue(p.descricoes["new_message"]?.any { it.contains("New message") } == true)

        // O perfil aprendido resolve a operacao pelos ids do aparelho.
        val prof = SelectorProfile.IG_448.comAprendido(p)
        assertEquals("ab_title_v2", prof.idFor("inbox_title"))
        assertEquals("new_chat_to_v2", prof.idFor("new_chat_to"))
    }

    @Test fun bloqueiaQuandoFaltaUmPapel() {
        // Mesmo alvo, mas a tela "New message" nao traz o rotulo "To:".
        val p = aprenderDm(inboxEn(), novaMsgEn(comPara = false))
        assertTrue(!p.completo)
        assertEquals(listOf("new_chat_to"), p.faltando)
        // Diz o que ACHOU, para o dono saber que so faltou um.
        assertTrue("direct_tab" in p.encontrados)
        assertTrue("inbox_title" in p.encontrados)
        assertTrue("new_message" in p.encontrados)
    }

    @Test fun soAInboxNaoBastaFaltaOParaDaNovaMensagem() {
        // Sem navegar para "New message": o "To:" nunca aparece -> bloqueia nele.
        val p = aprenderDm(inboxEn())
        assertEquals(listOf("new_chat_to"), p.faltando)
    }

    // ---- Amigos Proximos, mesmo alvo diferente ----

    /** Perfil proprio em ingles, ids diferentes. */
    private fun perfilEn() = ig(bounds = Bounds(0, 0, 1080, H), children = listOf(
        ig(id = "prof_title_v2", text = "@my.account", cls = "android.widget.TextView", bounds = Bounds(40, 60, 600, 150)),
        ig(id = "opt_btn_v2", desc = "Options", cls = "android.widget.ImageView", clickable = true, bounds = Bounds(950, 60, 1040, 150)),
        abasDeBaixo("perfil", direct = "Direct", perfil = "Profile", idDirect = "tab_inbox_v2", idPerfil = "tab_profile_v2"),
    ))

    /** "Settings and activity" com a entrada "Close friends" (ids diferentes). */
    private fun settingsEn() = ig(bounds = Bounds(0, 0, 1080, H), children = listOf(
        ig(id = "hdr_v2", text = "Settings and activity", cls = "android.widget.TextView", bounds = Bounds(40, 60, 1040, 150)),
        ig(id = "row_cf_v2", text = "Close friends", cls = "android.widget.TextView", clickable = true, bounds = Bounds(40, 800, 1040, 900)),
    ))

    /** Tela Amigos Proximos (picker) em ingles, ids diferentes; [comDone] controla o "Done". */
    private fun cfPickerEn(comDone: Boolean = true) = ig(bounds = Bounds(0, 0, 1080, H), children = listOfNotNull(
        ig(id = "cf_search_v2", text = "Search", cls = "android.widget.EditText", clickable = true, bounds = Bounds(40, 160, 1040, 260)),
        // Cabecalho com "Clear all" (o botao que NUNCA pode ser tocado): o modo so libera
        // depois de o app reconhece-lo neste aparelho/idioma (ver naoLimpar / CF_KEYS).
        ig(id = "clear_v2", text = "Clear all", cls = "android.widget.TextView", clickable = true, bounds = Bounds(800, 280, 1040, 360)),
        ig(id = "rv_v2", scrollable = true, bounds = Bounds(0, 400, 1080, 2200), children = listOf(
            ig(id = "row_v2", clickable = true, bounds = Bounds(0, 420, 1080, 560), children = listOf(
                ig(id = "uname_v2", text = "ana.souza", bounds = Bounds(120, 440, 700, 520)),
            )),
        )),
        if (comDone) ig(id = "done_v2", text = "Done", cls = "com.instagram.igds.components.button.IgdsButton", clickable = true, bounds = Bounds(40, 2300, 1040, H)) else null,
    ))

    private fun aprenderCf(vararg telas: UiNode): PerfilAprendido {
        val aprendiz = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.CF))
        telas.forEach { aprendiz.viu(it) }
        return aprendiz.perfil("999.0.0", "en", "AMIGOS_PROXIMOS", SelectorProfile.CF_KEYS, 0L)
    }

    @Test fun cfAprendeELiberaNoAlvoDiferente() {
        val p = aprenderCf(perfilEn(), settingsEn(), cfPickerEn(comDone = true))
        assertTrue("faltou: ${p.faltando}", p.completo)
        assertEquals("tab_profile_v2", p.ids["profile_tab"])
        assertEquals("row_cf_v2", p.ids["cf_entry"])
        assertEquals("cf_search_v2", p.ids["cf_search"])
        assertEquals("done_v2", p.ids["cf_done"])
        // cf_clear reconhecido pela assinatura (id diferente, "Clear all"): parte do gate.
        assertEquals("clear_v2", p.ids["cf_clear"])
    }

    @Test fun naoGravaDadoDeTerceiroNoPerfil() {
        // Seguranca/privacidade: a calibracao ve a lista real de Amigos Proximos, mas o
        // perfil no aparelho guarda so o ID dos papeis pessoais (cf_row/cf_username),
        // NUNCA o @ da pessoa. Nenhum rotulo gravado pode conter o username lido.
        val p = aprenderCf(perfilEn(), settingsEn(), cfPickerEn(comDone = true))
        // O id (nao e pessoal) pode ser guardado.
        assertEquals("uname_v2", p.ids["cf_username"])
        assertEquals("row_v2", p.ids["cf_row"])
        // O texto (o @ de terceiro) NAO entra em descricoes.
        assertNull(p.descricoes["cf_username"])
        assertNull(p.descricoes["cf_row"])
        assertTrue(
            "vazou @ de terceiro: ${p.descricoes}",
            p.descricoes.values.none { rotulos -> rotulos.any { it.contains("ana.souza") } },
        )
    }

    @Test fun cfBloqueiaSemOConcluir() {
        // O picker sem "Done": bloqueia dizendo cf_done.
        val p = aprenderCf(perfilEn(), settingsEn(), cfPickerEn(comDone = false))
        assertTrue(!p.completo)
        assertEquals(listOf("cf_done"), p.faltando)
        assertTrue("cf_search" in p.encontrados)
        assertTrue("cf_entry" in p.encontrados)
    }

    // ---- mesmo objetivo (aprende e libera) em ESPANHOL, ids diferentes ----

    private fun inboxEs() = ig(bounds = Bounds(0, 0, 1080, H), children = listOf(
        ig(id = "ab_title_v2", text = "@mi.cuenta", cls = "android.widget.Button", clickable = true, bounds = Bounds(40, 60, 600, 150)),
        ig(desc = "Nuevo mensaje", cls = "android.widget.ImageView", clickable = true, bounds = Bounds(950, 60, 1040, 150)),
        ig(id = "thread_list_rv_v2", scrollable = true, bounds = Bounds(0, 200, 1080, 2200)),
        abasDeBaixo("direct", direct = "Mensajes", perfil = "Perfil", idDirect = "tab_inbox_v2", idPerfil = "tab_profile_v2"),
    ))

    private fun novaMsgEs(comPara: Boolean = true) = ig(bounds = Bounds(0, 0, 1080, H), children = listOfNotNull(
        ig(id = "ab_title_new_v2", text = "Nuevo mensaje", cls = "android.widget.TextView", bounds = Bounds(40, 60, 1040, 150)),
        if (comPara) ig(id = "new_chat_to_v2", text = "Para:", cls = "android.widget.TextView", bounds = Bounds(40, 180, 200, 260)) else null,
        ig(id = "search_field_v2", text = "Buscar", cls = "android.widget.EditText", clickable = true, bounds = Bounds(210, 180, 1040, 260)),
        ig(id = "recipients_v2", scrollable = true, bounds = Bounds(0, 300, 1080, 2200)),
    ))

    private fun perfilEs() = ig(bounds = Bounds(0, 0, 1080, H), children = listOf(
        ig(id = "prof_title_v2", text = "@mi.cuenta", cls = "android.widget.TextView", bounds = Bounds(40, 60, 600, 150)),
        ig(id = "opt_btn_v2", desc = "Opciones", cls = "android.widget.ImageView", clickable = true, bounds = Bounds(950, 60, 1040, 150)),
        abasDeBaixo("perfil", direct = "Mensajes", perfil = "Perfil", idDirect = "tab_inbox_v2", idPerfil = "tab_profile_v2"),
    ))

    private fun settingsEs() = ig(bounds = Bounds(0, 0, 1080, H), children = listOf(
        ig(id = "hdr_v2", text = "Configuración y actividad", cls = "android.widget.TextView", bounds = Bounds(40, 60, 1040, 150)),
        ig(id = "row_cf_v2", text = "Amigos cercanos", cls = "android.widget.TextView", clickable = true, bounds = Bounds(40, 800, 1040, 900)),
    ))

    private fun cfPickerEs(comDone: Boolean = true) = ig(bounds = Bounds(0, 0, 1080, H), children = listOfNotNull(
        ig(id = "cf_search_v2", text = "Buscar", cls = "android.widget.EditText", clickable = true, bounds = Bounds(40, 160, 1040, 260)),
        ig(id = "clear_v2", text = "Borrar todo", cls = "android.widget.TextView", clickable = true, bounds = Bounds(800, 280, 1040, 360)),
        ig(id = "rv_v2", scrollable = true, bounds = Bounds(0, 400, 1080, 2200), children = listOf(
            ig(id = "row_v2", clickable = true, bounds = Bounds(0, 420, 1080, 560), children = listOf(
                ig(id = "uname_v2", text = "ana.souza", bounds = Bounds(120, 440, 700, 520)),
            )),
        )),
        if (comDone) ig(id = "done_v2", text = "Listo", cls = "com.instagram.igds.components.button.IgdsButton", clickable = true, bounds = Bounds(40, 2300, 1040, H)) else null,
    ))

    @Test fun aprendeELiberaEmEspanhol() {
        val aprendiz = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.DM))
        listOf(inboxEs(), novaMsgEs(comPara = true)).forEach { aprendiz.viu(it) }
        val p = aprendiz.perfil("999.0.0", "es", "DM", SelectorProfile.DM_KEYS, 0L)
        assertTrue("faltou: ${p.faltando}", p.completo)
        assertEquals("ab_title_v2", p.ids["inbox_title"])
        assertEquals("new_chat_to_v2", p.ids["new_chat_to"])
    }

    @Test fun cfAprendeELiberaEmEspanhol() {
        val aprendiz = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.CF))
        listOf(perfilEs(), settingsEs(), cfPickerEs(comDone = true)).forEach { aprendiz.viu(it) }
        val p = aprendiz.perfil("999.0.0", "es", "AMIGOS_PROXIMOS", SelectorProfile.CF_KEYS, 0L)
        assertTrue("faltou: ${p.faltando}", p.completo)
        assertEquals("row_cf_v2", p.ids["cf_entry"])
        assertEquals("done_v2", p.ids["cf_done"])
        assertEquals("clear_v2", p.ids["cf_clear"])
        // Privacidade tambem em es: nada de @ de terceiro nos rotulos gravados.
        assertNull(p.descricoes["cf_username"])
    }

    // ---- escopo do aprendiz: a linha do Feed nao rouba cf_row/cf_username (finding 1) ----

    /** Feed-like: lista rolavel de posts clicaveis, cada um com um @ nu (a forma de cf_row/cf_username). */
    private fun feedDecoy() = ig(bounds = Bounds(0, 0, 1080, H), children = listOf(
        ig(id = "feed_rv", scrollable = true, bounds = Bounds(0, 200, 1080, 2200), children = listOf(
            ig(id = "feed_post_1", clickable = true, bounds = Bounds(0, 220, 1080, 700), children = listOf(
                ig(id = "poster_handle", text = "ana.souza", bounds = Bounds(40, 240, 600, 300)),
            )),
        )),
        abasDeBaixo(direct = "Direct", perfil = "Profile", idDirect = "tab_inbox_v2", idPerfil = "tab_profile_v2"),
    ))

    @Test fun cfNaoAprendeLinhaDoFeedAntesDoSeletor() {
        val aprendiz = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.CF))
        // Telas antes do seletor (Feed): exclui os papeis de linha/@, como CloseFriendsFlow.abrir faz.
        aprendiz.viu(feedDecoy(), excluir = SignatureLibrary.ROTULO_PESSOAL)
        // O seletor de verdade: aprende tudo, inclusive cf_row/cf_username.
        aprendiz.viu(cfPickerEn(comDone = true))
        val p = aprendiz.perfil("999.0.0", "en", "AMIGOS_PROXIMOS", SelectorProfile.CF_KEYS, 0L)
        assertEquals("row_v2", p.ids["cf_row"])       // do seletor, nao "feed_post_1"
        assertEquals("uname_v2", p.ids["cf_username"]) // do seletor, nao "poster_handle"
    }

    @Test fun semEscopoOFeedRoubariaALinha() {
        // Prova do risco que o escopo evita: sem exclusao, o Feed (visto primeiro) fica com
        // cf_row no empate de pontos (o Aprendiz mantem o primeiro visto no empate).
        val aprendiz = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.CF))
        aprendiz.viu(feedDecoy())
        aprendiz.viu(cfPickerEn(comDone = true))
        val p = aprendiz.perfil("999.0.0", "en", "AMIGOS_PROXIMOS", SelectorProfile.CF_KEYS, 0L)
        assertEquals("feed_post_1", p.ids["cf_row"])
    }

    // ---- ids compartilhados entre modos (finding 5) ----

    @Test fun perfilDeUmModoHerdaIdsCompartilhadosDeOutro() {
        val dm = PerfilAprendido("999", "pt", "DM", 0L, ids = mapOf("direct_tab" to "d_v2"))
        val cf = PerfilAprendido("999", "pt", "AMIGOS_PROXIMOS", 0L, ids = mapOf("profile_tab" to "p_v2", "direct_tab" to "OUTRO"))
        val unido = dm.comIdsDe(listOf(cf))
        assertEquals("p_v2", unido.ids["profile_tab"]) // herdado do CF (profile_tab so calibra la)
        assertEquals("d_v2", unido.ids["direct_tab"])  // o proprio modo vence o do outro
    }
}
