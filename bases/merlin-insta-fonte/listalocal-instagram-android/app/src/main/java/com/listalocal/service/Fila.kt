package com.listalocal.service

import android.util.Log
import com.listalocal.core.followers.Batch
import com.listalocal.core.followers.Follower
import com.listalocal.core.privacy.Redaction
import java.time.LocalTime

/**
 * A ordem de uma execucao, sem Android: quem vem agora, quando esperar a
 * pausa, quando esperar o intervalo, quando pedir a conferencia do lote e
 * quando parar. O servico so liga isto a tela real e ao app; os testes, a um
 * Instagram falso. O estado da execucao fica no AutomationController.
 */
class Fila(
    private val tela: Tela,
    /**
     * A versao do Instagram agora, se nao for mais a conferida (null = a mesma). A Play Store atualiza de noite, com o
     * celular carregando: a fila religava sozinha e seguia enviando numa versao nunca conferida.
     */
    private val versaoMudou: () -> String? = { null },
    /** Conferencia do operador, feita no app. false = parar. */
    private val confirmar: suspend (String) -> Boolean,
) {
    /** Parada segura pedida pela fila: a tela nao conferiu e seguir queimaria a lista. */
    class FailedSafe(msg: String) : Exception(msg)

    /** O que barra uma pessoa na operacao ao vivo. Tudo pelo @. */
    data class Filtro(
        /** A conta aberta no Instagram (a conferida). */
        val conta: String,
        /** "Nao enviar para", digitado pelo dono. */
        val naoEnviar: Set<String> = emptySet(),
        /** "So para estes @", digitado pelo dono. Vazio = todos da lista. */
        val soPara: Set<String> = emptySet(),
        /** Pediram para parar (lido pelo app nas conversas desta conta). */
        val pediramParar: Set<String> = emptySet(),
        /** Receberam ou podem ter recebido antes (gravado no aparelho): nunca de novo. */
        val jaReceberam: Set<String> = emptySet(),
        val pularComerciais: Boolean = false,
    ) {
        /** null = pode receber; "" = fora sem contar (nao esta em "So para estes @"); senao, o motivo. */
        fun motivo(a: Achado): String? {
            val u = a.username ?: return a.motivo.ifEmpty { "sem @ legível" }
            if (a.motivo.isNotEmpty()) return a.motivo
            return when {
                u == conta -> "é a própria conta"
                u in pediramParar -> "pediu para parar"
                u in naoEnviar -> "na lista Não enviar para"
                soPara.isNotEmpty() && u !in soPara -> ""
                pularComerciais && a.comercial -> "conversa comercial"
                u in jaReceberam -> "já recebeu antes"
                else -> null
            }
        }
    }

    /** Relogio do ultimo toque em Enviar: o intervalo do dono conta dali. */
    private var ultimoToque: Long? = null

    /** "Parar as HH:MM" da operacao em andamento, no relogio de [tela]: o intervalo nao passa dele. */
    private var prazo: Long? = null

    /** Houve pausa desde a ultima conferencia da conta: a proxima e firme ([Fonte.contaAgora]). */
    private var houvePausa = false

    /**
     * Modo C com a lista ao vivo: pede uma tela de pessoas a [fonte], envia a
     * cada uma que pode receber e volta a [fonte] ate o fim da lista. O
     * intervalo escolhido pelo dono fica ENTRE AS PESSOAS (de um toque em
     * Enviar ao proximo da pessoa seguinte): a proxima conversa abre logo que a
     * anterior confirma, e o resto do intervalo corre com ela aberta, antes de
     * escrever ([vez]). Nada antes do primeiro (salvo um envio ha menos de um
     * intervalo, [ultimoEnvio]) nem depois do ultimo. [ponto]
     * grava o ultimo processado (ponto de parada). Quem ja tem desfecho nesta
     * operacao (retomada: vem em state.results) nao volta.
     *
     * As escolhas do dono vem da operacao (AutomationController.campanha):
     * ate 3 mensagens em revezamento ou sequencia, "ate X pessoas", "comecar a
     * partir de @". [prazo] ("Parar as HH:MM") e no relogio de [tela].
     *
     * Antes de cada pessoa, a conta aberta no Instagram e lida de novo na
     * lista: outra conta que a da operacao pausa com aviso, e nada sai como a
     * outra conta.
     */
    suspend fun aoVivo(
        fonte: Fonte,
        flow: DmFlow,
        f: Filtro,
        prazo: Long? = null,
        /**
         * O ultimo toque em Enviar desta conta antes desta execucao, no relogio de [tela] (Parar e Retomar, servico
         * religado): a 1a pessoa espera o intervalo desde ele. Antes, saia 2 a 5 s depois do envio anterior.
         */
        ultimoEnvio: Long? = null,
        ponto: (String) -> Unit,
    ) {
        val c = AutomationController.campanha ?: throw FailedSafe("operação sem origem")
        this.prazo = prazo
        val mensagens = c.mensagens.map(String::trim).filter(String::isNotEmpty)
        if (mensagens.isEmpty()) throw FailedSafe("operação sem mensagem")
        val anteriores = AutomationController.state.value.results
        // FALHA e NAO_ENCONTRADO ([refazer]) voltam na retomada: nada saiu. O resto que ja tem desfecho nao volta.
        val feitas = anteriores.filterNot(::refazer).mapTo(HashSet()) { it.username }
        // Conversas sem @ ja puladas nesta operacao (religar o servico relê a caixa): uma vez no relatorio.
        val puladasSemArroba = anteriores.filter { it.username.isEmpty() }.mapTo(HashSet()) { it.name + it.motivo }
        // Um @ ja lido num cabecalho nesta operacao (na retomada: qualquer desfecho com @)
        // prova que a acessibilidade le a conversa: conversa sem @ depois disso e grupo.
        var leuArroba = feitas.any { it.isNotEmpty() }
        // "Comecar a partir de @": quem vem antes dele fica de fora. Em Seus seguidores tambem na retomada: a
        // lista e lida de novo do topo e quem vem antes do @ de partida nunca teve desfecho gravado. (Na caixa
        // de entrada a ordem muda a cada envio e a conversa de partida nao e reaberta: la segue como antes.)
        var comecou = c.aPartirDe == null || (anteriores.isNotEmpty() && c.origem != Origem.SEGUIDORES)
        if (limiteAtingido(c)) return
        AutomationController.setPhase(RunPhase.OPENING_SCREEN)
        val conta = abrirFonte(fonte) ?: run {
            AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return
        }
        if (conta != f.conta) {
            throw FailedSafe("A conta aberta no Instagram (@$conta) não é a conferida (@${f.conta}).")
        }
        // Dois contadores para a parada de MAX_SEM_TELA: a conversa da caixa que nao se deixa ler (zera com um @
        // lido no cabecalho) e o envio sem conversa conferida ou sem confirmacao (so zera com um ENVIADO). Juntos,
        // o @ lido de cada pessoa da caixa zerava tambem as falhas de envio, e a fila nunca parava por elas.
        var semTelaSeguidos = 0
        var falhasSeguidas = 0
        ultimoToque = ultimoEnvio
        while (true) {
            aguardarPausa()
            if (AutomationController.cancelRequested) {
                AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return
            }
            if (passouDaHora(prazo) || atualizado()) return
            if (f.soPara.isNotEmpty() && f.soPara.all { it in feitas }) break
            AutomationController.update { it.copy(phase = RunPhase.READING_LIST, atual = "") }
            // Pausa no meio da leitura recusa o toque ou a rolagem: nao e o fim nem erro.
            val lote = try {
                // O @ de partida, ja feito na retomada, volta da fonte ate ser visto: marca onde a lista comeca.
                fonte.proximas { it in feitas && (comecou || it != c.aPartirDe) }
            } catch (e: FailedSafe) {
                if (AutomationController.pauseRequested || AutomationController.cancelRequested) continue
                throw e
            }
            if (lote == null) {
                if (AutomationController.pauseRequested || AutomationController.cancelRequested) continue
                break
            }
            // O lote foi lido na conta da operacao? Lido noutra (o dono trocou de conta; a fonte reabriu a
            // lista na conta nova), ele e jogado fora: de volta a conta certa, a lista e lida de novo.
            when (mesmaConta(fonte, f)) {
                Conferida.PAROU -> { AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return }
                Conferida.VOLTOU -> continue
                Conferida.MESMA -> Unit
            }
            for (a in lote) {
                aguardarPausa()
                if (AutomationController.cancelRequested) {
                    AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return
                }
                val u = a.username
                // Da caixa de entrada, o @ foi lido no cabecalho da conversa aberta: a tela
                // conferiu, mesmo que a pessoa seja pulada ou ja feita. (Um @ da lista de
                // seguidores nao abriu conversa nenhuma: nao conta nem zera.)
                if (a.exigirNome && u != null) { leuArroba = true; semTelaSeguidos = 0 }
                // Linha sem conversa legivel (ou sem @ antes de algum @ lido) = tela nao conferida.
                val semTela = a.ilegivel || (a.semArroba && !leuArroba)
                if (!comecou) {
                    // Antes do @ de partida: fora sem contar e sem entrar no relatorio. Passar pelo proprio @
                    // de partida comeca, mesmo ja feito (na retomada ele ja tem desfecho). A tela nao conferida conta
                    // mesmo aqui: sem isso, a busca pelo @ de partida abria a caixa inteira sem ler ninguem.
                    if (u == null || u != c.aPartirDe) {
                        u?.let(feitas::add)
                        if (semTela) semTelaSeguidos = pararSeSemTela(semTelaSeguidos + 1)
                        continue
                    }
                    comecou = true
                }
                if (u != null && !feitas.add(u)) continue // repetido entre telas ou ja feito
                val motivo = f.motivo(a)
                if (motivo != null) {
                    if (motivo.isNotEmpty() && (u != null || puladasSemArroba.add(a.nome + motivo))) {
                        AutomationController.addResult(PersonResult(u.orEmpty(), a.nome, 0, Desfecho.PULADO, motivo))
                    }
                    ponto(u ?: a.nome)
                    if (semTela) semTelaSeguidos = pararSeSemTela(semTelaSeguidos + 1)
                    continue
                }
                if (limiteAtingido(c) || passouDaHora(prazo)) return
                // Revezar: a vez segue quem ja teve Enviar tocado nesta operacao (a retomada continua a roda).
                val (textos, numeros) = when (c.modoMensagens) {
                    ModoMensagens.SEQUENCIA -> mensagens to mensagens.indices.map { it + 1 }
                    ModoMensagens.REVEZAR -> {
                        val k = AutomationController.state.value.results.count { it.enviadas.isNotEmpty() } % mensagens.size
                        listOf(mensagens[k]) to listOf(k + 1)
                    }
                }
                // Pausar ou Parar no intervalo (conversa aberta, nada escrito): a mesma pessoa de novo.
                var r: DmFlow.Resultado
                while (true) {
                    if (atualizado()) return
                    // Voltou a conta certa no meio do lote: segue com ele (foi lido e conferido nela).
                    if (mesmaConta(fonte, f) == Conferida.PAROU) {
                        AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return
                    }
                    AutomationController.update { it.copy(phase = RunPhase.WORKING, atual = u!!, message = "") }
                    try {
                        r = flow.enviar(
                            Follower(u!!, a.nome), textos, a.exigirNome, ::vez, numeros = numeros, nossos = mensagens,
                            parado = ::parado,
                        )
                        break
                    } catch (_: Adiado) {
                        aguardarPausa()
                        if (AutomationController.cancelRequested) {
                            AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return
                        }
                        if (passouDaHora(prazo)) return
                    } catch (e: Exception) {
                        // Parar, prazo ou servico religado depois de algo escrito ou tocado: o desfecho ja gravado no
                        // aparelho entra no relatorio (e no CSV e no limite) e em state.results, de onde a instancia
                        // religada do servico tira quem ja foi feito: sem isso, a 1a mensagem de uma sequencia saia de
                        // novo com "Pular quem ja recebeu" desligado.
                        flow.interrompida?.let { g -> registrar(u!!, a.nome, g) }
                        throw e
                    }
                }
                r.tocouEm?.let { ultimoToque = it }
                registrar(u!!, a.nome, r)
                ponto(u)
                Log.i(TAG, "pessoa ${Redaction.mask(u)}: ${r.desfecho} trilha=${r.trilha}")
                if (r.restricao) {
                    AutomationController.pause()
                    AutomationController.setPhase(
                        RunPhase.RESTRICTED,
                        "O Instagram mostrou um aviso de restrição. A fila pausou; só continue se tiver certeza.",
                    )
                    continue
                }
                // O primeiro envio confirmado prova, neste aparelho, a leitura da conversa
                // inteira (cabecalho, campo, Enviar e bolhas). Antes dele, um envio sem
                // confirmacao para: se a tela nao confirma nada, o resto da lista nao pode
                // virar INCERTO (bloqueado para sempre) sem o dono conferir.
                // (Na sequencia, uma mensagem confirmada antes desta ja provou a leitura.)
                if (r.desfecho == Desfecho.INCERTO && r.enviadas.size <= 1 &&
                    AutomationController.state.value.results.none { it.desfecho == Desfecho.ENVIADO }
                ) {
                    throw FailedSafe(
                        "O envio para @$u ficou sem confirmação na tela. Confira no Instagram se a mensagem " +
                            "chegou antes de continuar (essa pessoa não recebe de novo).",
                    )
                }
                if (limiteAtingido(c)) return
                falhasSeguidas = when {
                    r.desfecho == Desfecho.ENVIADO -> 0
                    // Recusa por regra (temporarias, rascunho, nao enviada antiga): a tela foi lida e conferida.
                    r.recusada -> falhasSeguidas
                    r.desfecho in FALHAS_SEGUIDAS -> pararSeSemTela(falhasSeguidas + 1)
                    else -> falhasSeguidas // pediu para parar, nao segue, indisponivel: nem conta nem prova o envio
                }
            }
        }
        if (!comecou) {
            val nada = if (anteriores.isEmpty()) "Nada foi enviado." else "Nada mais foi enviado."
            throw FailedSafe("@${c.aPartirDe} não apareceu em ${c.origem.titulo}. $nada")
        }
        AutomationController.finish(RunPhase.COMPLETED, "Fim da lista.")
    }

    /**
     * Abre a fonte. Pausar enquanto ela abre recusa o toque (NodeOps) e a abertura falha: e a pausa,
     * nao "a lista nao abriu". Espera o Continuar e abre de novo. null = o dono tocou em Parar.
     */
    private suspend fun abrirFonte(fonte: Fonte): String? {
        while (true) {
            val conta = try {
                fonte.abrir()
            } catch (e: FailedSafe) {
                if (parado()) null else throw e
            }
            if (conta != null) return conta
            if (!parado()) throw FailedSafe("A lista não abriu no Instagram.")
            aguardarPausa()
            if (AutomationController.cancelRequested) return null
        }
    }

    /** "Enviar para ate X pessoas": chegou, termina (nada a retomar). */
    private fun limiteAtingido(c: Campanha): Boolean {
        val limite = c.limite ?: return false
        if (AutomationController.state.value.enviados < limite) return false
        AutomationController.finish(RunPhase.COMPLETED, "Chegou ao limite: $limite ${if (limite == 1) "pessoa" else "pessoas"}.")
        return true
    }

    /**
     * O Instagram foi atualizado no meio: para antes da proxima pessoa, com a operacao salva. A conferencia na tela
     * real e o que autoriza o modo numa versao; depois dela, o Retomar segue de onde parou.
     */
    private fun atualizado(): Boolean {
        val nova = versaoMudou() ?: return false
        AutomationController.finish(
            RunPhase.VERSION_UNSUPPORTED,
            "O Instagram foi atualizado (versão $nova) no meio da operação; nada mais foi enviado. Toque em " +
                "\"Conferir o Instagram\" e depois em \"Retomar de onde parou\".",
        )
        return true
    }

    /** "Parar as HH:MM": passou da hora, para como um Parar (a operacao fica para retomar). */
    private fun passouDaHora(prazo: Long?): Boolean {
        if (prazo == null || tela.agora() < prazo) return false
        AutomationController.finish(RunPhase.CANCELLED, "Parou no horário escolhido. Dá para retomar depois.")
        return true
    }

    private enum class Conferida { MESMA, VOLTOU, PAROU }

    /**
     * A conta aberta no Instagram ainda e a da operacao? Outra conta: pausa
     * com aviso e le de novo depois de Continuar, ate voltar a conta certa
     * ([Conferida.VOLTOU]). Nada sai como a outra conta. Depois de qualquer
     * pausa a leitura e firme: na pausa o dono pode ter aberto, noutra conta,
     * a lista de seguidores da conta da operacao (o titulo dela engana).
     * [Conferida.PAROU] = o dono tocou em Parar.
     */
    private suspend fun mesmaConta(fonte: Fonte, f: Filtro): Conferida {
        var trocou = false
        while (true) {
            if (AutomationController.cancelRequested) return Conferida.PAROU
            val viva = try {
                fonte.contaAgora(firme = houvePausa)
            } catch (e: FailedSafe) {
                if (parado()) null else throw e
            }
            if (viva == f.conta) {
                houvePausa = false
                return if (trocou) Conferida.VOLTOU else Conferida.MESMA
            }
            if (viva == null) {
                if (!parado()) throw FailedSafe(Conta.SEM_CONTA)
            } else {
                trocou = true
                AutomationController.pause()
                AutomationController.setPhase(RunPhase.PAUSED, Conta.trocada(f.conta, viva))
            }
            aguardarPausa()
        }
    }

    /**
     * Tres pessoas seguidas sem conversa legivel ou sem confirmacao do envio:
     * a tela mudou ou a acessibilidade nao enxerga a conversa. Parar em vez de
     * queimar a lista.
     */
    private fun pararSeSemTela(n: Int): Int {
        if (n >= MAX_SEM_TELA) {
            throw FailedSafe(
                "$MAX_SEM_TELA pessoas seguidas sem conversa conferida ou sem confirmação do envio. " +
                    "Confira o Instagram de novo antes de continuar.",
            )
        }
        return n
    }

    /** Modo A: marca todos na tela de Amigos Proximos, tira quem pediu para parar e so conclui com o dono. */
    suspend fun amigos(plan: List<Batch>, flow: CloseFriendsFlow, conferida: String?) {
        AutomationController.setPhase(RunPhase.OPENING_SCREEN)
        if (!abrirAmigos(flow)) return
        // A conta aberta agora e a da operacao. Outra que a conferida so com tudo conferido nela nesta abertura; senao,
        // nenhum toque. (Antes: trocar de conta pelo Instagram parava dizendo que a conferencia dela "nao passou", sem
        // ela nem ter sido tentada; so o Modo DM reconferia.)
        val conferidaAgora = com.listalocal.core.selectors.SelectorProfile.CF_KEYS.all { flow.encontradas[it] == true }
        Conta.amigosNaConta(conferida, flow.conta, conferidaAgora)?.let { throw FailedSafe(it) }
        val viva = flow.conta ?: throw FailedSafe(Conta.SEM_CONTA)
        // Retomada numa conta diferente da que a operacao comecou: os desfechos eram da lista da outra.
        AutomationController.update { s ->
            s.copy(conta = viva, results = if (s.conta != null && s.conta != viva) emptyList() else s.results)
        }
        val lote = plan.firstOrNull()?.index ?: 1
        AutomationController.update { it.copy(currentListIndex = lote) }
        // @ -> (nome, entrar). Quem pediu para parar sai sempre, mesmo estando no plano (antes: marcado e desmarcado).
        val alvos = LinkedHashMap<String, Pair<String, Boolean>>()
        plan.flatMap { it.followers }.forEach { alvos.putIfAbsent(it.username, it.name to true) }
        AutomationController.remover.forEach { u -> alvos[u] = (alvos[u]?.first ?: u) to false }
        // Todos, tambem na retomada (servico religado): a tela reabre sem Concluir e as marcas de antes podem ter se
        // perdido (nao medido se gravam sem Concluir). marcar() le a marca numa busca assentada e so toca quando ela
        // nao esta como deveria: refazer quem ja tem desfecho nao desfaz nada. Antes pulava quem ja tinha desfecho.
        val lista = alvos.entries.toList()
        // Fora da tela Amigos Proximos (o dono mexeu no Instagram na pausa, ou ela saiu da frente): reabre pelo Perfil,
        // so na mesma conta, e confere todos de novo (sair sem Concluir pode perder as marcas; marcar e idempotente).
        var reaberturas = 0
        suspend fun reabrir(): Boolean {
            if (++reaberturas > MAX_REABRIR) throw CloseFriendsFlow.ParadaSegura(CloseFriendsFlow.FORA_DA_TELA)
            return reabrirAmigos(flow, viva)
        }
        var i = 0
        while (true) {
            if (i == lista.size) {
                // Antes do Concluir, a tela provada na frente (uma pausa no meio da ultima pessoa, por exemplo).
                if (flow.naFrente()) break
                if (!reabrir()) return
                i = 0
                continue
            }
            val (u, alvo) = lista[i]
            val (nome, entrar) = alvo
            if (aguardarPausa() && !AutomationController.cancelRequested && !flow.naFrente()) {
                if (!reabrir()) return
                i = 0
                continue
            }
            if (AutomationController.cancelRequested) {
                AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono; Concluir não foi tocado"); return
            }
            AutomationController.update { it.copy(phase = RunPhase.WORKING, atual = u) }
            val d = try {
                flow.marcar(u, adicionar = entrar, parado = ::parado)
            } catch (_: Adiado) {
                continue // Pausar/Parar antes do toque: a MESMA pessoa depois da pausa
            } catch (_: CloseFriendsFlow.ForaDaTela) {
                if (!reabrir()) return
                i = 0
                continue
            }
            anotar(u, nome, lote, entrar, d)
            i++
        }
        flow.prontoParaConcluir()
        val semProva = AutomationController.state.value.results.filter { it.motivo == PAROU_SEM_PROVA }.map { "@${it.username}" }
        val aviso = if (semProva.isEmpty()) "" else
            " Pediram para parar e não consegui tirar pela busca: ${semProva.joinToString()}. Confira se saíram da lista."
        val ok = confirmar("Tocar em Concluir para gravar a lista Amigos Próximos de @$viva?$aviso")
        if (!ok) {
            AutomationController.finish(RunPhase.CANCELLED, "Concluir não foi tocado. Confira a lista em Amigos Próximos.")
            return
        }
        aguardarPausa()
        if (AutomationController.cancelRequested) {
            AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono; Concluir não foi tocado"); return
        }
        AutomationController.setPhase(RunPhase.SAVING)
        if (!AutomationController.autoConfirm) tela.abrirInstagram() // volta a tela de Amigos Proximos
        flow.concluir()
        AutomationController.update { st -> st.copy(lists = st.lists.map { it.copy(done = true) }) }
        AutomationController.finish(
            RunPhase.COMPLETED, "Lista gravada.$aviso Publique um story e escolha Amigos Próximos.",
        )
    }

    /**
     * Abre Amigos Proximos. Pausar enquanto ela abre recusa os toques e os gestos (NodeOps) e a abertura falhava com
     * "Configuracoes nao abriu" ou "rolagem recusada": e a pausa. Espera o Continuar e abre de novo do comeco (nada foi
     * marcado ainda). false = o dono tocou em Parar.
     */
    private suspend fun abrirAmigos(flow: CloseFriendsFlow): Boolean {
        while (true) {
            aguardarPausa()
            if (AutomationController.cancelRequested) {
                AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono; Concluir não foi tocado"); return false
            }
            try {
                flow.abrir()
                return true
            } catch (e: CloseFriendsFlow.ParadaSegura) {
                if (!parado()) throw e
            } catch (e: FailedSafe) {
                if (!parado()) throw e
            }
            flow.reiniciar()
        }
    }

    /** Depois de uma pausa fora da tela: abre de novo e so segue na MESMA conta. false = Parar. */
    private suspend fun reabrirAmigos(flow: CloseFriendsFlow, viva: String): Boolean {
        AutomationController.setPhase(RunPhase.OPENING_SCREEN, "reabrindo Amigos Próximos depois da pausa…")
        flow.reiniciar()
        if (!abrirAmigos(flow)) return false
        if (flow.conta != viva) {
            throw FailedSafe(
                "Depois da pausa o Instagram está na conta @${flow.conta ?: "?"}, e esta lista é de @$viva. Nada foi " +
                    "marcado nela e Concluir não foi tocado. Volte para @$viva no Instagram e toque em Iniciar de novo.",
            )
        }
        return true
    }

    /**
     * Amigos Proximos: um desfecho por @. Refeita na retomada, vale o de agora, menos quando nao toca em nada: o
     * ADICIONADO/REMOVIDO de antes fica (nunca rebaixado a FALHA, como no Direct). Confirmado ("ja estava", ou nada a
     * tirar), como era; sem resposta (busca que nao assentou, nao achada, ambigua), com o motivo pedindo para conferir,
     * e quem pediu para parar continua no aviso do Concluir (a marca de antes pode ter se perdido na reabertura).
     * null = nao estava na lista para sair. Nao achada, ambigua ou sem resposta na remocao entra no relatorio (antes:
     * sem desfecho e "Lista gravada").
     */
    private fun anotar(u: String, nome: String, lote: Int, entrar: Boolean, d: Desfecho?) {
        fun motivo(d: Desfecho?) = when {
            d == null -> ""
            entrar -> if (d == Desfecho.FALHA) BUSCA_SEM_RESPOSTA else ""
            d == Desfecho.REMOVIDO -> "pediu para parar"
            else -> PAROU_SEM_PROVA
        }
        fun tocou(d: Desfecho?) = d == Desfecho.ADICIONADO || d == Desfecho.REMOVIDO
        AutomationController.update { s ->
            val antes = s.results.firstOrNull { it.username == u }
            if (antes != null && tocou(antes.desfecho) && !tocou(d)) {
                val confirma = d == null || d == Desfecho.JA_NA_LISTA
                val m = if (confirma) motivo(antes.desfecho) else if (entrar) SEM_RECONFERIR else PAROU_SEM_PROVA
                s.copy(results = s.results.map { if (it === antes) it.copy(motivo = m) else it })
            } else {
                s.copy(results = s.results.filterNot { it.username == u } + listOfNotNull(d?.let { PersonResult(u, nome, lote, it, motivo(it)) }))
            }
        }
    }

    private fun parado() = AutomationController.pauseRequested || AutomationController.cancelRequested

    /** Uma linha por pessoa: a de antes (retomada de quem volta, [refazer]) sai. */
    private fun registrar(u: String, nome: String, r: DmFlow.Resultado) = AutomationController.update { s ->
        s.copy(results = s.results.filterNot { it.username == u && refazer(it) } + PersonResult(u, nome, 0, r.desfecho, r.motivo, r.enviadas))
    }

    /** true = havia pausa e ela passou (o dono pode ter mexido no Instagram nesse tempo). */
    private suspend fun aguardarPausa(): Boolean {
        if (!AutomationController.pauseRequested) return false
        houvePausa = true
        val antes = AutomationController.state.value.phase
        // Restricao e conta trocada ja pausaram com o aviso delas: fica o aviso. Tela bloqueada e ligacao pausam com o
        // motivo (a fila pode ter mudado a fase depois).
        if (antes != RunPhase.RESTRICTED && antes != RunPhase.PAUSED) {
            AutomationController.setPhase(RunPhase.PAUSED, AutomationController.motivoPausa.ifEmpty { "pausado pelo dono" })
        }
        while (AutomationController.pauseRequested) {
            tela.esperar(400)
        }
        AutomationController.setPhase(RunPhase.WORKING)
        return true
    }

    /**
     * A vez da pessoa: o intervalo fixo do dono desde o ultimo toque em Enviar,
     * contado no relogio (abrir e provar a conversa ja contam). true = esperou.
     * Em passos curtos para ouvir Pausar e Parar: qualquer um dos dois, aqui
     * ou ja pedido ao abrir a conversa, adia a pessoa ([Adiado]) sem nada
     * escrito, em vez de deixar o toque no campo ser recusado (falha).
     */
    private suspend fun vez(): Boolean {
        if (AutomationController.pauseRequested || AutomationController.cancelRequested) throw Adiado()
        // A hora de parar chega no meio do intervalo: a pessoa fica sem nada escrito.
        val prazo = prazo
        fun passou() = prazo != null && tela.agora() >= prazo
        val desde = ultimoToque ?: return false
        fun falta() = desde + AutomationController.intervaloMs - tela.agora()
        if (falta() <= 0) return false
        AutomationController.update { it.copy(phase = RunPhase.WAITING_INTERVAL) }
        while (falta() > 0) {
            if (AutomationController.pauseRequested || AutomationController.cancelRequested || passou()) throw Adiado()
            try {
                tela.esperar(minOf(falta(), 250L))
            } catch (e: Interrompido) {
                if (AutomationController.cancelRequested) throw Adiado() else throw e
            }
        }
        AutomationController.update { it.copy(phase = RunPhase.WORKING) }
        return true
    }

    /**
     * Modo C pelo WhatsApp: usa [WaDmFlow.enviarWa] em vez de [DmFlow].
     * Lógica simplificada: sem verificação de conta, sem aviso de restrição do Instagram.
     */
    suspend fun aoVivoWa(
        fonte: Fonte,
        flow: WaDmFlow,
        f: Filtro,
        prazo: Long? = null,
        ultimoEnvio: Long? = null,
        ponto: (String) -> Unit,
    ) {
        val c = AutomationController.campanha ?: throw FailedSafe("operação sem origem")
        this.prazo = prazo
        val mensagens = c.mensagens.map(String::trim).filter(String::isNotEmpty)
        if (mensagens.isEmpty()) throw FailedSafe("operação sem mensagem")
        val anteriores = AutomationController.state.value.results
        val feitas = anteriores.filterNot(::refazer).mapTo(HashSet()) { it.username }
        if (limiteAtingido(c)) return
        AutomationController.setPhase(RunPhase.OPENING_SCREEN)
        abrirFonte(fonte) ?: run { AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return }
        ultimoToque = ultimoEnvio
        while (true) {
            aguardarPausa()
            if (AutomationController.cancelRequested) { AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return }
            if (passouDaHora(prazo) || atualizado()) return
            AutomationController.update { it.copy(phase = RunPhase.READING_LIST, atual = "") }
            val lote = try {
                fonte.proximas { it in feitas }
            } catch (e: FailedSafe) {
                if (AutomationController.pauseRequested || AutomationController.cancelRequested) continue
                throw e
            }
            if (lote == null) {
                if (AutomationController.pauseRequested || AutomationController.cancelRequested) continue
                break
            }
            for (a in lote) {
                aguardarPausa()
                if (AutomationController.cancelRequested) { AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return }
                val u = a.username ?: continue
                if (!feitas.add(u)) continue
                val motivo = f.motivo(a)
                if (motivo != null) {
                    if (motivo.isNotEmpty()) {
                        AutomationController.addResult(PersonResult(u, a.nome, 0, Desfecho.PULADO, motivo))
                    }
                    ponto(u)
                    continue
                }
                if (limiteAtingido(c) || passouDaHora(prazo)) return
                val (textos, numeros) = when (c.modoMensagens) {
                    ModoMensagens.SEQUENCIA -> mensagens to mensagens.indices.map { it + 1 }
                    ModoMensagens.REVEZAR -> {
                        val k = AutomationController.state.value.results.count { it.enviadas.isNotEmpty() } % mensagens.size
                        listOf(mensagens[k]) to listOf(k + 1)
                    }
                }
                var resultado: WaDmFlow.WaResult
                while (true) {
                    if (atualizado()) return
                    AutomationController.update { it.copy(phase = RunPhase.WORKING, atual = u, message = "") }
                    try {
                        resultado = flow.enviarWa(u, a.nome, textos.first(), ::vez, ::parado)
                        break
                    } catch (_: Adiado) {
                        aguardarPausa()
                        if (AutomationController.cancelRequested) { AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo dono"); return }
                        if (passouDaHora(prazo)) return
                    }
                }
                resultado.tocouEm?.let { ultimoToque = it }
                AutomationController.update { s ->
                    s.copy(results = s.results.filterNot { it.username == u && refazer(it) } +
                        PersonResult(u, a.nome, 1, resultado.desfecho, resultado.motivo, resultado.enviadas))
                }
                ponto(u)
                Log.i(TAG, "WA ${Redaction.mask(u)}: ${resultado.desfecho}")
                if (limiteAtingido(c)) return
            }
        }
        AutomationController.finish(RunPhase.COMPLETED, "Fim da lista.")
    }

    companion object {
        /** Quanto falta para [minutoDoDia] (0..1439) a partir de [agora]; ja passou hoje = amanha. */
        fun msAte(minutoDoDia: Int, agora: LocalTime): Long {
            val dia = 86_400_000L
            val d = minutoDoDia * 60_000L - agora.toNanoOfDay() / 1_000_000L
            return if (d <= 0) d + dia else d
        }

        /**
         * "Parar as HH:MM" numa retomada: o horario ja passou nesta operacao (depois do [inicio]) ha menos de 12 h. O
         * proximo seria amanha, e a operacao enviaria a noite toda, ignorando o limite escolhido (antes: Retomar as
         * 22:05, depois de parar as 22:00, seguia ate 22:00 do dia seguinte). Devolve o que dizer ao dono; null = segue.
         * ponytail: 12 h depois do horario = meio dia; retomar depois disso vale ate o proximo horario.
         */
        fun horarioJaPassou(pararAs: Int, inicio: Long, agora: java.time.ZonedDateTime): String? {
            val hhmm = "%02d:%02d".format(pararAs / 60, pararAs % 60)
            var ultimo = agora.toLocalDate().atTime(pararAs / 60, pararAs % 60).atZone(agora.zone)
            if (ultimo.isAfter(agora)) ultimo = ultimo.minusDays(1)
            if (ultimo.toInstant().toEpochMilli() <= inicio) return null
            val libera = ultimo.plusHours(12)
            if (!agora.isBefore(libera)) return null
            return "Esta operação parou às $hhmm, o horário escolhido. Retomar agora enviaria até $hhmm de amanhã; " +
                "nada foi enviado. Retome a partir das %02d:%02d, ou comece uma operação nova com outro horário (quem já recebeu é pulado)."
                    .format(libera.hour, libera.minute)
        }

        private const val TAG = "ListaLocalIG"
        const val MAX_SEM_TELA = 3
        /** Amigos Proximos: quantas vezes a tela perdida e reaberta numa operacao antes de parar. */
        const val MAX_REABRIR = 3
        /** Amigos Proximos: a busca pelo @ nao assentou (servidor lento, lista mudando): nenhum toque. */
        const val BUSCA_SEM_RESPOSTA = "a busca pelo @ não assentou; nada foi tocado"
        /** Amigos Proximos: pediu para parar e a busca nao provou que saiu da lista. */
        const val PAROU_SEM_PROVA = "pediu para parar; não consegui tirar pela busca: confira se saiu da lista"
        /** Amigos Proximos: marcado numa passada anterior; a tela reaberta nao confirmou pela busca (nada tocado). */
        const val SEM_RECONFERIR = "marcado antes de a tela reabrir; a busca de agora não confirmou: confira se está na lista"
        /**
         * Desfecho de antes que a retomada refaz: FALHA e NAO_ENCONTRADO, em que com certeza nada saiu (antes do
         * commit tudo e FALHA; o toque recusado com prova tambem). Antes so a conversa nao lida voltava: uma falha
         * passageira (tela bloqueada, ligacao, link lento) virava exclusao calada, e o "Fim da lista" encerrava a
         * operacao sem essas pessoas. Uma falha por regra (temporarias, rascunho) e so conferida de novo.
         */
        fun refazer(r: PersonResult) = r.desfecho == Desfecho.FALHA || r.desfecho == Desfecho.NAO_ENCONTRADO
        /** Desfechos que contam para a parada de [MAX_SEM_TELA]: a conversa nao conferiu ou o envio nao confirmou. */
        private val FALHAS_SEGUIDAS = setOf(Desfecho.FALHA, Desfecho.NAO_ENCONTRADO, Desfecho.INCERTO)
    }
}
