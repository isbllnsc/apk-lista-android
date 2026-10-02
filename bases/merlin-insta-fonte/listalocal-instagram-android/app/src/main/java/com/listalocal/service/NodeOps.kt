package com.listalocal.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.core.selectors.TargetApp
import com.listalocal.core.tree.UiGroup
import com.listalocal.core.tree.UiNode
import com.listalocal.expiry.ExpiryGate

/**
 * Operacoes de baixo nivel sobre a arvore de acessibilidade do Instagram.
 * Toda leitura parte de janelas frescas; nenhuma coordenada absoluta.
 *
 * JANELA: `rootInActiveWindow` NAO serve no Instagram (a "janela ativa" fica
 * presa na atividade principal com a conversa na frente; INSTAGRAM-APP-REAL.md,
 * 0.1 e 7.1). A escolha esta em [Janelas]: todas as janelas interativas
 * (flagRetrieveInteractiveWindows), a do Instagram que tem a conversa, e
 * `rootInActiveWindow` e a fonte dos eventos como reserva. Os ids da conversa
 * sao procurados com findAccessibilityNodeInfosByViewId (uma chamada por
 * janela), e so a janela escolhida e copiada. Se o servico enxerga os nos da
 * Modal: PRECISA VERIFICAR NO INSTAGRAM REAL (ModalReadProbeTest).
 *
 * CUSTO (OTIMIZACAO.md): cada chamada ao Instagram e uma ida e volta entre
 * processos. Por isso: o evento so e copiado na thread principal (a fonte e
 * buscada depois, e so para janela fora de service.windows); a reserva nao
 * repete janela ja listada; a conferencia de id e busca direta (sem copiar a
 * arvore); o toque so confere a lista de janelas; e os nos de passagem voltam
 * ao pool, e as janelas lidas tambem (antes do Android 13; depois, recycle
 * nao faz nada). Os nos do
 * retrato ([snapshot]) NAO voltam: cada NodeAdapter guarda o seu para o toque
 * (o DmFlow ainda apaga o campo com um no lido muitas leituras antes); saem
 * com o retrato, pelo coletor. Sem pool, so deixam de ser reaproveitados.
 *
 * Cada acao reconfere, logo antes do toque: a janela, o prazo, a pausa e o
 * cancelamento ([bloqueado]).
 */
class NodeOps(
    private val ctx: Context,
    /** As janelas interativas (service.windows; no teste real, UiAutomation.windows). */
    private val listarJanelas: () -> List<AccessibilityWindowInfo>,
    private val raizAtiva: () -> AccessibilityNodeInfo?,
    @Volatile var targetPackage: String = TargetApp.INSTAGRAM.packageName,
    private val prof: SelectorProfile = SelectorProfile.IG_448,
) {
    constructor(service: AccessibilityService, targetPackage: String = TargetApp.INSTAGRAM.packageName) :
        this(service, { service.windows }, { service.rootInActiveWindow }, targetPackage)

    /** Pausa ou cancelamento pedidos: nenhuma acao nova. */
    @Volatile var bloqueado: () -> Boolean = { false }

    private fun isTargetNode(node: AccessibilityNodeInfo?): Boolean =
        TargetWindowPolicy.allows(targetPackage, node?.packageName)

    /** Copia do ultimo evento do Instagram por janela (reserva se service.windows nao mostrar a janela). */
    private val eventos = object : LinkedHashMap<Int, AccessibilityEvent>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, AccessibilityEvent>?): Boolean =
            (size > 4).also { if (it) eldest?.value?.let(::reciclar) }
    }

    /**
     * Na thread principal, a cada evento: so guarda uma copia (nenhuma
     * chamada ao Instagram aqui). true = mudanca de tela do Instagram.
     */
    fun lembrar(event: AccessibilityEvent): Boolean {
        if (event.packageName?.toString() != targetPackage) return false
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return false
        @Suppress("DEPRECATION")
        val copia = AccessibilityEvent.obtain(event)
        synchronized(eventos) {
            eventos.remove(event.windowId)?.let(::reciclar)
            eventos[event.windowId] = copia
        }
        return true
    }

    /** Raiz da janela de cada evento guardado fora de [listadas] (janela to raiz), a mais nova primeiro. */
    private fun raizesDosEventos(listadas: Set<Int>): List<Pair<Int, AccessibilityNodeInfo>> {
        val lista = synchronized(eventos) { eventos.entries.filter { it.key !in listadas }.map { it.key to it.value } }.asReversed()
        return lista.mapNotNull { (w, e) -> runCatching { e.source }.getOrNull()?.let(::raizDe)?.let { w to it } }
    }

    /** Sobe ate a raiz da janela; os nos do caminho voltam ao pool. */
    private fun raizDe(no: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n = no
        for (i in 0 until 64) {
            val pai = n.parent ?: return n
            reciclar(n)
            n = pai
        }
        reciclar(n)
        return null
    }

    private fun temIds(ids: List<String>): (AccessibilityNodeInfo) -> Boolean = { raiz ->
        ids.any { id ->
            runCatching {
                val achados = raiz.findAccessibilityNodeInfosByViewId("$targetPackage:id/$id")
                achados.forEach(::reciclar)
                achados.isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun janelas(): List<AccessibilityWindowInfo> = try { listarJanelas() } catch (_: Exception) { emptyList() }

    /** Raizes a ler, a principal primeiro ([Janelas]); vazio fora do Instagram. */
    fun raizes(): List<AccessibilityNodeInfo> {
        if (ExpiryGate.isExpired(ctx)) return emptyList()
        val lidas = janelas()
        val janelas = lidas.map { w ->
            val app = w.type == AccessibilityWindowInfo.TYPE_APPLICATION
            val raiz = if (app) w.root else null
            Janelas.Janela(raiz, raiz?.packageName?.toString(), app, w.layer, w.id)
        }
        // Reserva so do que a lista nao mostrou COM raiz (a mesma janela daria a mesma raiz). A de cima sem raiz
        // (a ModalActivity neste aparelho) so e lida pela fonte do evento dela (Janelas, passo 2).
        val listadas = janelas.filter { it.raiz != null }.mapTo(HashSet()) { it.id }
        val ativaListada = lidas.any { it.isActive }
        lidas.forEach(::reciclar)
        val escolhidas = Janelas.escolher(janelas, targetPackage, temIds(prof.conversaIds), temIds(prof.abasIds)) {
            val ativa = if (ativaListada) null else raizAtiva()?.let { it.windowId to it }
            (listOfNotNull(ativa) + raizesDosEventos(listadas)).map { (w, r) -> Janelas.Reserva(r, r.packageName?.toString(), w) }
        }
        janelas.forEach { j -> j.raiz?.takeIf { r -> escolhidas.none { it === r } }?.let(::reciclar) }
        return escolhidas
    }

    /** Copia da arvore para as provas puras. */
    fun snapshot(): UiNode? = UiGroup.juntar(raizes().mapNotNull(NodeAdapter::from))

    /** Algum destes ids nas janelas escolhidas, por busca direta: nada e copiado. */
    fun existe(ids: List<String>): Boolean {
        val r = raizes()
        return r.any(temIds(ids)).also { r.forEach(::reciclar) }
    }

    /** Os textos (ou descricoes) dos nos com este id nas janelas escolhidas, por busca direta. */
    fun textosPorId(id: String): List<String> {
        val r = raizes()
        return r.flatMap { raiz ->
            runCatching {
                val achados = raiz.findAccessibilityNodeInfosByViewId("$targetPackage:id/$id")
                achados.mapNotNull { (it.text ?: it.contentDescription)?.toString() }.also { achados.forEach(::reciclar) }
            }.getOrDefault(emptyList())
        }.also { r.forEach(::reciclar) }
    }

    fun teclado(): Boolean = janelas().let { l ->
        l.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }.also { l.forEach(::reciclar) }
    }

    /**
     * O Instagram e o app de cima, pela lista de janelas, sem buscar ids nem
     * reserva: o mesmo que [raizes] nao vazia, pelo custo de uma raiz.
     */
    private fun instagramNaFrente(no: AccessibilityNodeInfo? = null): Boolean {
        val lidas = janelas()
        val apps = lidas.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val janelaTopo = apps.maxByOrNull { it.layer }
        val topoId = janelaTopo?.id
        val topo = janelaTopo?.root
        lidas.forEach(::reciclar)
        if (apps.isEmpty()) return raizes().also { it.forEach(::reciclar) }.isNotEmpty()
        // De cima sem raiz (a ModalActivity neste aparelho): vale so para um no do Instagram dessa MESMA janela.
        topo ?: return no != null && no.windowId == topoId
        return (topo.packageName?.toString() == targetPackage).also { reciclar(topo) }
    }

    private fun podeAgir(info: AccessibilityNodeInfo, mesmoPausado: Boolean): Boolean =
        isTargetNode(info) && !ExpiryGate.isExpired(ctx) && (mesmoPausado || !bloqueado()) && instagramNaFrente(info)

    /** Clica no no ou no ancestral clicavel mais proximo (sempre dentro do Instagram). */
    fun click(node: UiNode?, mesmoPausado: Boolean = false): Boolean = clicar(node, mesmoPausado) == true

    /**
     * Como [click], separando a recusa: null = recusado ANTES da acao (fora do Instagram, prazo, pausa, sem ancestral
     * clicavel): nada foi tocado, com certeza. false = a acao foi tentada e a acessibilidade nao confirmou.
     */
    fun clicar(node: UiNode?, mesmoPausado: Boolean = false): Boolean? {
        val alvo = (node as? NodeAdapter)?.info ?: return null
        var n: AccessibilityNodeInfo? = alvo
        var hops = 0
        while (n != null && hops < 8) {
            if (!isTargetNode(n)) return null
            if (n.isClickable) {
                return if (podeAgir(n, mesmoPausado)) n.performAction(AccessibilityNodeInfo.ACTION_CLICK) else null
            }
            n = n.parent
            hops++
        }
        return null
    }

    /** Toque longo no no ou no ancestral que aceita (a aba Perfil: long-clickable na rodada 1). */
    fun longClick(node: UiNode?): Boolean {
        var n: AccessibilityNodeInfo? = (node as? NodeAdapter)?.info ?: return false
        var hops = 0
        while (n != null && hops < 4) {
            if (!isTargetNode(n)) return false
            if (n.isLongClickable) {
                return podeAgir(n, false) && n.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            }
            n = n.parent
            hops++
        }
        return false
    }

    /** Gesto por posicao so com o Instagram na frente, sem pausa, cancelamento nem prazo vencido. */
    fun podeGesto(): Boolean = !ExpiryGate.isExpired(ctx) && !bloqueado() && instagramNaFrente()

    /**
     * Centro do no na tela, se ele esta visivel e o gesto pode acontecer agora.
     * O no do retrato e REFRESCADO (refresh) no instante do gesto: sem isso os bounds e o
     * "visivel" eram os da leitura. Sumiu, nao esta mais visivel ou mudou de rotulo (a View
     * foi reaproveitada para outro item): null, e o chamador nao toca em coordenada nenhuma.
     * Unico uso de coordenada de pixel do app (tela Bloks; ver CloseFriendsFlow.abrir e
     * accessibility_service_config.xml).
     */
    fun centro(node: UiNode?): Pair<Float, Float>? {
        val info = (node as? NodeAdapter)?.info?.takeIf { isTargetNode(it) } ?: return null
        val rotulo = node.contentDescription ?: node.text
        if (!info.refresh() || !info.isVisibleToUser) return null
        if ((info.contentDescription ?: info.text)?.toString() != rotulo) return null
        if (ExpiryGate.isExpired(ctx) || bloqueado() || !instagramNaFrente(info)) return null
        val r = android.graphics.Rect()
        info.getBoundsInScreen(r)
        return if (r.isEmpty) null else r.exactCenterX() to r.exactCenterY()
    }

    /** Texto inteiro de uma vez. Apagar ("") vale mesmo em pausa: e limpeza, nao acao nova. */
    fun setText(node: UiNode?, text: String): Boolean {
        val info = (node as? NodeAdapter)?.info ?: return false
        if (!podeAgir(info, mesmoPausado = text.isEmpty())) return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * false = a lista nao andou (talvez o fim). Recusada (fora do Instagram,
     * pausa, prazo) LANCA: uma recusa nunca pode virar "fim da lista".
     */
    fun scrollForward(node: UiNode?): Boolean {
        val info = (node as? NodeAdapter)?.info?.takeIf { podeAgir(it, mesmoPausado = false) }
            ?: throw Fila.FailedSafe(ROLAGEM_RECUSADA)
        return info.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /** Como [scrollForward], para tras. */
    fun scrollBackward(node: UiNode?): Boolean {
        val info = (node as? NodeAdapter)?.info?.takeIf { podeAgir(it, mesmoPausado = false) }
            ?: throw Fila.FailedSafe(ROLAGEM_RECUSADA)
        return info.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    /**
     * As janelas como o servico as ve agora, sem nada de pessoa: tipo, camada, id, pacote (ou "sem raiz"), ativa,
     * se tem a conversa; e as janelas dos eventos guardados. Vai para o log quando a conversa nao aparece: separa
     * "nao abriu", "abriu com a raiz nula" e "abriu sem o id da conversa".
     */
    fun diagnostico(): String {
        val lidas = janelas()
        val js = lidas.joinToString("; ") { w ->
            val r = if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) w.root else null
            val conversa = r?.let(temIds(prof.conversaIds)) == true
            val pacote = r?.packageName ?: "sem raiz"
            r?.let(::reciclar)
            "tipo=${w.type} camada=${w.layer} id=${w.id} $pacote${if (w.isActive) " ativa" else ""}${if (conversa) " conversa" else ""}"
        }
        lidas.forEach(::reciclar)
        val ev = synchronized(eventos) { eventos.keys.toList() }
        return "janelas[$js] eventos=$ev existe(conversa)=${existe(prof.conversaIds)}"
    }

    companion object {
        const val ROLAGEM_RECUSADA =
            "A rolagem da lista foi recusada (o Instagram saiu da frente). Nada se perdeu: continue do ponto salvo."
    }
}

/** Compara o pacote por igualdade exata: Instagram Lite, WhatsApp e o proprio app ficam de fora. */
internal object TargetWindowPolicy {
    fun allows(targetPackage: String, actualPackage: CharSequence?): Boolean =
        TargetApp.fromPackage(targetPackage) != null && actualPackage?.toString() == targetPackage
}

/** Devolve ao pool (antes do Android 13; depois, recycle nao faz nada). So o que ninguem mais segura. */
@Suppress("DEPRECATION")
private fun reciclar(n: AccessibilityNodeInfo) {
    if (Build.VERSION.SDK_INT < 33) runCatching { n.recycle() }
}

@Suppress("DEPRECATION")
private fun reciclar(e: AccessibilityEvent) {
    if (Build.VERSION.SDK_INT < 33) runCatching { e.recycle() }
}

@Suppress("DEPRECATION")
private fun reciclar(w: AccessibilityWindowInfo) {
    if (Build.VERSION.SDK_INT < 33) runCatching { w.recycle() }
}
