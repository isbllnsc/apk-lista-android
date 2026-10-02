package com.listalocal.core.ig

import com.listalocal.core.followers.Follower
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.inboxFixture
import com.listalocal.core.tree.seguidoresFixture
import com.listalocal.core.tree.threadFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** As listas do Instagram lidas uma tela por vez, sobre arvores no formato medido na rodada 1. */
class ListasTest {

    private val prof = SelectorProfile.IG_448

    @Test fun `seguidores - o arroba e o nome de cada linha visivel, na ordem`() {
        val t = Listas.seguidores(seguidoresFixture("minhaconta", listOf("ana.souza" to "Ana", "Bia_L" to "")), prof)
        assertEquals(listOf(Follower("ana.souza", "Ana"), Follower("bia_l", "bia_l")), t.linhas)
        assertFalse(t.fim)
    }

    @Test fun `seguidores - Sugestoes para voce e o fim e nao sao seguidores`() {
        val t = Listas.seguidores(seguidoresFixture("minhaconta", listOf("ana" to "Ana"), sugestoes = 2), prof)
        assertEquals(listOf("ana"), t.linhas.map { it.username })
        assertTrue(t.fim)
        assertTrue(Listas.naListaDeSeguidores(seguidoresFixture("minhaconta", emptyList(), sugestoes = 1), prof))
    }

    @Test fun `seguidores - a pagina seguindo fora da tela nao entra`() {
        val raiz = seguidoresFixture("minhaconta", listOf("ana" to "Ana"), seguindoAoLado = listOf("estranho"))
        assertEquals(listOf("ana"), Listas.seguidores(raiz, prof).linhas.map { it.username })
    }

    @Test fun `seguidores - so a aba de seguidores marcada serve`() {
        assertTrue(Listas.abaSeguidores(seguidoresFixture("c", emptyList()), prof))
        assertFalse(Listas.abaSeguidores(seguidoresFixture("c", emptyList(), aba = "seguindo"), prof))
        assertFalse(Listas.abaSeguidores(seguidoresFixture("c", emptyList(), aba = "nenhuma"), prof))
    }

    @Test fun `seguidores - recusa so quando a aba Seguindo esta marcada`() {
        // Seguindo marcada: recusar (mesmas linhas, seria a lista errada).
        assertTrue(Listas.abaSeguindoSelecionada(seguidoresFixture("c", emptyList(), aba = "seguindo"), prof))
        // Seguidores marcada ou selecao nao legivel: NAO e "Seguindo".
        assertFalse(Listas.abaSeguindoSelecionada(seguidoresFixture("c", emptyList()), prof))
        assertFalse(Listas.abaSeguindoSelecionada(seguidoresFixture("c", emptyList(), aba = "nenhuma"), prof))
    }

    @Test fun `seguidores - rola o ListView das linhas, nunca o ViewPager`() {
        val alvo = Listas.rolagemSeguidores(seguidoresFixture("c", listOf("ana" to "Ana")), prof)
        assertEquals("android:id/list", alvo?.viewIdResourceName)
    }

    @Test fun `caixa - so as conversas, sem busca, notas e filtros, com o nome da linha`() {
        val linhas = Listas.conversas(inboxFixture("minhaconta"), prof)
        assertEquals(listOf("Ana Souza", "Usuário do Instagram", "Bia Lima"), linhas.map { it.nome })
        // O no a tocar e a linha, nao o avatar (que abre story).
        assertTrue(linhas.all { l -> l.no.children.first().contentDescription!!.startsWith(l.nome + ",") })
    }

    @Test fun `caixa - filtro Pedidos marcado e reconhecido`() {
        assertFalse(Listas.emPedidos(inboxFixture("c"), prof))
        assertTrue(Listas.emPedidos(inboxFixture("c", pedidos = true), prof))
    }

    @Test fun `cabecalho - o arroba inteiro do subtitulo, ou do titulo na conversa comercial`() {
        assertEquals(Evidence.Cabecalho("ana.souza", false), Evidence.arrobaDoCabecalho(threadFixture("ana.souza"), prof))
        assertEquals(
            Evidence.Cabecalho("loja.x", true),
            Evidence.arrobaDoCabecalho(threadFixture("Conversa comercial", nome = "loja.x"), prof),
        )
        assertEquals(
            "ana.souza",
            Evidence.arrobaDoCabecalho(threadFixture("ana.souza", nome = "Ana", subtituloNaDescricao = true), prof).username,
        )
    }

    @Test fun `cabecalho - estado, lista de grupo ou nada nao sao arroba`() {
        listOf("Online agora", "Visto", "Online", "Digitando...", "Ana, Bia e mais 2", "ana, bia").forEach { s ->
            assertNull(s, Evidence.arrobaDoCabecalho(threadFixture(s), prof).username)
        }
        assertNull(Evidence.arrobaDoCabecalho(threadFixture(null), prof).username)
        assertNull(Evidence.arrobaDoCabecalho(inboxFixture("minhaconta"), prof).username)
    }

    @Test fun `nome do cabecalho truncado confere com o comeco do nome`() {
        assertTrue(Evidence.mesmoNome("Ana Souza", "ana souza"))
        assertTrue(Evidence.mesmoNome("Ana Souza Li...", "Ana Souza Lima"))
        assertFalse(Evidence.mesmoNome("Ana...", "Ana Souza")) // truncado curto demais
        assertFalse(Evidence.mesmoNome("Família", "Ana Souza"))
        assertFalse(Evidence.mesmoNome(null, "Ana"))
    }

    @Test fun `conversa nova com a bandeja de figurinhas e reconhecida`() {
        assertTrue(Evidence.conversaNova(threadFixture("ana", novaConversa = true), prof))
        assertFalse(Evidence.conversaNova(threadFixture("ana", mensagens = listOf("oi")), prof))
    }
}
