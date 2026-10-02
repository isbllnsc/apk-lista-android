package com.listalocal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tokens do design system. Toda medida da interface sai daqui — nenhuma tela
 * inventa espacamento, raio ou cor semantica.
 */

/** Escala de espacamento em multiplos de 4dp. */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Sizes {
    /** Alvo minimo de toque exigido pelas diretrizes do Android. */
    val minTouch = 48.dp
    /** Altura da acao principal de cada tela. */
    val primaryAction = 56.dp
    /** Largura maxima de leitura: em tablet o conteudo centraliza em vez de esticar. */
    val readableMaxWidth = 640.dp
    /** A partir daqui cabe layout de duas colunas (paisagem, dobravel, tablet). */
    val twoColumnBreakpoint = 720.dp
    val progressBar = 8.dp
}

object Radii {
    val card = RoundedCornerShape(16.dp)
    val field = RoundedCornerShape(12.dp)
    val badge = RoundedCornerShape(8.dp)
}

/**
 * Cores semanticas que o Material 3 nao define: sucesso, atencao e informacao.
 * Ficam separadas do accent da marca — cor de estado nunca vira decoracao.
 */
@Immutable
data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
)

internal val SemanticLight = SemanticColors(
    success = Color(0xFF15683E),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFC8EDD6),
    onSuccessContainer = Color(0xFF04210F),
    warning = Color(0xFF7A5300),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDEA6),
    onWarningContainer = Color(0xFF271900),
    info = Color(0xFF145D65),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFD6F8FA),
    onInfoContainer = Color(0xFF062E31),
    neutralContainer = Color(0xFFEAE6F7),
    onNeutralContainer = Color(0xFF514764),
)

internal val SemanticDark = SemanticColors(
    success = Color(0xFF7ED6A4),
    onSuccess = Color(0xFF00391E),
    successContainer = Color(0xFF14512F),
    onSuccessContainer = Color(0xFFC8EDD6),
    warning = Color(0xFFF6B93B),
    onWarning = Color(0xFF412C00),
    warningContainer = Color(0xFF5B3F13),
    onWarningContainer = Color(0xFFFFF0C1),
    info = Color(0xFF28E0E8),
    onInfo = Color(0xFF0B0619),
    infoContainer = Color(0xFF0F535C),
    onInfoContainer = Color(0xFFD5FAFB),
    neutralContainer = Color(0xFF30205E),
    onNeutralContainer = Color(0xFFD6CDEA),
)

val LocalSemanticColors = staticCompositionLocalOf { SemanticLight }
