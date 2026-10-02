package com.listalocal.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.listalocal.core.selectors.TargetApp
import com.listalocal.expiry.ExpiryGate
import com.listalocal.data.OneShotGate

/**
 * Operacoes de baixo nivel sobre a arvore de acessibilidade do app alvo.
 * Todas as buscas partem do rootInActiveWindow atual (leitura fresca), como no
 * spike Python. Nenhuma coordenada absoluta.
 *
 * O pacote e variavel porque o identificador de tela do Android carrega o
 * pacote: `com.whatsapp:id/next_btn` no WhatsApp e `com.whatsapp.w4b:id/next_btn`
 * no WhatsApp Business. Trocar de app alvo e trocar [targetPackage].
 */
class NodeOps(
    private val service: AccessibilityService,
    @Volatile var targetPackage: String = TargetApp.WHATSAPP.packageName,
) {

    private fun isTargetNode(node: AccessibilityNodeInfo?): Boolean =
        TargetWindowPolicy.allows(targetPackage, node?.packageName)

    /** Nunca expõe a árvore de uma janela que não seja do app alvo selecionado. */
    fun root(): AccessibilityNodeInfo? = if (OneShotGate.isConsumed(service) ||
        OneShotGate.isAwaitingVerification(service) || ExpiryGate.isExpired(service)) null else
        service.rootInActiveWindow?.takeIf(::isTargetNode)

    /** O app alvo esta em primeiro plano? */
    fun onTargetApp(): Boolean = root() != null

    fun byViewId(suffix: String): List<AccessibilityNodeInfo> {
        val r = root() ?: return emptyList()
        return (r.findAccessibilityNodeInfosByViewId("$targetPackage:id/$suffix") ?: emptyList())
            .filter(::isTargetNode)
    }

    fun firstByViewId(suffix: String): AccessibilityNodeInfo? = byViewId(suffix).firstOrNull()

    fun existsViewId(suffix: String): Boolean = byViewId(suffix).isNotEmpty()

    /** Nós cujo texto e EXATAMENTE igual (nunca 'contem'). */
    fun byExactText(text: String): List<AccessibilityNodeInfo> {
        val r = root() ?: return emptyList()
        return (r.findAccessibilityNodeInfosByText(text) ?: emptyList())
            .filter { isTargetNode(it) && it.text?.toString()?.trim() == text.trim() }
    }

    /** Nó de menu (title) cujo texto contem — usado só para itens de menu. */
    fun menuItemContaining(part: String): AccessibilityNodeInfo? {
        val r = root() ?: return null
        return (r.findAccessibilityNodeInfosByText(part) ?: emptyList())
            .firstOrNull { isTargetNode(it) && it.text?.contains(part, ignoreCase = true) == true }
    }

    fun byContentDesc(desc: String): AccessibilityNodeInfo? {
        val r = root() ?: return null
        return findRec(r) { it.contentDescription?.toString() == desc }
    }

    /** Descendente de [node] cujo viewId termina em [suffix] (ex.: status da linha). */
    fun descendantByViewId(node: AccessibilityNodeInfo?, suffix: String): AccessibilityNodeInfo? {
        if (root() == null) return null
        return findRec(node) { it.viewIdResourceName?.endsWith(":id/$suffix") == true }
    }

    /** Primeiro no cujo texto ou descricao CONTEM [part] (usado so para reconhecer
     * dialogos conhecidos pelo texto, ex.: "bloqueado" — nunca para decidir em quem clicar). */
    fun firstTextContaining(part: String): AccessibilityNodeInfo? {
        val r = root() ?: return null
        return findRec(r) {
            it.text?.contains(part, ignoreCase = true) == true ||
                it.contentDescription?.contains(part, ignoreCase = true) == true
        }
    }

    private fun findRec(
        node: AccessibilityNodeInfo?,
        pred: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (!isTargetNode(node)) return null
        node ?: return null
        if (pred(node)) return node
        for (i in 0 until node.childCount) {
            findRec(node.getChild(i), pred)?.let { return it }
        }
        return null
    }

    /** Clica no nó ou no ancestral clicável mais proximo. */
    fun click(node: AccessibilityNodeInfo?): Boolean {
        if (root() == null || !isTargetNode(node)) return false
        var n = node
        var hops = 0
        while (n != null && hops < 8) {
            if (!isTargetNode(n)) return false
            if (n.isClickable) {
                if (root() == null || OneShotGate.isConsumed(service) || ExpiryGate.isExpired(service)) return false
                return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            n = n.parent
            hops++
        }
        // ultimo recurso: aciona no proprio nó (o Android roteia p/ o container)
        return if (root() != null && !OneShotGate.isConsumed(service) && !ExpiryGate.isExpired(service) && isTargetNode(node)) {
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } else false
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (root() == null || !isTargetNode(node)) return false
        node ?: return false
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return if (root() != null && !OneShotGate.isConsumed(service) && !ExpiryGate.isExpired(service) && isTargetNode(node)) {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else false
    }

    fun clearText(node: AccessibilityNodeInfo?): Boolean = setText(node, "")

    /** Um nó desta lista esta marcado como selecionado? */
    fun anySelected(nodes: List<AccessibilityNodeInfo>): Boolean {
        if (root() == null) return false
        return nodes.any { isTargetNode(it) && (it.isChecked || it.isSelected ||
            it.contentDescription?.contains("elecionad", ignoreCase = true) == true) }
    }
}

/** Compara o pacote por igualdade exata, inclusive quando o alvo muda para Business. */
internal object TargetWindowPolicy {
    fun allows(targetPackage: String, actualPackage: CharSequence?): Boolean =
        TargetApp.fromPackage(targetPackage) != null && actualPackage?.toString() == targetPackage
}
