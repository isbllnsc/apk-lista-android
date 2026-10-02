package com.listalocal.real

import com.listalocal.core.ig.Evidence
import com.listalocal.core.selectors.Aprendiz
import com.listalocal.core.selectors.SignatureLibrary
import com.listalocal.core.tree.ArvoreReal
import com.listalocal.real.Perfis.APARELHO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolucao por papel (id aprendido -> id fixo -> assinatura ao vivo) e
 * auto-calibracao (RoleMatcher/Aprendiz) sobre as telas REAIS.
 *
 * Tres perguntas por papel:
 *  1. na tela onde o papel ESTA, o app acha o no medido (com o id do dono e com ids trocados);
 *  2. na tela onde o papel NAO esta, o app nao acha nada (senao toca/decide no no errado:
 *     no aparelho do dono o perfil da operacao liga a assinatura ao vivo, e ela entra
 *     justamente quando o id nao esta na tela);
 *  3. a conferencia aprende os ids certos e nenhum dado de terceiro.
 */
class PapeisReaisTest {

    @Test fun `o corpus real carrega inteiro e cada tela e da fonte certa`() {
        for (n in Telas.TODAS) {
            val t = ArvoreReal.tela(n)
            assertEquals(n, if (n.startsWith("v_")) "views" else "uiautomator", t.fonte)
            assertTrue(n, t.raiz.walk().count() > 20)
        }
    }

    /** papel -> (id medido na rodada 1, telas onde ele esta). */
    private val presentes = listOf(
        Triple("direct_tab", "direct_tab", Telas.PERFIL + Telas.CAIXA + Telas.PEDIDOS),
        Triple("profile_tab", "profile_tab", Telas.PERFIL + Telas.CAIXA + Telas.PEDIDOS),
        Triple("inbox_title", "igds_action_bar_title", Telas.CAIXA + Telas.PEDIDOS),
        Triple("profile_title", "action_bar_title", Telas.PERFIL + Telas.SEGUIDORES + Telas.SEGUINDO),
        Triple("profile_followers", "profile_header_followers_stacked_familiar", Telas.PERFIL_COM_NUMEROS),
        Triple("inbox_list", "inbox_refreshable_thread_list_recyclerview", Telas.CAIXA + Telas.PEDIDOS),
        Triple("follow_row", "follow_list_container", Telas.SEGUIDORES),
        Triple("follow_username", "follow_list_username", Telas.SEGUIDORES),
        Triple("follow_end", "recommended_user_row_content_identifier", listOf("seguidores_fim") + Telas.SO_SUGESTOES),
    )

    @Test fun `aparelho do dono - cada papel presente resolve no no medido`() {
        for ((key, id, telas) in presentes) emTodas(telas, "nos($key) deveria ser #$id") { _, r ->
            val no = APARELHO.nos(r, key).firstOrNull()
            if (no?.id() == id) null else "achou ${no?.descr()}"
        }
    }

    /**
     * Os papeis de CONTA (inbox_title, profile_title) nunca vao por assinatura (25034dd): com ids
     * diferentes eles dependem do id aprendido na conferencia (testes abaixo). follow_end e julgado
     * pelo resultado (fim da lista) em SeguidoresRealTest.
     */
    @Test fun `ids diferentes - a assinatura acha o mesmo no de cada papel presente`() {
        val porAssinatura = presentes.filter { (key) ->
            key !in com.listalocal.core.selectors.SelectorProfile.CONTA_KEYS && key != "follow_end" &&
                key !in com.listalocal.core.selectors.SelectorProfile.SO_POR_ID
        }
        val erros = porAssinatura.mapNotNull { (key, id, telas) ->
            val ruins = telas.mapNotNull { n ->
                val no = APARELHO.nos(ArvoreReal.comIdsTrocados(Telas.raiz(n)), key).firstOrNull()
                if (no?.id() == id + "_v2") null else "$n -> ${no?.descr()}"
            }
            ruins.takeIf { it.isNotEmpty() }?.let { "$key (#$id): ${it.size}/${telas.size} telas erradas, ex.: ${it.take(3)}" }
        }
        assertTrue("assinatura errou o papel em tela real:\n" + erros.joinToString("\n"), erros.isEmpty())
    }

    /**
     * O id do numero de seguidores e de um desenho em teste ("..._familiar"): noutro desenho ou versao, a
     * assinatura acha "N seguidores". Com 10 mil ou mais o Instagram abrevia ("12,3 mil"), e a regex exigia a
     * palavra logo depois do numero: a lista nao abria numa situacao valida.
     */
    @Test fun `ids diferentes - numero de seguidores abreviado (10 mil ou mais) leva a lista`() {
        for (rotulo in listOf("12,3 milseguidores", "12,3 mil seguidores", "1,2 mi seguidores", "12.3Kfollowers", "1.2M followers", "1.234 seguidores")) {
            emTodas(Telas.PERFIL_COM_NUMEROS, "profile_followers '$rotulo' com ids trocados") { _, r ->
                val t = ArvoreReal.comIdsTrocados(ArvoreReal.comRotulo(r, "388seguidores", rotulo))
                val no = APARELHO.nos(t, "profile_followers").firstOrNull()
                if (no?.id() == "profile_header_followers_stacked_familiar_v2") null else "achou ${no?.descr()}"
            }
        }
    }

    /** Linha e @ de seguidor nunca pela forma (SO_POR_ID): com ids trocados, nada (antes: a aba "Sinalizadas"). */
    @Test fun `ids diferentes - linha e @ de seguidor nao sao resolvidos pela forma`() =
        emTodas(Telas.SEGUIDORES + Telas.SO_SUGESTOES, "follow_row/follow_username com ids trocados") { n, _ ->
            val t = ArvoreReal.comIdsTrocados(Telas.raiz(n))
            listOf("follow_row", "follow_username").mapNotNull { k -> APARELHO.nos(t, k).firstOrNull()?.let { "$k=${it.descr()}" } }
                .takeIf { it.isNotEmpty() }?.joinToString()
        }

    /**
     * direct_tab e profile_tab sao procurados na tela que estiver na frente (DmFlow.conferir,
     * que roda em TODA conferencia e no inicio de TODA operacao DM; CloseFriendsFlow.abrir;
     * Conta.abrirSeletor, com toque longo). Nas telas sem a barra de abas nada pode casar.
     */
    @Test fun `abas Direct e Perfil - nas telas sem barra de abas nada e tomado por elas`() =
        emTodas(Telas.SEM_BARRA_DE_ABAS, "direct_tab/profile_tab nao existem nesta tela") { _, r -> abasAchadas(r, APARELHO) }

    /** O que nos() e porRotulo() dao para as abas numa tela sem elas; null = nada (certo). */
    private fun abasAchadas(r: com.listalocal.core.tree.UiNode, prof: com.listalocal.core.selectors.SelectorProfile): String? =
        listOf("direct_tab", "profile_tab").mapNotNull { key ->
            val no = prof.nos(r, key).firstOrNull() ?: Evidence.porRotulo(r, prof, key)
            no?.let { "$key=${it.descr()}" }
        }.takeIf { it.isNotEmpty() }?.joinToString()

    /** FonteSeguidores.abrir, logo depois do toque na aba Perfil, espera "N seguidores" na tela que estiver. */
    @Test fun `numero de seguidores - so no perfil`() =
        emTodas(Telas.CAIXA + Telas.PEDIDOS + Telas.CONFIG, "profile_followers nao existe nesta tela") { _, r ->
            (APARELHO.nos(r, "profile_followers").firstOrNull() ?: Evidence.porRotulo(r, APARELHO, "profile_followers"))
                ?.let { "achou ${it.descr()}" }
        }

    /** CloseFriendsFlow.abrir: depois de tocar em Perfil, "Opcoes"; depois de Opcoes, "Configuracoes e atividade". */
    @Test fun `menu Opcoes e Configuracoes - so nas telas deles`() {
        emTodas(Telas.CAIXA + Telas.PEDIDOS + Telas.SEGUIDORES + Telas.SEGUINDO + Telas.CONFIG, "options nao existe nesta tela") { _, r ->
            Evidence.porRotulo(r, APARELHO, "options")?.let { "achou ${it.descr()}" }
        }
        emTodas(Telas.PERFIL + Telas.CAIXA + Telas.SEGUIDORES, "settings/cf_entry nao existem nesta tela") { _, r ->
            listOf("settings", "cf_entry").mapNotNull { k -> Evidence.porRotulo(r, APARELHO, k)?.let { "$k=${it.descr()}" } }
                .takeIf { it.isNotEmpty() }?.joinToString()
        }
        emTodas(Telas.PERFIL, "Opcoes no perfil proprio") { _, r ->
            val no = Evidence.porRotulo(r, APARELHO, "options")
            if (no?.contentDescription == "Opções" && no.isClickable) null else "achou ${no?.descr()}"
        }
        emTodas(listOf("config_4"), "Amigos Proximos em Configuracoes") { _, r ->
            val no = Evidence.porRotulo(r, APARELHO, "cf_entry")
            if (no?.rotulo().orEmpty().startsWith("Amigos Próximos")) null else "achou ${no?.descr()}"
        }
    }

    // ---------------- conferencia (Aprendiz) nas telas reais ----------------

    @Test fun `conferencia DM aprende os ids medidos e nada de terceiros`() {
        val a = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.DM))
        // O caminho do DmFlow.conferir: a tela com a aba Direct, a caixa, Nova mensagem.
        listOf("perfil_proprio", "caixa_principal", "v_nova_mensagem").forEach { a.viu(Telas.raiz(it)) }
        val p = a.perfil("448.0.0.52.84", "pt", "DM", SignatureLibrary.DM, 0)
        assertEquals(emptyList<String>(), p.faltando)
        assertEquals("direct_tab", p.ids["direct_tab"])
        assertEquals("igds_action_bar_title", p.ids["inbox_title"])
        assertEquals("inbox_refreshable_thread_list_recyclerview", p.ids["inbox_list"])
        assertEquals("direct_new_chat_to_field", p.ids["new_chat_to"])
        assertEquals(listOf("Nova mensagem"), p.descricoes["new_message"])
        val pessoais = p.descricoes.filterValues { v -> v.any { it.contains("pessoa", ignoreCase = true) } }
        assertTrue("rotulo de terceiro no perfil aprendido: $pessoais", pessoais.isEmpty())
    }

    @Test fun `conferencia Amigos Proximos aprende os ids medidos e nada de terceiros`() {
        val a = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.CF))
        val semLinha = SignatureLibrary.ROTULO_PESSOAL
        (listOf("perfil_proprio") + Telas.CONFIG).forEach { a.viu(Telas.raiz(it), excluir = semLinha) }
        a.viu(Telas.raiz("v_amigos_topo"))
        val p = a.perfil("448.0.0.52.84", "pt", "AMIGOS_PROXIMOS", SignatureLibrary.CF, 0)
        assertEquals(emptyList<String>(), p.faltando)
        assertEquals("profile_tab", p.ids["profile_tab"])
        assertEquals("action_bar_title", p.ids["profile_title"])
        assertEquals("search_edit_text", p.ids["cf_search"])
        assertEquals("done_button", p.ids["cf_done"])
        assertEquals("row_header_action", p.ids["cf_clear"])
        assertEquals("row_user_container", p.ids["cf_row"])
        assertEquals("row_user_username", p.ids["cf_username"])
        assertEquals(listOf("Opções"), p.descricoes["options"])
        assertTrue(p.descricoes["cf_entry"].toString(), p.descricoes["cf_entry"].orEmpty().all { it.startsWith("Amigos Próximos") })
        val pessoais = p.descricoes.filterValues { v -> v.any { it.contains("amigo", ignoreCase = true) && !it.startsWith("Amigos Próximos") } }
        assertTrue("rotulo de terceiro no perfil aprendido: $pessoais", pessoais.isEmpty())
    }

    /**
     * A conferencia DM de verdade (DmFlow.conferir, o caminho e as exclusoes dele) num Instagram de ids diferentes (as
     * mesmas telas, ids trocados), partindo da caixa: onde a conferencia DM termina e o dono costuma deixar o Instagram.
     * Alimentar o aprendiz com o perfil proprio sem as exclusoes escondia que, da caixa, o titulo aprendido era o dela.
     */
    private fun calibradoDmComIdsTrocados(): com.listalocal.core.selectors.SelectorProfile {
        val a = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.DM))
        val t = TelaRoteiro("caixa_principal", 300, ver = ArvoreReal::comIdsTrocados)
        kotlinx.coroutines.runBlocking {
            com.listalocal.service.DmFlow(t, APARELHO, com.listalocal.service.FakeRegistro(mutableListOf()))
                .conferir(aprender = { r, ex -> a.viu(r, excluir = ex) })
        }
        assertEquals(emptyList<String>(), t.inesperados)
        val p = a.perfil("448.0.0.52.84", "pt", "DM", SignatureLibrary.DM, 0)
        return com.listalocal.core.selectors.SelectorProfile.IG_448.comAprendido(p)
    }

    @Test fun `ids diferentes - depois da conferencia DM a conta da caixa e lida pelo id aprendido`() {
        val cal = calibradoDmComIdsTrocados()
        emTodas(Telas.CAIXA + Telas.PEDIDOS, "inbox_title calibrado (aprendeu #${cal.aprendidos["inbox_title"]})") { _, r ->
            soMinhaConta(Evidence.conta(ArvoreReal.comIdsTrocados(r), cal))
        }
    }

    /** Modo DM + "Seus seguidores" le a conta no titulo da lista (profile_title): a conferencia DM o aprende no perfil proprio. */
    @Test fun `ids diferentes - so com a conferencia DM, Seus seguidores le a conta no titulo da lista`() {
        val cal = calibradoDmComIdsTrocados()
        emTodas(Telas.SEGUIDORES, "profile_title calibrado so pelo modo DM (aprendeu #${cal.aprendidos["profile_title"]})") { _, r ->
            soMinhaConta(Evidence.conta(ArvoreReal.comIdsTrocados(r), cal, "profile_title"))
        }
    }

    /**
     * Um id aprendido errado gravado no aparelho passava na frente do id fixo em nos(): a lista de notas como
     * a lista de conversas, a busca da lista de seguidores como a de Amigos Proximos. O fixo na tela vence.
     */
    @Test fun `perfil aprendido - id aprendido errado nunca vence o id fixo que esta na tela`() {
        val errado = com.listalocal.core.selectors.SelectorProfile.IG_448.comAprendido(
            com.listalocal.core.selectors.PerfilAprendido(
                "448.0.0.52.84", "pt", "DM", 0,
                ids = mapOf("inbox_list" to "cf_hub_recycler_view", "follow_list" to "unified_follow_list_view_pager"),
            ),
        )
        emTodas(Telas.CAIXA + Telas.PEDIDOS, "inbox_list com o aprendido errado") { _, r ->
            errado.nos(r, "inbox_list").firstOrNull()?.takeIf { it.id() != "inbox_refreshable_thread_list_recyclerview" }?.let { "achou ${it.descr()}" }
        }
        emTodas(Telas.SEGUIDORES, "follow_list com o aprendido errado") { _, r ->
            errado.nos(r, "follow_list").firstOrNull()?.takeIf { it.id() != "list" }?.let { "achou ${it.descr()}" }
        }
    }

    /**
     * O perfil aprendido fica no aparelho: nenhum texto de pessoa. A nota de um amigo "Para quem vai..." (casa o
     * "Para:"), "Mais opcoes para <pessoa>" (casa Opcoes por pedaco) e "Enviar mensagem para <Nome>" iam para la.
     */
    @Test fun `perfil aprendido - nenhum rotulo de pessoa e gravado, de qualquer tela real`() {
        val a = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.DM + SignatureLibrary.CF))
        val caixa = ArvoreReal.comRotulo(Telas.raiz("caixa_principal"), "Primeira nota da semana…", "Para quem vai no jogo?")
        (listOf(caixa) + Telas.TODAS.map(Telas::raiz)).forEach { a.viu(it) }
        val p = a.perfil("448.0.0.52.84", "pt", "DM", SignatureLibrary.DM, 0)
        val pessoais = p.descricoes.filterValues { v -> v.any { l -> listOf("pessoa", "amigo0", "jogo", "minhaconta").any { l.contains(it, ignoreCase = true) } } }
        assertTrue("rotulo de terceiro no perfil aprendido: $pessoais", pessoais.isEmpty())
        assertTrue(p.descricoes.keys.toString(), p.descricoes.keys.none { it in SignatureLibrary.SEM_ROTULO })
    }

    /**
     * Depois da conferencia, os rotulos aprendidos entram em porRotulo (texto EXATO).
     * "Mensagem" (aba Direct) e "Perfil" nao podem casar com botoes de pessoas.
     */
    @Test fun `depois de calibrar - rotulos aprendidos das abas nao casam com linhas de pessoas`() {
        val a = Aprendiz(SignatureLibrary.doModo(SignatureLibrary.DM + SignatureLibrary.CF))
        listOf("perfil_proprio", "caixa_principal").forEach { a.viu(Telas.raiz(it), excluir = SignatureLibrary.ROTULO_PESSOAL) }
        val calibrado = com.listalocal.core.selectors.SelectorProfile.IG_448.comAprendido(
            a.perfil("448.0.0.52.84", "pt", "DM", SignatureLibrary.DM, 0),
        )
        emTodas(Telas.SEM_BARRA_DE_ABAS, "calibrado: direct_tab/profile_tab nao existem nesta tela") { _, r -> abasAchadas(r, calibrado) }
    }
}
