package com.listalocal.service

import com.listalocal.core.followers.Follower
import com.listalocal.core.selectors.SelectorProfile
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Medida local, sem celular: quantas leituras da arvore inteira (varreduras),
 * quantas esperas e quanto tempo de relogio cada pessoa custa sobre o
 * Instagram falso. Tempos do falso: a conversa aparece 1,7 s depois do link
 * (ig.me medido: 1,6 a 2,7 s) e a bolha 0,4 s depois do toque em Enviar
 * (suposicao); a rolagem da lista anda por 0,2 s (AbsListView: 200 ms).
 * A leitura em si nao custa relogio aqui: o custo dela no
 * aparelho e contado a parte, em leituras.
 *
 * Imprime "DESEMPENHO|..." (vai para OTIMIZACAO.md) e trava os tetos medidos
 * depois da otimizacao: uma volta das varreduras ou das esperas falha aqui.
 */
class DesempenhoTest {

    private val prof = SelectorProfile.IG_448
    private val texto = "Oi! Sábado tem evento. Responda PARE para não receber mais."

    /** Esperas fora do intervalo do dono (o intervalo espera em passos de 250 ms para ouvir Pausar). */
    private var esperasDeTela = 0

    private fun novo() = FakeInstagram().apply {
        atrasoAbrir = 1_700
        atrasoBolha = 400
        animacaoRolagem = 200
        aoEsperar = { if (AutomationController.state.value.phase != RunPhase.WAITING_INTERVAL) esperasDeTela++ }
    }

    /** Leituras e esperas de tela por pessoa. */
    private fun linha(cenario: String, ig: FakeInstagram, pessoas: Int): Pair<Double, Double> {
        val t = ig.toquesEnviar
        val entre = t.zipWithNext { a, b -> b - a }
        println(
            "DESEMPENHO|$cenario|pessoas=$pessoas" +
                "|leituras/pessoa=${"%.1f".format(ig.leituras.toDouble() / pessoas)}" +
                "|esperas de tela/pessoa=${"%.1f".format(esperasDeTela.toDouble() / pessoas)}" +
                "|buscas por id/pessoa=${"%.1f".format(ig.buscas.toDouble() / pessoas)}" +
                "|ms/pessoa=${ig.relogio / pessoas}" +
                "|entre toques ms=${entre.minOrNull() ?: 0}..${entre.maxOrNull() ?: 0}",
        )
        return ig.leituras.toDouble() / pessoas to esperasDeTela.toDouble() / pessoas
    }

    private fun teto(medido: Pair<Double, Double>, leituras: Double, esperas: Double) {
        check(medido.first <= leituras && medido.second <= esperas) { "acima do teto: $medido" }
    }

    @Test fun `uma pessoa pelo link`() = runBlocking {
        AutomationController.update { RunState() }
        val ig = novo()
        ig.conversa("ana.souza") { nome = "Ana" }
        val r = DmFlow(ig, prof, FakeRegistro(ig.log)).enviar(Follower("ana.souza", "Ana"), texto)
        check(r.desfecho == Desfecho.ENVIADO) { r.motivo }
        teto(linha("dm", ig, 1), leituras = 5.0, esperas = 5.0) // antes: 16 e 12
        check(ig.relogio <= 3_800) { "${ig.relogio}" } // antes: 4 250 ms
    }

    private fun campanha(origem: Origem, v: Velocidade) =
        Campanha(conta = "minhaconta", origem = origem, mensagens = listOf(texto), velocidade = v, inicio = 0)

    private fun fila(ig: FakeInstagram, origem: Origem, v: Velocidade, anteriores: List<PersonResult> = emptyList()) = runBlocking {
        AutomationController.startCampanha(campanha(origem, v), anteriores)
        val fonte = if (origem == Origem.SEGUIDORES) FonteSeguidores(ig, prof) else FonteConversas(ig, prof)
        Fila(ig) { true }.aoVivo(fonte, DmFlow(ig, prof, FakeRegistro(ig.log)), Fila.Filtro("minhaconta")) {}
        check(AutomationController.state.value.phase == RunPhase.COMPLETED) { AutomationController.state.value.message }
    }

    private fun seguidores(ig: FakeInstagram, n: Int) = (1..n).map { "p%03d".format(it) }.onEach { u ->
        ig.seguidores += u to "Nome $u"
        ig.conversa(u) { nome = "Nome $u" }
    }

    @Test fun `seguidores ao vivo`() {
        for (v in listOf(Velocidade.MUITO_RAPIDO, Velocidade.RAPIDO, Velocidade.NORMAL)) {
            esperasDeTela = 0
            val ig = novo()
            seguidores(ig, 20)
            fila(ig, Origem.SEGUIDORES, v)
            check(ig.toquesEnviar.size == 20)
            teto(linha("seguidores ${v.segundos}s", ig, 20), leituras = 7.0, esperas = 7.0) // antes: 16,9 e 12,4
            // O intervalo do dono e o espaco entre dois envios, nao somado ao trabalho de cada um.
            if (v != Velocidade.MUITO_RAPIDO) {
                check(ig.toquesEnviar.zipWithNext { a, b -> b - a }.all { it in v.segundos * 1_000L..v.segundos * 1_000L + 500 })
            }
        }
    }

    @Test fun `seguidores ao vivo num aparelho que nao entrega eventos`() {
        val ig = novo().apply { semEventos = true }
        seguidores(ig, 20)
        fila(ig, Origem.SEGUIDORES, Velocidade.MUITO_RAPIDO)
        check(ig.toquesEnviar.size == 20)
        // Sem eventos, espera fixa de 250 ms como antes: nada pior que antes.
        teto(linha("seguidores 1s sem eventos", ig, 20), leituras = 17.0, esperas = 13.0)
    }

    @Test fun `conversas do Direct ao vivo`() {
        val ig = novo()
        val chaves = (1..12).map { "c%02d".format(it) }
        chaves.forEach { c -> ig.conversa(c) { nome = "Pessoa $c" } }
        ig.caixa += chaves
        fila(ig, Origem.CONVERSAS, Velocidade.MUITO_RAPIDO)
        check(ig.toquesEnviar.size == 12)
        // +1 leitura e ~1 espera por pessoa: a caixa assenta antes do toque na linha (no velho abria outra conversa).
        teto(linha("conversas 1s", ig, 12), leituras = 9.0, esperas = 11.0) // antes: 27,3 e 20,1; sem assentar, 8 e 10
    }

    @Test fun `amigos proximos`() = runBlocking {
        AutomationController.update { RunState() }
        val ig = novo()
        val us = (1..10).map { "a%02d".format(it) }
        us.forEach { ig.amigos[it] = false }
        val flow = CloseFriendsFlow(ig, prof)
        flow.abrir()
        // Abrir e uma vez so: arrasta Configuracoes por gesto e le com a tela parada (t01a-t01d).
        println("DESEMPENHO|amigos proximos abrir|leituras=${ig.leituras}|esperas de tela=$esperasDeTela")
        // +1 leitura e +1 espera: a tela de Amigos Proximos so e medida parada (linhas e cabecalho vem do servidor).
        check(ig.leituras <= 11 && esperasDeTela <= 7) { "abrir: ${ig.leituras} leituras, $esperasDeTela esperas" }
        ig.leituras = 0
        esperasDeTela = 0
        us.forEach { check(flow.marcar(it, adicionar = true) == Desfecho.ADICIONADO) }
        check(flow.marcar("ninguem", adicionar = true) == Desfecho.NAO_ENCONTRADO) // "nao achou" so com a lista parada
        // Esperas por evento (antes, espera fixa de 200 ms): sem evento, a arvore nao e copiada de novo.
        // +0,5 leitura e +0,2 espera: a busca so decide com uma leitura NOVA 400 ms depois da primeira igual (a lista
        // inicial mostra os membros desmarcados, t02a real), e "nao achou" espera 3 s (busca no servidor).
        teto(linha("amigos proximos (por pessoa, sem abrir)", ig, 11), leituras = 4.2, esperas = 1.3) // antes: 3,6 e 1,0
    }

    @Test fun `retomada depois de 280 feitos`() {
        val ig = novo()
        val us = seguidores(ig, 300)
        val feitos = us.take(280).map { PersonResult(it, it, 0, Desfecho.ENVIADO, "") }
        fila(ig, Origem.SEGUIDORES, Velocidade.MUITO_RAPIDO, feitos)
        val ateOPrimeiro = ig.toquesEnviar.first()
        println(
            "DESEMPENHO|retomada 280 de 300|ate o 1o envio: ms=$ateOPrimeiro leituras=${ig.leiturasNosToques.first()}" +
                "|rolagens=${ig.rolagensDaLista}",
        )
        linha("retomada 280 de 300 (20 pessoas)", ig, 20)
        check(ateOPrimeiro <= 14_000) { "$ateOPrimeiro" } // antes: 21 500 ms
    }
}
