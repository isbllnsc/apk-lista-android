package com.listalocal.real

import com.listalocal.core.ig.Listas
import com.listalocal.core.tree.ArvoreReal
import com.listalocal.core.tree.UiNode
import com.listalocal.real.Perfis.APARELHO
import com.listalocal.service.Fila
import com.listalocal.service.FonteSeguidores
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Perfil > Seguidores sobre as telas REAIS (t10a, rolagem _f0-_f12, fim _fs, so
 * sugestoes _fx, busca t10b/t10e, aba Seguindo t13a, aba Sinalizadas t10c).
 * Historico: "a lista aberta nao e a aba Seguidores" numa lista VALIDA (0.2.1).
 *
 * A verdade de cada tela vem dela mesma: o @ de uma linha de seguidor e o
 * `follow_list_username` dentro de `follow_list_container` (INSTAGRAM-APP-REAL.md 2);
 * as linhas de "Sugestoes para voce" (recommended_*) nunca sao seguidores.
 */
class SeguidoresRealTest {

    /** Os @ das linhas de seguidor na tela, na ordem, lidos direto pelo id medido (a verdade da tela). */
    private fun arrobasNaTela(r: UiNode): List<String> =
        r.findByViewIdSuffix("follow_list_container")
            .mapNotNull { it.findByViewIdSuffix("follow_list_username").firstOrNull()?.text?.lowercase() }
            .distinct()

    @Test fun `a tela e a lista de seguidores com a aba Seguidores marcada e linhas de seguidor ou o fim`() {
        emTodas(Telas.SEGUIDORES + Telas.SO_SUGESTOES, "naListaDeSeguidores deveria ser true") { n, r ->
            "false".takeIf { !Listas.naListaDeSeguidores(r, APARELHO) }
        }
    }

    @Test fun `nenhuma outra tela real e tomada pela lista de seguidores`() {
        // naLista/proximas/contaAgora confiam nisto. Seguindo tem as mesmas linhas: nao e a lista de seguidores.
        emTodas(Telas.PERFIL + Telas.CAIXA + Telas.PEDIDOS + Telas.CONFIG + Telas.SINALIZADAS + Telas.SEGUINDO, "naListaDeSeguidores deveria ser false") { _, r ->
            if (!Listas.naListaDeSeguidores(r, APARELHO)) null
            else "true (follow_row=${APARELHO.nos(r, "follow_row").take(2).map { it.descr() }}, " +
                "follow_end=${APARELHO.nos(r, "follow_end").take(2).map { it.descr() }})"
        }
    }

    @Test fun `aba Seguidores marcada e reconhecida em toda tela da lista de seguidores`() =
        emTodas(Telas.SEGUIDORES + Telas.SO_SUGESTOES, "abaSeguidores deveria ser true") { _, r ->
            "false".takeIf { !Listas.abaSeguidores(r, APARELHO) }
        }

    @Test fun `aba Seguindo marcada e recusada, e so ela`() {
        emTodas(Telas.SEGUINDO, "abaSeguindoSelecionada deveria ser true") { _, r ->
            "false".takeIf { !Listas.abaSeguindoSelecionada(r, APARELHO) }
        }
        emTodas(Telas.SEGUIDORES + Telas.SO_SUGESTOES + Telas.SINALIZADAS, "abaSeguindoSelecionada deveria ser false") { _, r ->
            "true".takeIf { Listas.abaSeguindoSelecionada(r, APARELHO) }
        }
        emTodas(Telas.SEGUINDO + Telas.SINALIZADAS, "abaSeguidores deveria ser false") { _, r ->
            "true".takeIf { Listas.abaSeguidores(r, APARELHO) }
        }
    }

    @Test fun `cada tela da lista le exatamente os @ das linhas de seguidor, sem as Sugestoes`() =
        emTodas(Telas.SEGUIDORES + Telas.SO_SUGESTOES, "Listas.seguidores") { _, r ->
            val lidos = Listas.seguidores(r, APARELHO).linhas.map { it.username }
            val certos = arrobasNaTela(r)
            if (lidos == certos) null else "leu $lidos, a tela tem $certos"
        }

    @Test fun `o nome de cada linha e o follow_list_subtitle`() {
        val r = Telas.raiz("seguidores_topo")
        val certos = r.findByViewIdSuffix("follow_list_container").mapNotNull { c ->
            val u = c.findByViewIdSuffix("follow_list_username").firstOrNull()?.text ?: return@mapNotNull null
            u.lowercase() to (c.findByViewIdSuffix("follow_list_subtitle").firstOrNull()?.text ?: u)
        }
        assertEquals(certos, Listas.seguidores(r, APARELHO).linhas.map { it.username to it.name })
    }

    @Test fun `fim da lista so onde aparecem as Sugestoes para voce`() {
        emTodas(listOf("seguidores_fim") + Telas.SO_SUGESTOES, "fim deveria ser true") { _, r ->
            "false".takeIf { !Listas.seguidores(r, APARELHO).fim }
        }
        emTodas(Telas.SEGUIDORES - "seguidores_fim", "fim deveria ser false") { _, r ->
            "true".takeIf { Listas.seguidores(r, APARELHO).fim }
        }
    }

    @Test fun `rola o ListView das linhas (android id list), nunca o ViewPager das abas`() =
        emTodas(Telas.SEGUIDORES - "seguidores_busca_1" - "seguidores_busca_3", "rolagemSeguidores") { _, r ->
            val no = Listas.rolagemSeguidores(r, APARELHO)
            if (no?.viewIdResourceName == "android:id/list" && no.isScrollable) null else "rola ${no?.descr()}"
        }

    @Test fun `a lista de outra aba nao entrega ninguem como seguidor`() =
        // Seguindo tem as mesmas linhas; Sinalizadas nao tem linha. Ler pessoas ali = enviar para quem nao segue.
        emTodas(Telas.SINALIZADAS, "Listas.seguidores deveria ser vazio") { _, r ->
            Listas.seguidores(r, APARELHO).linhas.map { it.username }.takeIf { it.isNotEmpty() }?.let { "leu $it" }
        }

    // ---------------- outro aparelho / outra versao (ids diferentes: so a assinatura) ----------------

    private fun trocada(nome: String) = ArvoreReal.comIdsTrocados(Telas.raiz(nome))

    /**
     * Linha e @ so pelo id de verdade (SelectorProfile.SO_POR_ID). Pela forma, TODA tela da lista dava
     * [sinalizadas] (o rotulo da aba) e as linhas de "Sugestoes para voce" (estranhos) tem a mesma forma
     * das de seguidor: sem o id, ninguem e lido e a tela nao passa por lista de seguidores.
     */
    @Test fun `ids diferentes - sem o id das linhas ninguem e lido e a tela nao passa por lista`() =
        emTodas(Telas.SEGUIDORES + Telas.SO_SUGESTOES, "Listas com ids trocados") { n, _ ->
            val t = trocada(n)
            val lidos = Listas.seguidores(t, APARELHO).linhas.map { it.username }
            listOfNotNull(
                "leu $lidos".takeIf { lidos.isNotEmpty() },
                "naListaDeSeguidores=true".takeIf { Listas.naListaDeSeguidores(t, APARELHO) },
            ).takeIf { it.isNotEmpty() }?.joinToString()
        }

    @Test fun `ids diferentes - fim da lista so no fim`() =
        emTodas(Telas.SEGUIDORES - "seguidores_fim", "fim com ids trocados deveria ser false") { n, _ ->
            val t = trocada(n)
            "true (follow_end=${APARELHO.nos(t, "follow_end").take(2).map { it.descr() }})".takeIf { Listas.seguidores(t, APARELHO).fim }
        }

    /** Com ids diferentes, "Seus seguidores" para com aviso: nunca le pela forma nem declara "Fim da lista". */
    @Test fun `ids diferentes - a operacao para em vez de ler pela forma ou dar Fim da lista`() =
        emTodas(listOf("seguidores_topo", "seguidores_so_sugestoes"), "FonteSeguidores com ids trocados") { n, _ ->
            val t = TelaRoteiro(n, ver = ArvoreReal::comIdsTrocados)
            val r = try {
                runBlocking { FonteSeguidores(t, APARELHO).proximas { false } }
                    ?.let { "leu ${it.map { a -> a.username }}" } ?: "deu Fim da lista"
            } catch (_: Fila.FailedSafe) {
                null
            }
            listOfNotNull(r, t.inesperados.takeIf { it.isNotEmpty() }?.let { "toques: $it" }).takeIf { it.isNotEmpty() }?.joinToString()
        }

    @Test fun `ids diferentes - aba Seguindo marcada continua recusada`() =
        emTodas(Telas.SEGUINDO, "abaSeguindoSelecionada com ids trocados deveria ser true") { n, _ ->
            "false".takeIf { !Listas.abaSeguindoSelecionada(trocada(n), APARELHO) }
        }

    @Test fun `ids diferentes - aba Seguidores marcada reconhecida`() =
        emTodas(Telas.SEGUIDORES, "abaSeguidores com ids trocados deveria ser true") { n, _ ->
            "false".takeIf { !Listas.abaSeguidores(trocada(n), APARELHO) }
        }
}
