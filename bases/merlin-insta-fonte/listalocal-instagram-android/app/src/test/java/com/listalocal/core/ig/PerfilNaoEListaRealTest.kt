package com.listalocal.core.ig

import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.ig
import com.listalocal.core.tree.seguidoresFixture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste real 26/09 (16:40 e 17:18): o perfil proprio tem um carrossel de sugestoes com botoes "Seguir".
 * O "fim da lista" casava com "Seguir", o perfil parecia a lista de Seguidores ja no fim e o app
 * terminava com "Fim da lista" sem enviar (ou parava com "nao e a aba Seguidores").
 */
class PerfilNaoEListaRealTest {
    private val comAssinatura = SelectorProfile.IG_448.copy(assinaturaAoVivo = true)
    // Neste aparelho o id fixo do fim da lista nao existe: o fim nunca vem da forma (SO_POR_ID).
    private val semIdDoFim = comAssinatura.copy(ids = comAssinatura.ids - "follow_end")

    private val perfilComSugestoes = ig(children = listOf(
        ig(id = "action_bar_title", text = "frutacarecafc"),
        ig(id = "profile_header_followers_stacked_familiar", text = "388seguidores", clickable = true),
        ig(text = "Descubra pessoas"),
        ig(scrollable = true, children = listOf(
            ig(text = "pessoa01", children = listOf(ig(text = "Seguir", clickable = true, cls = "android.widget.Button"))),
            ig(text = "pessoa02", children = listOf(ig(text = "Seguir", clickable = true, cls = "android.widget.Button"))),
        )),
    ))

    @Test fun `perfil com sugestoes nao e a lista de seguidores`() {
        assertFalse(Listas.naListaDeSeguidores(perfilComSugestoes, semIdDoFim))
    }

    @Test fun `botoes Seguir nao sao o fim da lista`() {
        assertFalse(Listas.seguidores(perfilComSugestoes, semIdDoFim).fim)
    }

    @Test fun `a lista de verdade continua reconhecida`() {
        assertTrue(Listas.naListaDeSeguidores(seguidoresFixture("frutacarecafc", listOf("pessoa01" to "P1")), semIdDoFim))
    }
}
