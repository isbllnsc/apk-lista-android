package com.listalocal.e2e

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.listalocal.core.followers.FollowerImport
import com.listalocal.core.tree.UiNode
import com.listalocal.service.NodeOps
import com.listalocal.service.Tela
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A tela real para os testes no celular: o MESMO NodeOps do servico (janelas,
 * Janelas.escolher, NodeAdapter, conferencias antes do toque), so que lendo
 * pelo UiAutomation com as flags do servico (FLAG_REPORT_VIEW_IDS |
 * FLAG_RETRIEVE_INTERACTIVE_WINDOWS) e sem suprimir os servicos de
 * Acessibilidade do dono. Os eventos do Instagram acordam as esperas, como no
 * servico (OTIMIZACAO.md).
 *
 * Diferenca consciente: os links abrem por "am start" (shell do UiAutomation),
 * nao por startActivity do processo de teste, que o Android pode barrar como
 * inicio em segundo plano. O servico abre pelo proprio contexto.
 */
internal class TelaReal : Tela {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ua: UiAutomation = instr.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).also {
        val info = it.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        it.serviceInfo = info
    }
    val ops = NodeOps(instr.targetContext, { ua.windows }, { ua.rootInActiveWindow }, IG)
    private val sinal = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var comEventos = false

    init {
        ua.setOnAccessibilityEventListener { e ->
            if (ops.lembrar(e)) {
                comEventos = true
                sinal.trySend(Unit)
            }
        }
    }

    fun fechar() = ua.setOnAccessibilityEventListener(null)

    override fun ler(): UiNode? = ops.snapshot()
    override fun tocar(no: UiNode, mesmoPausado: Boolean) = ops.click(no, mesmoPausado)
    override fun escrever(no: UiNode, texto: String) = ops.setText(no, texto)
    override fun rolar(no: UiNode) = ops.scrollForward(no)
    override fun existe(ids: List<String>) = ops.existe(ids)
    override fun teclado() = ops.teclado()
    override val avisaMudancas get() = comEventos
    override fun agora(): Long = SystemClock.elapsedRealtime()

    override fun abrirConversa(username: String): Boolean {
        if (FollowerImport.username(username) != username) return false
        return shell("am start -a android.intent.action.VIEW -d https://ig.me/m/$username -p $IG")
    }

    override fun abrirInstagram(): Boolean {
        val c = instr.targetContext.packageManager.getLaunchIntentForPackage(IG)?.component ?: return false
        return shell("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ${c.flattenToShortString()}")
    }

    override fun voltar(): Boolean = ua.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    // Gestos como no servico (Configuracoes e atividade, Bloks), pelo "input" do shell.
    override fun arrastar(): Boolean {
        if (!ops.podeGesto()) return false
        val m = instr.targetContext.resources.displayMetrics
        val x = m.widthPixels / 2
        return shell("input swipe $x ${(m.heightPixels * 0.70f).toInt()} $x ${(m.heightPixels * 0.35f).toInt()} 600")
    }

    override fun tocarNoPonto(no: UiNode): Boolean {
        val (x, y) = ops.centro(no) ?: return false
        return shell("input tap ${x.toInt()} ${y.toInt()}")
    }

    override fun tocarLongo(no: UiNode) = ops.longClick(no)

    override suspend fun esperar(ms: Long) = delay(ms)

    override suspend fun esperarEvento(ms: Long): Boolean {
        if (!comEventos) {
            delay(ms)
            return true
        }
        return withTimeoutOrNull(ms) { sinal.receive() } != null
    }

    /** Roda no shell e espera terminar; false se o am reclamou. */
    private fun shell(cmd: String): Boolean {
        val saida = ua.executeShellCommand(cmd).let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        }
        val ok = !saida.contains("Error", ignoreCase = true)
        if (!ok) Log.i(TAG, "am falhou (a saida traz o link com o @: nao vai ao log)")
        return ok
    }

    companion object {
        const val IG = "com.instagram.android"
        const val TAG = "E2E"
    }
}
