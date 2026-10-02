package com.listalocal.core.selectors

import com.listalocal.core.tree.Bounds
import com.listalocal.core.tree.UiNode
import com.listalocal.core.tree.ig
import com.listalocal.core.tree.inboxFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordem de resolução por papel (SelectorProfile.nos) e a rede de segurança: o
 * perfil FIXO nunca chuta por assinatura; o perfil APRENDIDO resolve id
 * aprendido -> id fixo -> assinatura ao vivo. E o aprendiz diz o que faltou.
 */
class ResolucaoPorPapelTest {

    private fun idDe(n: UiNode?) = n?.viewIdResourceName?.substringAfter(":id/")

    /** Campo no rodapé com um id qualquer (não o do perfil fixo). */
    private fun campoComId(id: String) = ig(bounds = Bounds(0, 0, 1000, 2400), children = listOf(
        ig(id = "message_composer_bar", bounds = Bounds(0, 2100, 1000, 2260), children = listOf(
            ig(id = id, text = "Mensagem...", cls = "android.widget.EditText", clickable = true, bounds = Bounds(0, 2150, 1000, 2250)),
        )),
    ))

    @Test fun perfilFixoNaoChutaSemIdConhecido() {
        // IG_448 (assinaturaAoVivo = false): id do campo diferente -> não resolve (comportamento de sempre).
        val nos = SelectorProfile.IG_448.nos(campoComId("chat_input_v2"), "composer")
        assertTrue(nos.isEmpty())
    }

    @Test fun perfilDoAparelhoLigaAssinaturaMesmoSemAprender() {
        // Bootstrap (finding 2): o perfil que a operacao/conferencia usa (paraAparelho) SEMPRE liga
        // a assinatura ao vivo, mesmo sem nada aprendido, para a 1a conferencia navegar por forma num
        // aparelho de ids diferentes. O IG_448 CRU segue "id conhecido ou nada" (teste acima).
        val doAparelho = SelectorProfile.IG_448.paraAparelho(null)
        assertEquals("chat_input_v2", idDe(doAparelho.nos(campoComId("chat_input_v2"), "composer").firstOrNull()))
    }

    @Test fun perfilComAssinaturaResolvePelaForma() {
        val prof = SelectorProfile.IG_448.copy(assinaturaAoVivo = true)
        assertEquals("chat_input_v2", idDe(prof.nos(campoComId("chat_input_v2"), "composer").firstOrNull()))
    }

    @Test fun idAprendidoVenceAssinatura() {
        val prof = SelectorProfile.IG_448.copy(aprendidos = mapOf("composer" to "chat_input_v2"), assinaturaAoVivo = true)
        // A árvore tem o id aprendido: usa-o direto, sem correr assinatura.
        assertEquals("chat_input_v2", idDe(prof.nos(campoComId("chat_input_v2"), "composer").firstOrNull()))
    }

    @Test fun idFixoVemAntesDaAssinatura() {
        val prof = SelectorProfile.IG_448.copy(assinaturaAoVivo = true)
        // A árvore tem o id fixo: resolve por ele (não cai na assinatura).
        val nos = prof.nos(campoComId("row_thread_composer_edittext"), "composer")
        assertEquals(1, nos.size)
        assertEquals("row_thread_composer_edittext", idDe(nos.first()))
    }

    @Test fun aprendizDizOQueFaltou() {
        val aprendiz = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.DM))
        aprendiz.viu(inboxFixture("minhaconta"))
        val p = aprendiz.perfil("500.0", "pt", "DM", listOf("direct_tab", "inbox_title", "new_message", "new_chat_to"), 0L)
        // A caixa de entrada não tem a tela "Nova mensagem": só o "Para:" fica faltando.
        assertEquals(listOf("new_chat_to"), p.faltando)
        assertTrue("direct_tab" in p.encontrados)
        assertTrue("inbox_title" in p.encontrados)
        assertTrue("new_message" in p.encontrados)
        assertEquals("igds_action_bar_title", p.ids["inbox_title"])
    }
}
