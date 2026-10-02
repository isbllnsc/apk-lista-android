package com.listalocal.service

import com.listalocal.core.contacts.Batch
import com.listalocal.core.selectors.TargetApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ponte entre a UI (Compose) e o AccessibilityService. Singleton em processo:
 * a UI observa [state] e chama [start]/[confirm]/[pause]/[resume]/[cancel]; o
 * serviço registra-se via [attach] e le [pendingPlan]/[autoConfirm].
 *
 * A confirmacao por lista e um requisito do produto: sem [autoConfirm], o motor
 * para antes de criar cada lista e espera [confirm].
 */
object AutomationController {

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    @Volatile var autoConfirm: Boolean = false
        private set

    /** Modo turbo: seleciona por PREFIXO do nome (uma consulta por prefixo). */
    @Volatile var turbo: Boolean = false
        private set
    @Volatile var prefixosNome: List<String> = emptyList()
        private set

    /** App alvo da automacao (WhatsApp ou WhatsApp Business). */
    @Volatile var targetApp: TargetApp = TargetApp.WHATSAPP
        private set

    // preenchido pela UI, consumido pelo serviço
    @Volatile internal var pendingPlan: List<Batch>? = null
    @Volatile internal var pendingCompatCheck: Boolean = false
    @Volatile internal var cancelRequested: Boolean = false
    @Volatile internal var pauseRequested: Boolean = false

    // gate de confirmacao por lista
    @Volatile private var confirmGate: CompletableDeferred<Boolean>? = null

    private var serviceAvailable: Boolean = false

    fun attach(available: Boolean) { serviceAvailable = available }
    fun isServiceAvailable(): Boolean = serviceAvailable

    /** Troca o app alvo. Descarta a verificacao anterior: ela era de outro app. */
    fun selectTargetApp(app: TargetApp) {
        if (targetApp == app) return
        targetApp = app
        _state.value = _state.value.copy(targetApp = app, compat = null)
    }

    /**
     * Pede ao serviço uma verificacao de compatibilidade na tela real: ele abre
     * o app alvo, chega ao seletor de contatos e confere se cada identificador
     * esperado existe. Nao seleciona ninguem e nao cria lista.
     */
    fun checkCompatibility() {
        if (_state.value.running || _state.value.checkingCompat) return
        pendingCompatCheck = true
        _state.value = _state.value.copy(checkingCompat = true, compat = null, message = "")
    }

    /** Chamado pela UI. O serviço observa [pendingPlan] e comeca a rodar. */
    fun start(
        plan: List<Batch>,
        autoConfirm: Boolean,
        turbo: Boolean = false,
        prefixosNome: List<String> = emptyList(),
    ) {
        this.autoConfirm = autoConfirm
        this.turbo = turbo && prefixosNome.isNotEmpty()
        this.prefixosNome = prefixosNome
        cancelRequested = false
        pauseRequested = false
        pendingPlan = plan
        _state.value = RunState(
            phase = RunPhase.PREPARING, plan = plan, running = true,
            lists = plan.map { ListProgress(it.index, it.label, it.numbers.size) },
            targetApp = targetApp,
            compat = _state.value.compat,
        )
    }

    fun pause() { pauseRequested = true }
    fun resume() { pauseRequested = false }
    fun cancel() {
        cancelRequested = true
        confirmGate?.complete(false)
    }

    /** UI confirma (ou nega) a criacao da lista atual. */
    fun confirm(create: Boolean) {
        confirmGate?.complete(create)
    }

    // ---- usados pelo serviço ----

    internal fun update(transform: (RunState) -> RunState) {
        _state.value = transform(_state.value)
    }

    internal fun setPhase(p: RunPhase, msg: String = "") {
        _state.value = _state.value.copy(phase = p, message = msg)
    }

    internal fun publishCompat(check: CompatCheck) {
        pendingCompatCheck = false
        _state.value = _state.value.copy(compat = check, checkingCompat = false)
    }

    internal suspend fun awaitConfirmation(): Boolean {
        if (autoConfirm) return true
        val gate = CompletableDeferred<Boolean>()
        confirmGate = gate
        _state.value = _state.value.copy(
            phase = RunPhase.WAITING_CONFIRMATION, needsConfirmation = true)
        val ok = gate.await()
        confirmGate = null
        _state.value = _state.value.copy(needsConfirmation = false)
        return ok
    }

    internal fun finish(phase: RunPhase, msg: String = "") {
        pendingPlan = null
        _state.value = _state.value.copy(phase = phase, message = msg, running = false)
    }
}
