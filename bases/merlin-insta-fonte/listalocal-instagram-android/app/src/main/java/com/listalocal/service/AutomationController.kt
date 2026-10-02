package com.listalocal.service

import com.listalocal.core.followers.Batch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ponte entre a UI (Compose) e o AccessibilityService. Singleton em processo:
 * a UI observa [state] e chama [start]/[confirm]/[pause]/[resume]/[cancel]; o
 * servico registra-se via [attach] e le o plano pendente.
 *
 * A conferencia por lote e um requisito do produto: sem [autoConfirm], o motor
 * para no fim de cada lote (e antes de "Concluir" em Amigos Proximos) e espera
 * [confirm].
 */
object AutomationController {

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    @Volatile var autoConfirm: Boolean = false
        private set
    @Volatile var intervaloMs: Long = Velocidade.NORMAL.segundos * 1000L
        private set
    /** Modo C: a operacao ao vivo em andamento. */
    @Volatile var campanha: Campanha? = null
        private set
    /**
     * Conversas do Direct: as linhas ja abertas na operacao em andamento (nome -> @, FonteConversas). So na memoria,
     * e so da mesma operacao (conta + inicio): o Retomar e o servico religado nao reabrem quem ja tem desfecho.
     * ponytail: com o processo morto, some, e a retomada reabre desde o topo (sem reenviar); gravar nomes de
     * terceiros no disco nao vale isso.
     */
    internal val linhasAbertas = HashMap<String, String>()
    private var linhasDe: Pair<String, Long>? = null

    /** Amigos Proximos: quem pediu para parar sai da lista, se estiver nela. */
    @Volatile var remover: List<String> = emptyList()
        private set

    // preenchido pela UI, consumido pelo servico
    @Volatile internal var pendingPlan: List<Batch>? = null
    @Volatile internal var pendingCompat: Modo? = null
    /** "Trocar de conta" pedido na tela do plano: o modo a conferir depois da troca. */
    @Volatile internal var pendingTroca: Modo? = null
    @Volatile internal var cancelRequested: Boolean = false
    @Volatile internal var pauseRequested: Boolean = false

    @Volatile private var confirmGate: CompletableDeferred<Boolean>? = null

    @Volatile private var serviceAvailable: Boolean = false

    fun attach(available: Boolean) { serviceAvailable = available }

    // ── WhatsApp (serviço paralelo) ───────────────────────────────────────────
    @Volatile internal var pendingPlanWa: List<Batch>? = null
    @Volatile internal var pendingCompatWa: Modo? = null
    @Volatile private var serviceAvailableWa: Boolean = false
    /** Modo LISTAS_TRANSMISSAO: mensagem a enviar nas listas criadas. Nulo = não está neste modo. */
    @Volatile internal var pendingBroadcastWa: String? = null
    /** Mensagem para a Fase 2 independente (disparada pelo usuário após Fase 1). */
    @Volatile internal var pendingBroadcastPhase2: String? = null
    /** Índices das listas de transmissão selecionadas pelo usuário para envio na Fase 2. */
    @Volatile internal var pendingBroadcastPhase2Listas: Set<Int>? = null
    /** Quando true, filtra contatos com DDD 21 (+5521) na Fase 1. */
    @Volatile var soPor5521: Boolean = false

    fun attachWa(available: Boolean) { serviceAvailableWa = available }
    val isWaServiceAvailable: Boolean get() = serviceAvailableWa

    internal fun publishCompatWa(check: CompatCheck) {
        pendingCompatWa = null
        update {
            it.copy(
                appVersionWa = check.version,
                compatWa = it.compatWa + (check.modo to check),
            )
        }
    }

    fun isServiceAvailable(): Boolean = serviceAvailable

    /**
     * Pede ao servico a conferencia de um modo na tela real: ele abre o
     * Instagram e navega ate a tela do modo sem escrever, enviar ou marcar ninguem.
     */
    fun checkCompatibility(modo: Modo) {
        if (_state.value.running || _state.value.checkingCompat != null || pendingTroca != null) return
        // Um "Parar" de antes não pode recusar os toques da conferência.
        cancelRequested = false
        pauseRequested = false
        pendingCompat = modo
        _state.value = _state.value.copy(
            checkingCompat = modo, compat = _state.value.compat - modo, message = "",
        )
    }

    /** Inicia a conferência do WhatsApp para [modo] (só DM). */
    fun checkCompatibilityWa(modo: Modo) {
        if (_state.value.running || _state.value.checkingCompat != null) return
        cancelRequested = false
        pauseRequested = false
        pendingCompatWa = modo
        _state.value = _state.value.copy(
            checkingCompat = modo, compatWa = _state.value.compatWa - modo, message = "",
        )
    }

    /**
     * Abre o seletor de contas do Instagram (toque longo na aba Perfil). O
     * dono escolhe; o servico ve a troca, refaz a conferencia so leitura de
     * [modo] na conta nova e volta para o app. Nenhuma senha passa pelo app.
     */
    fun trocarConta(modo: Modo) {
        if (_state.value.running || _state.value.checkingCompat != null || pendingTroca != null) return
        cancelRequested = false
        pauseRequested = false
        pendingTroca = modo
        _state.value = _state.value.copy(trocandoConta = true, message = "")
    }

    /** Chamado pela UI. O servico observa [pendingPlan] e comeca a rodar. [texto] nao se usa no modo A. */
    fun start(
        plan: List<Batch>,
        modo: Modo,
        texto: String,
        intervaloMs: Long,
        autoConfirm: Boolean,
        remover: List<String> = emptyList(),
    ) {
        this.autoConfirm = autoConfirm
        this.intervaloMs = intervaloMs
        this.remover = remover
        campanha = null
        cancelRequested = false
        pauseRequested = false
        pendingPlan = plan
        _state.value = RunState(
            phase = RunPhase.PREPARING, modo = modo, plan = plan, running = true,
            lists = plan.map { ListProgress(it.index, it.label, it.followers.size) },
            compat = _state.value.compat,
            appVersion = _state.value.appVersion,
        )
    }

    /** Por que a operacao pausou sozinha (tela bloqueada, ligacao); "" = pelo dono. */
    @Volatile var motivoPausa: String = ""
        private set

    fun pause(motivo: String = "") { motivoPausa = motivo; pauseRequested = true }
    fun resume() { pauseRequested = false; motivoPausa = "" }
    fun cancel() {
        cancelRequested = true
        confirmGate?.complete(false)
    }

    /**
     * Modo C, lista ao vivo: sem lotes nem conferencia no meio. [anteriores] =
     * desfechos ja gravados desta operacao (retomada): contam e nao voltam.
     */
    fun startCampanha(c: Campanha, anteriores: List<PersonResult> = emptyList()) {
        (c.conta to c.inicio).let { if (it != linhasDe) { linhasAbertas.clear(); linhasDe = it } }
        autoConfirm = true
        intervaloMs = c.velocidade.segundos * 1000L
        remover = emptyList()
        campanha = c
        cancelRequested = false
        pauseRequested = false
        pendingPlan = emptyList()
        _state.value = RunState(
            phase = RunPhase.PREPARING, modo = Modo.DM, origem = c.origem, running = true,
            results = anteriores, limite = c.limite, compat = _state.value.compat, appVersion = _state.value.appVersion,
        )
    }

    /**
     * Modo C pelo WhatsApp: encaminha para o [WhatsAppAccessibilityService] via [pendingPlanWa].
     * Preserva [compatWa] e [appVersionWa] para que [valeParaVersao] passe dentro do serviço.
     */
    fun startCampanhaWa(c: Campanha, anteriores: List<PersonResult> = emptyList()) {
        (c.conta to c.inicio).let { if (it != linhasDe) { linhasAbertas.clear(); linhasDe = it } }
        autoConfirm = true
        intervaloMs = c.velocidade.segundos * 1000L
        remover = emptyList()
        campanha = c
        cancelRequested = false
        pauseRequested = false
        pendingPlanWa = emptyList()
        _state.value = RunState(
            phase = RunPhase.PREPARING, modo = Modo.DM, origem = c.origem, running = true,
            results = anteriores, limite = c.limite, isWa = true,
            // Preserva WA compat para que runPlan não bloqueie
            compatWa = _state.value.compatWa, appVersionWa = _state.value.appVersionWa,
        )
    }

    /**
     * Modo LISTAS_TRANSMISSAO pelo WhatsApp:
     * Fase 1 — o serviço criará as listas de transmissão usando a agenda do celular;
     * Fase 2 — o serviço enviará [mensagem] via cada lista criada.
     *
     * @param c campanha de origem (conta, velocidade, etc.)
     * @param mensagem texto a ser enviado nas listas (Fase 2)
     */
    fun startBroadcastWa(c: Campanha, mensagem: String) {
        (c.conta to c.inicio).let { if (it != linhasDe) { linhasAbertas.clear(); linhasDe = it } }
        autoConfirm = true
        intervaloMs = c.velocidade.segundos * 1000L
        remover = emptyList()
        campanha = c
        cancelRequested = false
        pauseRequested = false
        pendingBroadcastWa = mensagem
        _state.value = RunState(
            phase = RunPhase.PREPARING, modo = Modo.LISTAS_TRANSMISSAO, origem = null, running = true,
            isWa = true, broadcastFase = 1,
            compatWa = _state.value.compatWa, appVersionWa = _state.value.appVersionWa,
        )
    }

    /**
     * Inicia apenas a Fase 1 (cria listas de transmissão) — sem mensagem.
     * Após [RunPhase.LISTS_CREATED], o app navega para a tela de mensagem.
     */
    fun startCreateListsWa(c: Campanha) {
        (c.conta to c.inicio).let { if (it != linhasDe) { linhasAbertas.clear(); linhasDe = it } }
        autoConfirm = true
        intervaloMs = c.velocidade.segundos * 1000L
        remover = emptyList()
        campanha = c
        cancelRequested = false
        pauseRequested = false
        pendingBroadcastWa = ONLY_PHASE_1  // sinaliza: só Fase 1
        pendingBroadcastPhase2 = null
        pendingBroadcastPhase2Listas = null
        _state.value = RunState(
            phase = RunPhase.PREPARING, modo = Modo.LISTAS_TRANSMISSAO, origem = null, running = true,
            isWa = true, broadcastFase = 1,
            compatWa = _state.value.compatWa, appVersionWa = _state.value.appVersionWa,
        )
    }

    /**
     * Apenas escaneia as listas de transmissão existentes no WhatsApp — sem criar novas.
     * Útil para atualizar a lista na Etapa 4 quando o usuário criou listas manualmente.
     */
    fun startScanOnlyWa() {
        cancelRequested = false
        pauseRequested = false
        pendingBroadcastWa = ONLY_SCAN   // sinaliza: só escanear
        pendingBroadcastPhase2 = null
        pendingBroadcastPhase2Listas = null
        // Preserva as listas e os parâmetros de compatibilidade; apenas reinicia a fase
        _state.value = _state.value.copy(
            phase = RunPhase.PREPARING,
            modo = Modo.LISTAS_TRANSMISSAO,
            origem = null,
            running = true,
            isWa = true,
            broadcastFase = 1,
            message = "Lendo listas do WhatsApp…",
            // broadcastListas é preservado para o usuário continuar vendo as listas enquanto o scan ocorre
        )
    }

    /**
     * Inicia a Fase 2 (envia mensagem pelas listas selecionadas ou todas as criadas).
     * Requer que a Fase 1 já tenha sido executada ([RunPhase.LISTS_CREATED] no estado).
     *
     * @param mensagem texto da mensagem a enviar
     * @param listasIndices conjunto de índices das listas a enviar (vazio = envia por todas)
     */
    fun startSendViaListsWa(mensagem: String, listasIndices: Set<Int> = emptySet()) {
        cancelRequested = false
        pauseRequested = false
        pendingBroadcastPhase2 = mensagem
        pendingBroadcastPhase2Listas = listasIndices
        _state.value = _state.value.copy(
            phase = RunPhase.PREPARING,
            running = true,
            broadcastFase = 2,
        )
    }

    /** Sentinel: indica que só a Fase 1 deve ser executada (sem envio de mensagem). */
    const val ONLY_PHASE_1 = "__only_phase_1__"

    /** Sentinel: indica que só o escaneamento deve ser executado (sem criar listas nem enviar). */
    const val ONLY_SCAN = "__only_scan__"

    /** UI confirma (ou nega) seguir para o proximo lote / tocar em Concluir. */
    fun confirm(ok: Boolean) {
        confirmGate?.complete(ok)
    }

    // ---- usados pelo servico ----

    internal fun update(transform: (RunState) -> RunState) {
        _state.value = transform(_state.value)
    }

    internal fun setPhase(p: RunPhase, msg: String = "") {
        _state.value = _state.value.copy(phase = p, message = msg)
    }

    internal fun addResult(r: PersonResult) {
        _state.value = _state.value.copy(results = _state.value.results + r)
    }

    /** A conta da operacao, conferida ao vivo na hora de iniciar ("Enviando como @"). */
    internal fun usarCampanha(c: Campanha) {
        campanha = c
        _state.value = _state.value.copy(conta = c.conta)
    }

    internal fun fimDaTroca() {
        pendingTroca = null
        _state.value = _state.value.copy(trocandoConta = false)
    }

    internal fun publishCompat(check: CompatCheck) {
        pendingCompat = null
        _state.value = _state.value.copy(
            compat = _state.value.compat + (check.modo to check), checkingCompat = null,
            appVersion = check.version,
        )
    }

    internal suspend fun awaitConfirmation(msg: String): Boolean {
        if (autoConfirm) return true
        val gate = CompletableDeferred<Boolean>()
        confirmGate = gate
        _state.value = _state.value.copy(
            phase = RunPhase.WAITING_CONFIRMATION, needsConfirmation = true, message = msg,
        )
        val ok = gate.await()
        confirmGate = null
        _state.value = _state.value.copy(needsConfirmation = false)
        return ok
    }

    internal fun finish(phase: RunPhase, msg: String = "") {
        pendingPlan = null
        pendingBroadcastWa = null
        pendingBroadcastPhase2 = null
        pendingBroadcastPhase2Listas = null
        _state.value = _state.value.copy(phase = phase, message = msg, running = false, atual = "")
    }

    /** Atualiza o progresso de uma lista de transmissão específica pelo index. */
    internal fun updateBroadcastLista(index: Int, f: (BroadcastListaProgress) -> BroadcastListaProgress) {
        _state.value = _state.value.copy(
            broadcastListas = _state.value.broadcastListas.map { if (it.index == index) f(it) else it }
        )
    }
}
