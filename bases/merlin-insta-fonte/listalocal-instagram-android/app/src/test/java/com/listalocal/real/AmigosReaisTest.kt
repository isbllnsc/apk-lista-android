package com.listalocal.real

import com.listalocal.core.tree.ArvoreReal
import com.listalocal.core.tree.UiNode
import com.listalocal.real.Perfis.APARELHO
import com.listalocal.service.CloseFriendsFlow
import com.listalocal.service.CompatCheck
import com.listalocal.service.Desfecho
import com.listalocal.service.Modo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O modo Amigos Proximos (CloseFriendsFlow) sobre as telas REAIS da ModalActivity (t02a, t03b anonimizadas):
 * o que o app decide e onde toca, com a tela como ela chega de verdade.
 */
class AmigosReaisTest {

    private companion object {
        const val ATRASO = 300L
    }

    /**
     * t02a real: logo que Amigos Proximos abre, as caixas dos 9 membros do topo vem SEM a marca na acessibilidade,
     * embora marcadas na tela. A 1a leitura depois de escrever o @ ainda e essa lista (a resposta da busca vem do
     * servidor). O app lia "desmarcada", tocava e TIRAVA da lista um amigo proximo. So a busca filtrada e parada
     * (t03b: o membro sozinho, marcado) decide: ja esta, nenhum toque.
     */
    @Test fun `membro do topo da lista inicial nao recebe toque - so a busca filtrada decide`() = runBlocking {
        val t = TelaRoteiro("perfil_proprio", ATRASO, busca = mapOf("amigo01" to "v_amigos_busca_membro"))
        val f = CloseFriendsFlow(t, APARELHO)
        f.abrir()
        assertEquals("v_amigos_topo", t.assentar())
        assertEquals(Desfecho.JA_NA_LISTA, f.marcar("amigo01", adicionar = true))
        assertEquals(emptyList<String>(), t.inesperados)
    }

    private fun conferir(t: TelaRoteiro): Pair<CloseFriendsFlow, CompatCheck> {
        val f = CloseFriendsFlow(t, APARELHO)
        val achou = runBlocking { f.conferir() }
        return f to CompatCheck(Modo.AMIGOS_PROXIMOS, "448.0.0.52.84", conta = f.conta, found = achou)
    }

    /**
     * A moldura (aviso, busca, Concluir) chega antes das linhas e do cabecalho "162 pessoas / Limpar tudo", que vem
     * do servidor: a mesma classe do caso real 2 (a barra de abas depois das linhas, 14e3eb8). A conferencia julgava a
     * 1a arvore com a busca e dizia "faltou: linha, @, Limpar tudo" numa tela valida.
     */
    @Test fun `conferencia - linhas e Limpar tudo chegando depois da busca`() {
        val topo = Telas.raiz("v_amigos_topo")
        val carregando = ArvoreReal.semNos(ArvoreReal.semNos(topo, "row_user_container"), "frame_header")
        var leituras = 0
        val t = TelaRoteiro("perfil_proprio", ATRASO, ver = { if (it === topo && leituras++ < 3) carregando else it })
        val (f, check) = conferir(t)
        assertTrue(check.missing.toString(), check.ok)
        assertEquals("minhaconta", f.conta)
        assertEquals(emptyList<String>(), t.inesperados)
    }

    /**
     * Conta com a lista Amigos Proximos vazia (primeiro uso: montar a lista do zero). Nao ha secao de membros nem o
     * "Limpar tudo" dela; so "Sugestoes" (desmarcadas), a busca e o Concluir. Antes o modo nunca liberava ali.
     */
    @Test fun `conferencia - lista vazia (so Sugestoes, sem Limpar tudo) libera o modo sem tocar em nada`() {
        val topo = Telas.raiz("v_amigos_topo")
        val vazia = ArvoreReal.comRotuloNoId(ArvoreReal.semNos(topo, "row_header_action"), "row_header_textview", texto = "Sugestões")
        val t = TelaRoteiro("perfil_proprio", ATRASO, ver = { if (it === topo) vazia else it })
        val (_, check) = conferir(t)
        assertTrue(check.missing.toString(), check.ok)
        assertEquals(emptyList<String>(), t.inesperados)
    }

    /** Com membros na tela e o "Limpar tudo" nao reconhecido, o modo continua sem liberar. */
    @Test fun `conferencia - membros na tela sem Limpar tudo reconhecido nao libera`() {
        val topo = Telas.raiz("v_amigos_topo")
        val semLimpar = ArvoreReal.comRotuloNoId(ArvoreReal.semNos(topo, "row_header_action"), "row_header_textview", texto = "162 pessoas")
        val t = TelaRoteiro("perfil_proprio", ATRASO, ver = { if (it === topo) semLimpar else it })
        val (_, check) = conferir(t)
        assertEquals(listOf("cf_clear"), check.missing)
    }

    /**
     * Partindo das listas de Seguidores/Seguindo/Sinalizadas (onde "Seus seguidores" termina), sem barra de abas: a
     * "Foto do perfil" de um seguidor casava a aba Perfil e "Mais opcoes para <pessoa>" o menu Opcoes. Nenhum toque
     * em pessoa; a conta lida e a do dono.
     */
    @Test fun `abrir partindo das listas de pessoas nao toca em ninguem`() =
        emTodas(Telas.SEGUIDORES.take(2) + Telas.SEGUINDO + Telas.SINALIZADAS, "CloseFriendsFlow.abrir()") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            val f = CloseFriendsFlow(t, APARELHO)
            runCatching { runBlocking { f.abrir() } }.exceptionOrNull()?.let { return@emTodas "parou: ${it.message}" }
            listOfNotNull(
                "conta @${f.conta}".takeIf { f.conta != "minhaconta" },
                t.inesperados.takeIf { it.isNotEmpty() }?.let { "toques fora do roteiro: $it" },
            ).takeIf { it.isNotEmpty() }?.joinToString("; ")
        }

    /** Depois de cada arraste, as proximas leituras mostram a tela ainda deslizando ([deslocamentos], px). */
    private class Embalo(val t: TelaRoteiro, private val deslocamentos: List<Int>) : com.listalocal.service.Tela by t {
        private var faltam = mutableListOf<Int>()
        override fun arrastar(): Boolean = t.arrastar().also { faltam = deslocamentos.toMutableList() }
        override fun ler(): UiNode {
            val d = faltam.removeFirstOrNull() ?: 0
            return if (d == 0) t.ler() else ArvoreReal.deslocada(t.ler(), d)
        }
    }

    /**
     * O unico toque por pixel do app (o item Bloks "Amigos Proximos, 162", config_4 real [687-823]). "Parada" era so
     * os mesmos rotulos: com linhas de 136 px o embalo desliza a lista com os mesmos itens, e o toque ia para a
     * posicao velha (o vizinho). Agora rotulos E posicoes iguais em duas leituras.
     */
    @Test fun `toque no ponto de Amigos Proximos so com a lista parada (posicao, nao so rotulos)`() = runBlocking {
        val t = TelaRoteiro("perfil_proprio")
        CloseFriendsFlow(Embalo(t, listOf(-400, -350, -300, -250)), APARELHO).abrir()
        val ponto = t.log.single { it.startsWith("ponto") }
        assertTrue(ponto, ponto.endsWith("'Amigos Próximos, 162' [687-823]"))
        assertEquals(emptyList<String>(), t.inesperados)
    }

    /** A releitura logo antes do toque volta vazia (o Instagram saiu da frente): nenhum toque na posicao da leitura velha. */
    @Test fun `sem releitura valida antes do toque no ponto, nenhum toque`() {
        val t = TelaRoteiro("perfil_proprio")
        var lidas = 0
        val tela = object : com.listalocal.service.Tela by t {
            override fun arrastar() = t.arrastar().also { lidas = 0 }
            override fun ler(): UiNode? = if (t.atual == "config_4" && ++lidas > 2) null else t.ler()
        }
        assertThrows(CloseFriendsFlow.ParadaSegura::class.java) { runBlocking { CloseFriendsFlow(tela, APARELHO).abrir() } }
        assertEquals(t.log.toString(), 0, t.log.count { it.startsWith("ponto") })
    }

    /**
     * O dono pausou, abriu a Nova mensagem (a mesma busca search_edit_text; cada linha com caixa e um destinatario) e
     * tocou Continuar. marcar() escrevia o @ ali e tocava na linha. Agora so age na tela provada.
     */
    @Test fun `marcar na Nova mensagem (mesma busca) nao escreve nem toca`() = runBlocking {
        val t = TelaRoteiro("perfil_proprio", ATRASO)
        val f = CloseFriendsFlow(t, APARELHO)
        f.abrir()
        t.trocarPara("v_nova_mensagem")
        assertThrows(CloseFriendsFlow.ParadaSegura::class.java) { runBlocking { f.marcar("amigo01", adicionar = true) } }
        assertEquals(t.log.toString(), 0, t.log.count { it.startsWith("escrever") || it.contains("row_user") })
        assertEquals(emptyList<String>(), t.inesperados)
    }
}
