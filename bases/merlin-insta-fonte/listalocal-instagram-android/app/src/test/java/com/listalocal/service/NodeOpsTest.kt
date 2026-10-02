package com.listalocal.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.listalocal.core.tree.UiGroup
import org.junit.Assert.assertThrows
import org.junit.Test

/** O caminho real da rolagem (nao o do FakeInstagram): recusa nunca vira "a lista nao andou". */
class NodeOpsTest {

    private val ops = NodeOps(object : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    })

    @Test fun `rolagem recusada lanca em vez de devolver false`() {
        // No de outro app (sem pacote do Instagram): podeAgir recusa.
        assertThrows(Fila.FailedSafe::class.java) { ops.scrollForward(NodeAdapter.from(AccessibilityNodeInfo())) }
        // No que nao veio da acessibilidade.
        assertThrows(Fila.FailedSafe::class.java) { ops.scrollForward(UiGroup(emptyList())) }
        assertThrows(Fila.FailedSafe::class.java) { ops.scrollForward(null) }
    }
}
