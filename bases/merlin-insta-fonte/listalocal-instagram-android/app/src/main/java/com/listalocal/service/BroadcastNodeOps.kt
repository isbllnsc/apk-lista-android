package com.listalocal.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Interface de baixo nível usada pelo [BroadcastEngine].
 *
 * Abstrai as operações sobre a árvore de acessibilidade, permitindo que o motor
 * de criação de listas opere sem depender do NodeOps orientado a UiNode do merlin.
 * A implementação concreta é [WaBroadcastNodeOps].
 */
interface BroadcastNodeOps {
    fun root(): AccessibilityNodeInfo?
    fun onTargetApp(): Boolean

    fun byViewId(suffix: String): List<AccessibilityNodeInfo>
    fun firstByViewId(suffix: String): AccessibilityNodeInfo? = byViewId(suffix).firstOrNull()
    fun existsViewId(suffix: String): Boolean = byViewId(suffix).isNotEmpty()

    fun byExactText(text: String): List<AccessibilityNodeInfo>
    fun menuItemContaining(part: String): AccessibilityNodeInfo?
    fun byContentDesc(desc: String): AccessibilityNodeInfo?
    fun descendantByViewId(node: AccessibilityNodeInfo?, suffix: String): AccessibilityNodeInfo?
    fun firstTextContaining(part: String): AccessibilityNodeInfo?

    fun click(node: AccessibilityNodeInfo?): Boolean
    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean
    fun anySelected(nodes: List<AccessibilityNodeInfo>): Boolean

    fun performGlobalBack(): Boolean
}

/**
 * Implementação de [BroadcastNodeOps] para o WhatsApp, usando o
 * [AccessibilityService] do merlin diretamente.
 *
 * Criado uma vez pelo [WhatsAppAccessibilityService] e passado ao [BroadcastEngine].
 */
class WaBroadcastNodeOps(
    private val service: AccessibilityService,
    private val targetPackage: String,
) : BroadcastNodeOps {

    override fun root(): AccessibilityNodeInfo? =
        service.rootInActiveWindow?.takeIf {
            it.packageName?.toString() == targetPackage
        }

    override fun onTargetApp(): Boolean = root() != null

    override fun byViewId(suffix: String): List<AccessibilityNodeInfo> {
        val r = root() ?: return emptyList()
        return runCatching {
            r.findAccessibilityNodeInfosByViewId("$targetPackage:id/$suffix") ?: emptyList()
        }.getOrDefault(emptyList())
    }

    override fun byExactText(text: String): List<AccessibilityNodeInfo> {
        val r = root() ?: return emptyList()
        return runCatching {
            r.findAccessibilityNodeInfosByText(text)?.filter {
                it.text?.toString()?.trim() == text.trim()
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    override fun menuItemContaining(part: String): AccessibilityNodeInfo? {
        val r = root() ?: return null
        return runCatching {
            r.findAccessibilityNodeInfosByText(part)?.firstOrNull {
                it.text?.toString()?.contains(part, ignoreCase = true) == true ||
                    it.contentDescription?.toString()?.contains(part, ignoreCase = true) == true
            }
        }.getOrNull()
    }

    override fun byContentDesc(desc: String): AccessibilityNodeInfo? {
        val r = root() ?: return null
        return runCatching {
            r.findAccessibilityNodeInfosByText(desc)?.firstOrNull {
                it.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true
            }
        }.getOrNull()
    }

    override fun descendantByViewId(node: AccessibilityNodeInfo?, suffix: String): AccessibilityNodeInfo? {
        node ?: return null
        return runCatching {
            node.findAccessibilityNodeInfosByViewId("$targetPackage:id/$suffix")?.firstOrNull()
        }.getOrNull()
    }

    override fun firstTextContaining(part: String): AccessibilityNodeInfo? {
        val r = root() ?: return null
        return runCatching {
            r.findAccessibilityNodeInfosByText(part)?.firstOrNull {
                it.text?.toString()?.contains(part, ignoreCase = true) == true ||
                    it.contentDescription?.toString()?.contains(part, ignoreCase = true) == true
            }
        }.getOrNull()
    }

    override fun click(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !onTargetApp()) return false
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops < 8) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
            hops++
        }
        // Fallback: força o clique direto no node original, mesmo sem isClickable = true.
        // O APK de referência (v16) faz isso quando nenhum parent clicável é achado.
        if (!onTargetApp()) return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        node ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun anySelected(nodes: List<AccessibilityNodeInfo>): Boolean =
        nodes.any { it.isChecked || it.isSelected ||
            it.contentDescription?.toString()?.contains("elecionad", ignoreCase = true) == true }

    override fun performGlobalBack(): Boolean =
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
}
