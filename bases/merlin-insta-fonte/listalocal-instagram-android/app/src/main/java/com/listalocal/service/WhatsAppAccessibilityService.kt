package com.listalocal.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.listalocal.core.selectors.Aprendiz
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.selectors.SignatureLibrary
import com.listalocal.core.selectors.TargetApp
import com.listalocal.core.tree.UiNode
import com.listalocal.data.Campanhas
import com.listalocal.data.Outcomes
import com.listalocal.data.PerfisAprendidos
import com.listalocal.expiry.ExpiryGate
import com.listalocal.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.listalocal.core.followers.Batch

/**
 * Motor no aparelho para o WhatsApp. Restrito a com.whatsapp
 * (ver TargetApp e accessibility_service_config_wa.xml).
 *
 * Sem canPerformGestures (R10): toda interação é por performAction no nó.
 * Sem atraso sorteado, sem digitação simulada. Logs mascarados.
 */
class WhatsAppAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var ops: NodeOps
    private lateinit var waOps: WaBroadcastNodeOps
    @Volatile private var running = false
    @Volatile private var disabledForExpiry = false

    private val app = TargetApp.WHATSAPP
    private val perfis by lazy { PerfisAprendidos(this) }

    private fun idioma(): String =
        runCatching { resources.configuration.locales[0].language }.getOrDefault("")

    private fun perfilDoAparelho(version: String, modo: Modo): SelectorProfile? {
        val base = SelectorProfile.forVersion(version) ?: return null
        val idioma = idioma()
        val aprendido = perfis.ler(version, idioma, modo.name)?.comIdsDe(
            Modo.entries.filter { it != modo }.mapNotNull { perfis.ler(version, idioma, it.name) },
        )
        return base.paraAparelho(aprendido)
    }

    private val sinal = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var comEventos = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (ExpiryGate.isExpired(this)) { expireService(); return }
        ops = NodeOps(this, app.packageName)
        ops.bloqueado = { AutomationController.cancelRequested || AutomationController.pauseRequested }
        waOps = WaBroadcastNodeOps(this, app.packageName)
        AutomationController.attachWa(true)
        AutomationController.update { it.copy(appVersionWa = appVersionWa() ?: "?") }
        scope.launch { watchLoop() }
        if (ExpiryGate.enabled) scope.launch {
            while (isActive) {
                if (ExpiryGate.isExpired(this@WhatsAppAccessibilityService)) { expireService(); return@launch }
                delay(250)
            }
        }
        Log.i(TAG, "serviço WA conectado; versão=${appVersionWa() ?: "?"}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && running && ::ops.isInitialized && ops.lembrar(event)) {
            comEventos = true
            sinal.trySend(Unit)
        }
    }

    override fun onInterrupt() { AutomationController.pause() }

    override fun onUnbind(intent: Intent?): Boolean {
        AutomationController.attachWa(false)
        scope.cancel()
        return super.onUnbind(intent)
    }

    // ── Tela real para os fluxos ──────────────────────────────────────────────

    private val tela = object : Tela {
        override fun ler(): UiNode? = ops.snapshot()
        override fun tocar(no: UiNode, mesmoPausado: Boolean) = ops.click(no, mesmoPausado)
        override fun tocarEnviar(no: UiNode) = ops.clicar(no, mesmoPausado = true)
        override fun escrever(no: UiNode, texto: String) = ops.setText(no, texto)
        override fun rolar(no: UiNode) = ops.scrollForward(no)
        override fun rolarParaTras(no: UiNode) = ops.scrollBackward(no)
        override fun existe(ids: List<String>) = ops.existe(ids)
        override fun textosPorId(id: String) = ops.textosPorId(id)
        override fun teclado() = ops.teclado()
        override val avisaMudancas get() = comEventos
        override fun agora(): Long = SystemClock.elapsedRealtime()
        override fun tocarLongo(no: UiNode) = ops.longClick(no)
        override fun diagnostico() = ops.diagnostico()

        // Sem gestos (canPerformGestures=false, R10)
        override fun tocarNoPonto(no: UiNode): Boolean = false
        override fun arrastar(): Boolean = false

        override fun abrirConversa(username: String): Boolean {
            checkExpiry()
            if (AutomationController.pauseRequested) return false
            val numero = username.removePrefix("+").filter { it.isDigit() }
            if (numero.length < 8) return false
            // whatsapp://send?phone= espera apenas os dígitos do número E.164 (sem o + sinal).
            // Usar %2B ou + no parâmetro phone abre o diálogo "Pesquisando..." em vez da conversa.
            val uri = Uri.parse("whatsapp://send?phone=$numero")
            val i = Intent(Intent.ACTION_VIEW, uri)
                .setPackage(app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return try {
                startActivity(i); true
            } catch (_: Exception) {
                false
            }
        }

        // Tela.abrirInstagram() — no contexto WA, abre a tela principal do WhatsApp (lista de conversas)
        override fun abrirInstagram(): Boolean {
            checkExpiry()
            if (AutomationController.pauseRequested) return false
            // Abre diretamente a activity principal (com.whatsapp.Main) com CLEAR_TOP + CLEAR_TASK:
            // garante que o WhatsApp abre na lista de conversas, não na última conversa aberta
            // (caso o APK tenha sido instalado via WhatsApp e o app retomasse aquela conversa).
            val i = Intent().apply {
                setClassName(app.packageName, "com.whatsapp.Main")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            return try {
                startActivity(i); true
            } catch (_: Exception) {
                // Fallback: getLaunchIntentForPackage se Main não for acessível
                val fallback = packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                try { startActivity(fallback); true } catch (_: Exception) { false }
            }
        }


        override fun voltar(): Boolean {
            checkExpiry()
            return performGlobalAction(GLOBAL_ACTION_BACK)
        }

        override suspend fun esperar(ms: Long) {
            checkExpiry()
            if (AutomationController.cancelRequested) throw Interrompido("cancelado pelo dono")
            delay(ms)
        }

        override suspend fun esperarEvento(ms: Long): Boolean {
            checkExpiry()
            if (AutomationController.cancelRequested) throw Interrompido("cancelado pelo dono")
            if (!comEventos) { delay(ms); return true }
            return withTimeoutOrNull(ms) { sinal.receive() } != null
        }
    }

    // ── Loop principal ────────────────────────────────────────────────────────

    private suspend fun watchLoop() {
        while (scope.isActive) {
            if (ExpiryGate.isExpired(this)) { expireService(); return }

            val modo = AutomationController.pendingCompatWa
            if (modo != null && !running) {
                running = true
                try {
                    conferir(modo)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (ExpiryGate.isExpired(this)) expireService()
                    else AutomationController.publishCompatWa(
                        CompatCheck(modo, appVersionWa() ?: "?", failure = e.message ?: "falha na conferência WA"),
                    )
                } finally {
                    AutomationController.pendingCompatWa = null
                    running = false
                }
                voltarAoApp()
            }

            // ── Modo LISTAS_TRANSMISSAO — Fase 1 (criar listas) ──────────────
            val mensagemBroadcast = AutomationController.pendingBroadcastWa
            if (mensagemBroadcast != null && !running) {
                running = true
                try {
                    val version = appVersionWa()
                    val prof = if (version != null) perfilDoAparelho(version, Modo.DM)
                        ?: com.listalocal.core.selectors.SelectorProfile.forVersion("") else null
                    if (prof == null) {
                        AutomationController.finish(
                            RunPhase.VERSION_UNSUPPORTED,
                            "Versão do WhatsApp sem perfil de seletores. Reconecte e tente novamente."
                        )
                    } else {
                        val runner = WaBroadcastRunner(
                            ctx = this,
                            waOps = waOps,
                            tela = tela,
                            prof = prof,
                            checkExpiry = { checkExpiry() },
                            confirmarNoApp = { pergunta -> confirmarNoApp(pergunta) },
                            soPor5521 = AutomationController.soPor5521,
                        )
                        if (mensagemBroadcast == AutomationController.ONLY_PHASE_1) {
                            // Novo fluxo: só Fase 1, termina com LISTS_CREATED
                            runner.runPhase1()
                        } else {
                            // Fluxo antigo: Fase 1 + Fase 2 contínuas
                            runner.run(mensagemBroadcast)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Expired) {
                    expireService()
                } catch (e: Interrompido) {
                    AutomationController.finish(RunPhase.CANCELLED, e.message ?: "cancelado")
                } catch (e: Exception) {
                    Log.e(TAG, "falha no broadcast WA Fase 1", e)
                    AutomationController.finish(RunPhase.FAILED_SAFE, "erro: ${e.message}")
                } finally {
                    AutomationController.pendingBroadcastWa = null
                    running = false
                    voltarAoApp()
                }
            }

            // ── Modo LISTAS_TRANSMISSAO — Fase 2 (enviar mensagem) ───────────
            val mensagemFase2 = AutomationController.pendingBroadcastPhase2
            val listasFase2 = AutomationController.pendingBroadcastPhase2Listas ?: emptySet()
            if (mensagemFase2 != null && !running) {
                running = true
                try {
                    val version = appVersionWa()
                    val prof = if (version != null) perfilDoAparelho(version, Modo.DM)
                        ?: com.listalocal.core.selectors.SelectorProfile.forVersion("") else null
                    if (prof == null) {
                        AutomationController.finish(RunPhase.VERSION_UNSUPPORTED, "Versão sem perfil.")
                    } else {
                        val runner = WaBroadcastRunner(
                            ctx = this,
                            waOps = waOps,
                            tela = tela,
                            prof = prof,
                            checkExpiry = { checkExpiry() },
                            confirmarNoApp = { pergunta -> confirmarNoApp(pergunta) },
                            soPor5521 = AutomationController.soPor5521,
                        )
                        runner.runPhase2(mensagemFase2, listasFase2)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Expired) {
                    expireService()
                } catch (e: Interrompido) {
                    AutomationController.finish(RunPhase.CANCELLED, e.message ?: "cancelado")
                } catch (e: Exception) {
                    Log.e(TAG, "falha no broadcast WA Fase 2", e)
                    AutomationController.finish(RunPhase.FAILED_SAFE, "erro: ${e.message}")
                } finally {
                    AutomationController.pendingBroadcastPhase2 = null
                    AutomationController.pendingBroadcastPhase2Listas = null
                    running = false
                    voltarAoApp()
                }
            }

            val plan = AutomationController.pendingPlanWa
            if (plan != null && !running) {
                running = true
                var manterPlano = false
                val vigia = vigiarTela()
                try {
                    runPlan(plan)
                } catch (e: CancellationException) {
                    if (ExpiryGate.isExpired(this)) expireService()
                    else { manterPlano = true; throw e }
                } catch (e: Expired) {
                    expireService()
                } catch (e: Interrompido) {
                    AutomationController.finish(RunPhase.CANCELLED, e.message ?: "cancelado")
                } catch (e: Fila.FailedSafe) {
                    AutomationController.finish(RunPhase.FAILED_SAFE, e.message ?: "parada segura")
                } catch (e: Exception) {
                    Log.e(TAG, "falha inesperada WA", e)
                    AutomationController.finish(RunPhase.FAILED_SAFE, "erro: ${e.message}")
                } finally {
                    vigia.cancel()
                    if (!manterPlano) AutomationController.pendingPlanWa = null
                    running = false
                    if (!manterPlano) voltarAoApp()
                }
            }

            delay(300)
        }
    }

    private class Expired : Exception()

    private fun checkExpiry() {
        if (ExpiryGate.isExpired(this)) { expireService(); throw Expired() }
    }

    @Synchronized
    private fun expireService() {
        if (disabledForExpiry) return
        disabledForExpiry = true
        AutomationController.cancel()
        AutomationController.pendingPlanWa = null
        AutomationController.pendingCompatWa = null
        AutomationController.update {
            RunState(phase = RunPhase.CANCELLED, message = "Prazo encerrado em 04/10/2026.")
        }
        AutomationController.attachWa(false)
        Handler(Looper.getMainLooper()).post {
            try { disableSelf() } catch (e: Exception) { Log.e(TAG, "não foi possível desligar acessibilidade WA", e) }
        }
    }

    // ── Conferência ───────────────────────────────────────────────────────────

    private suspend fun conferir(modo: Modo) {
        checkExpiry()
        val version = appVersionWa() ?: run {
            AutomationController.publishCompatWa(CompatCheck(modo, "?", failure = "o WhatsApp não está instalado"))
            return
        }
        // WaDmFlow usa IDs diretos do WhatsApp — SelectorProfile não é usado na conferência WA.
        val (achou, _) = WaDmFlow(tela, perfilDoAparelho(version, modo) ?: SelectorProfile.forVersion("") ?: run { AutomationController.publishCompatWa(CompatCheck(modo, version, failure = "sem perfil base")); return }, SemRegistro).conferir()
        val chavesFaltando = WaDmFlow.CHAVES_CONFERENCIA.filter { achou[it] != true }
        val check = if (chavesFaltando.isEmpty()) {
            // Passado: mapear todas as chaves do modo para `true` → CompatCheck.ok = true
            CompatCheck(modo, version, found = modo.chaves.associateWith { true })
        } else {
            val motivo = "faltou na tela do WhatsApp: ${chavesFaltando.joinToString(", ") { waRotulo(it) }}"
            CompatCheck(modo, version, failure = motivo)
        }
        Log.i(TAG, "conferência WA ${modo.name} $version: achou=$achou faltando=$chavesFaltando ok=${check.ok}")
        AutomationController.publishCompatWa(check)
    }

    /** Rótulo legível para chaves internas do WaDmFlow. */
    private fun waRotulo(chave: String) = when (chave) {
        "wa_inbox" -> "lista de conversas"
        "wa_fab" -> "botão Nova conversa"
        "wa_search_field" -> "campo de busca de contato (opcional)"
        else -> chave
    }

    private fun voltarAoApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    private object SemRegistro : Registro {
        override fun marcarCommit(username: String) = false
        override fun gravar(username: String, desfecho: Desfecho, motivo: String, enviadas: List<Int>) = Unit
    }

    // ── Execução do plano ─────────────────────────────────────────────────────

    private suspend fun runPlan(plan: List<Batch>) {
        checkExpiry()
        val modo = AutomationController.state.value.modo  // sempre DM para WhatsApp
        val version = appVersionWa()
        AutomationController.update { it.copy(appVersionWa = version ?: "?") }
        if (version == null) {
            AutomationController.finish(RunPhase.VERSION_UNSUPPORTED, "O WhatsApp não está instalado neste aparelho.")
            return
        }
        val prof = perfilDoAparelho(version, modo)
        val compat = AutomationController.state.value.compatWa[modo]
        // Para WA: basta o perfil existir. A verificação de compat é opcional — o usuário
        // já passou pela etapa de conferência. Se compat existe mas é de versão diferente,
        // ainda assim deixamos prosseguir (atualização menor do WA não quebra seletores).
        if (prof == null) {
            AutomationController.finish(
                RunPhase.VERSION_UNSUPPORTED,
                "Confira o WhatsApp de novo antes de iniciar (versão $version sem perfil).",
            )
            return
        }
        if (compat != null && !compat.ok) {
            AutomationController.finish(
                RunPhase.VERSION_UNSUPPORTED,
                "Conferência do WhatsApp falhou: ${compat.failure}. Confira novamente na Etapa 2.",
            )
            return
        }
        AutomationController.setPhase(RunPhase.OPENING_APP)
        val fila = Fila(
            tela,
            versaoMudou = { appVersionWa().let { if (it == version) null else it ?: "?" } },
        ) { confirmarNoApp(it) }
        val c = AutomationController.campanha ?: throw Fila.FailedSafe("operação sem origem")
        c.pararAs?.let { Fila.horarioJaPassou(it, c.inicio, java.time.ZonedDateTime.now()) }?.let { throw Fila.FailedSafe(it) }
        AutomationController.setPhase(RunPhase.OPENING_APP, "abrindo o WhatsApp…")
        val campanhas = Campanhas(this)
        AutomationController.usarCampanha(c)
        val registro = Outcomes(this, c.conta)
        val filtro = Fila.Filtro(
            conta = c.conta, naoEnviar = c.naoEnviar, soPara = c.soPara,
            pediramParar = registro.pediramParaParar(), jaReceberam = registro.bloqueados(c.pularJaRecebeu),
            pularComerciais = false,
        )
        val prazo = c.pararAs?.let { tela.agora() + Fila.msAte(it, java.time.LocalTime.now()) }
        val fonte = FonteAgenda(this, c)
        val ultimoEnvio = registro.ultimoEnvio()?.let { tela.agora() - (System.currentTimeMillis() - it).coerceAtLeast(0) }
        fila.aoVivoWa(fonte, WaDmFlow(tela, prof, registro), filtro, prazo, ultimoEnvio) { campanhas.ponto(c.conta, it) }
        if (AutomationController.state.value.phase == RunPhase.COMPLETED) campanhas.encerrar(c.conta)
    }

    // ── Vigilância de bloqueio/ligação ────────────────────────────────────────

    private fun vigiarTela() = scope.launch {
        delay(1_500) // pequena janela de graca para o fluxo inicializar
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val audio = getSystemService(AudioManager::class.java)
        while (isActive) {
            // So vigiar quando a automação estiver em execução — evita falso-positivo logo
            // ao iniciar, quando o sistema transiciona antes da campanha começar de fato.
            if (AutomationController.state.value.running && !AutomationController.pauseRequested) {
                val bloqueio = Painel.bloqueio(!power.isInteractive, keyguard.isKeyguardLocked, audio.mode)
                if (bloqueio != null) {
                    AutomationController.pause()
                    AutomationController.setPhase(RunPhase.RESTRICTED, bloqueio)
                }
            }
            delay(500)
        }
    }

    // ── Versão do WhatsApp ────────────────────────────────────────────────────

    private fun appVersionWa(): String? = runCatching {
        packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull()

    private suspend fun confirmarNoApp(pergunta: String): Boolean {
        if (!AutomationController.autoConfirm) voltarAoApp()
        return AutomationController.awaitConfirmation(pergunta)
    }


    companion object {
        private const val TAG = "WaA11y"
    }
}
