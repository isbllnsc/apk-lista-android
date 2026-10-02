package com.listalocal.real

import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.tree.UiNode
import com.listalocal.real.Perfis.APARELHO
import com.listalocal.service.CloseFriendsFlow
import com.listalocal.service.Conta
import com.listalocal.service.DmFlow
import com.listalocal.service.FakeRegistro
import com.listalocal.service.Fila
import com.listalocal.service.FonteConversas
import com.listalocal.service.FonteSeguidores
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O codigo REAL dos fluxos (DmFlow, FonteSeguidores, FonteConversas,
 * CloseFriendsFlow, Conta) andando pelas telas REAIS do Instagram do dono
 * ([TelaRoteiro]): cada toque leva a tela que o Instagram abre de verdade.
 *
 * Telas de partida = onde o Instagram costuma estar quando o dono toca em
 * Conferir/Iniciar: a caixa de entrada (a conferencia DM termina ali), o perfil,
 * a lista de seguidores (a operacao "Seus seguidores" termina ali) e
 * Configuracoes (a conferencia de Amigos Proximos passa por ali).
 *
 * [ATRASO]: a tela nova aparece 300 ms depois do toque (a troca de tela do
 * Instagram nao e instantanea; a leitura da arvore leva ~0,8 s na rodada 1).
 * Os testes "sem atraso" separam o que e corrida do que e leitura errada.
 */
class FluxosReaisTest {

    private companion object {
        const val ATRASO = 300L
        val PARTIDAS = listOf("caixa_principal", "perfil_proprio", "seguidores_topo", "seguidores_fim", "config_1", "caixa_pedidos")
    }

    private fun falhas(vararg itens: String?): String? = itens.filterNotNull().takeIf { it.isNotEmpty() }?.joinToString("; ")

    private fun inesperados(t: TelaRoteiro) = t.inesperados.takeIf { it.isNotEmpty() }?.let { "toques fora do roteiro: $it" }

    // ---------------- conta aberta (inicio de TODA operacao DM e a conferencia DM) ----------------

    private fun contaAberta(inicio: String, atraso: Long): String? {
        val t = TelaRoteiro(inicio, atraso)
        val c = runBlocking { DmFlow(t, APARELHO, FakeRegistro(mutableListOf())).contaAberta() }
        return falhas("leu a conta @$c".takeIf { c != "minhaconta" }, inesperados(t))
    }

    @Test fun `conta aberta - de qualquer tela real, sem atraso`() =
        emTodas(PARTIDAS, "DmFlow.contaAberta() deveria dar @minhaconta sem tocar em ninguem") { n, _ -> contaAberta(n, 0) }

    @Test fun `conta aberta - de qualquer tela real, com a troca de tela de 300 ms`() =
        emTodas(PARTIDAS, "DmFlow.contaAberta() deveria dar @minhaconta sem tocar em ninguem") { n, _ -> contaAberta(n, ATRASO) }

    @Test fun `conferencia DM a partir da caixa - acha tudo, le a conta e volta sem tocar em ninguem`() = runBlocking {
        val t = TelaRoteiro("caixa_principal", ATRASO)
        val (achou, conta) = DmFlow(t, APARELHO, FakeRegistro(mutableListOf())).conferir()
        assertEquals(mapOf("direct_tab" to true, "inbox_title" to true, "new_message" to true, "new_chat_to" to true), achou)
        assertEquals("minhaconta", conta)
        assertEquals(emptyList<String>(), t.inesperados)
        assertEquals("caixa_principal", t.assentar())
    }

    /**
     * Outra versao do Instagram (ids trocados): a conferencia DM so lia a conta pelo id conhecido, entao nunca via a
     * caixa, nunca aprendia o titulo dela e o modo DM ficava bloqueado para sempre; comecando no perfil, aprendia
     * "15posts" como a conta. Agora: a conta na caixa provada, pela forma do titulo, e o id dele aprendido; a
     * operacao seguinte le a conta so por esse id.
     */
    @Test fun `ids diferentes - conferencia DM de qualquer tela real le a conta na caixa e a operacao le pelo id aprendido`() =
        emTodas(PARTIDAS, "conferencia DM com ids trocados") { n, _ ->
            val t = TelaRoteiro(n, ATRASO, ver = com.listalocal.core.tree.ArvoreReal::comIdsTrocados)
            val a = com.listalocal.core.selectors.Aprendiz(
                com.listalocal.core.selectors.SignatureLibrary.doModo(com.listalocal.core.selectors.SignatureLibrary.DM),
            )
            val (achou, conta) = runBlocking { DmFlow(t, APARELHO, FakeRegistro(mutableListOf())).conferir(aprender = { r, ex -> a.viu(r, excluir = ex) }) }
            val p = a.perfil("448.0.0.52.84", "pt", "DM", SelectorProfile.DM_KEYS, 0)
            val cal = SelectorProfile.IG_448.comAprendido(p)
            val t2 = TelaRoteiro("perfil_proprio", ATRASO, ver = com.listalocal.core.tree.ArvoreReal::comIdsTrocados)
            val viva = runBlocking { DmFlow(t2, cal, FakeRegistro(mutableListOf())).contaAberta() }
            val t3 = TelaRoteiro("perfil_proprio", ATRASO, ver = com.listalocal.core.tree.ArvoreReal::comIdsTrocados)
            val caixa = runCatching { runBlocking { FonteConversas(t3, cal).abrir() } }.getOrElse { "parou: ${it.message}" }
            // "Seus seguidores" le a conta no perfil e na lista pelo titulo (profile_title): so com a conferencia DM, chega
            // a lista. As linhas sao so pelo id (SelectorProfile.SO_POR_ID): com ids trocados, a parada segura SEM_LINHAS
            // na lista aberta e a esperada; antes parava no perfil ("a lista de seguidores nao abriu").
            val t4 = TelaRoteiro("caixa_principal", ATRASO, ver = com.listalocal.core.tree.ArvoreReal::comIdsTrocados)
            val seguidores = runCatching { runBlocking { FonteSeguidores(t4, cal).abrir() } }
                .getOrElse { if (it.message == FonteSeguidores.SEM_LINHAS) "minhaconta" else "parou: ${it.message}" }
            falhas(
                "leu a conta @$conta".takeIf { conta != "minhaconta" },
                "inbox_title nao achado".takeIf { achou["inbox_title"] != true },
                "aprendeu inbox_title=#${p.ids["inbox_title"]}".takeIf { p.ids["inbox_title"] != "igds_action_bar_title_v2" },
                "aprendeu inbox_list=#${p.ids["inbox_list"]}".takeIf { p.ids["inbox_list"] != "inbox_refreshable_thread_list_recyclerview_v2" },
                "aprendeu profile_title=#${p.ids["profile_title"]}".takeIf { p.ids["profile_title"] != "action_bar_title_v2" },
                "a operacao leu @$viva".takeIf { viva != "minhaconta" },
                "Conversas do Direct: $caixa".takeIf { caixa != "minhaconta" },
                "Seus seguidores: $seguidores".takeIf { seguidores != "minhaconta" || t4.atual !in Telas.SEGUIDORES },
                inesperados(t), inesperados(t2), inesperados(t3), inesperados(t4),
            )
        }

    /** O mesmo em Amigos Proximos: a conta no titulo do perfil PROPRIO (aba Perfil marcada), pela forma, e o id dele. */
    @Test fun `ids diferentes - conferencia Amigos Proximos le a conta no perfil proprio e aprende o titulo dele`() =
        emTodas(PARTIDAS - "caixa_pedidos", "conferencia de Amigos Proximos com ids trocados") { n, _ ->
            val t = TelaRoteiro(n, ATRASO, ver = com.listalocal.core.tree.ArvoreReal::comIdsTrocados)
            val cf = com.listalocal.core.selectors.SignatureLibrary.CF
            val a = com.listalocal.core.selectors.Aprendiz(com.listalocal.core.selectors.SignatureLibrary.doModo(cf))
            val f = CloseFriendsFlow(t, APARELHO, aprender = { r, ex -> a.viu(r, excluir = ex) })
            val achou = runBlocking { f.conferir() }
            val p = a.perfil("448.0.0.52.84", "pt", "AMIGOS_PROXIMOS", cf, 0)
            falhas(
                "conta @${f.conta}".takeIf { f.conta != "minhaconta" },
                "profile_title nao achado".takeIf { achou["profile_title"] != true },
                "aprendeu profile_title=#${p.ids["profile_title"]}".takeIf { p.ids["profile_title"] != "action_bar_title_v2" },
                inesperados(t),
            )
        }

    /** Na operacao a conta nunca e lida pela forma: sem o id (fixo ou aprendido) na tela, nao ha conta. */
    @Test fun `ids diferentes - sem conferir, a operacao nao le a conta pela forma`() {
        val t = TelaRoteiro("caixa_principal", ATRASO, ver = com.listalocal.core.tree.ArvoreReal::comIdsTrocados)
        assertEquals(null, runBlocking { DmFlow(t, APARELHO, FakeRegistro(mutableListOf())).contaAberta() })
        val t2 = TelaRoteiro("perfil_proprio", ATRASO, ver = com.listalocal.core.tree.ArvoreReal::comIdsTrocados)
        val f = CloseFriendsFlow(t2, APARELHO)
        runCatching { runBlocking { f.abrir() } }
        assertEquals(null, f.conta)
    }

    // ---------------- Seus seguidores ----------------

    private fun abrirSeguidores(inicio: String, atraso: Long): String? {
        val t = TelaRoteiro(inicio, atraso)
        return try {
            val fonte = FonteSeguidores(t, APARELHO)
            val c = runBlocking { fonte.abrir() }
            // Se aceitou outra tela como a lista, o que a operacao leria dali como "seguidores".
            val leria = if (c != null && t.atual !in Telas.SEGUIDORES) {
                runCatching {
                    runBlocking { fonte.proximas { false } }?.let { l -> "e leria como seguidores: ${l.map { it.username }}" }
                        ?: "e daria FIM DA LISTA sem ninguem"
                }.getOrElse { "e pararia: ${it.message}" }
            } else null
            falhas(
                "abrir() devolveu null (a Fila para: 'A lista nao abriu no Instagram.')".takeIf { c == null },
                "leu a conta @$c".takeIf { c != null && c != "minhaconta" },
                "tratou '$inicio' como a lista e ficou em ${t.atual}".takeIf { t.atual !in Telas.SEGUIDORES },
                leria,
                inesperados(t),
            )
        } catch (e: Fila.FailedSafe) {
            "parou: ${e.message}"
        }
    }

    @Test fun `Seus seguidores - abre a lista de seguidores de qualquer tela real de partida`() =
        emTodas(PARTIDAS - "caixa_pedidos", "FonteSeguidores.abrir() deveria chegar a aba Seguidores") { n, _ -> abrirSeguidores(n, ATRASO) }

    @Test fun `Seus seguidores - na aba Seguindo ou Sinalizadas nunca aceita a lista`() =
        emTodas(Telas.LISTA_DE_SEGUIDORES_ABA_OUTRA, "abriu na aba errada") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            try {
                runBlocking { FonteSeguidores(t, APARELHO).abrir() }
                "aceitou a lista com a tela em ${t.atual}".takeIf { t.atual !in Telas.SEGUIDORES }
            } catch (_: Fila.FailedSafe) {
                null // parada segura: ok
            }
        }

    /** Os @ das linhas de seguidor de uma tela, pelo id medido (a verdade da tela). */
    private fun arrobas(r: UiNode) = r.findByViewIdSuffix("follow_list_container")
        .mapNotNull { it.findByViewIdSuffix("follow_list_username").firstOrNull()?.text?.lowercase() }

    /** Abre e le a lista ate o fim como a Fila faz (cada @ uma vez). */
    private fun lerTudo(t: TelaRoteiro, prof: SelectorProfile = APARELHO): List<String> = runBlocking {
        val fonte = FonteSeguidores(t, prof)
        assertEquals("minhaconta", fonte.abrir())
        val feitos = mutableListOf<String>()
        repeat(300) {
            val lote = fonte.proximas { it in feitos } ?: return@runBlocking feitos
            lote.mapNotNull { it.username }.filterNot { it in feitos }.forEach { feitos += it }
        }
        error("a lista nao terminou")
    }

    @Test fun `Seus seguidores - le a lista pela rolagem real, cada @ uma vez, sem Sugestoes, ate o fim`() {
        val seq = Telas.ROLAGEM + "seguidores_fim"
        val t = TelaRoteiro(seq.first(), rolagem = seq)
        val lidos = lerTudo(t)
        assertEquals(seq.flatMap { arrobas(Telas.raiz(it)) }.distinct(), lidos)
        assertEquals("seguidores_fim", t.atual)
        assertTrue(t.log.toString(), t.log.filter { it.startsWith("rolar") }.all { it.startsWith("rolar list ") })
    }

    /**
     * Com uma busca digitada, a lista mostra 1 a 3 linhas (e nao rola). Ler isso como a lista inteira dava
     * "Fim da lista" (operacao encerrada) depois de 1 pessoa. Acontece quando o dono pesquisa alguem na
     * pausa e toca em Continuar (o Instagram mantem a busca ao voltar a lista): parada segura.
     */
    @Test fun `Seus seguidores - busca digitada na lista nao termina a operacao como lista inteira`() =
        emTodas(listOf("seguidores_busca_1", "seguidores_busca_3"), "FonteSeguidores com a busca digitada") { n, _ ->
            val t = TelaRoteiro("seguidores_topo")
            val fonte = FonteSeguidores(t, APARELHO)
            assertEquals("minhaconta", runBlocking { fonte.abrir() })
            t.trocarPara(n)
            try {
                runBlocking { fonte.proximas { false } }?.let { "leu ${it.map { a -> a.username }}" } ?: "deu Fim da lista"
            } catch (_: Fila.FailedSafe) {
                null
            }
        }

    @Test fun `Seus seguidores - a aba virou Seguindo no meio da operacao, ninguem de Seguindo e lido`() = runBlocking {
        val t = TelaRoteiro("seguidores_topo")
        val fonte = FonteSeguidores(t, APARELHO)
        assertEquals("minhaconta", fonte.abrir())
        t.trocarPara("seguindo_topo") // o dono arrastou a tela, ou o Instagram voltou em outra aba
        val lidos = try { fonte.proximas { false }?.mapNotNull { it.username } } catch (_: Fila.FailedSafe) { null }
        assertTrue("leu da aba Seguindo como seguidores: $lidos", lidos.isNullOrEmpty())
    }

    /**
     * Em outro idioma a aba Seguindo e "413 seguidos" (es), "413 a seguir" (pt-PT), "413 Gefolgt" (de): a recusa
     * por rotulo nao reconhecia, e a lista era aceita. A lista so vale com a aba de SEGUIDORES marcada.
     */
    @Test fun `Seus seguidores - aba Seguindo em outro idioma nunca e lida como seguidores`() {
        val soSeguindo = arrobas(Telas.raiz("seguindo_topo")) - arrobas(Telas.raiz("seguidores_topo")).toSet()
        for (rotulo in listOf("413 seguidos", "413 a seguir", "413 Gefolgt")) {
            val t = TelaRoteiro("seguidores_topo", ver = { com.listalocal.core.tree.ArvoreReal.comRotulo(it, "413 seguindo", rotulo) })
            val fonte = FonteSeguidores(t, APARELHO)
            assertEquals("minhaconta", runBlocking { fonte.abrir() })
            t.trocarPara("seguindo_topo")
            val lidos = try { runBlocking { fonte.proximas { false } }?.mapNotNull { it.username } } catch (_: Fila.FailedSafe) { null }
            assertTrue("'$rotulo': leu da aba Seguindo ${lidos.orEmpty().filter { it in soSeguindo }}", lidos.orEmpty().none { it in soSeguindo })
        }
    }

    // ---------------- a conferencia grava o que aprendeu (e vale nas proximas operacoes) ----------------

    /**
     * InstagramAccessibilityService.conferir grava o perfil aprendido SEMPRE (mesmo com a
     * conferencia falhando), e o id aprendido passa na frente do fixo em nos(). Comecando
     * na lista de seguidores (onde "Seus seguidores" termina), nada errado pode ser aprendido.
     */
    @Test fun `conferencia DM comecando na lista de seguidores nao aprende a aba Direct errada`() =
        emTodas(listOf("seguidores_topo", "seguidores_fim"), "Aprendiz na conferencia DM") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            val a = com.listalocal.core.selectors.Aprendiz(
                com.listalocal.core.selectors.SignatureLibrary.doModo(com.listalocal.core.selectors.SignatureLibrary.DM),
            )
            runBlocking { DmFlow(t, APARELHO, FakeRegistro(mutableListOf())).conferir(aprender = { r, ex -> a.viu(r, excluir = ex) }) }
            val p = a.perfil("448.0.0.52.84", "pt", "DM", com.listalocal.core.selectors.SignatureLibrary.DM, 0)
            falhas(
                p.ids["direct_tab"]?.takeIf { it != "direct_tab" }?.let { "aprendeu direct_tab=#$it" },
                p.ids["inbox_title"]?.takeIf { it != "igds_action_bar_title" }?.let { "aprendeu inbox_title=#$it" },
                p.ids["profile_title"]?.takeIf { it != "action_bar_title" }?.let { "aprendeu profile_title=#$it" },
            )
        }

    @Test fun `conferencia Amigos Proximos comecando na lista de seguidores nao aprende a aba Perfil errada`() =
        emTodas(listOf("seguidores_topo", "seguidores_fim"), "Aprendiz na conferencia de Amigos Proximos") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            val cf = com.listalocal.core.selectors.SignatureLibrary.CF
            val a = com.listalocal.core.selectors.Aprendiz(com.listalocal.core.selectors.SignatureLibrary.doModo(cf))
            runBlocking { CloseFriendsFlow(t, APARELHO, aprender = { r, ex -> a.viu(r, excluir = ex) }).conferir() }
            val p = a.perfil("448.0.0.52.84", "pt", "AMIGOS_PROXIMOS", cf, 0)
            falhas(
                p.ids["profile_tab"]?.takeIf { it != "profile_tab" }?.let { "aprendeu profile_tab=#$it" },
                p.ids["profile_title"]?.takeIf { it != "action_bar_title" }?.let { "aprendeu profile_title=#$it" },
            )
        }

    /**
     * Nota de amigo no topo da caixa comecando por "Para" e a Nova mensagem ilegivel (a Modal fora da lista de
     * janelas: a leitura continua sendo a caixa). A assinatura do "Para:" casava com a nota: new_chat_to=true e o
     * modo DM liberava num aparelho que nao le a janela das conversas, e o id aprendido era o da nota.
     */
    @Test fun `conferencia DM - nota Para no topo da caixa nunca vale como a Nova mensagem`() = runBlocking {
        val caixa = Telas.raiz("caixa_principal")
        val nova = Telas.raiz("v_nova_mensagem")
        val comNota = com.listalocal.core.tree.ArvoreReal.comRotulo(caixa, "Primeira nota da semana…", "Para quem vai no jogo?")
        val t = TelaRoteiro("caixa_principal", ATRASO, ver = { if (it === caixa || it === nova) comNota else it })
        val a = com.listalocal.core.selectors.Aprendiz(
            com.listalocal.core.selectors.SignatureLibrary.doModo(com.listalocal.core.selectors.SignatureLibrary.DM),
        )
        val (achou, _) = DmFlow(t, APARELHO, FakeRegistro(mutableListOf())).conferir(aprender = { r, ex -> a.viu(r, excluir = ex) })
        assertEquals(false, achou["new_chat_to"])
        assertTrue("new_chat_to" !in a.encontrados)
        // A caixa ficou na frente: nenhum Voltar (sairia dela).
        assertEquals(t.log.toString(), 0, t.log.count { it == "voltar" })
        // O modo nao liga e nada do que o aprendiz viu nessa conferencia fica gravado no aparelho.
        val (check, gravar) = com.listalocal.service.CompatCheck.fechar(
            com.listalocal.service.Modo.DM, "448.0.0.52.84", achou, "minhaconta",
            a.perfil("448.0.0.52.84", "pt", "DM", SelectorProfile.DM_KEYS, 0),
        )
        assertEquals(false, check.ok)
        assertEquals(null, gravar)
    }

    /**
     * O aprendiz ve TODAS as telas do caminho; o que ele acha la nao liga o modo (antes: found[k] = true por cima do
     * que o fluxo mediu). E o perfil so e gravado de uma conferencia que passou.
     */
    @Test fun `conferencia - so o que o fluxo mediu liga o modo, e so a que passou grava o perfil`() {
        val tudo = SelectorProfile.DM_KEYS.associateWith { true }
        val aprendeuTudo = com.listalocal.core.selectors.PerfilAprendido("1", "pt", "DM", 0, encontrados = SelectorProfile.DM_KEYS)
        val (falhou, naoGrava) = com.listalocal.service.CompatCheck.fechar(
            com.listalocal.service.Modo.DM, "1", tudo + ("new_chat_to" to false), "minhaconta", aprendeuTudo,
        )
        assertEquals(listOf("new_chat_to"), falhou.missing)
        assertTrue(falhou.failure.orEmpty().startsWith("faltou na tela"))
        assertEquals(null, naoGrava)
        val (passou, grava) = com.listalocal.service.CompatCheck.fechar(com.listalocal.service.Modo.DM, "1", tudo, "@MinhaConta", aprendeuTudo)
        assertTrue(passou.ok)
        assertEquals("minhaconta", passou.conta)
        assertEquals(aprendeuTudo, grava)
    }

    /**
     * Um direct_tab aprendido errado (o botao "Enviar mensagem para <seguidor>" da lista de Seguidores, onde "Seus
     * seguidores" termina) fica gravado no aparelho. Comecando ali, a operacao tocava nele: a conversa de um terceiro.
     */
    @Test fun `aba Direct aprendida errada nunca e tocada, nem na operacao nem na conferencia`() = emTodas(listOf("seguidores_topo"), "direct_tab aprendido errado") { n, _ ->
        val errado = SelectorProfile.IG_448.comAprendido(
            com.listalocal.core.selectors.PerfilAprendido("448.0.0.52.84", "pt", "DM", 0, ids = mapOf("direct_tab" to "follow_list_row_large_follow_button")),
        )
        val t = TelaRoteiro(n, ATRASO)
        val c = runBlocking { FonteConversas(t, errado).abrir() }
        val t2 = TelaRoteiro(n, ATRASO)
        val (achou, _) = runBlocking { DmFlow(t2, errado, FakeRegistro(mutableListOf())).conferir() }
        falhas("leu @$c".takeIf { c != "minhaconta" }, inesperados(t), inesperados(t2), "conferencia sem a aba".takeIf { achou["direct_tab"] != true })
    }

    // ---------------- Suas conversas ----------------

    @Test fun `Suas conversas - abre a caixa de qualquer tela real e recusa Pedidos`() {
        emTodas(PARTIDAS - "caixa_pedidos", "FonteConversas.abrir() deveria chegar a caixa") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            try {
                val c = runBlocking { FonteConversas(t, APARELHO).abrir() }
                falhas("leu @$c".takeIf { c != "minhaconta" }, "ficou em ${t.atual}".takeIf { t.atual !in Telas.CAIXA }, inesperados(t))
            } catch (e: Fila.FailedSafe) {
                "parou: ${e.message}"
            }
        }
        val t = TelaRoteiro("caixa_pedidos")
        val parou = try { runBlocking { FonteConversas(t, APARELHO).abrir() }; false } catch (_: Fila.FailedSafe) { true }
        assertTrue("Pedidos marcado deveria parar", parou)
    }

    /** Caixa deixada rolada pelo dono (caixa_rolada real): as conversas de cima ficavam de fora sem aviso. */
    @Test fun `Suas conversas - caixa rolada volta ao topo antes de ler`() = emTodas(listOf("caixa_rolada", "caixa_rolada_2"), "abrir()") { n, _ ->
        val t = TelaRoteiro(n, ATRASO)
        val c = runBlocking { FonteConversas(t, APARELHO).abrir() }
        falhas("leu @$c".takeIf { c != "minhaconta" }, "ficou em ${t.assentar()}".takeIf { t.atual != "caixa_principal" }, inesperados(t))
    }

    @Test fun `Suas conversas - filtro ligado na caixa para antes de ler`() {
        val t = TelaRoteiro("caixa_principal", ver = { com.listalocal.core.tree.ArvoreReal.comRotulo(it, "Filtros", "Não lidos") })
        val e = try { runBlocking { FonteConversas(t, APARELHO).abrir() }; null } catch (e: Fila.FailedSafe) { e }
        assertEquals(FonteConversas.FILTRO_LIGADO, e?.message)
    }

    // ---------------- Amigos Proximos e troca de conta ----------------

    @Test fun `Amigos Proximos - conferencia de qualquer tela real acha tudo sem tocar em pessoa, Limpar tudo ou Concluir`() =
        emTodas(PARTIDAS - "caixa_pedidos", "CloseFriendsFlow.conferir()") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            val f = CloseFriendsFlow(t, APARELHO)
            val achou = runBlocking { f.conferir() }
            falhas(
                "faltou ${achou.filterValues { !it }.keys}".takeIf { achou.values.any { !it } || achou.size < 9 },
                "conta @${f.conta}".takeIf { f.conta != "minhaconta" },
                "tocou em linha/Limpar/Concluir".takeIf {
                    t.log.any { l -> listOf("row_user_container", "row_header_action", "done_button").any { it in l } }
                },
                inesperados(t),
            )
        }

    @Test fun `Trocar de conta - o toque longo e so na aba Perfil`() =
        emTodas(PARTIDAS, "Conta.abrirSeletor()") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            runBlocking { Conta.abrirSeletor(t, APARELHO) }
            falhas(inesperados(t), "sem toque longo".takeIf { t.log.none { it.startsWith("longo profile_tab") } })
        }

    /**
     * "Trocar de conta" depois de o dono ja ter trocado de conta pelo proprio Instagram: a conta "de antes" era a da
     * ultima conferencia, e a conta ja aberta (o titulo atras do seletor) parecia a escolhida na hora; o seletor
     * fechava sozinho e o dono nao escolhia nada. A de antes e a lida na tela da aba (a caixa ou o perfil proprio,
     * provados); sem escolha no prazo, nada muda. "outraconta" = a da ultima conferencia, o que o servico usa so
     * quando a tela da aba nao mostra a conta.
     */
    @Test fun `Trocar de conta - a conta de antes e a aberta agora, nao a da ultima conferencia`() =
        emTodas(PARTIDAS + Telas.MODAIS, "Conta.abrirSeletor() + esperarOutra() sem o dono escolher") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            val s = runBlocking { Conta.abrirSeletor(t, APARELHO) }
            val trocou = runBlocking { Conta.esperarOutra(t, APARELHO, s?.antes ?: "outraconta", ms = 3_000) }
            falhas(
                "o seletor nao abriu".takeIf { s == null },
                "conta de antes @${s?.antes}".takeIf { s != null && s.antes != "minhaconta" },
                "deu a troca para @$trocou sem o dono escolher".takeIf { trocou != null },
                inesperados(t),
            )
        }

    /**
     * "Conferir o Instagram" nao abre conversa (tocaria numa pessoa): num Instagram com os ids da conversa trocados
     * o modo DM liberava e, na operacao, a conversa abria mas nao era reconhecida pelo id, e cada pessoa ficava
     * "Conta nao encontrada". Agora e FALHA (retomada tenta de novo; tres seguidas param). Com @ que nao existe, o
     * Instagram fica na tela de antes: continua NAO_ENCONTRADO.
     */
    @Test fun `ids diferentes - conversa aberta que o app nao reconhece nao vira Conta nao encontrada`() = runBlocking {
        fun tela(abre: Boolean) = object : com.listalocal.service.Tela {
            var aberta = false
            var t = 0L
            override fun ler(): UiNode = if (aberta) com.listalocal.core.tree.ArvoreReal.comIdsTrocados(Telas.raiz("v_conversa")) else Telas.raiz("caixa_principal")
            override fun tocar(no: UiNode, mesmoPausado: Boolean) = false
            override fun escrever(no: UiNode, texto: String) = false
            override fun rolar(no: UiNode) = false
            override fun agora() = t
            override fun abrirConversa(username: String): Boolean { aberta = abre; return true }
            override fun abrirInstagram() = true
            override fun voltar() = true
            override suspend fun esperar(ms: Long) { t += ms }
        }
        val pessoa = com.listalocal.core.followers.Follower("pessoa01", "Pessoa 01")
        val abriu = DmFlow(tela(abre = true), APARELHO, FakeRegistro(mutableListOf())).abrirEProvar(pessoa)
        assertEquals(DmFlow.NAO_RECONHECIDA, abriu.erro)
        assertEquals(false, abriu.naoEncontrado)
        val naoExiste = DmFlow(tela(abre = false), APARELHO, FakeRegistro(mutableListOf())).abrirEProvar(pessoa)
        assertEquals(true, naoExiste.naoEncontrado)
    }

    // ---------------- partindo de uma tela da ModalActivity (sem barra de abas) ----------------

    /**
     * O Instagram fica numa tela da ModalActivity quando o dono responde Nao ao Concluir (Amigos Proximos, com as
     * marcas pendentes), deixa uma conversa aberta (o app manda conferir se a mensagem chegou) ou a Nova mensagem.
     * Ali nao ha barra de abas: a "aba Direct/Perfil" pela forma era a linha de um amigo (marca/desmarca), o
     * Concluir (grava a lista recusada), o campo "Mensagem..." ou uma sugestao da Nova mensagem. Nenhum toque em
     * nada disso: o app volta ate a barra de abas.
     */
    private fun tocouEmPessoaOuBotao(t: TelaRoteiro) = t.log.filter { l ->
        listOf(
            "row_user_container", "row_header_action", "done_button", "row_thread_composer_edittext", "message_header", "user_row",
            "direct_share_sheet_grid_view_pog", "recipient_toggle", "direct_send", "direct_private_share", "row_feed",
        ).any { it in l } ||
            (l.startsWith("tocar") && listOf("direct_tab", "profile_tab", "Nova mensagem", "Opções", "Amigos Próximos").none { it in l })
    }.takeIf { it.isNotEmpty() }?.let { "tocou em $it" }

    @Test fun `modais - a conta aberta e lida sem tocar em nada da tela modal`() =
        emTodas(Telas.MODAIS, "DmFlow.contaAberta() partindo de uma tela modal") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            val c = runBlocking { DmFlow(t, APARELHO, FakeRegistro(mutableListOf())).contaAberta() }
            falhas("leu a conta @$c".takeIf { c != "minhaconta" }, tocouEmPessoaOuBotao(t), inesperados(t))
        }

    @Test fun `modais - conferencia DM acha tudo sem tocar em nada da tela modal`() =
        emTodas(Telas.MODAIS, "DmFlow.conferir() partindo de uma tela modal") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            val (achou, c) = runBlocking { DmFlow(t, APARELHO, FakeRegistro(mutableListOf())).conferir() }
            falhas(
                "faltou ${achou.filterValues { !it }.keys}".takeIf { achou.values.any { !it } || achou.size < 4 },
                "leu a conta @$c".takeIf { c != "minhaconta" }, tocouEmPessoaOuBotao(t), inesperados(t),
            )
        }

    @Test fun `modais - conferencia de Amigos Proximos sem tocar em pessoa, Limpar tudo ou Concluir`() =
        emTodas(Telas.MODAIS, "CloseFriendsFlow.conferir() partindo de uma tela modal") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            val f = CloseFriendsFlow(t, APARELHO)
            val achou = runBlocking { f.conferir() }
            falhas(
                "faltou ${achou.filterValues { !it }.keys}".takeIf { achou.values.any { !it } },
                "conta @${f.conta}".takeIf { f.conta != "minhaconta" }, tocouEmPessoaOuBotao(t), inesperados(t),
            )
        }

    @Test fun `modais - Trocar de conta so da toque longo na aba Perfil`() =
        emTodas(Telas.MODAIS, "Conta.abrirSeletor() partindo de uma tela modal") { n, _ ->
            val t = TelaRoteiro(n, ATRASO)
            runBlocking { Conta.abrirSeletor(t, APARELHO) }
            falhas(inesperados(t), tocouEmPessoaOuBotao(t), "sem toque longo".takeIf { t.log.none { it.startsWith("longo profile_tab") } })
        }
}
