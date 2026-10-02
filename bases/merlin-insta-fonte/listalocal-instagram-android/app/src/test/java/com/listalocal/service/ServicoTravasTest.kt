package com.listalocal.service

import com.listalocal.core.selectors.SelectorProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Travas do servico (a fila ligada ao aparelho) achadas na caca de 26/09: falso-bloqueio numa situacao valida e
 * trava frouxa que deixaria enviar errado. Cada teste falha sem a correcao.
 */
class ServicoTravasTest {

    private val ig = FakeInstagram()
    private val prof = SelectorProfile.IG_448
    private val registro = FakeRegistro(ig.log)
    private val flow = DmFlow(ig, prof, registro)
    private val fila = Fila(ig) { true }
    private val st get() = AutomationController.state.value

    private fun campanha(origem: Origem, velocidade: Velocidade = Velocidade.MUITO_RAPIDO) = Campanha(
        conta = "minhaconta", origem = origem, mensagens = listOf("Oi! Sábado tem evento."),
        velocidade = velocidade, inicio = 0,
    )

    private fun enviados() = st.results.filter { it.desfecho == Desfecho.ENVIADO }.map { it.username }

    // ---------------- conta trocada no meio ----------------

    /**
     * O dono troca de conta no Instagram no meio de "Conversas do Direct". A fonte reabria a caixa (agora da outra
     * conta) e abria a primeira conversa dela, marcando como lida a conversa de um amigo pessoal, antes de a fila
     * ver a conta trocada. Nenhuma conversa da outra conta e aberta; ninguem dela recebe.
     */
    @Test fun `conversas - troca de conta no meio, nenhuma conversa da outra conta e aberta nem recebe`() {
        listOf("a1", "a2", "a3", "a4").forEach { u -> ig.conversa(u) { nome = "Pessoa $u" } }
        ig.caixa += listOf("a1", "a2", "a3", "a4")
        listOf("b1", "b2").forEach { u -> ig.conversa(u) { nome = "Amigo $u" } }
        ig.caixaDe["outraconta"] = mutableListOf("b1", "b2")
        var trocou = false
        var aviso = ""
        ig.aoEsperar = {
            if (!trocou && ig.toquesEnviar.size == 2) { trocou = true; ig.conta = "outraconta" }
            if (st.phase == RunPhase.PAUSED && ig.conta == "outraconta") {
                aviso = st.message
                ig.conta = "minhaconta" // o dono volta para a conta da operacao e toca em Continuar
                AutomationController.resume()
            }
        }
        runBlocking {
            AutomationController.startCampanha(campanha(Origem.CONVERSAS))
            fila.aoVivo(FonteConversas(ig, prof, "minhaconta"), flow, Fila.Filtro("minhaconta")) {}
        }
        assertTrue(aviso, aviso.contains("@outraconta"))
        assertEquals(emptyList<String>(), ig.log.filter { it.startsWith("tocar linha Amigo") || it.startsWith("abrir b") })
        assertEquals(listOf("a1", "a2", "a3", "a4"), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    // ---------------- pausa, painel e tela bloqueada ----------------

    private fun seguidores(vararg us: String) = us.forEach { u ->
        ig.seguidores += u to "Nome $u"
        ig.conversa(u) { nome = "Nome $u" }
    }

    private fun rodar(c: Campanha = campanha(Origem.SEGUIDORES), anteriores: List<PersonResult> = emptyList()) = runBlocking {
        AutomationController.startCampanha(c, anteriores)
        val fonte = if (c.origem == Origem.SEGUIDORES) FonteSeguidores(ig, prof) else FonteConversas(ig, prof, c.conta)
        fila.aoVivo(fonte, flow, Fila.Filtro("minhaconta")) {}
    }

    /**
     * Rapido ou Muito rapido: o intervalo ja passou quando a conversa abre, e o dono toca em Pausar no instante em que
     * ela aparece. O toque no campo e a escrita sao recusados pela pausa: a pessoa virava FALHA "nao consegui escrever
     * no campo", sem ter recebido nada, e era pulada no Continuar e na retomada.
     */
    @Test fun `Pausar logo antes da escrita adia a pessoa em vez de virar FALHA`() {
        seguidores("ana", "bia")
        ig.bloqueado = { AutomationController.pauseRequested }
        var pausou = false
        ig.antesDeTocar = { no ->
            if (!pausou && no.viewIdResourceName?.endsWith("row_thread_composer_edittext") == true) {
                pausou = true; AutomationController.pause()
            }
        }
        ig.aoEsperar = { ms -> if (ms == 400L && AutomationController.pauseRequested) AutomationController.resume() }
        rodar()
        assertTrue(pausou)
        assertTrue(ig.log.contains("recusado"))
        assertEquals(listOf("ana", "bia"), enviados())
        assertEquals(st.results.toString(), 2, st.results.size)
        assertEquals(null, registro.gravados.filterValues { it == Desfecho.FALHA }.keys.firstOrNull())
    }

    /**
     * O botao de cima do painel so mudava o rotulo quando tocado. A fila pausa sozinha no aviso de restricao: com
     * "Pausar" ainda escrito, o dono tocava nele para garantir a parada e RETOMAVA o envio logo depois do aviso.
     */
    @Test fun `painel - pausa da propria fila nunca e desfeita pelo toque em Pausar`() {
        seguidores("ana", "bia")
        ig.envio = FakeInstagram.Envio.RESTRICAO
        var noAviso: Painel.Acao? = null
        var toque: Painel.Acao? = Painel.Acao.PAUSAR
        ig.aoEsperar = { ms ->
            if (ms == 400L && AutomationController.pauseRequested && noAviso == null) {
                noAviso = Painel.acao(AutomationController.pauseRequested, st.phase)
                // O painel mostrava "Pausar" (nenhum toque nele): o dono toca para garantir a parada.
                toque = Painel.aoTocar(Painel.Acao.PAUSAR, AutomationController.pauseRequested, st.phase)
                ig.envio = FakeInstagram.Envio.OK
                AutomationController.resume() // o Continuar do app, com o aviso lido
            }
        }
        rodar()
        assertEquals(Painel.Acao.VER_AVISO, noAviso)
        assertEquals(null, toque)
        // Pausa do dono ou da conta trocada: o botao diz Continuar; sem pausa, Pausar.
        assertEquals(Painel.Acao.CONTINUAR, Painel.acao(true, RunPhase.PAUSED))
        assertEquals(Painel.Acao.PAUSAR, Painel.aoTocar(Painel.Acao.PAUSAR, false, RunPhase.WORKING))
        assertEquals(Painel.Acao.CONTINUAR, Painel.aoTocar(Painel.Acao.CONTINUAR, true, RunPhase.PAUSED))
    }

    /**
     * O dono aperta o botao de energia no meio (o vigia do servico pausa com o motivo). Antes: a pessoa da vez virava
     * FALHA (a conversa nao aparece com a tela bloqueada), e a fila abria o Instagram e apertava Voltar na tela de
     * bloqueio ate parar com "Nao consegui ver qual conta esta aberta".
     */
    @Test fun `tela bloqueada no meio - pausa sem desfecho para a pessoa da vez e sem Voltar na tela de bloqueio`() {
        seguidores("p01", "p02", "p03")
        ig.atrasoAbrir = 1_000
        ig.bloqueado = { AutomationController.pauseRequested }
        var bloqueou = -1
        var desbloqueou = -1
        var aviso = ""
        ig.aoEsperar = { ms ->
            if (bloqueou < 0 && ig.log.lastOrNull() == "abrir p02") {
                bloqueou = ig.log.size
                ig.visao = FakeInstagram.Visao.OUTRO_APP_NA_FRENTE // a tela de bloqueio por cima
                Painel.bloqueio(telaLigada = false, bloqueada = true, modoAudio = 0)!!.let { m ->
                    AutomationController.pause(m); AutomationController.setPhase(RunPhase.PAUSED, m)
                }
            }
            if (ms == 400L && AutomationController.pauseRequested && desbloqueou < 0) {
                desbloqueou = ig.log.size
                aviso = st.message
                ig.visao = FakeInstagram.Visao.NORMAL
                AutomationController.resume()
            }
        }
        rodar()
        assertTrue("$bloqueou $desbloqueou", bloqueou > 0 && desbloqueou >= bloqueou)
        assertTrue(aviso, aviso.contains("bloqueada"))
        val noBloqueio = ig.log.subList(bloqueou, desbloqueou)
        assertTrue(noBloqueio.toString(), noBloqueio.none { it == "voltar" || it == "abrirInstagram" })
        assertEquals(listOf("p01", "p02", "p03"), enviados())
        assertTrue(st.results.toString(), st.results.none { it.desfecho == Desfecho.FALHA })
    }

    @Test fun `tela bloqueada - as fontes nao apertam Voltar nem abrem o Instagram por cima`() = runBlocking {
        ig.visao = FakeInstagram.Visao.OUTRO_APP_NA_FRENTE
        ig.bloqueado = { true }
        assertEquals(null, FonteSeguidores(ig, prof).abrir())
        assertEquals(null, FonteConversas(ig, prof, "minhaconta").abrir())
        assertEquals(null, DmFlow(ig, prof, registro).contaAberta())
        assertEquals(ig.log.toString(), 0, ig.log.count { it == "voltar" })
    }

    // ---------------- registro e retomada ----------------

    private val M1 = "Oi! Sábado tem evento."
    private val M2 = "Começa às 20h, na praça."

    /**
     * Op1: @ana recebe (ENVIADO). Op2, com "Pular quem ja recebeu" desligado: a conversa da ana falha (temporarias,
     * rascunho...). O FALHA sobrescrevia o ENVIADO, e a op3 (opcao ligada) mandava de novo para a ana.
     */
    @Test fun `registro - desfecho que nao bloqueia nunca apaga quem ja recebeu`() {
        val enviado = "ENVIADO|1000|"
        assertEquals(enviado, com.listalocal.data.Outcomes.manter(enviado, Desfecho.FALHA))
        assertEquals(enviado, com.listalocal.data.Outcomes.manter(enviado, Desfecho.NAO_ENCONTRADO))
        assertEquals("PEDIU_PARA_PARAR|1|x", com.listalocal.data.Outcomes.manter("PEDIU_PARA_PARAR|1|x", Desfecho.NAO_SEGUE))
        assertEquals("INCERTO|1|x", com.listalocal.data.Outcomes.manter("INCERTO|1|x", Desfecho.FALHA))
        // O que bloqueia sobrescreve; sem nada antes, ou antes uma FALHA, grava o novo.
        assertEquals(null, com.listalocal.data.Outcomes.manter(enviado, Desfecho.INCERTO))
        assertEquals(null, com.listalocal.data.Outcomes.manter(null, Desfecho.FALHA))
        assertEquals(null, com.listalocal.data.Outcomes.manter("FALHA|1|x", Desfecho.ENVIADO))
    }

    /**
     * Sequencia de 2 com "Pular quem ja recebeu" desligado: o sistema religa a Acessibilidade depois da 1a mensagem da
     * ana confirmar e antes do Enviar da 2a. O DmFlow gravava ENVIADO ("1 de 2") mas a fila nao o registrava, e a
     * nova instancia do servico mandava a sequencia inteira de novo para a ana.
     */
    @Test fun `servico religado no meio da sequencia - a pessoa nao recebe a 1a mensagem de novo`() {
        seguidores("ana", "bia")
        val c = campanha(Origem.SEGUIDORES).copy(mensagens = listOf(M1, M2), modoMensagens = ModoMensagens.SEQUENCIA, pularJaRecebeu = false)
        // Depois do 1o Enviar, o botao da 2a demora (a conferencia do campo espera): o servico religa ali.
        ig.aoEsperar = { if (ig.toquesEnviar.size == 1) ig.conversas.getValue("ana").enviarHabilitado = false }
        ig.interromperApos = "escrever row_thread_composer_edittext '$M2'"
        assertThrows(Interrompido::class.java) { rodar(c) }
        assertEquals(listOf(M1), ig.conversas.getValue("ana").mensagens)
        ig.interromperApos = null
        ig.aoEsperar = {}
        ig.conversas.getValue("ana").enviarHabilitado = true
        // A nova instancia do servico: fila, fonte e fluxo novos, o mesmo estado; ninguem bloqueado pelo disco.
        runBlocking { Fila(ig) { true }.aoVivo(FonteSeguidores(ig, prof), DmFlow(ig, prof, registro), Fila.Filtro("minhaconta")) {} }
        assertEquals(listOf(M1), ig.conversas.getValue("ana").mensagens)
        assertEquals(listOf(M1, M2), ig.conversas.getValue("bia").mensagens)
        assertEquals(Desfecho.ENVIADO, st.results.single { it.username == "ana" }.desfecho)
    }

    /** Parar nos 10 s da evidencia, depois do toque em Enviar: o INCERTO gravado no disco sumia do relatorio e do CSV. */
    @Test fun `Parar depois do toque em Enviar - o envio sem confirmacao entra no relatorio`() {
        seguidores("ana", "bia")
        ig.envio = FakeInstagram.Envio.NADA
        ig.interromperApos = "tocar row_thread_composer_send_button_container"
        assertThrows(Interrompido::class.java) { rodar() }
        assertEquals(listOf("ana" to Desfecho.INCERTO), st.results.map { it.username to it.desfecho })
        assertEquals(1, st.enviados) // conta no "ate X pessoas"
    }

    /**
     * A tela bloqueada (ou uma ligacao, ou link lento) no meio: a pessoa da vez ficou FALHA. Na retomada, qualquer
     * desfecho entrava nos feitos e ela nunca mais era alcancada ("Fim da lista" encerrava a operacao).
     */
    @Test fun `retomada - quem ficou FALHA ou nao encontrado volta, quem recebeu nao`() {
        seguidores("p01", "p02", "p03", "p04")
        val antes = listOf(
            PersonResult("p01", "p01", 0, Desfecho.ENVIADO, ""),
            PersonResult("p02", "p02", 0, Desfecho.FALHA, "a conversa saiu da frente durante o intervalo"),
            PersonResult("p03", "p03", 0, Desfecho.NAO_ENCONTRADO, "a conversa não abriu em 5 s (o @ pode não existir)"),
        )
        rodar(anteriores = antes)
        assertEquals(0, ig.log.count { it == "abrir p01" })
        assertEquals(listOf("p01", "p02", "p03", "p04"), enviados().sorted())
        assertEquals("uma linha por pessoa", 4, st.results.size)
    }

    /**
     * Conversas do Direct paradas depois de algumas pessoas. No Retomar, a retomada vinha do disco so com o @ (que nao
     * bate com o nome da linha) e reabria, desde o topo, todas as conversas ja feitas (marcando como lidas).
     */
    @Test fun `conversas - retomada nao reabre a conversa de quem ja recebeu, e reabre a de quem falhou`() {
        listOf("a", "b", "c", "d").forEach { u -> ig.conversa(u) { nome = "Pessoa $u" } }
        ig.caixa += listOf("a", "b", "c", "d")
        val c = campanha(Origem.CONVERSAS).copy(inicio = 4242) // outra operacao: a memoria das linhas e so dela
        val linhas = AutomationController.linhasAbertas
        ig.interromperApos = "gravar b ENVIADO"
        assertThrows(Interrompido::class.java) {
            runBlocking {
                AutomationController.startCampanha(c)
                fila.aoVivo(FonteConversas(ig, prof, c.conta, linhas), flow, Fila.Filtro("minhaconta")) {}
            }
        }
        ig.interromperApos = null
        // O Retomar: os desfechos vem do disco (Outcomes.desde: o @ no lugar do nome); a b falhou la.
        val doDisco = listOf(PersonResult("a", "a", 0, Desfecho.ENVIADO, ""), PersonResult("b", "b", 0, Desfecho.FALHA, "x"))
        runBlocking {
            AutomationController.startCampanha(c, doDisco)
            Fila(ig) { true }.aoVivo(FonteConversas(ig, prof, c.conta, linhas), flow, Fila.Filtro("minhaconta")) {}
        }
        assertEquals(1, ig.log.count { it == "tocar linha Pessoa a" })
        assertEquals(2, ig.log.count { it == "tocar linha Pessoa b" })
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    /** Parar por engano logo depois de um envio e Retomar: o proximo saia 2 a 5 s depois, apesar da velocidade escolhida. */
    @Test fun `retomada logo depois de um envio - a 1a pessoa espera o intervalo desde ele`() {
        seguidores("ana")
        ig.relogio = 100_000
        runBlocking {
            AutomationController.startCampanha(campanha(Origem.SEGUIDORES, Velocidade.NORMAL))
            fila.aoVivo(FonteSeguidores(ig, prof), flow, Fila.Filtro("minhaconta"), ultimoEnvio = 95_000) {}
        }
        assertTrue(ig.toquesEnviar.toString(), ig.toquesEnviar.single() >= 95_000 + Velocidade.NORMAL.segundos * 1000L)
    }

    /** Parar as 22:00: parou no horario, e o Retomar as 22:05 seguia ate 22:00 do dia seguinte, a noite toda. */
    @Test fun `parar as - retomada logo depois do horario nao empurra para amanha`() {
        val z = java.time.ZoneId.of("America/Sao_Paulo")
        fun em(d: Int, h: Int, m: Int) = java.time.ZonedDateTime.of(2026, 9, d, h, m, 0, 0, z)
        val inicio = em(26, 19, 0).toInstant().toEpochMilli()
        val dez = 22 * 60
        val msg = Fila.horarioJaPassou(dez, inicio, em(26, 22, 5))
        assertTrue(msg, msg!!.contains("22:00") && msg.contains("10:00") && msg.contains("nada foi enviado"))
        assertTrue(Fila.horarioJaPassou(dez, inicio, em(27, 1, 0)) != null)
        // No dia seguinte, antes do horario: vale ate as 22:00 dele. Operacao nova: o horario de hoje.
        assertEquals(null, Fila.horarioJaPassou(dez, inicio, em(27, 20, 0)))
        assertEquals(null, Fila.horarioJaPassou(dez, em(26, 22, 30).toInstant().toEpochMilli(), em(26, 22, 30)))
        assertEquals(null, Fila.horarioJaPassou(dez, inicio, em(26, 21, 0)))
    }

    // ---------------- versao do Instagram e toque em Enviar ----------------

    /**
     * A Play Store atualiza o Instagram no meio de uma operacao longa (de noite, carregando): a fila seguia escrevendo
     * e tocando em Enviar numa versao nunca conferida.
     */
    @Test fun `Instagram atualizado no meio - para antes da proxima pessoa, para conferir e retomar`() {
        seguidores("p01", "p02", "p03")
        val f = Fila(ig, versaoMudou = { "449.0.0.1.2".takeIf { ig.toquesEnviar.size >= 1 } }) { true }
        runBlocking {
            AutomationController.startCampanha(campanha(Origem.SEGUIDORES))
            f.aoVivo(FonteSeguidores(ig, prof), flow, Fila.Filtro("minhaconta")) {}
        }
        assertEquals(listOf("p01"), enviados())
        assertEquals(0, ig.log.count { it == "abrir p02" })
        assertEquals(RunPhase.VERSION_UNSUPPORTED, st.phase)
        assertTrue(st.message, st.message.contains("449.0.0.1.2") && st.message.contains("Conferir o Instagram"))
    }

    /**
     * O toque em Enviar recusado ANTES de acontecer (ligacao, Home, tela bloqueada no instante do toque): nada foi
     * tocado. Sem a conversa legivel de volta em 10 s, virava INCERTO (bloqueado para sempre) e, na 1a pessoa, parava
     * a operacao mandando conferir se a mensagem chegou.
     */
    @Test fun `toque em Enviar recusado antes de acontecer - FALHA, nunca INCERTO, e a operacao segue`() {
        seguidores("ana", "bia")
        ig.recusarEnviarAntes = 1
        var saiuEm = -1L
        ig.aoRecusarEnviar = { saiuEm = ig.relogio; ig.visao = FakeInstagram.Visao.OUTRO_APP_NA_FRENTE } // a ligacao na frente
        ig.aoEsperar = { if (saiuEm >= 0 && ig.relogio >= saiuEm + 12_000) ig.visao = FakeInstagram.Visao.NORMAL }
        rodar()
        assertEquals(Desfecho.FALHA, st.results.first { it.username == "ana" }.desfecho)
        assertEquals(Desfecho.FALHA, registro.gravados["ana"])
        assertTrue(ig.conversas.getValue("ana").mensagens.isEmpty())
        assertEquals(listOf("bia"), enviados())
        assertEquals(RunPhase.COMPLETED, st.phase)
    }

    @Test fun `tela apagada, bloqueada ou ligacao pausam, o resto nao`() {
        assertTrue(Painel.bloqueio(false, false, android.media.AudioManager.MODE_NORMAL)!!.contains("bloqueada"))
        assertTrue(Painel.bloqueio(true, true, android.media.AudioManager.MODE_NORMAL)!!.contains("bloqueada"))
        assertTrue(Painel.bloqueio(true, false, android.media.AudioManager.MODE_IN_CALL)!!.contains("ligação"))
        assertTrue(Painel.bloqueio(true, false, android.media.AudioManager.MODE_RINGTONE)!!.contains("ligação"))
        assertEquals(null, Painel.bloqueio(true, false, android.media.AudioManager.MODE_NORMAL))
    }
}
