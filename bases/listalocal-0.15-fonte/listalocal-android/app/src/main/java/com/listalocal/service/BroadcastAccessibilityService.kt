package com.listalocal.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.listalocal.BuildConfig
import com.listalocal.core.contacts.Batch
import com.listalocal.core.privacy.Redaction
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.selectors.TargetApp
import com.listalocal.data.OneShotGate
import com.listalocal.expiry.ExpiryGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Motor on-device. Restrito ao WhatsApp e ao WhatsApp Business (ver TargetApp).
 * Traduz a arvore de nos em acoes,
 * dirigindo o fluxo "Nova transmissao" e criando as listas — o mesmo fluxo
 * validado no spike Python, agora em Kotlin.
 *
 * Seguranca: nunca envia mensagem, nunca abre conversa, nunca toca no compositor;
 * so seleciona correspondencia EXATA e UNICA; para em UI desconhecida
 * (FAILED_SAFE); versao sem perfil validado => pausa; criacao exige confirmacao
 * (a menos que autoConfirm). Logs mascarados.
 */
class BroadcastAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var ops: NodeOps
    private var running = false
    /** Campo de busca do seletor, reaproveitado entre contatos (uma consulta a menos). */
    private var searchBox: AccessibilityNodeInfo? = null
    /** Cota mensal lida na ultima passagem pela tela "Listas de transmissao". */
    private var quotaMensal: QuotaMensal? = null
    /** Ultima consulta digitada e se ela terminou sem linhas (decide se limpamos o campo). */
    private var ultimaConsulta: String? = null
    private var ultimoResultadoVazio: Boolean = false
    @Volatile private var disabledForExpiry = false
    @Volatile private var disabledForOneShot = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (OneShotGate.isConsumed(this)) {
            stopForOneShot()
            return
        }
        if (ExpiryGate.isExpired(this)) {
            expireService()
            return
        }
        ops = NodeOps(this, AutomationController.targetApp.packageName)
        val app = AutomationController.targetApp
        val version = appVersion(app)
        AutomationController.attach(true)
        AutomationController.update { it.copy(whatsAppVersion = version ?: "?", targetApp = app) }
        if (OneShotGate.isAwaitingVerification(this) &&
            AutomationController.state.value.phase == RunPhase.IDLE) {
            AutomationController.update {
                it.copy(phase = RunPhase.COMPLETED,
                    message = "Confira no WhatsApp se todas as listas foram criadas.")
            }
        }
        scope.launch { watchLoop() }
        // Independente do runner: interrompe inclusive espera de confirmacao ou pausa
        // se o dia virar enquanto o app alvo esta aberto.
        if (ExpiryGate.enabled) scope.launch {
            while (isActive) {
                if (ExpiryGate.isExpired(this@BroadcastAccessibilityService)) {
                    expireService()
                    return@launch
                }
                delay(250)
            }
        }
        Log.i(TAG, "servico conectado; alvo=${app.packageName} versao=${version ?: "?"}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* dirigido pelo runner */ }
    override fun onInterrupt() { AutomationController.pause() }

    override fun onUnbind(intent: Intent?): Boolean {
        AutomationController.attach(false)
        scope.cancel()
        return super.onUnbind(intent)
    }

    // -------------------- loop principal --------------------

    private suspend fun watchLoop() {
        while (scope.isActive) {
            if (OneShotGate.isConsumed(this)) {
                stopForOneShot()
                return
            }
            if (ExpiryGate.isExpired(this)) {
                expireService()
                return
            }
            if (OneShotGate.isAwaitingVerification(this)) {
                // A operação anterior ainda aguarda conferência. Manter o
                // serviço disponível permite retomar caso a pessoa encontre
                // uma lista ausente, mas nenhuma ação nova pode começar agora.
                if (AutomationController.pendingPlan != null ||
                    AutomationController.pendingCompatCheck) {
                    AutomationController.pendingPlan = null
                    AutomationController.pendingCompatCheck = false
                    AutomationController.finish(RunPhase.FAILED_SAFE,
                        "Confira as listas no WhatsApp antes de iniciar outra operação.")
                }
                delay(300)
                continue
            }
            if (AutomationController.pendingCompatCheck && !running) {
                running = true
                try {
                    verificarCompatibilidade()
                } catch (e: Exception) {
                    if (OneShotGate.isConsumed(this)) {
                        stopForOneShot()
                    } else if (OneShotGate.isAwaitingVerification(this)) {
                        AutomationController.finish(RunPhase.COMPLETED,
                            "Confira no WhatsApp se todas as listas foram criadas.")
                    } else if (ExpiryGate.isExpired(this)) {
                        expireService()
                    } else AutomationController.publishCompat(
                        CompatCheck(
                            app = AutomationController.targetApp,
                            version = appVersion(AutomationController.targetApp) ?: "?",
                            failure = e.message ?: "falha na verificacao",
                            issue = CompatIssue.CHECK_FAILED,
                        ),
                    )
                } finally {
                    AutomationController.pendingCompatCheck = false
                    running = false
                }
            }
            val plan = AutomationController.pendingPlan
            if (plan != null && !running) {
                running = true
                var manterPlano = false
                try {
                    runPlan(plan)
                } catch (e: CancellationException) {
                    // O sistema religou o servico (onUnbind cancela o escopo). Nao e
                    // falha: a nova instancia encontra o plano e retoma da lista em
                    // andamento. Visto no Samsung: ~10 reconexoes em 2 min matavam
                    // a execucao com "Job was cancelled".
                    if (OneShotGate.isConsumed(this)) {
                        stopForOneShot()
                    } else if (OneShotGate.isAwaitingVerification(this)) {
                        AutomationController.finish(RunPhase.COMPLETED,
                            "Confira no WhatsApp se todas as listas foram criadas.")
                    } else if (ExpiryGate.isExpired(this)) {
                        expireService()
                    } else {
                        manterPlano = true
                        Log.w(TAG, "execucao interrompida por reconexao do servico; plano mantido para retomada")
                        AutomationController.setPhase(RunPhase.PREPARING, "servico religado; retomando…")
                        throw e
                    }
                } catch (e: Expired) {
                    expireService()
                } catch (e: AlreadyUsed) {
                    stopForOneShot()
                } catch (e: AwaitingVerification) {
                    AutomationController.finish(RunPhase.COMPLETED,
                        "Confira no WhatsApp se todas as listas foram criadas.")
                } catch (e: FailedSafe) {
                    if (OneShotGate.isConsumed(this)) stopForOneShot()
                    else if (OneShotGate.isAwaitingVerification(this)) {
                        AutomationController.finish(RunPhase.FAILED_SAFE,
                            "Operação aguardando conferência. Confira as listas no WhatsApp.")
                    }
                    else if (ExpiryGate.isExpired(this)) expireService()
                    else AutomationController.finish(RunPhase.FAILED_SAFE, e.message ?: "parada segura")
                } catch (e: Exception) {
                    if (OneShotGate.isConsumed(this)) stopForOneShot()
                    else if (OneShotGate.isAwaitingVerification(this)) {
                        AutomationController.finish(RunPhase.FAILED_SAFE,
                            "Operação aguardando conferência. Confira as listas no WhatsApp.")
                    }
                    else {
                        Log.e(TAG, "falha inesperada na execucao", e)
                        AutomationController.finish(RunPhase.FAILED_SAFE, "erro: ${e.message}")
                    }
                } finally {
                    if (!manterPlano) AutomationController.pendingPlan = null
                    running = false
                }
            }
            delay(300)
        }
    }

    private class FailedSafe(msg: String) : Exception(msg)
    private class Expired : Exception()
    private class AlreadyUsed : Exception()
    private class AwaitingVerification : Exception()

    private fun checkExpiry() {
        if (OneShotGate.isConsumed(this)) {
            stopForOneShot()
            throw AlreadyUsed()
        }
        if (ExpiryGate.isExpired(this)) {
            expireService()
            throw Expired()
        }
        if (OneShotGate.isAwaitingVerification(this)) throw AwaitingVerification()
    }

    @Synchronized
    private fun stopForOneShot() {
        if (disabledForOneShot) return
        disabledForOneShot = true
        AutomationController.cancel()
        AutomationController.pendingPlan = null
        AutomationController.pendingCompatCheck = false
        AutomationController.update {
            RunState(phase = RunPhase.COMPLETED, message = "Uso único concluído.")
        }
        AutomationController.attach(false)
        Handler(Looper.getMainLooper()).post {
            try { disableSelf() }
            catch (e: Exception) { Log.e(TAG, "nao foi possivel desligar acessibilidade", e) }
        }
    }

    @Synchronized
    private fun expireService() {
        if (disabledForExpiry) return
        disabledForExpiry = true
        AutomationController.cancel()
        AutomationController.pendingPlan = null
        AutomationController.pendingCompatCheck = false
        AutomationController.update {
            RunState(phase = RunPhase.CANCELLED, message = "Prazo encerrado em 04/10/2026.")
        }
        AutomationController.attach(false)
        // A permissao READ_CONTACTS deve ser revogada pelo usuario nas configuracoes.
        // O acesso de acessibilidade e desligado pelo proprio servico.
        Handler(Looper.getMainLooper()).post {
            try { disableSelf() }
            catch (e: Exception) { Log.e(TAG, "nao foi possivel desligar acessibilidade", e) }
        }
    }

    private suspend fun runPlan(plan: List<Batch>) {
        checkExpiry()
        val app = AutomationController.targetApp
        ops.targetPackage = app.packageName
        val version = appVersion(app)
        AutomationController.update { it.copy(whatsAppVersion = version ?: "?", targetApp = app) }

        if (version == null) {
            AutomationController.finish(RunPhase.VERSION_UNSUPPORTED,
                "${app.label} nao esta instalado neste aparelho.")
            return
        }
        val prof = SelectorProfile.forApp(app, version) ?: run {
            AutomationController.finish(RunPhase.VERSION_UNSUPPORTED,
                "Versao ${version} do ${app.label} nao validada. Automacao pausada por seguranca.")
            return
        }
        // App diferente daquele em que o perfil foi comprovado (ex.: Business):
        // so operamos com evidencia colhida na tela DESTE aparelho.
        if (!prof.isValidatedFor(app)) {
            val compat = AutomationController.state.value.compat
            val provado = compat != null && compat.ok &&
                compat.app == app && compat.version == version
            if (!provado) {
                AutomationController.finish(RunPhase.VERSION_UNSUPPORTED,
                    "O ${app.label} ainda nao foi verificado neste aparelho. " +
                        "Rode a verificacao de compatibilidade antes de criar listas.")
                return
            }
        }
        val jaCriadas = AutomationController.state.value.lists.filter { it.created }.map { it.index }.toSet()
        if (jaCriadas.isNotEmpty()) Log.i(TAG, "retomando: ${jaCriadas.size} lista(s) ja criada(s) serao puladas")
        for (batch in plan) {
            checkExpiry()
            if (batch.index in jaCriadas) continue
            if (AutomationController.cancelRequested) {
                AutomationController.finish(RunPhase.CANCELLED, "cancelado pelo usuario"); return
            }
            // lista retomada recomeça do zero: o seletor sera reaberto sem selecoes
            markList(batch.index) { it.copy(selected = 0, notFound = 0, ambiguous = 0,
                alreadySelected = 0, blocked = 0, business = 0, unconfirmed = 0) }
            AutomationController.update {
                it.copy(currentListIndex = batch.index, phase = RunPhase.PREPARING)
            }
            if (AutomationController.turbo) {
                selectBatchTurbo(prof, batch, AutomationController.prefixosNome)
            } else {
                selectBatch(prof, batch)
            }
            checkExpiry()

            val selected = AutomationController.state.value.lists
                .firstOrNull { it.index == batch.index }?.selected ?: 0
            if (selected < MIN_RECIPIENTS) {
                markList(batch.index) { it.copy(created = false) }
                continue // sem o minimo, o WhatsApp nem oferece criar
            }

            val ok = AutomationController.awaitConfirmation()
            checkExpiry()
            if (!ok) {
                AutomationController.finish(RunPhase.CANCELLED, "criacao nao confirmada"); return
            }
            AutomationController.setPhase(RunPhase.SAVING)
            createList(prof)
            checkExpiry()
            markList(batch.index) { it.copy(created = true) }
        }
        checkExpiry()
        val state = AutomationController.state.value
        if (OneShotGate.shouldConsume(true, plan.size, state.listsCreated)) {
            val persisted = OneShotGate.markPending(this, state.listsCreated)
            if (persisted) {
                AutomationController.finish(RunPhase.COMPLETED,
                    "Toques de criação concluídos. Confira as listas no WhatsApp e confirme no app.")
            } else {
                Log.e(TAG, "falha ao gravar estado de conferencia do uso unico")
                AutomationController.finish(RunPhase.FAILED_SAFE,
                    "Não foi possível salvar a conferência pendente. Confira as listas sem fechar o app.")
            }
        } else {
            AutomationController.finish(RunPhase.COMPLETED,
                "Nem todas as listas foram criadas. Confira o resultado antes de tentar novamente.")
        }
    }

    // -------------------- fluxo --------------------

    private suspend fun selectBatch(prof: SelectorProfile, batch: Batch) {
        checkExpiry()
        AutomationController.setPhase(RunPhase.OPENING_WHATSAPP)
        launchTargetApp()
        if (!ensureHome(prof)) {
            throw FailedSafe("tela inicial do ${AutomationController.targetApp.label} nao alcancada")
        }

        AutomationController.setPhase(RunPhase.OPENING_BROADCAST)
        if (abrirSeletorDaTransmissao(prof) == null) {
            throw FailedSafe(
                "nao encontrei 'Nova transmissao' no ${AutomationController.targetApp.label}. " +
                    "Rode a verificacao de compatibilidade para ver o que existe nesta versao.",
            )
        }
        if (!waitViewId(prof.idFor("picker_search"), 8000) &&
            !waitViewId(prof.idFor("picker_search_input"), 2000)) {
            throw FailedSafe("seletor de contatos nao abriu")
        }

        // A lista do seletor e o sinal de que ele carregou de verdade (agenda
        // grande leva segundos); sem isso o primeiro snapshot sairia vazio.
        waitViewId(prof.idFor("row_name"), 10000)
        // Cada lista abre um seletor novo: o campo de busca anterior expirou.
        searchBox = null
        ultimaConsulta = null
        ultimoResultadoVazio = false

        AutomationController.setPhase(RunPhase.SELECTING)
        for (nc in batch.numbers) {
            checkExpiry()
            if (AutomationController.cancelRequested) return
            while (AutomationController.pauseRequested) { checkExpiry(); delay(400) }
            selectOne(prof, nc.e164, nc.displayName, batch.index)
        }
    }

    private suspend fun selectOne(prof: SelectorProfile, e164: String, name: String, listIndex: Int) {
        // A busca do seletor mudou de comportamento entre versoes: no 2.26.33
        // o E.164 (+55...) filtrava; no 2.26.34.81 ele devolve "sem resultados".
        // Tentamos, nesta ordem, o E.164, o numero nacional (sem +55) e o nome —
        // e paramos na primeira consulta com linhas. A escolha do destinatario
        // continua sendo por nome EXATO e UNICO na lista filtrada.
        val candidatos = consultasPara(e164, name)
        var rows: List<AccessibilityNodeInfo> = emptyList()
        for ((i, consulta) in candidatos.withIndex()) {
            checkExpiry()
            // VELOCIDADE: o snapshot so precisa ser de um estado DIFERENTE do
            // resultado novo. O resultado da consulta anterior serve, exceto
            // quando ele foi vazio (snapshot vazio nao e referencia) ou quando a
            // consulta e a mesma (homonimo seguido) — ai limpamos o campo antes.
            if (ultimoResultadoVazio || consulta.equals(ultimaConsulta, ignoreCase = true)) {
                prepararBusca(prof)
            }
            val antes = fotografarLinhas(prof)
            if (!digitarBusca(prof, consulta)) throw FailedSafe("nao consegui digitar a busca")
            rows = waitExactNameRows(prof, name, BUSCA_TIMEOUT_MS, antes)
            ultimaConsulta = consulta
            ultimoResultadoVazio = rows.isEmpty()
            if (rows.isNotEmpty()) break
            Log.d(TAG, "consulta ${i + 1}/${candidatos.size} sem resultado")
        }
        when {
            rows.size == 1 -> {
                if (ops.anySelected(rows)) {
                    bump(listIndex) { it.copy(alreadySelected = it.alreadySelected + 1) }
                } else if (linhaDeEmpresa(prof, rows[0])) {
                    // WhatsApp 2.26.34+: contas comerciais aparecem no seletor mas
                    // nao entram em transmissao ("You can't add this business to a
                    // Broadcast list"). O toque nao marca nada — nao contamos como
                    // selecionado nem tocamos.
                    bump(listIndex) { it.copy(business = it.business + 1) }
                    if (BuildConfig.DEBUG) Log.i(TAG, "contato ${Redaction.mask(name)}: conta comercial — nao adicionavel")
                } else {
                    val chipsAntes = contarChips(prof)
                    ops.click(rows[0])
                    if (!ops.onTargetApp()) {
                        safeBack()
                        throw FailedSafe("saimos do ${AutomationController.targetApp.label} apos tocar na linha")
                    }
                    // So conta como selecionado com EVIDENCIA de marcacao (chip novo,
                    // container marcado ou check visivel). Sem evidencia, nao repete o
                    // toque (repetir desmarcaria) e registra como "sem confirmacao".
                    val oraculo = esperarMarcacao(prof, name, chipsAntes)
                    if (oraculo != null) {
                        bump(listIndex) { it.copy(selected = it.selected + 1) }
                        if (BuildConfig.DEBUG) Log.i(TAG, "contato ${Redaction.mask(name)}: selecionado ($oraculo)")
                        return
                    }
                    // Contato bloqueado: o WhatsApp cobre a tela com um dialogo
                    // ("bloqueado"/"desbloquear") em vez de marcar o item. Reconhecido
                    // pelo texto (nunca clicamos as cegas): fecha pelo botao seguro
                    // ("Cancelar", que NUNCA desbloqueia) ou volta, pula o contato.
                    if (dismissBlockedDialogIfPresent()) {
                        bump(listIndex) { it.copy(blocked = it.blocked + 1) }
                        if (BuildConfig.DEBUG) Log.i(TAG, "contato ${Redaction.mask(name)}: bloqueado — pulado")
                        return
                    }
                    bump(listIndex) { it.copy(unconfirmed = it.unconfirmed + 1) }
                    if (BuildConfig.DEBUG) Log.w(TAG, "contato ${Redaction.mask(name)}: toque sem confirmacao de marcacao")
                }
            }
            rows.size > 1 -> bump(listIndex) { it.copy(ambiguous = it.ambiguous + 1) }
            else -> bump(listIndex) { it.copy(notFound = it.notFound + 1) }
        }
        if (BuildConfig.DEBUG) Log.i(TAG, "contato ${Redaction.mask(name)}: rows=${rows.size}")
    }

    /**
     * A linha de uma conta comercial traz, no status, o aviso de que ela nao pode
     * entrar numa transmissao (texto do WhatsApp; pt-BR ou en, conforme a versao).
     * Sobe da celula do nome ate o container da linha e le o status irmao.
     */
    private fun linhaDeEmpresa(prof: SelectorProfile, nameNode: AccessibilityNodeInfo): Boolean {
        val statusId = prof.idFor("row_status") ?: return false
        var container: AccessibilityNodeInfo? = nameNode
        repeat(4) {
            val id = container?.viewIdResourceName ?: ""
            if (id.endsWith(":id/${prof.idFor("row_container") ?: "row_container"}")) return@repeat
            container = container?.parent
        }
        val status = ops.descendantByViewId(container ?: nameNode.parent, statusId)
            ?.text?.toString()?.lowercase() ?: return false
        return "add this business" in status || "adicionar esta empresa" in status ||
            "adicionar essa empresa" in status || "empresa a uma lista" in status
    }

    /** true se um dialogo de contato bloqueado apareceu e foi fechado com seguranca. */
    private suspend fun dismissBlockedDialogIfPresent(): Boolean {
        // Chamado so depois de esperarMarcacao (ate 800 ms): o dialogo, se houver, ja desenhou.
        if (ops.firstTextContaining("bloque") == null) return false
        ops.firstTextContaining("cancelar")?.let { ops.click(it) }
            ?: safeBack()
        delay(300)
        searchBox = null // a tela pode ter perdido o foco do campo de busca
        return true
    }

    /**
     * Consultas a tentar na busca do seletor. No 2.26.34.81 a busca casa com o
     * TEXTO EXIBIDO na linha: para contato salvo, o nome; para nao salvo, o
     * numero. Por isso o nome vem primeiro quando existe; os numeros ficam como
     * reserva. A escolha do destinatario segue sendo por nome exato e unico.
     */
    private fun consultasPara(e164: String, name: String): List<String> {
        val nome = name.trim()
        val nomeEhNumero = nome.isEmpty() || NUMERICO.matches(nome)
        if (!nomeEhNumero) return listOf(nome)
        val nacional = if (e164.startsWith("+55")) e164.removePrefix("+55") else e164.removePrefix("+")
        return listOf(nacional, e164).distinct()
    }

    /** Quantos chips de selecionados estao renderizados (oraculo barato de marcacao). */
    private fun contarChips(prof: SelectorProfile): Int =
        ops.byViewId(prof.idFor("selected_chip_name") ?: "contact_name").size

    private fun containerDe(node: AccessibilityNodeInfo, prof: SelectorProfile): AccessibilityNodeInfo? {
        val sufixo = ":id/${prof.idFor("row_container") ?: "row_container"}"
        var n: AccessibilityNodeInfo? = node
        repeat(5) {
            if (n?.viewIdResourceName?.endsWith(sufixo) == true) return n
            n = n?.parent
        }
        return null
    }

    /** Qual evidencia confirmou a marcacao apos o toque, ou null se nenhuma no prazo. */
    private suspend fun esperarMarcacao(prof: SelectorProfile, name: String, chipsAntes: Int): String? {
        val rowId = prof.idFor("row_name") ?: "chat_able_contacts_row_name"
        val checkId = prof.idFor("selection_check") ?: "selection_check"
        val alvo = name.trim()
        val fim = SystemClock.uptimeMillis() + MARCACAO_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < fim) {
            checkExpiry()
            if (contarChips(prof) > chipsAntes) return "chip"
            val linha = ops.byViewId(rowId).firstOrNull { it.text?.toString()?.trim() == alvo }
            if (linha != null) {
                val c = containerDe(linha, prof)
                if (c != null && (c.isChecked || c.isSelected)) return "container"
                if (c?.contentDescription?.contains("elecionad", ignoreCase = true) == true) return "descricao"
                val check = ops.descendantByViewId(c ?: linha, checkId)
                if (check != null && check.isVisibleToUser) return "check"
            }
            delay(POLL_NORMAL)
        }
        return null
    }

    /**
     * Limpa o campo de busca e espera a lista completa voltar, ESTAVEL (duas
     * leituras iguais, sem "sem resultados"). Garante que o proximo "sem
     * resultados" observado e da consulta nova, nao sobra da anterior.
     * O no do campo e cacheado: refresh() antes de ler o texto, senao le velho.
     */
    private suspend fun prepararBusca(prof: SelectorProfile) {
        val box = searchBox ?: return
        try { box.refresh() } catch (_: Exception) { }
        val atual = box.text?.toString() ?: ""
        if (atual.isEmpty()) return
        if (!ops.setText(box, "")) { searchBox = null; return }
        val rowId = prof.idFor("row_name") ?: "chat_able_contacts_row_name"
        val semResultadosId = prof.idFor("no_results")
        var anterior: Set<String>? = null
        val fim = SystemClock.uptimeMillis() + PREPARO_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < fim) {
            checkExpiry()
            val nomes = ops.byViewId(rowId).mapNotNull { it.text?.toString()?.trim() }.toSet()
            val semAviso = semResultadosId == null || !ops.existsViewId(semResultadosId)
            if (nomes.isNotEmpty() && semAviso && nomes == anterior) return
            anterior = if (nomes.isNotEmpty() && semAviso) nomes else null
            delay(POLL_NORMAL)
        }
        Log.w(TAG, "lista nao repovoou apos limpar a busca (${PREPARO_TIMEOUT_MS} ms)")
    }

    /** Linhas visiveis agora (para saber quando o filtro da busca foi aplicado). */
    private fun fotografarLinhas(prof: SelectorProfile): Set<String> {
        val rowId = prof.idFor("row_name") ?: "chat_able_contacts_row_name"
        return ops.byViewId(rowId).mapNotNull { it.text?.toString()?.trim() }.toSet()
    }

    /**
     * VELOCIDADE: reaproveita o campo de busca entre contatos. Localizar o no
     * custa uma consulta a arvore; so refazemos se o no expirar (setText falha).
     */
    private suspend fun digitarBusca(prof: SelectorProfile, texto: String): Boolean {
        repeat(2) { tentativa ->
            checkExpiry()
            var box = searchBox
            if (box == null || tentativa == 1) {
                box = openSearch(prof) ?: throw FailedSafe("campo de busca nao encontrado")
                searchBox = box
            }
            if (ops.setText(box, texto)) {
                try { box.refresh() } catch (_: Exception) { }
                if ((box.text?.toString() ?: "").trim() == texto.trim()) return true
                Log.w(TAG, "campo de busca nao refletiu a consulta (tentativa ${tentativa + 1})")
            }
            searchBox = null
        }
        return false
    }

    private suspend fun createList(prof: SelectorProfile) {
        checkExpiry()
        val btn = ops.firstByViewId(prof.idFor("create_button") ?: "next_btn")
            ?: prof.descriptionsFor("create_button").firstNotNullOfOrNull { ops.byContentDesc(it) }
            ?: throw FailedSafe("botao de criar nao localizado")
        if (!ops.click(btn)) throw FailedSafe("botao de criar nao pode ser acionado com seguranca")
        delay(600)
        // Nao confirmamos nada alem da criacao: nao tocamos em compositor/envio.
    }

    /**
     * Chega ao seletor de contatos da nova transmissao. Ha dois desenhos de tela
     * em circulacao, e tentamos os dois — sempre por texto/id conhecido, nunca
     * por posicao:
     *
     *  A) menu (⋮) -> "Listas de transmissao" -> botao de nova transmissao.
     *     Caminho VALIDADO no WhatsApp 2.26.33.74.
     *  B) botao de nova conversa -> linha "Nova transmissao" dentro do seletor.
     *     Desenho mais recente; presente em algumas versoes.
     *
     * Devolve qual caminho funcionou, ou null se nenhum existe nesta versao —
     * e o caso, por exemplo, do WhatsApp Business 2.26.34.75, onde a criacao de
     * lista de transmissao nao esta exposta na interface.
     */
    private suspend fun abrirSeletorDaTransmissao(prof: SelectorProfile): String? {
        checkExpiry()
        // A) menu -> Listas de transmissao
        val overflow = prof.idFor("menu_overflow")?.let { ops.firstByViewId(it) }
            ?: prof.descriptionsFor("menu_overflow").firstNotNullOfOrNull { ops.byContentDesc(it) }
        if (overflow != null) {
            ops.click(overflow)
            delay(500)
            val item = prof.descriptionsFor("menu_broadcast_lists")
                .firstNotNullOfOrNull { ops.menuItemContaining(it) }
            if (item != null) {
                ops.click(item)
                delay(700)
                lerQuotaMensal(prof)
                val novo = prof.idFor("broadcast_new_button")?.let { ops.firstByViewId(it) }
                    ?: prof.descriptionsFor("broadcast_new_button")
                        .firstNotNullOfOrNull { ops.byContentDesc(it) }
                if (novo != null) {
                    ops.click(novo)
                    delay(900)
                    return "menu"
                }
            }
            // o menu abriu mas nao tem transmissao: fecha e tenta o outro caminho
            safeBack()
            delay(400)
        }

        // B) nova conversa -> linha "Nova transmissao"
        val fab = prof.idFor("home_fab")?.let { ops.firstByViewId(it) }
        if (fab != null) {
            ops.click(fab)
            delay(900)
            val linha = prof.descriptionsFor("new_broadcast_row")
                .firstNotNullOfOrNull { ops.menuItemContaining(it) ?: ops.byContentDesc(it) }
            if (linha != null) {
                ops.click(linha)
                delay(900)
                return "seletor"
            }
            safeBack()
            delay(400)
        }
        // C) aba Ferramentas -> "Transmissoes comerciais" (caminho novo do Business).
        // So existe onde a conta tem o recurso liberado; sondamos por texto exato.
        val aba = prof.descriptionsFor("tools_tab")
            .firstNotNullOfOrNull { ops.byExactText(it).firstOrNull() }
        if (aba != null) {
            ops.click(aba)
            delay(900)
            val linha = prof.descriptionsFor("tools_business_broadcasts")
                .firstNotNullOfOrNull { ops.byExactText(it).firstOrNull() }
            if (linha != null) {
                ops.click(linha)
                delay(900)
                return "ferramentas"
            }
            // devolve a aba Conversas antes de desistir
            val conversas = prof.descriptionsFor("chats_tab")
                .firstNotNullOfOrNull { ops.byExactText(it).firstOrNull() }
            if (conversas != null) ops.click(conversas) else safeBack()
            delay(500)
        }
        return null
    }

    /**
     * A tela "Listas de transmissao" mostra a cota mensal de envios (ex.: 0
     * enviadas, 35 restantes, "01 set. - 01 out."). Lemos para informar o
     * operador; nao altera o comportamento da automacao.
     */
    private fun lerQuotaMensal(prof: SelectorProfile) {
        fun texto(key: String): String? =
            prof.idFor(key)?.let { ops.firstByViewId(it) }?.text?.toString()?.trim()
        val enviadas = texto("quota_sent")?.filter { it.isDigit() }?.toIntOrNull()
        val restantes = texto("quota_remaining")?.filter { it.isDigit() }?.toIntOrNull()
        val periodo = texto("quota_period")
        if (enviadas != null || restantes != null) {
            quotaMensal = QuotaMensal(enviadas, restantes, periodo)
            Log.i(TAG, "cota mensal: enviadas=$enviadas restantes=$restantes")
        }
    }

    // -------------------- modo turbo (prefixo do nome) --------------------

    /**
     * Seleciona um lote pelo PREFIXO do nome: digita o prefixo (o mais longo
     * comum aos nomes do lote) UMA vez e toca nas linhas visiveis cujo nome esta
     * no lote, rolando a lista por acao de acessibilidade. Mesmas regras do modo
     * normal: nome EXATO, um toque por nome, contas comerciais puladas, e cada
     * toque confirmado pelo subtitulo "Selecionados: N de 256" (ou pelos chips).
     */
    private suspend fun selectBatchTurbo(prof: SelectorProfile, batch: Batch, prefixos: List<String>) {
        checkExpiry()
        AutomationController.setPhase(RunPhase.OPENING_WHATSAPP)
        launchTargetApp()
        if (!ensureHome(prof)) {
            throw FailedSafe("tela inicial do ${AutomationController.targetApp.label} nao alcancada")
        }
        AutomationController.setPhase(RunPhase.OPENING_BROADCAST)
        if (abrirSeletorDaTransmissao(prof) == null) {
            throw FailedSafe("nao encontrei 'Nova transmissao' no ${AutomationController.targetApp.label}")
        }
        if (!waitViewId(prof.idFor("picker_search"), 8000) &&
            !waitViewId(prof.idFor("picker_search_input"), 2000)) {
            throw FailedSafe("seletor de contatos nao abriu")
        }
        waitViewId(prof.idFor("row_name"), 10000)
        searchBox = null
        AutomationController.setPhase(RunPhase.SELECTING)

        val rowId = prof.idFor("row_name") ?: "chat_able_contacts_row_name"
        val alvos = batch.numbers.map { it.displayName.trim() }.filter { it.isNotEmpty() }.toSet()
        val tocados = HashSet<String>()
        var chipsAntes = contarChips(prof)

        for (prefixo in prefixos) {
            checkExpiry()
            val grupo = alvos.filter { it.startsWith(prefixo, ignoreCase = true) && it !in tocados }
            if (grupo.isEmpty()) continue
            val consulta = prefixoComum(grupo, prefixo)
            val maior = grupo.maxOf { it.lowercase() }
            prepararBusca(prof)
            val antes = fotografarLinhas(prof)
            if (!digitarBusca(prof, consulta)) throw FailedSafe("nao consegui digitar o prefixo")
            val filtrou = waitUntil(BUSCA_TIMEOUT_MS) {
                val n = fotografarLinhas(prof); n.isNotEmpty() && n != antes
            }
            if (!filtrou) { Log.w(TAG, "prefixo sem resultado no seletor"); continue }
            Log.i(TAG, "turbo: prefixo de ${consulta.length} chars, ${grupo.size} alvo(s)")

            var paginasSemNovidade = 0
            while (true) {
                checkExpiry()
                if (AutomationController.cancelRequested) return
                while (AutomationController.pauseRequested) { checkExpiry(); delay(400) }
                val visiveis = ops.byViewId(rowId)
                for (linha in visiveis) {
                    checkExpiry()
                    val nome = linha.text?.toString()?.trim() ?: continue
                    if (nome !in alvos || nome in tocados) continue
                    if (linhaDeEmpresa(prof, linha)) {
                        tocados += nome
                        bump(batch.index) { it.copy(business = it.business + 1) }
                        continue
                    }
                    val contagemAntes = contagemSelecionados()
                    ops.click(linha)
                    if (!ops.onTargetApp()) {
                        safeBack()
                        throw FailedSafe("saimos do ${AutomationController.targetApp.label} apos tocar na linha")
                    }
                    val ok = waitUntil(MARCACAO_TIMEOUT_MS, 60) {
                        contagemSelecionados() > contagemAntes || contarChips(prof) > chipsAntes
                    }
                    tocados += nome
                    if (ok) {
                        chipsAntes = contarChips(prof)
                        bump(batch.index) { it.copy(selected = it.selected + 1) }
                    } else if (dismissBlockedDialogIfPresent()) {
                        bump(batch.index) { it.copy(blocked = it.blocked + 1) }
                    } else {
                        bump(batch.index) { it.copy(unconfirmed = it.unconfirmed + 1) }
                    }
                }
                if (grupo.all { it in tocados }) break
                // lista em ordem alfabetica: passou do ultimo alvo, nao ha mais o que achar
                val nomesVisiveis = visiveis.mapNotNull { it.text?.toString()?.trim()?.lowercase() }
                if (nomesVisiveis.isNotEmpty() && nomesVisiveis.min() > maior) break
                // rola uma pagina por acessibilidade; lista parada duas vezes = fim
                val lista = ops.firstByViewId(prof.idFor("picker_list") ?: "contacts_wds_list")
                val antesRolar = fotografarLinhas(prof)
                checkExpiry()
                val rolou = lista?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
                delay(350)
                val depoisRolar = fotografarLinhas(prof)
                paginasSemNovidade = if (!rolou || depoisRolar == antesRolar) paginasSemNovidade + 1 else 0
                if (paginasSemNovidade >= 2) break
            }
        }
        val faltaram = alvos.count { it !in tocados }
        if (faltaram > 0) {
            bump(batch.index) { it.copy(notFound = it.notFound + faltaram) }
            Log.i(TAG, "turbo: $faltaram nome(s) do lote nao apareceram na lista filtrada")
        }
    }

    /** Maior prefixo comum aos nomes do grupo, nunca menor que o configurado. */
    private fun prefixoComum(nomes: List<String>, minimo: String): String {
        var comum = nomes.first()
        for (n in nomes) {
            var i = 0
            while (i < comum.length && i < n.length && comum[i].equals(n[i], ignoreCase = true)) i++
            comum = comum.substring(0, i)
            if (comum.length <= minimo.length) return minimo
        }
        return comum.ifEmpty { minimo }
    }

    /** "Selecionados: N de 256" no subtitulo do seletor; -1 se nao exposto. */
    private fun contagemSelecionados(): Int {
        val r = ops.root() ?: return -1
        val nos = r.findAccessibilityNodeInfosByText("Selecionado") ?: return -1
        for (n in nos) {
            val t = n.text?.toString() ?: continue
            val m = CONTAGEM.find(t) ?: continue
            return m.groupValues[1].toInt()
        }
        return -1
    }

    // -------------------- verificacao de compatibilidade --------------------

    /**
     * Abre o app alvo, caminha ate o seletor de contatos da nova transmissao e
     * confere se cada identificador esperado existe NA TELA REAL deste aparelho.
     *
     * E o que autoriza operar num app cujo perfil nao foi validado em
     * laboratorio (o WhatsApp Business, hoje): em vez de supor que os
     * identificadores sao os mesmos, perguntamos ao aparelho.
     *
     * NAO seleciona contato, NAO cria lista, NAO toca no compositor. Ao final,
     * volta para tras deixando a tela como estava.
     */
    private suspend fun verificarCompatibilidade() {
        checkExpiry()
        val app = AutomationController.targetApp
        ops.targetPackage = app.packageName
        val version = appVersion(app)
        AutomationController.update { it.copy(whatsAppVersion = version ?: "?", targetApp = app) }

        if (version == null) {
            AutomationController.publishCompat(
                CompatCheck(
                    app,
                    "?",
                    failure = "${app.label} nao esta instalado neste aparelho.",
                    issue = CompatIssue.APP_NOT_INSTALLED,
                ),
            )
            return
        }
        val prof = SelectorProfile.forApp(app, version)
        if (prof == null) {
            AutomationController.publishCompat(
                CompatCheck(app, version,
                    failure = "Nao ha perfil de seletores para a versao $version.",
                    issue = CompatIssue.PROFILE_NOT_AVAILABLE),
            )
            return
        }

        val achados = LinkedHashMap<String, Boolean>()
        quotaMensal = null
        var chegouNoPicker = false
        var falha: String? = null
        var problema: CompatIssue? = null

        try {
            launchTargetApp()
            if (!ensureHome(prof)) {
                falha = "nao cheguei a tela inicial do ${app.label}"
            } else {
                achados["home_fab"] = ops.existsViewId(prof.idFor("home_fab") ?: "fab")
                achados["tools_tab"] = prof.descriptionsFor("tools_tab")
                    .any { ops.byExactText(it).isNotEmpty() }
                val caminho = abrirSeletorDaTransmissao(prof)
                achados["nova_transmissao"] = caminho != null
                if (caminho == null) {
                    problema = CompatIssue.BROADCAST_ENTRY_NOT_FOUND
                    falha = "a criacao de transmissao nao aparece nesta conta do ${app.label} " +
                        "(procurei no menu, na nova conversa e na aba Ferramentas). O WhatsApp " +
                        "libera esse recurso por conta e pais, e contas conectadas a Plataforma " +
                        "do WhatsApp Business ficam sem ele"
                } else {
                    chegouNoPicker =
                        waitViewId(prof.idFor("picker_search"), 6000) ||
                            waitViewId(prof.idFor("picker_search_input"), 2000) ||
                            waitViewId(prof.idFor("row_name"), 2000)
                    if (!chegouNoPicker) {
                        problema = CompatIssue.PICKER_NOT_OPENED
                        falha = "o seletor de contatos nao abriu (caminho: $caminho)"
                    }
                }
            }

            if (chegouNoPicker) {
                // Agendas grandes levam segundos para renderizar as linhas: espera
                // antes de julgar, senao "row_name ausente" e so a lista carregando.
                waitViewId(prof.idFor("row_name"), 8000)
                // No seletor aberto: confere os ids que o fluxo usa de verdade.
                for (key in prof.requiredIdKeys()) {
                    if (achados.containsKey(key)) continue
                    val id = prof.idFor(key) ?: continue
                    achados[key] = ops.existsViewId(id)
                }
                // O "Criar" existe mesmo desabilitado; se nao houver por id,
                // aceita a descricao (ele muda de rotulo entre versoes).
                if (achados["create_button"] == false) {
                    achados["create_button"] = prof.descriptionsFor("create_button")
                        .any { ops.byContentDesc(it) != null }
                }
            }
        } finally {
            // devolve a tela: sai do seletor sem tocar em nada.
            repeat(if (chegouNoPicker) 2 else 1) {
                safeBack()
                delay(400)
            }
        }

        var check = CompatCheck(
            app = app,
            version = version,
            found = achados,
            reachedPicker = chegouNoPicker,
            failure = falha,
            issue = problema,
            quota = quotaMensal,
        )
        if (!check.ok && check.failure == null && check.missing.isNotEmpty()) {
            check = check.copy(
                failure = "a tela mudou e faltam controles necessarios: ${check.missing.joinToString()}",
                issue = CompatIssue.SELECTORS_MISSING,
            )
        }
        Log.i(TAG, "compatibilidade ${app.packageName} $version: picker=$chegouNoPicker " +
            "faltando=${check.missing}")
        AutomationController.publishCompat(check)
    }

    // -------------------- navegacao --------------------

    private fun launchTargetApp() {
        checkExpiry()
        val pkg = AutomationController.targetApp.packageName
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun safeBack(): Boolean {
        checkExpiry()
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private suspend fun ensureHome(prof: SelectorProfile): Boolean {
        val fab = prof.idFor("home_fab") ?: "fab"
        repeat(4) {
            if (!ops.onTargetApp()) { launchTargetApp(); delay(500) }
            if (waitViewId(fab, 1500)) return true
            safeBack()
            delay(400)
        }
        return waitViewId(fab, 2000)
    }

    private suspend fun openSearch(prof: SelectorProfile): AccessibilityNodeInfo? {
        ops.firstByViewId(prof.idFor("picker_search_input") ?: "search_src_text")?.let { return it }
        ops.firstByViewId(prof.idFor("picker_search") ?: "menuitem_search")?.let { ops.click(it) }
        if (waitViewId(prof.idFor("picker_search_input") ?: "search_src_text", 3000)) {
            return ops.firstByViewId(prof.idFor("picker_search_input") ?: "search_src_text")
        }
        return null
    }

    private suspend fun clickMenuItem(prof: SelectorProfile, key: String) {
        for (label in prof.descriptionsFor(key)) {
            val n = ops.menuItemContaining(label)
            if (n != null) { ops.click(n); delay(400); return }
        }
        throw FailedSafe("item de menu '$key' nao encontrado")
    }

    private suspend fun click(idSuffix: String?, descKey: String, prof: SelectorProfile) {
        idSuffix?.let { ops.firstByViewId(it) }?.let { ops.click(it); delay(300); return }
        for (d in prof.descriptionsFor(descKey)) {
            ops.byContentDesc(d)?.let { ops.click(it); delay(300); return }
        }
        throw FailedSafe("elemento '$descKey' nao encontrado")
    }

    // -------------------- esperas condicionadas --------------------

    private suspend fun waitViewId(suffix: String?, timeoutMs: Long): Boolean {
        suffix ?: return false
        return waitUntil(timeoutMs) { ops.existsViewId(suffix) }
    }

    /**
     * Espera as linhas com nome EXATAMENTE igual depois de digitar a consulta.
     *
     * Regras (revisao adversarial de 2026-09-04; todas vistas no aparelho):
     *  - root nulo e leitura invalida, nao evidencia (transicao de janela/IME);
     *  - a lista so e julgada DEPOIS de mudar em relacao ao snapshot de antes
     *    da digitacao — antes disso estamos lendo a consulta anterior;
     *  - match exato so vale numa lista ja mudada (linha "velha" homonima do
     *    contato anterior nao serve);
     *  - 0 linhas SEM o aviso "sem resultados" e transitorio (o filtro esta
     *    trocando as linhas): so espera;
     *  - "sem resultados" precisa aparecer em 2 leituras seguidas;
     *  - linhas sem match so viram "nao e este" quando o conjunto ficou ESTAVEL
     *    por CICLOS_ESTAVEIS leituras e ja passou PISO_VEREDITO_MS.
     * O caso feliz continua saindo na hora: o match exato retorna assim que aparece.
     */
    private suspend fun waitExactNameRows(
        prof: SelectorProfile, name: String, timeoutMs: Long,
        antes: Set<String> = emptySet(),
    ): List<AccessibilityNodeInfo> {
        val rowId = prof.idFor("row_name") ?: "chat_able_contacts_row_name"
        val semResultadosId = prof.idFor("no_results")
        val alvo = name.trim()
        var anterior: Set<String>? = null
        var estaveis = 0
        var avisos = 0
        var vaziosEstaveis = 0
        val inicio = SystemClock.uptimeMillis()
        var decorrido = 0L
        var poll = POLL_RAPIDO
        while (SystemClock.uptimeMillis() - inicio < timeoutMs) {
            checkExpiry()
            decorrido = SystemClock.uptimeMillis() - inicio
            if (ops.root() == null) {
                delay(poll); poll = POLL_NORMAL; continue
            }
            val todas = ops.byViewId(rowId)
            val nomes = todas.mapNotNull { it.text?.toString()?.trim() }.toSet()
            val mudou = (todas.isEmpty() && antes.isNotEmpty()) ||
                (nomes.isNotEmpty() && nomes != antes)
            if (!mudou) {
                delay(poll); poll = POLL_NORMAL; continue
            }
            val exatas = todas.filter { it.text?.toString()?.trim() == alvo }
            if (exatas.isNotEmpty()) return exatas
            if (todas.isEmpty()) {
                val avisou = semResultadosId != null && ops.existsViewId(semResultadosId)
                avisos = if (avisou) avisos + 1 else 0
                if (avisos >= 2) return emptyList()
                // Sem o aviso conhecido (busca por nome usa outro estado vazio):
                // vazio ESTAVEL por varias leituras, apos um piso maior, e "nao achou".
                vaziosEstaveis++
                if (vaziosEstaveis >= CICLOS_VAZIO_ESTAVEL && decorrido >= PISO_VAZIO_MS) return emptyList()
            } else {
                vaziosEstaveis = 0
                estaveis = if (nomes == anterior) estaveis + 1 else 0
                anterior = nomes
                if (estaveis >= CICLOS_ESTAVEIS && decorrido >= PISO_VEREDITO_MS) return emptyList()
            }
            delay(poll); poll = POLL_NORMAL
        }
        return emptyList()
    }

    private suspend inline fun waitUntil(timeoutMs: Long, poll: Long = POLL_NORMAL,
                                         cond: () -> Boolean): Boolean {
        // checa ANTES de dormir: se ja esta pronto, nao paga a espera.
        val fim = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            checkExpiry()
            if (cond()) return true
            if (SystemClock.uptimeMillis() >= fim) return false
            delay(poll)
        }
    }

    // -------------------- helpers de estado --------------------

    private fun bump(listIndex: Int, f: (ListProgress) -> ListProgress) = markList(listIndex, f)

    private fun markList(index: Int, f: (ListProgress) -> ListProgress) {
        AutomationController.update { st ->
            st.copy(lists = st.lists.map { if (it.index == index) f(it) else it })
        }
    }

    private fun appVersion(app: TargetApp): String? = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(app.packageName, 0).versionName
    } catch (_: Exception) { null }

    companion object {
        private const val TAG = "ListaLocalA11y"
        private const val MIN_RECIPIENTS = 2

        // Ritmo das esperas condicionadas (nao sao pausas fixas: sao o intervalo
        // entre leituras da arvore).
        private const val POLL_RAPIDO = 50L    // 1a leitura quase imediata
        private const val POLL_NORMAL = 80L
        // Teto absoluto da busca; na pratica sai muito antes, quando a lista assenta.
        private const val BUSCA_TIMEOUT_MS = 6000L  // agendas de 9 mil+ filtram devagar
        private const val PREPARO_TIMEOUT_MS = 2500L // limpar o campo e ver a lista voltar
        private const val CICLOS_ESTAVEIS = 3      // leituras iguais seguidas para concluir "nao e este"
        private const val PISO_VEREDITO_MS = 400L  // nunca julgar negativo antes disso
        private const val MARCACAO_TIMEOUT_MS = 800L // prazo para o WhatsApp refletir a marcacao
        private const val CICLOS_VAZIO_ESTAVEL = 6   // leituras vazias seguidas sem o aviso conhecido
        private const val PISO_VAZIO_MS = 1200L      // ...e so depois disto (filtro lento nao vira "nao achou")
        private val NUMERICO = Regex("^[+\\d\\s()\\-]+$")
        private val CONTAGEM = Regex("(\\d+)\\s+de\\s+\\d+")
    }
}
