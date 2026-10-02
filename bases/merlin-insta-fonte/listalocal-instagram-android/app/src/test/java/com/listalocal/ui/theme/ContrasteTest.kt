package com.listalocal.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contraste WCAG 2.1 das cores do tema, claro e escuro: texto >= 4,5:1 sobre
 * o fundo em que aparece (AA), borda de campo >= 3:1. Trocar uma cor que
 * derrube isso falha aqui.
 */
class ContrasteTest {

    private fun contraste(a: Color, b: Color): Double {
        val (claro, escuro) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (claro + 0.05) / (escuro + 0.05)
    }

    private fun texto(cs: ColorScheme, sem: SemanticColors) = mapOf(
        "onSurface/surface" to (cs.onSurface to cs.surface),
        "onSurfaceVariant/surface" to (cs.onSurfaceVariant to cs.surface),
        "onSurfaceVariant/surfaceContainerLow" to (cs.onSurfaceVariant to cs.surfaceContainerLow),
        "onSurfaceVariant/surfaceContainerHigh" to (cs.onSurfaceVariant to cs.surfaceContainerHigh),
        "onPrimary/primary" to (cs.onPrimary to cs.primary),
        "onPrimaryContainer/primaryContainer" to (cs.onPrimaryContainer to cs.primaryContainer),
        "primary/surface" to (cs.primary to cs.surface),
        "error/surface" to (cs.error to cs.surface),
        "onErrorContainer/errorContainer" to (cs.onErrorContainer to cs.errorContainer),
        "onSecondaryContainer/secondaryContainer" to (cs.onSecondaryContainer to cs.secondaryContainer),
        "onSuccessContainer/successContainer" to (sem.onSuccessContainer to sem.successContainer),
        "onWarningContainer/warningContainer" to (sem.onWarningContainer to sem.warningContainer),
        "onInfoContainer/infoContainer" to (sem.onInfoContainer to sem.infoContainer),
        "onNeutralContainer/neutralContainer" to (sem.onNeutralContainer to sem.neutralContainer),
    )

    @Test fun `texto e bordas com contraste AA no tema claro e no escuro`() {
        for ((nome, cs, sem) in listOf(Triple("claro", LightColors, SemanticLight), Triple("escuro", DarkColors, SemanticDark))) {
            texto(cs, sem).forEach { (par, cores) ->
                val c = contraste(cores.first, cores.second)
                assertTrue("$nome $par = ${"%.2f".format(c)}", c >= 4.5)
            }
            val borda = contraste(cs.outline, cs.surface)
            assertTrue("$nome outline/surface = $borda", borda >= 3.0)
        }
    }
}
