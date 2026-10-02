package com.listalocal.service

import com.listalocal.core.followers.Batch
import com.listalocal.core.followers.Follower
import com.listalocal.core.selectors.SelectorProfile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A execucao sobre um Instagram falso, com os destinatarios lidos AO VIVO:
 * a lista de Seguidores (uma tela por vez, rolando) e a caixa de entrada do
 * Direct. Sem extracao e sem arquivo. O que importa: cada pessoa uma vez so,
 * o fim da lista, a lista que muda no meio, o intervalo do dono, a pausa no
 * aviso de restricao e a parada depois de tres pessoas sem conversa.
 */
class FilaTest {

    private val ig = FakeInstagram()
    private val prof = SelectorProfile.IG_448
    private val registro = FakeRegistro(ig.log)
    private val flow = DmFlow(ig, prof, registro)
    private val perguntas = mutableListOf<String>()
    private val fila = Fila(ig) { perguntas += it; ig.log += "conferir"; true }
    private val pontos = mutableListOf<String>()
    private var intervalos = 0

    init {
        // Um intervalo = uma entrada na fase WAITING_INTERVAL.
        var dentro = false
        ig.aoEsperar = {
            val agora = st.phase == RunPhase.WAITING_INTERVAL
            if (agora && !dentro) intervalos++
            dentro = agora
        }
    }

    private val st get() = AutomationController.state.value

    private fun campanha(origem: Origem, velocidade: Velocidade) = Campanha(
        conta = "minhaconta", origem = origem, mensagens = listOf("Oi! Sábado tem evento."),
        velocidade = velocidade, inicio = 0,
    )

    private fun rodar(
        origem: Origem = Origem.SEGUIDORES,
        filtro: Fila.Filtro = Fila.Filtro("minhaconta"),
        anteriores: List<PersonResult> = emptyList(),
        velocidade: Velocidade = Velocidade.MUITO_RAPIDO,
        c: Campanha? = null,
        prazo: Long? = null,
    ) = runBlocking {
        AutomationController.startCampanha((c ?: campanha(origem, velocidade)).copy(velocidade = velocidade), anteriores)
        val fonte = if (origem == Origem.SEGUIDORES) FonteSeguidores(ig, prof) else FonteConversas(ig, prof)
        fila.aoVivo(fonte, flow, filtro, prazo) { pontos += it }
    }

    private fun seguidores(vararg us: String, comConversa: Boolean = true) = us.forEach { u ->
        ig.seguidores += u to "Nome $u"
        if (comConversa) ig.conversa(u) { nome = "Nome $u" }
    }

    private fun nomes(n: Int) = (1..n).map { "p%02d".format(it) }.toTypedArray()

    private fun enviados() = st.results.filter { it.desfecho == Desfecho.ENVIADO }.map { it.username }

    private fun aberturas(u: String) = ig.log.count { it == "abrir $u" }

    private fun nuncaTocouNoPerigo() {
        val perigo = listOf("follow_list_row_large_follow_button", "Ignorar", "row_recommended_user_follow_button", "Foto do perfil")
        assertTrue(ig.log.toString(), ig.log.none { l -> l.startsWith("tocar") && perigo.any { l.contains(it) } })
        assertTrue("so a lista rola, nunca o ViewPager", ig.log.none { it == "rolar unified_follow_list_view_pager" })
    }

    // ---------------- Seus seguidores, ao vivo ----------------

    @Test fun `seguidores - uma tela por vez, rolando ate o fim, cada um uma vez so`() {
        seguidores(*nomes(20))
        rodar()
        assertEquals(nomes(20).toList(), enviados())
        // Telas com uma linha em comum (p08 e p15 aparecem em duas): uma abertura cada.
        nomes(20).forEach { assertEquals(it, 1, aberturas(it)) }
        assertEquals(2, ig.rolagensDaLista)
        assertEquals(RunPhase.COMPLETED, st.phase)
        // 1 s entre os envios: abrir, provar e confirmar ja levam mais que isso, nada a esperar.
        assertEquals(20, ig.toquesEnviar.size)
        assertTrue(ig.toquesEnviar.zipWithNext { a, b -> b - a }.all { it >= 1_000 })
        assertEquals(0, intervalos)
        assertEquals("p20", pontos.last())
        assertEquals(40, ig.log.count { it == "voltar" }) // por pessoa: um fecha o teclado, outro a conversa
        nuncaTocouNoPerigo()
    }

    @Test fun `seguidores - conversa que demora a fechar nao ganha um Voltar a mais que sairia da lista`() {
        seguidores(*nomes(10))
        ig.atrasoFechar = 600
        ig.animacaoRolagem = 200
        rodar()
        assertEquals(nomes(10).toList(), enviados())
        assertEquals(20, ig.log.count { it == "voltar" })
        assertEquals(1, ig.log.count { it == "abrirInstagram" }) // nunca precisou reabrir a lista
    }

    @Test fun `seguidores - lista que carrega aos poucos nao termina antes da hora com o circulo girando`() {
        seguidores(*nomes(40))
        ig.carregadas = 16
        ig.atrasoCarregar = 1_800 // eventos de "carregando" a cada 300 ms no meio
        rodar()
        assertEquals(nomes(40).toList(), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `seguidores - rede lenta com a rolagem parada no circulo de carregando nao termina no meio`() {
        // rolar() devolve false tambem com a proxima pagina vindo: uma espera de 2 s bastava para dar
        // "Fim da lista" (COMPLETED, operacao encerrada) com 16 de 40.
        seguidores(*nomes(40))
        ig.carregadas = 16
        ig.atrasoCarregar = 4_500
        ig.rolagemParadaAoCarregar = true
        rodar()
        assertEquals(nomes(40).toList(), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `seguidores - sem eventos de acessibilidade, espera fixa e cada um uma vez`() {
        seguidores(*nomes(20))
        ig.semEventos = true
        ig.atrasoAbrir = 1_700
        ig.animacaoRolagem = 200
        rodar()
        assertEquals(nomes(20).toList(), enviados())
        nomes(20).forEach { assertEquals(it, 1, aberturas(it)) }
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    private val ESCREVEU = "escrever row_thread_composer_edittext 'Oi! Sábado tem evento.'"

    @Test fun `intervalo do dono fica entre os envios e a proxima conversa abre logo que a anterior confirma`() {
        seguidores(*nomes(3))
        ig.atrasoAbrir = 1_700
        rodar(velocidade = Velocidade.NORMAL)
        val t = ig.toquesEnviar
        assertEquals(3, t.size)
        t.zipWithNext { a, b -> b - a }.forEach { assertTrue("$it", it in 30_000..30_500) }
        // p02 abre alguns segundos depois do envio da p01, nao 30 s depois.
        assertTrue(ig.abriuEm.toString(), ig.abriuEm.getValue("p02") - t[0] < 5_000)
        assertEquals(2, intervalos) // antes do 2o e do 3o envio; nada antes do 1o nem depois do ultimo
        assertEquals(3, ig.log.count { it == ESCREVEU })
    }

    @Test fun `pausa no intervalo sai da conversa sem escrever e retoma a mesma pessoa`() {
        seguidores("ana", "bia")
        var pausou = false
        ig.aoEsperar = { ms ->
            if (st.phase == RunPhase.WAITING_INTERVAL && !pausou) { pausou = true; AutomationController.pause() }
            if (ms == 400L && AutomationController.pauseRequested) AutomationController.resume()
        }
        rodar(velocidade = Velocidade.NORMAL)
        assertTrue(pausou)
        assertEquals(listOf("ana", "bia"), enviados())
        assertEquals(2, st.results.size) // a vez adiada nao deixa desfecho
        assertEquals(2, aberturas("bia")) // abriu, pausou (saiu sem escrever), abriu de novo
        assertEquals(2, ig.log.count { it == ESCREVEU })
        assertTrue(ig.toquesEnviar.let { it[1] - it[0] } >= 30_000)
    }

    @Test fun `pausa pedida enquanto a conversa abria adia a pessoa em vez de virar falha`() {
        seguidores("ana", "bia")
        ig.bloqueado = { AutomationController.pauseRequested }
        var pausou = false
        ig.aoEsperar = { ms ->
            if (!pausou && ig.log.lastOrNull() == "abrir ana") { pausou = true; AutomationController.pause() }
            if (ms == 400L && AutomationController.pauseRequested) AutomationController.resume()
        }
        ig.atrasoAbrir = 500
        rodar()
        assertTrue(pausou)
        assertEquals(listOf("ana", "bia"), enviados())
        assertEquals(2, st.results.size)
        assertEquals(2, aberturas("ana"))
        assertFalse(ig.log.contains("recusado"))
    }

    @Test fun `Parar no intervalo para sem desfecho nem texto para a pessoa da vez`() {
        seguidores("ana", "bia")
        ig.aoEsperar = { if (st.phase == RunPhase.WAITING_INTERVAL) AutomationController.cancel() }
        rodar(velocidade = Velocidade.NORMAL)
        assertEquals(RunPhase.CANCELLED, st.phase)
        assertEquals(listOf("ana"), st.results.map { it.username })
        assertEquals(1, ig.log.count { it == ESCREVEU })
        assertEquals(1, aberturas("bia"))
        assertEquals("voltar", ig.log.last()) // saiu da conversa da bia
    }

    @Test fun `seguidores - a lista muda no meio e segue pelo proximo nao feito`() {
        seguidores(*nomes(20))
        ig.seguidores.add(15, "entrou" to "Nome entrou")
        ig.conversa("entrou") { nome = "Nome entrou" }
        ig.conversa("novo.no.topo")
        ig.aoRolar = {
            if (ig.rolagensDaLista == 1) {
                ig.seguidores.removeAll { it.first == "p12" } // deixou de seguir
                ig.seguidores.add(0, "novo.no.topo" to "Novo") // comecou a seguir: fica acima de onde estamos
            }
        }
        rodar()
        assertEquals(0, aberturas("p12"))
        (nomes(20).toList() - "p12" + "entrou").forEach { assertEquals(it, 1, aberturas(it)) }
        assertTrue(st.results.groupBy { it.username }.values.all { it.size == 1 })
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `seguidores - o Instagram volta a lista ao topo e a fila rola ate reencontrar o ponto`() {
        seguidores(*nomes(20))
        ig.voltaAoTopo = true
        rodar()
        assertEquals(nomes(20).toList(), enviados())
        nomes(20).forEach { assertEquals(it, 1, aberturas(it)) }
        assertTrue(ig.rolagensDaLista > 2)
    }

    @Test fun `seguidores - retomada pula quem ja tem desfecho nesta operacao`() {
        seguidores(*nomes(10))
        // A FALHA (nada saiu) volta na retomada; o resto que ja tem desfecho, nao (Fila.refazer).
        val antes = nomes(10).take(4).map { PersonResult(it, it, 0, Desfecho.ENVIADO, "") } +
            PersonResult("p05", "p05", 0, Desfecho.FALHA, "a conversa não abriu")
        rodar(anteriores = antes)
        nomes(10).take(4).forEach { assertEquals(it, 0, aberturas(it)) }
        nomes(10).drop(4).forEach { assertEquals(it, 1, aberturas(it)) }
        assertEquals(10, st.results.size) // uma linha por pessoa: a FALHA de antes da p05 saiu
    }

    @Test fun `seguidores - Nao enviar para, pedido de parar e quem ja recebeu ficam fora sem abrir`() {
        seguidores(*nomes(6))
        rodar(filtro = Fila.Filtro(
            "minhaconta", naoEnviar = setOf("p02"), pediramParar = setOf("p03"), jaReceberam = setOf("p04"),
        ))
        listOf("p02", "p03", "p04").forEach { assertEquals(it, 0, aberturas(it)) }
        assertEquals(listOf("p01", "p05", "p06"), enviados())
        assertEquals(
            mapOf("p02" to "na lista Não enviar para", "p03" to "pediu para parar", "p04" to "já recebeu antes"),
            st.results.filter { it.desfecho == Desfecho.PULADO }.associate { it.username to it.motivo },
        )
    }

    @Test fun `seguidores - So para estes arrobas para de ler quando todos foram feitos`() {
        seguidores(*nomes(40))
        rodar(filtro = Fila.Filtro("minhaconta", soPara = setOf("p02", "p05")))
        assertEquals(listOf("p02", "p05"), enviados())
        assertEquals(2, st.results.size) // quem nao esta na lista nao conta como pulado
        assertEquals(0, ig.rolagensDaLista)
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `seguidores - a pagina seguindo ao lado, fora da tela, nao entra`() {
        seguidores(*nomes(3))
        ig.seguindoAoLado = listOf("estranho")
        ig.conversa("estranho")
        rodar()
        assertEquals(0, aberturas("estranho"))
        assertEquals(nomes(3).toList(), enviados())
    }

    @Test fun `seguidores - aba seguindo marcada para sem abrir ninguem`() {
        seguidores(*nomes(3))
        ig.abaMarcada = "seguindo"
        assertThrows(Fila.FailedSafe::class.java) { rodar() }
        assertEquals(-1, ig.indice("abrir "))
    }

    @Test fun `seguidores - aba seguindo com a barra de abas chegando depois das linhas para sem abrir ninguem`() {
        // A decisao sobre a aba nunca usa a leitura de antes da barra aparecer.
        seguidores(*nomes(3))
        ig.abaMarcada = "seguindo"
        ig.barraAtrasa = 1_500
        assertThrows(Fila.FailedSafe::class.java) { rodar() }
        assertEquals(-1, ig.indice("abrir "))
    }

    @Test fun `seguidores - barra de abas chegando depois das linhas nao trava a aba Seguidores`() {
        seguidores(*nomes(3))
        ig.barraAtrasa = 1_500
        rodar()
        assertEquals(nomes(3).toList(), enviados())
    }

    @Test fun `seguidores - conta aberta diferente da conferida para`() {
        seguidores(*nomes(3))
        assertThrows(Fila.FailedSafe::class.java) { rodar(filtro = Fila.Filtro("outraconta")) }
        assertEquals(-1, ig.indice("abrir "))
    }

    @Test fun `tres pessoas seguidas sem conversa param a fila`() {
        seguidores("x1", "x2", "x3", comConversa = false)
        seguidores("ana")
        assertThrows(Fila.FailedSafe::class.java) { rodar() }
        assertEquals(0, aberturas("ana"))
    }

    @Test fun `uma conversa boa no meio zera a contagem`() {
        seguidores("x1", "x2", comConversa = false)
        seguidores("ana")
        seguidores("x3", "x4", comConversa = false)
        rodar()
        assertEquals(RunPhase.COMPLETED, st.phase)
        assertEquals(listOf("ana"), enviados())
    }

    @Test fun `seguidores - Instagram fora da frente ao rolar nao vira fim da lista`() {
        seguidores(*nomes(12))
        var saiu = false
        ig.antesDeRolar = { if (!saiu) { saiu = true; ig.visao = FakeInstagram.Visao.OUTRO_APP_NA_FRENTE } }
        // Parada segura (o ponto fica salvo), nunca "Fim da lista" com p09..p12 esquecidos.
        assertThrows(Fila.FailedSafe::class.java) { rodar() }
        assertTrue(ig.log.contains("recusado"))
        assertTrue(st.phase != RunPhase.COMPLETED)
        assertEquals(nomes(8).toList(), enviados())
    }

    @Test fun `seguidores - sem Sugestoes no fim, a lista que nao anda termina depois de esperar carregar`() {
        seguidores(*nomes(12))
        ig.sugestoes = 0
        rodar()
        assertEquals(nomes(12).toList(), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `primeiro envio sem confirmacao para antes de marcar o resto da lista`() {
        seguidores(*nomes(5))
        ig.envio = FakeInstagram.Envio.NADA
        val e = assertThrows(Fila.FailedSafe::class.java) { rodar() }
        assertTrue(e.message, e.message!!.contains("sem confirmação"))
        assertEquals(listOf(Desfecho.INCERTO), st.results.map { it.desfecho })
        assertEquals(0, aberturas("p02"))
    }

    @Test fun `tres envios seguidos sem confirmacao param mesmo com um envio ja confirmado na operacao`() {
        seguidores(*nomes(8))
        ig.envio = FakeInstagram.Envio.NADA
        val antes = listOf(PersonResult("p00", "p00", 0, Desfecho.ENVIADO, "")) // retomada
        assertThrows(Fila.FailedSafe::class.java) { rodar(anteriores = antes) }
        assertEquals(
            listOf(Desfecho.ENVIADO, Desfecho.INCERTO, Desfecho.INCERTO, Desfecho.INCERTO),
            st.results.map { it.desfecho },
        )
        assertEquals(0, aberturas("p04"))
    }

    @Test fun `aviso de restricao pausa e fica na tela ate o dono continuar`() {
        seguidores("ana", "bia")
        ig.envio = FakeInstagram.Envio.RESTRICAO
        val vistos = mutableListOf<RunPhase>()
        ig.aoEsperar = { ms ->
            if (ms == 400L && AutomationController.pauseRequested) {
                vistos += st.phase
                assertTrue(st.message, st.message.contains("restrição"))
                ig.envio = FakeInstagram.Envio.OK
                AutomationController.resume()
            }
        }
        rodar()
        assertEquals(listOf(RunPhase.RESTRICTED), vistos)
        assertEquals(listOf(Desfecho.INCERTO, Desfecho.ENVIADO), st.results.map { it.desfecho })
    }

    @Test fun `pausa no meio da leitura recusa a rolagem e nao vira fim da lista`() {
        seguidores(*nomes(12))
        var pausou = false
        ig.bloqueado = { AutomationController.pauseRequested }
        ig.antesDeRolar = { if (!pausou) { pausou = true; AutomationController.pause() } }
        ig.aoEsperar = { ms -> if (ms == 400L && AutomationController.pauseRequested) AutomationController.resume() }
        rodar()
        assertTrue(ig.log.contains("recusado"))
        assertEquals(nomes(12).toList(), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    // ---------------- Conversas do Direct, ao vivo ----------------

    private fun caixa(vararg chaves: String) { ig.caixa += chaves }

    @Test fun `conversas - abre pela linha, le o arroba, envia pelo link e pula o grupo`() {
        ig.conversa("ana") { nome = "Ana Souza" }
        ig.grupo("g1", "Família", "Ana, Bia e mais 2")
        ig.conversa("bia") { nome = "Bia Lima" }
        caixa("ana", "g1", "bia")
        rodar(Origem.CONVERSAS)
        assertEquals(listOf("ana", "bia"), enviados())
        val pulado = st.results.single { it.desfecho == Desfecho.PULADO }
        assertEquals("Família", pulado.name)
        assertTrue(pulado.motivo, pulado.motivo.contains("sem @"))
        assertTrue(ig.conversas["g1"]!!.mensagens.isEmpty())
        assertTrue(ig.log.toString(), ig.indice("tocar linha Ana Souza") in 0 until ig.indice("abrir ana"))
        assertEquals(RunPhase.COMPLETED, st.phase)
        nuncaTocouNoPerigo()
    }

    @Test fun `conversas - quem recebe sobe para o topo e a caixa rola ate o fim, uma vez cada`() {
        val chaves = (1..12).map { "c%02d".format(it) }
        chaves.forEach { c -> ig.conversa(c) { nome = "Pessoa $c" } }
        caixa(*chaves.toTypedArray())
        rodar(Origem.CONVERSAS)
        assertEquals(chaves.toSet(), enviados().toSet())
        chaves.forEach { assertEquals(it, 1, aberturas(it)) }
        assertEquals(chaves.size, ig.log.count { it.startsWith("tocar linha") })
    }

    @Test fun `conversas - comercial so e pulada se o dono marcar`() {
        ig.conversa("loja.x") { nome = "loja.x"; cabecalho = "Conversa comercial"; cartao = null }
        caixa("loja.x")
        rodar(Origem.CONVERSAS, Fila.Filtro("minhaconta", pularComerciais = true))
        assertEquals("conversa comercial", st.results.single().motivo)
        assertEquals(0, aberturas("loja.x"))

        ig.log.clear()
        rodar(Origem.CONVERSAS)
        assertEquals(listOf("loja.x"), enviados())
    }

    @Test fun `conversas - grupo com subtitulo que parece arroba nao manda para ninguem`() {
        ig.conversa("ana") { nome = "Ana Souza" }
        ig.grupo("g2", "Família", "ana")
        caixa("g2")
        rodar(Origem.CONVERSAS)
        // O link do @ abriu a conversa da Ana, com outro nome: nada escrito, nada enviado.
        assertEquals(Desfecho.FALHA, st.results.single().desfecho)
        assertTrue(ig.conversas["ana"]!!.mensagens.isEmpty())
        assertEquals(-1, ig.indice("escrever row_thread_composer_edittext 'Oi"))
    }

    @Test fun `conversas - sugestao sem mensagens nao e conversa e fica fora`() {
        ig.conversa("nova") { nome = "Nova Pessoa"; novaConversa = true }
        caixa("nova")
        rodar(Origem.CONVERSAS)
        assertEquals(Desfecho.PULADO, st.results.single().desfecho)
        assertEquals(0, aberturas("nova"))
    }

    @Test fun `conversas - tres seguidas sem arroba legivel param antes de abrir a caixa toda`() {
        (1..5).forEach { i -> ig.conversa("o$i") { nome = "Online $i"; cabecalho = "Online agora"; cartao = null } }
        caixa("o1", "o2", "o3", "o4", "o5")
        assertThrows(Fila.FailedSafe::class.java) { rodar(Origem.CONVERSAS) }
        assertEquals(3, ig.log.count { it.startsWith("tocar linha") })
        assertEquals(-1, ig.indice("abrir "))
    }

    /**
     * Caso (b), 26/09: 3 conversas de terceiros abertas (marcadas como lidas) sem nada lido antes de parar. Agora
     * para na primeira, dizendo qual caso foi: o toque nao abriu nada (a caixa continuou na frente).
     */
    @Test fun `conversas - linha que nao abre conversa para na primeira, dizendo que o toque nao abriu nada`() {
        (1..5).forEach { i -> ig.conversa("n$i") { nome = "Pessoa $i"; linhaNaoAbre = true } }
        caixa("n1", "n2", "n3", "n4", "n5")
        val e = assertThrows(Fila.FailedSafe::class.java) { rodar(Origem.CONVERSAS) }
        assertEquals(FonteConversas.TOQUE_NAO_ABRIU, e.message)
        assertEquals(1, ig.log.count { it.startsWith("tocar linha") })
        assertEquals(-1, ig.indice("voltar")) // a caixa estava na frente: nenhum Voltar (sairia dela)
    }

    /** A conversa abriu numa janela que o servico nao le: para na primeira, fecha a conversa e diz por que. */
    @Test fun `conversas - conversa numa janela ilegivel para na primeira e volta a caixa`() {
        ig.visao = FakeInstagram.Visao.MODAL_ILEGIVEL
        (1..5).forEach { i -> ig.conversa("n$i") { nome = "Pessoa $i" } }
        caixa("n1", "n2", "n3", "n4", "n5")
        val e = assertThrows(Fila.FailedSafe::class.java) { rodar(Origem.CONVERSAS) }
        assertEquals(FonteConversas.CONVERSA_ILEGIVEL, e.message)
        assertEquals(1, ig.log.count { it.startsWith("tocar linha") })
        assertEquals("direct", ig.telaAtual)
        assertEquals(-1, ig.indice("abrir "))
    }

    /** O no da linha ficou velho (a caixa mudou): o toque recusado le a caixa de novo e toca uma vez mais. */
    @Test fun `conversas - toque recusado numa linha velha tenta de novo na caixa relida`() {
        ig.conversa("ana") { nome = "Ana Souza" }
        caixa("ana")
        ig.recusarToquesEmLinha = 1
        rodar(Origem.CONVERSAS)
        assertEquals(listOf("ana"), enviados())
        assertEquals(2, ig.log.count { it == "tocar linha Ana Souza" })
    }

    /**
     * Uma mensagem nova sobe outra conversa para o topo logo depois da leitura da caixa: a View de cada linha passa
     * a mostrar a conversa de cima. Tocando na leitura velha, abria a conversa errada ("nao e a da linha tocada") e a
     * pessoa da linha nunca recebia. A caixa e lida de novo ate assentar.
     */
    @Test fun `conversas - caixa que muda logo depois da leitura assenta antes do toque`() {
        listOf("a", "b", "c", "z").forEach { u -> ig.conversa(u) { nome = "Pessoa $u" } }
        caixa("a", "b", "c", "z")
        ig.custoLeitura = 800 // leitura medida na rodada 1
        ig.copiaNoInicio = true
        ig.subida = Triple("gravar a ENVIADO", "z", 150)
        rodar(Origem.CONVERSAS)
        assertEquals(setOf("a", "b", "c", "z"), enviados().toSet())
        assertTrue(st.results.toString(), st.results.none { it.motivo.contains("não é a da linha") })
    }

    @Test fun `conversas - religar o servico nao trava a fila nos grupos que ficaram juntos no topo`() {
        listOf("g1", "g2", "g3").forEach { ig.grupo(it, "Grupo $it", "Ana, Bia e mais 2") }
        listOf("a", "b", "c").forEach { u -> ig.conversa(u) { nome = "Pessoa $u" } }
        caixa("g1", "g2", "a", "g3", "b", "c")
        // O sistema religa o servico logo depois do envio para a (a sobe para o topo da caixa).
        ig.interromperApos = "gravar a ENVIADO"
        assertThrows(Interrompido::class.java) { rodar(Origem.CONVERSAS) }
        assertEquals(listOf("a"), enviados())
        ig.interromperApos = null
        // A nova instancia do servico: fila e fonte novas, o mesmo estado da operacao.
        runBlocking { Fila(ig) { true }.aoVivo(FonteConversas(ig, prof), flow, Fila.Filtro("minhaconta")) { pontos += it } }
        assertEquals(listOf("a", "b", "c"), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
        // Quem ja tem desfecho nao e tocado de novo, mesmo no topo da caixa.
        listOf("Pessoa a", "Grupo g1", "Grupo g2").forEach { n -> assertEquals(n, 1, ig.log.count { it == "tocar linha $n" }) }
        assertEquals(1, aberturas("a"))
        // Cada grupo uma vez no relatorio, mesmo relido depois de religar.
        assertEquals(listOf("Grupo g1", "Grupo g2", "Grupo g3"), st.results.filter { it.desfecho == Desfecho.PULADO }.map { it.name })
    }

    @Test fun `conversas - arroba lido numa conversa pulada tambem prova a leitura`() {
        listOf("o1", "o2", "o3", "o4").forEach { i -> ig.conversa(i) { nome = "Online $i"; cabecalho = "Online agora"; cartao = null } }
        ig.conversa("x") { nome = "Pessoa x" }
        caixa("o1", "o2", "x", "o3", "o4")
        rodar(Origem.CONVERSAS, Fila.Filtro("minhaconta", naoEnviar = setOf("x")))
        assertEquals(RunPhase.COMPLETED, st.phase)
        assertEquals(5, st.results.count { it.desfecho == Desfecho.PULADO })
        assertEquals(-1, ig.indice("abrir "))
    }

    @Test fun `conversas - duas conversas com o mesmo nome na tela, a segunda entra no relatorio`() {
        ig.conversa("ana1") { nome = "Ana" }
        ig.conversa("ana2") { nome = "Ana" }
        caixa("ana1", "ana2")
        rodar(Origem.CONVERSAS)
        assertEquals(listOf("ana1"), enviados())
        assertEquals(FonteConversas.MESMO_NOME, st.results.single { it.desfecho == Desfecho.PULADO }.motivo)
    }

    @Test fun `conversas - conversa na tela diferente da linha tocada fica fora`() {
        ig.conversa("ana") { nome = "Outra Pessoa"; nomeNaLinha = "Ana Souza" }
        ig.conversa("bia") { nome = "Bia Lima" }
        caixa("ana", "bia")
        rodar(Origem.CONVERSAS)
        assertEquals(0, aberturas("ana"))
        assertEquals(listOf("bia"), enviados())
        assertTrue(st.results.first().motivo, st.results.first().motivo.contains("não é a da linha"))
    }

    /**
     * Na "Conversa comercial" o titulo e o @ e a linha da caixa mostra o nome da loja (caixa_principal real: a
     * linha de uma loja com o nome). Comparar o titulo com o nome da linha dava "nao e a da linha tocada"
     * (ilegivel): 3 lojas no topo paravam a fila sem enviar nada, e pularComerciais nunca valia.
     */
    @Test fun `conversas - conversa comercial com o nome da loja na linha e o @ no titulo`() {
        listOf("loja.a", "loja.b", "loja.c").forEach { u ->
            ig.conversa(u) { nome = u; nomeNaLinha = "Loja ${u.last().uppercase()}"; cabecalho = "Conversa comercial"; cartao = null }
        }
        ig.conversa("ana") { nome = "Ana Souza" }
        caixa("loja.a", "loja.b", "loja.c", "ana")
        rodar(Origem.CONVERSAS, Fila.Filtro("minhaconta", pularComerciais = true))
        assertEquals(RunPhase.COMPLETED, st.phase)
        assertEquals(listOf("ana"), enviados())
        assertEquals(listOf("conversa comercial"), st.results.filter { it.desfecho == Desfecho.PULADO }.map { it.motivo }.distinct())
        assertEquals(listOf("loja.a", "loja.b", "loja.c"), st.results.filter { it.desfecho == Desfecho.PULADO }.map { it.username })

        ig.log.clear()
        rodar(Origem.CONVERSAS)
        assertEquals(setOf("loja.a", "loja.b", "loja.c", "ana"), enviados().toSet())
    }

    /** Pessoa online: o subtitulo fica em "Online agora" no prazo, mas o cartao do topo tem o @ e o nome. */
    @Test fun `conversas - pessoa online com o @ so no cartao do topo recebe`() {
        (1..3).forEach { i -> ig.conversa("on$i") { nome = "Online $i"; cabecalho = "Online agora" } }
        caixa("on1", "on2", "on3")
        rodar(Origem.CONVERSAS)
        assertEquals(listOf("on1", "on2", "on3"), enviados())
    }

    /** Grupos fixados no topo, com o subtitulo legivel: lidos (nao e tela ilegivel), pulados sem parar a fila. */
    @Test fun `conversas - tres grupos no topo antes de qualquer @ nao param a fila`() {
        listOf("g1", "g2", "g3").forEach { ig.grupo(it, "Grupo $it", "Ana, Bia e mais 2") }
        ig.conversa("ana") { nome = "Ana Souza" }
        caixa("g1", "g2", "g3", "ana")
        rodar(Origem.CONVERSAS)
        assertEquals(RunPhase.COMPLETED, st.phase)
        assertEquals(listOf("ana"), enviados())
        assertEquals(3, st.results.count { it.desfecho == Desfecho.PULADO })
    }

    /** Uma leitura sem a conversa no meio da espera do @ (transicao) nao encerra a espera: a pessoa nao vira grupo. */
    @Test fun `conversas - leitura vazia no meio da espera do @ nao pula a pessoa como grupo`() {
        ig.conversa("on1") { nome = "Online 1"; cabecalho = "Online agora"; cartao = null; arrobaApos = 2_000 }
        caixa("on1")
        var n = 0
        ig.leituraNula = { ig.telaAtual == "conversa" && n++ == 1 }
        rodar(Origem.CONVERSAS)
        assertEquals(listOf("on1"), enviados())
    }

    /**
     * O @ lido de cada pessoa da caixa zerava o mesmo contador em que o envio falho somava: nunca passava de 1.
     * Depois do 1o ENVIADO o Instagram engole as mensagens (bloqueio leve, sem aviso): a fila tocava Enviar
     * sem confirmacao na caixa inteira. Como em Seus seguidores, para na 3a seguida.
     */
    @Test fun `conversas - envios sem confirmacao depois do primeiro param na terceira, como em Seus seguidores`() {
        val chaves = (1..6).map { "c$it" }
        chaves.forEach { c -> ig.conversa(c) { nome = "Pessoa $c" } }
        caixa(*chaves.toTypedArray())
        ig.antesDeTocar = { if ("gravar c1 ENVIADO" in ig.log) ig.envio = FakeInstagram.Envio.NADA }
        val e = assertThrows(Fila.FailedSafe::class.java) { rodar(Origem.CONVERSAS) }
        assertTrue(e.message, e.message!!.contains("sem confirmação do envio"))
        assertEquals(
            listOf(Desfecho.ENVIADO, Desfecho.INCERTO, Desfecho.INCERTO, Desfecho.INCERTO),
            st.results.map { it.desfecho },
        )
        assertEquals(0, ig.log.count { it == "tocar linha Pessoa c5" })
    }

    @Test fun `conversas - Modal sem raiz no topo (este aparelho) le a conversa pelo evento dela e envia`() {
        ig.visao = FakeInstagram.Visao.MODAL_SEM_RAIZ
        ig.conversa("ana") { nome = "Ana Souza" }
        ig.conversa("bia") { nome = "Bia Lima" }
        caixa("ana", "bia")
        rodar(Origem.CONVERSAS)
        assertEquals(listOf("ana", "bia"), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    /** O dono olhou Pedidos na pausa (ou o Instagram voltou ali): as linhas de pedido nunca viram "suas conversas". */
    @Test fun `conversas - caixa que vira Pedidos no meio para antes de abrir um pedido`() {
        ig.conversa("ana") { nome = "Ana Souza" }
        ig.conversa("bia") { nome = "Bia Lima" }
        caixa("ana", "bia")
        ig.aoEsperar = { if ("gravar ana ENVIADO" in ig.log) ig.caixaEmPedidos = true }
        val e = assertThrows(Fila.FailedSafe::class.java) { rodar(Origem.CONVERSAS) }
        assertEquals(FonteConversas.EM_PEDIDOS, e.message)
        assertEquals(listOf("ana"), enviados())
        assertEquals(-1, ig.indice("tocar linha Bia"))
    }

    @Test fun `conversas - caixa em Pedidos para`() {
        ig.conversa("ana") { nome = "Ana Souza" }
        caixa("ana")
        ig.caixaEmPedidos = true
        assertThrows(Fila.FailedSafe::class.java) { rodar(Origem.CONVERSAS) }
        assertEquals(-1, ig.indice("tocar linha"))
    }

    // ---------------- Amigos Proximos (lista digitada) ----------------

    @Test fun `amigos - arroba repetido no plano e marcado uma vez so`() = runTest {
        ig.amigos["bia"] = false
        ig.amigos["zeca"] = true
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"))), Batch(2, listOf(Follower("bia", "bia"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = listOf("zeca", "zeca"))
        fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta")
        assertEquals(
            listOf("bia" to Desfecho.ADICIONADO, "zeca" to Desfecho.REMOVIDO),
            st.results.map { it.username to it.desfecho },
        )
        assertEquals(listOf("Tocar em Concluir para gravar a lista Amigos Próximos de @minhaconta?"), perguntas)
        assertEquals(RunPhase.COMPLETED, st.phase)
        assertFalse(st.results.any { it.desfecho == Desfecho.PULADO })
    }

    /**
     * Quem pediu para parar trocou de @ (o antigo nao aparece na busca): antes marcar devolvia null, nada ficava no
     * relatorio e a operacao terminava "Lista gravada" com a pessoa ainda vendo os stories de Amigos Proximos.
     */
    @Test fun `amigos - quem pediu para parar e nao aparece na busca fica no relatorio e no aviso do Concluir`() = runTest {
        ig.amigos["bia"] = false
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = listOf("arroba.antigo"))
        fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta")
        assertEquals(
            listOf("bia" to Desfecho.ADICIONADO, "arroba.antigo" to Desfecho.NAO_ENCONTRADO),
            st.results.map { it.username to it.desfecho },
        )
        assertEquals(Fila.PAROU_SEM_PROVA, st.results.last().motivo)
        assertTrue(perguntas.toString(), perguntas.single().contains("@arroba.antigo"))
        assertTrue(st.message, st.message.contains("@arroba.antigo"))
    }

    /**
     * Duas contas no aparelho: o dono conferiu em @minhaconta e trocou para @outraconta pelo proprio Instagram. Antes
     * parava dizendo que "a conferencia dessa conta nao passou" sem ela ter sido tentada (o Modo DM reconferia, o A
     * nao). A abertura de Amigos Proximos confere nela as mesmas telas e papeis: segue nela, e o Concluir diz o @.
     */
    @Test fun `amigos - outra conta aberta, conferida na abertura, a operacao e dela`() = runTest {
        ig.amigos["bia"] = false
        ig.amigos["zeca"] = true
        ig.conta = "outraconta"
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = emptyList())
        fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta")
        assertEquals(listOf("bia" to Desfecho.ADICIONADO), st.results.map { it.username to it.desfecho })
        assertEquals("outraconta", st.conta)
        assertEquals(listOf("Tocar em Concluir para gravar a lista Amigos Próximos de @outraconta?"), perguntas)
    }

    /** Outra conta em que a abertura NAO conferiu tudo (aqui, sem nenhuma linha legivel): nenhum toque em ninguem. */
    @Test fun `amigos - outra conta sem tudo conferido na abertura nao toca em ninguem`() {
        ig.conta = "outraconta"
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = emptyList())
        val e = assertThrows(Fila.FailedSafe::class.java) {
            runBlocking { fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta") }
        }
        assertEquals(Conta.naoConferida("outraconta"), e.message)
        assertFalse(ig.log.any { it == "tocar row_user_container" || it.startsWith("escrever") })
        assertTrue(st.results.isEmpty())
    }

    /**
     * Servico religado no meio (plano mantido): a tela reabre sem Concluir e as marcas de antes podem ter se perdido
     * (nao medido se gravam sem Concluir). Antes a retomada pulava quem ja tinha desfecho: bia ficava fora, zeca
     * (pediu para parar) dentro, e com todos ja feitos a operacao parava em "fluxo fora de ordem" sem o Concluir.
     */
    @Test fun `amigos - retomada refaz todos na tela reaberta e so toca quem precisa`() = runTest {
        ig.amigos["bia"] = false // o ADICIONADO de antes nao ficou gravado
        ig.amigos["caio"] = true // este ficou
        ig.amigos["zeca"] = true // o REMOVIDO de antes nao ficou gravado
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"), Follower("caio", "caio"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = listOf("zeca"))
        AutomationController.update {
            it.copy(
                conta = "minhaconta",
                results = listOf(
                    PersonResult("bia", "bia", 1, Desfecho.ADICIONADO, ""), PersonResult("caio", "caio", 1, Desfecho.ADICIONADO, ""),
                    PersonResult("zeca", "zeca", 1, Desfecho.REMOVIDO, "pediu para parar"),
                ),
            )
        }
        fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta")
        assertEquals(mapOf("bia" to true, "caio" to true, "zeca" to false), ig.amigos.toMap())
        assertEquals(2, ig.log.count { it == "tocar row_user_container" })
        assertEquals(
            mapOf("bia" to Desfecho.ADICIONADO, "caio" to Desfecho.ADICIONADO, "zeca" to Desfecho.REMOVIDO),
            st.results.associate { it.username to it.desfecho },
        )
        assertEquals(3, st.results.size)
        assertEquals(st.message, RunPhase.COMPLETED, st.phase)
    }

    /**
     * Retomada com a busca sem resposta (servidor lento): marcar() devolve FALHA sem tocar em ninguem. Antes o
     * ADICIONADO/REMOVIDO da passada anterior virava FALHA no relatorio. Fica, pedindo para conferir, e quem pediu
     * para parar continua no aviso do Concluir (a marca de antes pode ter se perdido na reabertura).
     */
    @Test fun `amigos - retomada com a busca sem resposta nao rebaixa quem ja foi marcado`() = runTest {
        ig.amigos["bia"] = true
        ig.amigos["zeca"] = false
        ig.atrasoBusca = 600_000 // a resposta nunca chega no prazo da busca
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = listOf("zeca"))
        AutomationController.update {
            it.copy(
                conta = "minhaconta",
                results = listOf(
                    PersonResult("bia", "bia", 1, Desfecho.ADICIONADO, ""),
                    PersonResult("zeca", "zeca", 1, Desfecho.REMOVIDO, "pediu para parar"),
                ),
            )
        }
        fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta")
        assertEquals(0, ig.log.count { it == "tocar row_user_container" })
        assertEquals(
            listOf(
                Triple("bia", Desfecho.ADICIONADO, Fila.SEM_RECONFERIR),
                Triple("zeca", Desfecho.REMOVIDO, Fila.PAROU_SEM_PROVA),
            ),
            st.results.map { Triple(it.username, it.desfecho, it.motivo) },
        )
        assertTrue(perguntas.toString(), perguntas.single().contains("@zeca"))
    }

    private fun pausaQueVolta(aoVoltar: () -> Unit = {}) {
        ig.bloqueado = { AutomationController.pauseRequested || AutomationController.cancelRequested }
        ig.aoEsperar = { ms ->
            if (ms == 400L && AutomationController.pauseRequested) { aoVoltar(); AutomationController.resume() }
        }
    }

    /**
     * Pausar enquanto o app ainda navega Perfil > Opcoes > Configuracoes (10 a 25 s): o toque em Opcoes e recusado
     * pela pausa (NodeOps). Antes: "Configuracoes e atividade nao abriu" e parada segura. Agora espera o Continuar e
     * abre de novo.
     */
    @Test fun `amigos - Pausar durante a abertura pausa e abre de novo, nao para`() = runTest {
        ig.amigos["bia"] = false
        var pausou = false
        pausaQueVolta()
        ig.antesDeTocar = { if (it.contentDescription == "Opções" && !pausou) { pausou = true; AutomationController.pause() } }
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = emptyList())
        fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta")
        assertTrue(pausou)
        assertEquals(st.message, RunPhase.COMPLETED, st.phase)
        assertEquals(listOf("bia" to Desfecho.ADICIONADO), st.results.map { it.username to it.desfecho })
    }

    /**
     * Pausar no meio de uma pessoa (marcar ocupa quase todo o tempo da operacao): o toque na linha e recusado pela
     * pausa. Antes: "o toque na linha foi recusado" e parada segura. Agora a mesma pessoa de novo depois do Continuar.
     */
    @Test fun `amigos - Pausar no meio de uma pessoa refaz a mesma pessoa, sem parada`() = runTest {
        ig.amigos["bia"] = false
        ig.amigos["caio"] = false
        var pausou = false
        pausaQueVolta()
        ig.antesDeTocar = { if (ig.log.lastOrNull()?.startsWith("escrever") == true && !pausou) { pausou = true; AutomationController.pause() } }
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"), Follower("caio", "caio"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = emptyList())
        fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta")
        assertTrue(pausou)
        assertEquals(st.message, RunPhase.COMPLETED, st.phase)
        assertEquals(mapOf("bia" to true, "caio" to true), ig.amigos.toMap())
        assertEquals(listOf("bia" to Desfecho.ADICIONADO, "caio" to Desfecho.ADICIONADO), st.results.map { it.username to it.desfecho })
    }

    /**
     * Na pausa o dono abriu a Nova mensagem (a mesma busca search_edit_text) e tocou Continuar. Antes o app escrevia o
     * @ ali e seguia; agora reabre Amigos Proximos pelo Perfil (conferindo a conta) e confere todos de novo.
     */
    @Test fun `amigos - depois da pausa fora da tela reabre Amigos Proximos e confere todos`() = runTest {
        ig.amigos["bia"] = false
        ig.amigos["caio"] = false
        var pausou = false
        ig.bloqueado = { AutomationController.pauseRequested || AutomationController.cancelRequested }
        // Pausar enquanto a busca por caio assenta; na pausa o dono abre a Nova mensagem e toca Continuar.
        ig.aoEsperar = { ms ->
            if (!pausou && ig.log.contains("escrever search_edit_text 'caio'")) { pausou = true; AutomationController.pause() }
            else if (ms == 400L && AutomationController.pauseRequested) { ig.irPara("nova"); AutomationController.resume() }
        }
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"), Follower("caio", "caio"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = emptyList())
        fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta")
        assertTrue(pausou)
        assertEquals(st.message, RunPhase.COMPLETED, st.phase)
        assertEquals(mapOf("bia" to true, "caio" to true), ig.amigos.toMap())
        assertEquals(mapOf("bia" to Desfecho.ADICIONADO, "caio" to Desfecho.ADICIONADO), st.results.associate { it.username to it.desfecho })
        // Na Nova mensagem nada foi escrito nem tocado: de la, so Voltar ate a aba Perfil.
        val naNova = ig.log.drop(ig.indice("irPara nova") + 1).takeWhile { it != "tocar profile_tab" }
        assertTrue(naNova.toString(), naNova.none { it.startsWith("escrever") || it.startsWith("tocar") })
    }

    /** Retomada (servico religado) com o Instagram noutra conta que a da operacao: os desfechos da outra lista saem. */
    @Test fun `amigos - retomada noutra conta nao pula ninguem pelos desfechos da outra`() = runTest {
        ig.amigos["bia"] = false
        ig.amigos["zeca"] = true
        val plan = listOf(Batch(1, listOf(Follower("bia", "bia"))))
        AutomationController.start(plan, Modo.AMIGOS_PROXIMOS, "", 0, autoConfirm = false, remover = emptyList())
        AutomationController.update { it.copy(conta = "outraconta", results = listOf(PersonResult("bia", "bia", 1, Desfecho.ADICIONADO, ""))) }
        fila.amigos(plan, CloseFriendsFlow(ig, prof), "minhaconta")
        assertEquals(true, ig.amigos["bia"])
        assertEquals("minhaconta", st.conta)
    }

    // ---------------- Ate 3 mensagens, limite, comecar a partir de, parar as, conta ----------------

    private val M1 = "Oi! Sábado tem evento."
    private val M2 = "Começa às 20h, na praça."
    private val M3 = "Responda PARE para não receber mais."

    private fun op(
        modo: ModoMensagens = ModoMensagens.REVEZAR,
        limite: Int? = null,
        aPartirDe: String? = null,
        mensagens: List<String> = listOf(M1, M2, M3),
    ) = campanha(Origem.SEGUIDORES, Velocidade.NORMAL).copy(
        mensagens = mensagens, modoMensagens = modo, limite = limite, aPartirDe = aPartirDe,
    )

    /** As mensagens que chegaram a cada pessoa, na ordem da conversa. */
    private fun recebidas(u: String) = ig.conversas.getValue(u).mensagens.toList()

    @Test fun `revezar - cada pessoa recebe uma, em turnos 1 2 3 1, com o intervalo entre pessoas`() {
        seguidores(*nomes(4))
        rodar(c = op(ModoMensagens.REVEZAR), velocidade = Velocidade.NORMAL)
        assertEquals(listOf(listOf(M1), listOf(M2), listOf(M3), listOf(M1)), nomes(4).map(::recebidas))
        assertEquals(listOf(listOf(1), listOf(2), listOf(3), listOf(1)), st.results.map { it.enviadas })
        assertEquals(4, ig.toquesEnviar.size)
        assertEquals(3, intervalos)
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `revezar - a retomada continua a roda de onde parou`() {
        seguidores(*nomes(3))
        val antes = listOf(PersonResult("p01", "p01", 0, Desfecho.ENVIADO, "", listOf(1)))
        rodar(c = op(ModoMensagens.REVEZAR), anteriores = antes)
        assertEquals(listOf(M2), recebidas("p02"))
        assertEquals(listOf(M3), recebidas("p03"))
        assertEquals(0, aberturas("p01"))
    }

    @Test fun `sequencia - cada pessoa recebe as 3 na ordem, so a espera da confirmacao entre elas`() {
        seguidores(*nomes(2))
        ig.atrasoBolha = 700 // a bolha aparece depois do toque: a seguinte espera por ela
        rodar(c = op(ModoMensagens.SEQUENCIA), velocidade = Velocidade.NORMAL)
        assertEquals(listOf(M1, M2, M3), recebidas("p01"))
        assertEquals(listOf(M1, M2, M3), recebidas("p02"))
        assertEquals(listOf(listOf(1, 2, 3), listOf(1, 2, 3)), st.results.map { it.enviadas })
        val t = ig.toquesEnviar
        assertEquals(6, t.size)
        // Dentro da pessoa: so a confirmacao (bolha + assentar), nunca o intervalo de 30 s.
        listOf(t[1] - t[0], t[2] - t[1], t[4] - t[3], t[5] - t[4]).forEach { assertTrue("$it", it in 700..5_000) }
        assertTrue("${t[3] - t[2]}", t[3] - t[2] >= 30_000) // o intervalo do dono e entre pessoas
        assertEquals(1, intervalos)
        // A prova do @ uma vez por pessoa: um link aberto por pessoa.
        assertEquals(1, aberturas("p01"))
        // Cada mensagem so foi escrita depois da anterior confirmada na conversa.
        val escritas = ig.log.filter { it.startsWith("escrever row_thread_composer_edittext '") && !it.endsWith("''") }
        assertEquals(6, escritas.size)
    }

    @Test fun `sequencia - falha na 2a para a pessoa ali, a 3a nunca sai nem volta na retomada`() {
        seguidores("ana", "bia")
        // Depois do 1o toque em Enviar da ana, o botao Enviar dela nao habilita mais: a 2a falha antes do toque.
        ig.aoEsperar = { if (ig.toquesEnviar.size == 1) ig.conversas.getValue("ana").enviarHabilitado = false }
        rodar(c = op(ModoMensagens.SEQUENCIA))
        assertEquals(listOf(M1), recebidas("ana"))
        val ana = st.results.first { it.username == "ana" }
        assertEquals(Desfecho.ENVIADO, ana.desfecho)
        assertEquals(listOf(1), ana.enviadas)
        assertTrue(ana.motivo, ana.motivo.startsWith("1 de 3 mensagens; parou"))
        assertEquals(listOf(M1, M2, M3), recebidas("bia"))
        assertEquals(Desfecho.ENVIADO, registro.gravados["ana"])

        // Retomada da mesma operacao: a ana ja tem desfecho, nada mais sai para ela.
        val antes = st.results
        val toques = ig.toquesEnviar.size
        rodar(c = op(ModoMensagens.SEQUENCIA), anteriores = antes, filtro = Fila.Filtro("minhaconta", jaReceberam = setOf("ana", "bia")))
        assertEquals(toques, ig.toquesEnviar.size)
        assertEquals(listOf(M1), recebidas("ana"))
    }

    @Test fun `sequencia - 2a sem confirmacao fica INCERTO e a 3a nunca sai`() {
        seguidores("ana", "bia")
        ig.aoEsperar = { if (ig.toquesEnviar.size == 4) ig.envio = FakeInstagram.Envio.NADA } // a 2a da bia nao aparece
        rodar(c = op(ModoMensagens.SEQUENCIA))
        assertEquals(listOf(M1, M2, M3), recebidas("ana"))
        val bia = st.results.first { it.username == "bia" }
        assertEquals(Desfecho.INCERTO, bia.desfecho)
        assertEquals(listOf(1, 2), bia.enviadas)
        assertEquals(listOf(M1), recebidas("bia"))
        assertEquals(5, ig.toquesEnviar.size)
        assertFalse(ig.log.drop(ig.indice("abrir bia")).contains("escrever row_thread_composer_edittext '$M3'"))
    }

    @Test fun `mensagens vazias sao ignoradas`() {
        seguidores(*nomes(2))
        rodar(c = op(ModoMensagens.SEQUENCIA, mensagens = listOf(M1, "  ", "")))
        assertEquals(listOf(M1), recebidas("p01"))
        assertEquals(2, ig.toquesEnviar.size)
    }

    @Test fun `limite - ate X pessoas, pulados nao contam, e a operacao termina`() {
        seguidores(*nomes(10))
        rodar(c = op(limite = 3), filtro = Fila.Filtro("minhaconta", naoEnviar = setOf("p02")))
        assertEquals(listOf("p01", "p03", "p04"), enviados())
        assertEquals(3, st.enviados)
        assertEquals(RunPhase.COMPLETED, st.phase)
        assertTrue(st.message, st.message.contains("limite"))
        assertEquals(0, aberturas("p05"))
    }

    @Test fun `limite - a retomada respeita quem ja recebeu nesta operacao`() {
        seguidores(*nomes(10))
        val antes = listOf("p01", "p02").map { PersonResult(it, it, 0, Desfecho.ENVIADO, "", listOf(1)) }
        rodar(c = op(limite = 3), anteriores = antes)
        assertEquals(listOf("p01", "p02", "p03"), enviados())
        assertEquals(1, ig.toquesEnviar.size)
    }

    @Test fun `comecar a partir de - quem vem antes na lista fica de fora sem contar`() {
        seguidores(*nomes(12))
        rodar(c = op(aPartirDe = "p05"))
        assertEquals(nomes(12).drop(4), enviados())
        (1..4).forEach { assertEquals(0, aberturas("p%02d".format(it))) }
        assertTrue(st.results.none { it.username in nomes(4) })
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `comecar a partir de - a retomada nao manda para quem vem antes do arroba de partida`() {
        // Parou depois de p06 (Parar, "Parar as", servico religado); a retomada rele a lista do topo.
        seguidores(*nomes(10))
        val antes = listOf("p05", "p06").map { PersonResult(it, it, 0, Desfecho.ENVIADO, "", listOf(1)) }
        rodar(c = op(aPartirDe = "p05"), anteriores = antes)
        (1..6).forEach { assertEquals("p%02d".format(it), 0, aberturas("p%02d".format(it))) }
        assertEquals(nomes(10).drop(4), enviados())
        assertTrue(st.results.none { it.username in nomes(4) })
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `pausa enquanto a lista abre fica pausada e segue, em vez de parar com a lista nao abriu`() {
        seguidores(*nomes(3))
        ig.bloqueado = { AutomationController.pauseRequested }
        var pausou = false
        val fases = mutableListOf<RunPhase>()
        // O dono toca em Pausar logo no comeco: o toque da abertura e recusado.
        ig.antesDeTocar = { if (!pausou) { pausou = true; AutomationController.pause() } }
        ig.aoEsperar = { ms -> if (ms == 400L && AutomationController.pauseRequested) { fases += st.phase; AutomationController.resume() } }
        rodar()
        assertTrue(ig.log.contains("recusado"))
        assertEquals(listOf(RunPhase.PAUSED), fases.distinct())
        assertEquals(nomes(3).toList(), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `comecar a partir de um arroba que nao esta na lista para sem enviar nada`() {
        seguidores(*nomes(5))
        val e = assertThrows(Fila.FailedSafe::class.java) { rodar(c = op(aPartirDe = "zeca")) }
        assertTrue(e.message, e.message!!.contains("@zeca"))
        assertEquals(0, ig.toquesEnviar.size)
    }

    @Test fun `parar as - na hora escolhida para como Parar, para retomar depois`() {
        seguidores(*nomes(10))
        rodar(c = op(), velocidade = Velocidade.NORMAL, prazo = 45_000)
        assertEquals(RunPhase.CANCELLED, st.phase)
        assertTrue(st.message, st.message.contains("horário"))
        assertEquals(2, ig.toquesEnviar.size) // 0 s e 30 s; a 3a seria depois das 45 s
    }

    @Test fun `parar as - quanto falta, hoje ou amanha`() {
        assertEquals(30 * 60_000L, Fila.msAte(10 * 60 + 30, java.time.LocalTime.of(10, 0)))
        assertEquals(23 * 3_600_000L, Fila.msAte(9 * 60, java.time.LocalTime.of(10, 0)))
    }

    @Test fun `troca de conta entre dois lotes - ninguem da lista da outra conta recebe`() {
        seguidores(*nomes(10)) // 1o lote: p01..p08
        ig.seguidoresDe["outraconta"] = (1..10).map { "b%02d".format(it) to "Nome b$it" }.toMutableList()
        (1..10).forEach { ig.conversa("b%02d".format(it)) }
        var trocou = false
        var aviso = ""
        ig.aoEsperar = {
            // Troca logo depois do ultimo envio do lote: a proxima leitura da lista ja e na outra conta.
            if (!trocou && ig.toquesEnviar.size == 8) { trocou = true; ig.conta = "outraconta" }
            if (st.phase == RunPhase.PAUSED && ig.conta == "outraconta") {
                aviso = st.message
                ig.conta = "minhaconta" // o dono volta para a conta da operacao e toca em Continuar
                AutomationController.resume()
            }
        }
        rodar(c = op())
        assertTrue(aviso, aviso.contains("@outraconta"))
        assertEquals(emptyList<String>(), ig.log.filter { it.startsWith("abrir b") })
        assertEquals(nomes(10).toList(), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `na pausa, a lista de seguidores da conta da operacao aberta noutra conta nao engana a conferencia`() {
        // O titulo da lista de seguidores e o DONO da lista: na outra conta, a lista de @minhaconta mostra
        // "minhaconta". Depois da pausa, a conta e conferida pelo Direct (a conta logada), nunca por ali.
        seguidores(*nomes(5))
        var fase = 0
        var trocouEm = -1
        var voltouEm = -2
        var aviso = ""
        ig.aoEsperar = { ms ->
            if (fase == 0 && ig.toquesEnviar.size == 2) { fase = 1; AutomationController.pause() }
            if (ms == 400L && AutomationController.pauseRequested) {
                if (fase == 1) {
                    fase = 2
                    ig.conta = "outraconta"; ig.donoDaLista = "minhaconta"; trocouEm = ig.toquesEnviar.size
                    AutomationController.resume()
                } else if (fase == 2 && st.phase == RunPhase.PAUSED) {
                    fase = 3
                    aviso = st.message
                    ig.conta = "minhaconta"; ig.donoDaLista = null; voltouEm = ig.toquesEnviar.size
                    AutomationController.resume()
                }
            }
        }
        rodar(c = op())
        assertTrue(aviso, aviso.contains("@outraconta"))
        assertEquals(trocouEm, voltouEm) // nenhum toque em Enviar enquanto a outra conta estava logada
        assertEquals(nomes(5).toList(), enviados())
    }

    @Test fun `troca de conta no meio pausa com aviso e nada sai como a outra conta`() {
        seguidores(*nomes(5))
        var trocouEm = -1
        var voltouEm = -1
        var aviso = ""
        ig.aoEsperar = {
            if (ig.toquesEnviar.size == 2 && trocouEm < 0) { ig.conta = "outraconta"; trocouEm = ig.toquesEnviar.size }
            if (st.phase == RunPhase.PAUSED && ig.conta == "outraconta") {
                aviso = st.message
                // O dono volta para a conta da operacao e toca em Continuar.
                ig.conta = "minhaconta"; voltouEm = ig.toquesEnviar.size
                AutomationController.resume()
            }
        }
        rodar(c = op())
        assertTrue(aviso, aviso.contains("@outraconta") && aviso.contains("@minhaconta"))
        assertEquals(trocouEm, voltouEm) // nenhum toque em Enviar enquanto a outra conta estava aberta
        assertEquals(nomes(5).toList(), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }
}
