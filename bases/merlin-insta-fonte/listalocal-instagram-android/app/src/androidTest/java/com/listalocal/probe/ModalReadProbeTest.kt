package com.listalocal.probe

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.listalocal.core.privacy.Redaction
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.service.Janelas
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SONDA SO DE LEITURA (nada e digitado nem enviado). Prova, no celular real, se o canal
 * de Acessibilidade enxerga a conversa do Instagram (ModalActivity), que o uiautomator
 * e o rootInActiveWindow nao enxergam (INSTAGRAM-APP-REAL.md, 0.1 e 7.1).
 *
 * UiAutomation com as MESMAS flags do servico (flagReportViewIds|flagRetrieveInteractiveWindows)
 * e sem suprimir os servicos de Acessibilidade do dono. Abre https://ig.me/m/<@> como o app,
 * mede o tempo ate cada id aparecer em getWindows() e em rootInActiveWindow, e registra no
 * logcat (tag PROBE) o que cada API devolve. Resultado em PROBE-LEITURA.md.
 *
 * Nao roda sozinho: so com -e probe 1 (am instrument), para nunca abrir o Instagram do dono
 * num connectedAndroidTest comum. Os @ vem de -e usuarios a,b,c (nunca no codigo).
 *
 * Privacidade, como no app (Redaction): o log nunca traz @, nome nem texto de mensagem.
 * Cada caso vira c1, c2...; cada texto vira tamanho + Redaction.mask (hash curto e as 2
 * ultimas letras), com "=@" quando o texto E o @ do caso. Para conferir um texto esperado,
 * compare com Redaction.mask dele.
 */
@RunWith(AndroidJUnit4::class)
class ModalReadProbeTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ua: UiAutomation by lazy {
        instr.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).also {
            setFlags(it, includeNotImportant = false)
        }
    }

    private fun setFlags(u: UiAutomation, includeNotImportant: Boolean) {
        val info = u.serviceInfo
        var f = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        f = if (includeNotImportant) f or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        else f and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
        info.flags = f
        u.serviceInfo = info
    }

    private fun log(s: String) = Log.i("PROBE", s)

    @Test
    fun leTelasModais() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("so com -e probe 1", args.getString("probe") == "1")
        val usuarios = args.getString("usuarios").orEmpty().split(',').map { it.trim().removePrefix("@") }.filter { it.isNotEmpty() }
        assumeTrue("os @ vem de -e usuarios a,b,c", usuarios.isNotEmpty())
        val eventos = HashMap<String, Int>()
        // Quando cada janela avisou (ms desde o link): o servico acorda nesses eventos
        // para ler a conversa (OTIMIZACAO.md). Comparar com "tempo <id>".
        val quando = HashMap<Int, MutableList<Long>>()
        ua.setOnAccessibilityEventListener { e ->
            if (e.packageName == IG) synchronized(eventos) {
                val k = "win=${e.windowId} type=${e.eventType} src=${e.source != null}"
                eventos[k] = (eventos[k] ?: 0) + 1
                quando.getOrPut(e.windowId) { mutableListOf() } += SystemClock.uptimeMillis() - t0
            }
        }
        log("=== inicio sdk=${android.os.Build.VERSION.SDK_INT} ${android.os.Build.MODEL}")
        janelas("inicio")
        sairDaConversa("inicio")
        usuarios.forEachIndexed { i, u ->
            val caso = "c${i + 1}"
            log("[$caso] = ${Redaction.mask(u)}")
            alvo = u.lowercase()
            synchronized(eventos) { eventos.clear(); quando.clear() }
            sonda(caso, u)
            synchronized(eventos) {
                eventos.forEach { (k, n) -> log("[$caso] evento $k x$n") }
                quando.forEach { (w, ms) -> log("[$caso] evento_ms win=$w ${ms.take(40).joinToString(",")}") }
            }
            sairDaConversa(caso)
        }
        janelas("fim")
        ua.setOnAccessibilityEventListener(null)
    }

    /** Relogio do ultimo link aberto (os eventos contam dali). */
    @Volatile private var t0 = 0L

    /** O @ do caso em andamento: so para marcar "=@" no log, nunca escrito nele. */
    private var alvo = ""

    /** Texto de um no sem o texto: tamanho e hash curto (Redaction); "=@" se e o @ do caso. */
    private fun resumo(t: CharSequence?): String {
        val s = t?.toString() ?: return "null"
        val arroba = if (s.trim().removePrefix("@").lowercase() == alvo) " =@" else ""
        return "${s.length}c${Redaction.mask(s)}$arroba"
    }

    private fun sonda(u: String, arroba: String) {
        t0 = SystemClock.uptimeMillis()
        val aberto = runCatching {
            instr.targetContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://ig.me/m/$arroba"))
                    .setPackage(IG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        // So a classe: a mensagem da excecao traz o link, com o @.
        if (aberto.isFailure) { log("[$u] link nao abriu: ${aberto.exceptionOrNull()?.javaClass?.simpleName}"); return }
        val viaWindows = HashMap<String, Long>()
        val viaActive = HashMap<String, Long>()
        val viaServico = HashMap<String, Long>() // a regra do NodeOps (Janelas.escolher)
        var voltas = 0
        var msLeitura = 0L
        while (SystemClock.uptimeMillis() - t0 < 8000 && !viaWindows.keys.containsAll(ESPERADOS)) {
            val a = SystemClock.uptimeMillis()
            val dt = a - t0
            val ws = runCatching { ua.windows }.getOrDefault(emptyList())
            for (id in IDS) {
                if (id !in viaWindows && ws.any { achar(it.root, id).isNotEmpty() }) viaWindows[id] = dt
                if (id !in viaActive && achar(ua.rootInActiveWindow, id).isNotEmpty()) viaActive[id] = dt
            }
            val alvo = escolhaDoServico(ws)
            for (id in IDS) if (id !in viaServico && alvo.any { achar(it, id).isNotEmpty() }) viaServico[id] = dt
            msLeitura += SystemClock.uptimeMillis() - a
            voltas++
            SystemClock.sleep(100)
        }
        log("[$u] voltas=$voltas leitura_media_ms=${if (voltas > 0) msLeitura / voltas else 0}")
        for (id in IDS) {
            log("[$u] tempo $id windows=${viaWindows[id] ?: "-"} servico=${viaServico[id] ?: "-"} active=${viaActive[id] ?: "-"}")
        }
        SystemClock.sleep(1500) // cartao do topo e bolhas carregam depois
        detalhe(u)
    }

    private fun detalhe(u: String) {
        janelas(u)
        val ws = ua.windows
        ws.forEach { w ->
            for (id in IDS) achar(w.root, id).forEach { log("[$u] windows w${w.id} ${descr(id, it)}") }
        }
        ua.rootInActiveWindow.let { r ->
            log("[$u] active root win=${r?.windowId} pkg=${r?.packageName} cls=${r?.className}")
            for (id in IDS) achar(r, id).forEach { log("[$u] active ${descr(id, it)}") }
        }
        log("[$u] servico escolhe ${escolhaDoServico(ws).map { "w${it.windowId}" }}")
        // Percurso como o NodeAdapter (getChild), sem e com flagIncludeNotImportantViews.
        for (inc in listOf(false, true)) {
            setFlags(ua, inc)
            val root = escolhaDoServico(ua.windows).firstOrNull()
            val a = SystemClock.uptimeMillis()
            val nos = ArrayList<AccessibilityNodeInfo>()
            percorrer(root, nos)
            val ms = SystemClock.uptimeMillis() - a
            log("[$u] percurso notImportant=$inc nos=${nos.size} ms=$ms")
            for (id in IDS) nos.filter { it.viewIdResourceName == "$IG:id/$id" }
                .forEach { log("[$u] percurso notImportant=$inc ${descr(id, it)}") }
            ultimaBolha(u, inc, nos)
        }
        setFlags(ua, false)
    }

    /** A bolha mais baixa da message_list e os textos dentro dela. */
    private fun ultimaBolha(u: String, inc: Boolean, nos: List<AccessibilityNodeInfo>) {
        val lista = nos.firstOrNull { it.viewIdResourceName == "$IG:id/message_list" }
        if (lista == null) { log("[$u] bolha notImportant=$inc sem message_list"); return }
        val filhos = (0 until lista.childCount).mapNotNull { lista.getChild(it) }
        val ultima = filhos.maxByOrNull { Rect().also(it::getBoundsInScreen).bottom }
        val textos = ArrayList<AccessibilityNodeInfo>().also { percorrer(ultima, it) }
            .flatMap { listOfNotNull(it.text, it.contentDescription) }.map(::resumo)
        log("[$u] bolha notImportant=$inc filhos=${filhos.size} ultima=${ultima?.className} " +
            "b=${ultima?.let { Rect().also(it::getBoundsInScreen) }} textos=$textos")
    }

    private fun janelas(tag: String) {
        runCatching { ua.windowsOnAllDisplays }.getOrNull()?.let { log("[$tag] telas=${it.size()}") }
        ua.windows.forEach { w ->
            val r = w.root
            log("[$tag] janela w${w.id} type=${w.type} layer=${w.layer} active=${w.isActive} " +
                "focused=${w.isFocused} title=${resumo(w.title)} root=${r?.packageName}/${r?.className} filhos=${r?.childCount}")
        }
    }

    /** Volta ate nao haver conversa aberta (no maximo 3 BACK, conferindo antes de cada um). */
    private fun sairDaConversa(tag: String) {
        repeat(3) {
            if (ua.windows.none { achar(it.root, "direct_thread_header").isNotEmpty() }) return
            ua.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            SystemClock.sleep(1200)
        }
        log("[$tag] AVISO conversa ainda aberta apos 3 BACK")
    }

    /** A MESMA regra do servico (NodeOps): a janela do Instagram com a conversa; a ativa como reserva. */
    private fun escolhaDoServico(ws: List<AccessibilityWindowInfo>): List<AccessibilityNodeInfo> =
        Janelas.escolher(
            ws.map { w ->
                val app = w.type == AccessibilityWindowInfo.TYPE_APPLICATION
                val r = if (app) w.root else null
                Janelas.Janela(r, r?.packageName?.toString(), app, w.layer)
            },
            IG,
            temConversa = { r -> SelectorProfile.IG_448.conversaIds.any { achar(r, it).isNotEmpty() } },
            ehPrincipal = { r -> SelectorProfile.IG_448.abasIds.any { achar(r, it).isNotEmpty() } },
        ) { listOfNotNull(ua.rootInActiveWindow).map { Janelas.Reserva(it, it.packageName?.toString()) } }

    private fun achar(root: AccessibilityNodeInfo?, id: String): List<AccessibilityNodeInfo> =
        root?.findAccessibilityNodeInfosByViewId("$IG:id/$id") ?: emptyList()

    private fun percorrer(n: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (n == null || out.size > 3000) return
        out += n
        for (i in 0 until n.childCount) percorrer(n.getChild(i), out)
    }

    private fun descr(id: String, n: AccessibilityNodeInfo): String {
        val b = Rect().also(n::getBoundsInScreen)
        return "$id cls=${n.className} text=${resumo(n.text)} desc=${resumo(n.contentDescription)} " +
            "hint=${n.hintText} en=${n.isEnabled} click=${n.isClickable} vis=${n.isVisibleToUser} b=$b filhos=${n.childCount}"
    }

    companion object {
        const val IG = "com.instagram.android"
        val IDS = listOf(
            "direct_thread_header", "header_title", "header_subtitle", "row_thread_composer_edittext",
            "row_thread_composer_send_button_container", "message_list", "seen_state_text",
        )
        /** O botao Enviar so aparece com texto: a espera nao depende dele nem do "Visto". */
        val ESPERADOS = IDS - "row_thread_composer_send_button_container" - "seen_state_text"
    }
}
