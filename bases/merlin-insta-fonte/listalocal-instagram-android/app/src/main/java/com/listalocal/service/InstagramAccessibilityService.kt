package com.listalocal.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import com.listalocal.core.followers.Batch
import com.listalocal.core.followers.FollowerImport
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

/**
 * Motor no aparelho. Restrito ao Instagram (ver TargetApp e
 * accessibility_service_config.xml). Dirige os fluxos puros (DmFlow,
 * CloseFriendsFlow) com a tela real.
 *
 * Seguranca: so a conversa 1:1 aberta pelo link ig.me; o @ exato provado
 * antes de escrever; "vou enviar" gravado antes do toque; um toque em Enviar;
 * sem evidencia = INCERTO, nunca reenviado; nenhum grupo; tela desconhecida =
 * parada segura. Sem atraso sorteado, sem digitacao simulada: o intervalo e o
 * que o dono escolheu. Logs mascarados.
 */
class InstagramAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var ops: NodeOps
    @Volatile private var running = false
    private var overlay: View? = null
    @Volatile private var disabledForExpiry = false

    private val app = TargetApp.INSTAGRAM
    private val perfis by lazy { PerfisAprendidos(this) }

    /** Idioma do aparelho (parte da chave do perfil aprendido; "" se nao der para ler). */
    private fun idioma(): String = runCatching { resources.configuration.locales[0].language }.getOrDefault("")

    /**
     * O perfil que a operacao e a conferencia usam: o perfil fixo com o
     * APRENDIDO deste (versao + idioma + modo) por cima, e a assinatura ao vivo
     * ligada. Sem nada aprendido, o fixo com assinatura ao vivo (resolve por
     * papel quando o id fixo nao bate). Ordem por papel: aprendido -> fixo -> assinatura.
     */
    private fun perfilDoAparelho(version: String, modo: Modo): SelectorProfile? {
        val base = SelectorProfile.forVersion(version) ?: return null
        val idioma = idioma()
        // O aprendido do modo, com os ids/rotulos aprendidos NOUTROS modos (mesma versao+idioma)
        // por baixo: um papel compartilhado (ex.: profile_tab) calibrado noutro modo serve aqui.
        val aprendido = perfis.ler(version, idioma, modo.name)?.comIdsDe(
            Modo.entries.filter { it != modo }.mapNotNull { perfis.ler(version, idioma, it.name) },
        )
        return base.paraAparelho(aprendido)
    }

    /** Um sinal por mudanca de tela do Instagram (conflado: so "mudou desde a ultima vez"). */
    private val sinal = Channel<Unit>(Channel.CONFLATED)
    /** Este aparelho entrega eventos do Instagram: esperar por eles vale. Sem isso, espera fixa. */
    @Volatile private var comEventos = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (ExpiryGate.isExpired(this)) {
            expireService()
            return
        }
        ops = NodeOps(this, app.packageName)
        ops.bloqueado = { AutomationController.cancelRequested || AutomationController.pauseRequested }
        AutomationController.attach(true)
        AutomationController.update { it.copy(appVersion = appVersion() ?: "?") }
        scope.launch { watchLoop() }
        // Independente do runner: interrompe inclusive espera de confirmacao ou pausa.
        if (ExpiryGate.enabled) scope.launch {
            while (isActive) {
                if (ExpiryGate.isExpired(this@InstagramAccessibilityService)) {
                    expireService()
                    return@launch
                }
                delay(250)
            }
        }
        Log.i(TAG, "servico conectado; alvo=${app.packageName} versao=${appVersion() ?: "?"}")
    }

    /**
     * Thread principal: durante uma operacao ou conferencia, so copia o evento
     * (reserva de leitura, NodeOps) e acorda quem espera a tela mudar. Nenhuma
     * leitura do Instagram aqui. Fora delas, nada.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && running && ::ops.isInitialized && ops.lembrar(event)) {
            comEventos = true
            sinal.trySend(Unit)
        }
    }
    override fun onInterrupt() { AutomationController.pause() }

    override fun onUnbind(intent: Intent?): Boolean {
        AutomationController.attach(false)
        esconderPainel()
        scope.cancel()
        return super.onUnbind(intent)
    }

    // -------------------- a tela real para os fluxos --------------------

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
        override fun tocarNoPonto(no: UiNode): Boolean {
            val (x, y) = ops.centro(no) ?: return false
            return gesto(x, y, x, y, 80)
        }
        override fun arrastar(): Boolean {
            if (!ops.podeGesto()) return false
            val m = resources.displayMetrics
            val x = m.widthPixels / 2f
            // Devagar (600 ms) para quase nao embalar: o item e lido de novo com a tela parada.
            return gesto(x, m.heightPixels * 0.70f, x, m.heightPixels * 0.35f, 600)
        }

        override fun abrirConversa(username: String): Boolean {
            checkExpiry()
            // Pausado (o dono, tela bloqueada, ligacao): nada novo abre por cima, como os toques (NodeOps).
            if (AutomationController.pauseRequested) return false
            if (FollowerImport.username(username) != username) return false
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://ig.me/m/$username"))
                .setPackage(app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                startActivity(i); true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }

        override fun abrirInstagram(): Boolean {
            checkExpiry()
            if (AutomationController.pauseRequested) return false
            val i = packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            return true
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

        // ponytail: sem piso entre acordadas; o notificationTimeout (100 ms) ja
        // limita a ~10 eventos/s por tipo. Por piso aqui se o probe mostrar rajada.
        override suspend fun esperarEvento(ms: Long): Boolean {
            checkExpiry()
            if (AutomationController.cancelRequested) throw Interrompido("cancelado pelo dono")
            if (!comEventos) {
                delay(ms)
                return true
            }
            return withTimeoutOrNull(ms) { sinal.receive() } != null
        }
    }

    private fun gesto(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        checkExpiry()
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        return dispatchGesture(g, null, null)
    }

    // -------------------- loop principal --------------------

    private suspend fun watchLoop() {
        while (scope.isActive) {
            if (ExpiryGate.isExpired(this)) {
                expireService()
                return
            }
            val modo = AutomationController.pendingCompat
            if (modo != null && !running) {
                running = true
                try {
                    conferir(modo)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (ExpiryGate.isExpired(this)) expireService()
                    else AutomationController.publishCompat(
                        CompatCheck(modo, appVersion() ?: "?", failure = e.message ?: "falha na conferência"),
                    )
                } finally {
                    AutomationController.pendingCompat = null
                    running = false
                }
                voltarAoApp()
            }
            val troca = AutomationController.pendingTroca
            if (troca != null && !running) {
                running = true
                try {
                    trocarConta(troca)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (ExpiryGate.isExpired(this)) expireService()
                    else Log.w(TAG, "troca de conta: ${e.message}")
                } finally {
                    AutomationController.fimDaTroca()
                    running = false
                }
            }
            val plan = AutomationController.pendingPlan
            if (plan != null && !running) {
                running = true
                var manterPlano = false
                val vigia = vigiarTela()
                try {
                    runPlan(plan)
                } catch (e: CancellationException) {
                    // O sistema religou o servico (onUnbind cancela o escopo): a nova
                    // instancia encontra o plano e retoma, pulando quem ja tem desfecho.
                    if (ExpiryGate.isExpired(this)) {
                        expireService()
                    } else {
                        manterPlano = true
                        Log.w(TAG, "execucao interrompida por reconexao do servico; plano mantido para retomada")
                        AutomationController.setPhase(RunPhase.PREPARING, "serviço religado; retomando…")
                        throw e
                    }
                } catch (e: Expired) {
                    expireService()
                } catch (e: Interrompido) {
                    AutomationController.finish(RunPhase.CANCELLED, e.message ?: "cancelado")
                } catch (e: CloseFriendsFlow.ParadaSegura) {
                    AutomationController.finish(RunPhase.UI_CHANGED, e.message ?: "parada segura")
                } catch (e: Fila.FailedSafe) {
                    AutomationController.finish(RunPhase.FAILED_SAFE, e.message ?: "parada segura")
                } catch (e: Exception) {
                    Log.e(TAG, "falha inesperada na execucao", e)
                    AutomationController.finish(RunPhase.FAILED_SAFE, "erro: ${e.message}")
                } finally {
                    vigia.cancel()
                    if (!manterPlano) AutomationController.pendingPlan = null
                    esconderPainel()
                    running = false
                    // Fim (ou parada): o dono volta para o app e ve o resultado.
                    if (!manterPlano) voltarAoApp()
                }
            }
            delay(300)
        }
    }

    private class Expired : Exception()

    private fun checkExpiry() {
        if (ExpiryGate.isExpired(this)) {
            expireService()
            throw Expired()
        }
    }

    @Synchronized
    private fun expireService() {
        if (disabledForExpiry) return
        disabledForExpiry = true
        AutomationController.cancel()
        AutomationController.pendingPlan = null
        AutomationController.pendingCompat = null
        AutomationController.update {
            RunState(phase = RunPhase.CANCELLED, message = "Prazo encerrado em 04/10/2026.")
        }
        AutomationController.attach(false)
        Handler(Looper.getMainLooper()).post {
            try { disableSelf() }
            catch (e: Exception) { Log.e(TAG, "nao foi possivel desligar acessibilidade", e) }
        }
    }

    // -------------------- conferencia --------------------

    /**
     * "Conferir o Instagram" para um modo: navega ate a tela do modo sem
     * escrever, enviar nem marcar ninguem, anota o que encontrou e a conta
     * aberta, e volta para este app.
     */
    private suspend fun conferir(modo: Modo) {
        checkExpiry()
        val version = appVersion() ?: run {
            AutomationController.publishCompat(CompatCheck(modo, "?", failure = "o Instagram não está instalado"))
            return
        }
        val prof = perfilDoAparelho(version, modo) ?: run {
            AutomationController.publishCompat(CompatCheck(modo, version, failure = "sem perfil de seletores"))
            return
        }
        // Auto-calibracao: aprende os papeis alcançaveis do modo pela assinatura,
        // navegando so leitura (sem enviar nem marcar). Grava por versao+idioma+modo.
        val alvos = SignatureLibrary.doModo(if (modo == Modo.DM) SignatureLibrary.DM else SignatureLibrary.CF)
        val aprendiz = Aprendiz(alvos)
        val (achou, conta) = when (modo) {
            Modo.DM -> DmFlow(tela, prof, SemRegistro).conferir(aprender = { r, excluir -> aprendiz.viu(r, excluir = excluir) })
            Modo.AMIGOS_PROXIMOS ->
                CloseFriendsFlow(tela, prof, aprender = { r, excluir -> aprendiz.viu(r, excluir = excluir) })
                    .let { f -> f.conferir() to f.conta }
            Modo.LISTAS_TRANSMISSAO -> throw IllegalStateException("LISTAS_TRANSMISSAO não usa o serviço do Instagram")
        }
        // Liga o modo so o que o fluxo mediu; o perfil aprendido (so no disco, nunca em rede) so de uma que passou.
        val (check, gravar) = CompatCheck.fechar(
            modo, version, achou, conta,
            aprendiz.perfil(version, idioma(), modo.name, modo.chaves, System.currentTimeMillis()),
        )
        gravar?.let { p -> runCatching { perfis.salvar(p) }.onFailure { Log.w(TAG, "nao consegui gravar o perfil aprendido", it) } }
        Log.i(TAG, "conferencia ${modo.name} $version idioma=${idioma()}: aprendidos=${aprendiz.encontrados} faltando=${check.missing}")
        AutomationController.publishCompat(check)
    }

    /**
     * "Trocar de conta": abre o seletor de contas do Instagram e espera o dono
     * escolher. Trocou: refaz a conferencia so leitura de [modo] na conta nova
     * e volta para o app mostrando "Enviando como @nova". Nao trocou no prazo:
     * deixa o dono no Instagram (ao iniciar, a conta e lida de novo).
     */
    private suspend fun trocarConta(modo: Modo) {
        checkExpiry()
        val prof = appVersion()?.let { perfilDoAparelho(it, modo) } ?: return
        val seletor = Conta.abrirSeletor(tela, prof) ?: run {
            voltarAoApp()
            return
        }
        // Outra que a aberta agora (o dono pode ter trocado pelo proprio Instagram depois da conferencia); sem como
        // ler na tela da aba, a da conferencia.
        val antes = seletor.antes ?: AutomationController.state.value.compat[modo]?.conta
        Conta.esperarOutra(tela, prof, antes) ?: return
        tela.esperar(1_000) // o Instagram terminar de trocar antes da leitura
        conferir(modo)
        voltarAoApp()
    }

    /** A conferencia termina no Instagram; traz o dono de volta para ver o resultado. */
    private fun voltarAoApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    /** A conferencia nao envia nada: nao ha o que gravar. */
    private object SemRegistro : Registro {
        override fun marcarCommit(username: String) = false
        override fun gravar(username: String, desfecho: Desfecho, motivo: String, enviadas: List<Int>) = Unit
    }

    // -------------------- execucao --------------------

    /** [plan]: a lista do modo A. O modo C le as pessoas ao vivo (AutomationController.campanha). */
    private suspend fun runPlan(plan: List<Batch>) {
        checkExpiry()
        val modo = AutomationController.state.value.modo
        val version = appVersion()
        AutomationController.update { it.copy(appVersion = version ?: "?") }
        if (version == null) {
            AutomationController.finish(RunPhase.VERSION_UNSUPPORTED, "O Instagram não está instalado neste aparelho.")
            return
        }
        val prof = perfilDoAparelho(version, modo)
        val compat = AutomationController.state.value.compat[modo]
        if (prof == null || compat == null || !compat.valeParaVersao(version)) {
            AutomationController.finish(
                RunPhase.VERSION_UNSUPPORTED,
                "Confira o Instagram de novo antes de iniciar (versão $version ainda não conferida para este modo).",
            )
            return
        }
        mostrarPainel()
        AutomationController.setPhase(RunPhase.OPENING_APP)
        // A versao conferida vale so para ela: atualizada no meio (a Play Store, de noite), a fila para.
        val fila = Fila(tela, versaoMudou = { appVersion().let { if (it == version) null else it ?: "?" } }) { confirmarNoApp(it) }
        when (modo) {
            Modo.DM -> {
                val c = AutomationController.campanha ?: throw Fila.FailedSafe("operação sem origem")
                c.pararAs?.let { Fila.horarioJaPassou(it, c.inicio, java.time.ZonedDateTime.now()) }?.let { throw Fila.FailedSafe(it) }
                // A conta e a aberta no Instagram agora, lida ao vivo, e tem de ser a da operacao (a do plano). Outra:
                // para sem enviar, sem converter a operacao nem apagar a salva (Conta.naOperacao).
                AutomationController.setPhase(RunPhase.OPENING_APP, "lendo a conta aberta no Instagram…")
                val viva = Conta.lerAoIniciar(tela) { DmFlow(tela, prof, SemRegistro).contaAberta()?.let(FollowerImport::username) }
                Conta.naOperacao(c.conta, viva)?.let { throw Fila.FailedSafe(it) }
                val campanhas = Campanhas(this)
                AutomationController.usarCampanha(c)
                AutomationController.setPhase(RunPhase.OPENING_APP, "enviando como @$viva")
                val registro = Outcomes(this, c.conta)
                val filtro = Fila.Filtro(
                    conta = c.conta, naoEnviar = c.naoEnviar, soPara = c.soPara,
                    pediramParar = registro.pediramParaParar(), jaReceberam = registro.bloqueados(c.pularJaRecebeu),
                    pularComerciais = c.pularComerciais,
                )
                // "Parar as HH:MM" no relogio da tela (o do aparelho pode mudar no meio).
                val prazo = c.pararAs?.let { tela.agora() + Fila.msAte(it, java.time.LocalTime.now()) }
                val fonte = when (c.origem) {
                    Origem.SEGUIDORES -> FonteSeguidores(tela, prof)
                    Origem.CONVERSAS -> FonteConversas(tela, prof, c.conta, AutomationController.linhasAbertas)
                }
                // O intervalo do dono conta do ultimo envio gravado desta conta (Parar e Retomar, servico religado).
                val ultimoEnvio = registro.ultimoEnvio()?.let { tela.agora() - (System.currentTimeMillis() - it).coerceAtLeast(0) }
                fila.aoVivo(fonte, DmFlow(tela, prof, registro), filtro, prazo, ultimoEnvio) { campanhas.ponto(c.conta, it) }
                // Fim da lista: nada a retomar. Parada (Parar, restricao, seguranca): fica para retomar.
                if (AutomationController.state.value.phase == RunPhase.COMPLETED) campanhas.encerrar(c.conta)
            }
            Modo.AMIGOS_PROXIMOS -> fila.amigos(plan, CloseFriendsFlow(tela, prof), compat.conta)
            Modo.LISTAS_TRANSMISSAO -> throw Fila.FailedSafe("LISTAS_TRANSMISSAO opera via WhatsApp, não via Instagram")
        }
    }

    /**
     * A conferencia acontece no app (o Instagram esta na frente durante a
     * operacao): traz o app para a frente e espera a resposta do dono.
     */
    private suspend fun confirmarNoApp(pergunta: String): Boolean {
        if (!AutomationController.autoConfirm) voltarAoApp()
        return AutomationController.awaitConfirmation(pergunta)
    }

    // -------------------- painel flutuante --------------------

    /**
     * "Pausar/Continuar" e "Parar" por cima do Instagram durante a execucao.
     * Janela de acessibilidade que nao pega foco (nao atrapalha a leitura da
     * tela) e mantem a tela acesa (bloqueada pelo dono, pausa: [vigiarTela]).
     */
    private fun mostrarPainel() {
        val h = Handler(Looper.getMainLooper())
        h.post {
            if (overlay != null) return@post
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            // O rotulo segue o ESTADO (antes: so o ultimo toque nele). A fila pausa sozinha (restricao, conta trocada,
            // tela bloqueada): com "Pausar" ainda escrito, o toque do dono para garantir a parada RETOMAVA o envio.
            var mostrada = Painel.Acao.PAUSAR
            val pausar = Button(this)
            fun atualizar() {
                mostrada = Painel.acao(AutomationController.pauseRequested, AutomationController.state.value.phase)
                pausar.text = mostrada.rotulo
            }
            pausar.setOnClickListener {
                when (Painel.aoTocar(mostrada, AutomationController.pauseRequested, AutomationController.state.value.phase)) {
                    Painel.Acao.PAUSAR -> AutomationController.pause()
                    Painel.Acao.CONTINUAR -> AutomationController.resume()
                    Painel.Acao.VER_AVISO -> voltarAoApp()
                    null -> Unit // o estado mudou antes do toque: so atualiza o rotulo
                }
                atualizar()
            }
            atualizar()
            val parar = Button(this).apply {
                text = "Parar"
                setOnClickListener { AutomationController.cancel() }
            }
            val painel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(pausar)
                addView(parar)
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
            runCatching { wm.addView(painel, lp); overlay = painel }
                .onFailure { Log.w(TAG, "painel flutuante indisponivel", it) }
            h.post(object : Runnable {
                override fun run() {
                    if (overlay !== painel) return
                    atualizar()
                    h.postDelayed(this, 300)
                }
            })
        }
    }

    /**
     * Durante a operacao: a tela apagada ou bloqueada, ou uma ligacao comecando, pausam com o motivo. Antes, a tela
     * bloqueada virava "Nao consegui ver qual conta esta aberta" depois de Voltar na tela de bloqueio, e na ligacao o
     * Instagram era puxado por cima da chamada a cada pessoa. So na mudanca: com o dono tocando em Continuar, segue
     * (um app que prenda o modo de audio nao trava a operacao).
     */
    private fun vigiarTela() = scope.launch {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val kg = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        var antes: String? = null
        while (isActive) {
            val motivo = runCatching { Painel.bloqueio(power.isInteractive, kg.isKeyguardLocked, audio.mode) }.getOrNull()
            if (motivo != null && antes == null && !AutomationController.pauseRequested) {
                AutomationController.pause(motivo)
                AutomationController.setPhase(RunPhase.PAUSED, motivo)
            }
            antes = motivo
            delay(500)
        }
    }

    private fun esconderPainel() {
        Handler(Looper.getMainLooper()).post {
            val v = overlay ?: return@post
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
            overlay = null
        }
    }

    private fun appVersion(): String? = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(app.packageName, 0).versionName
    } catch (_: Exception) { null }

    companion object {
        private const val TAG = "ListaLocalIG"
    }
}

/** O painel flutuante e o que pausa a operacao sozinho, sem Android (testes). */
internal object Painel {
    enum class Acao(val rotulo: String) { PAUSAR("Pausar"), CONTINUAR("Continuar"), VER_AVISO("Ver aviso no app") }

    /** O botao de cima agora. Aviso de restricao: so o Continuar do app, com o aviso na frente (o toque aqui abre o app). */
    fun acao(pausado: Boolean, fase: RunPhase): Acao = when {
        !pausado -> Acao.PAUSAR
        fase == RunPhase.RESTRICTED -> Acao.VER_AVISO
        else -> Acao.CONTINUAR
    }

    /** O toque faz o que o botao MOSTRAVA ([mostrada]); se o estado mudou antes dele, nada (null): so o rotulo muda. */
    fun aoTocar(mostrada: Acao, pausado: Boolean, fase: RunPhase): Acao? = mostrada.takeIf { it == acao(pausado, fase) }

    /** Por que pausar sozinho: a tela apagada ou bloqueada, ou uma ligacao. null = nada. */
    fun bloqueio(telaLigada: Boolean, bloqueada: Boolean, modoAudio: Int): String? = when {
        !telaLigada || bloqueada ->
            "A tela do celular foi bloqueada: a operação pausou sem tocar em nada. Desbloqueie e toque em Continuar."
        modoAudio == AudioManager.MODE_IN_CALL || modoAudio == AudioManager.MODE_IN_COMMUNICATION ||
            modoAudio == AudioManager.MODE_RINGTONE ->
            "Uma ligação começou: a operação pausou sem tocar em nada. Quando terminar, toque em Continuar."
        else -> null
    }
}
