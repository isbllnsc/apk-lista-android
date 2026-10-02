package com.listalocal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Identidade Merlin aplicada ao Lista Local: indigo, violeta, ciano e ouro.
 *
 * A cor dinamica do Android 12+ foi DESLIGADA de proposito: ela trocaria a
 * paleta pela do papel de parede do usuario, e este app precisa de significado
 * estavel para cor (violeta = a marca, verde = sucesso, ouro = atencao,
 * vermelho = erro). Consistencia entre aparelhos vale mais aqui que
 * personalizacao.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF4C2A85),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAE6F7),
    onPrimaryContainer = Color(0xFF251443),
    secondary = Color(0xFF145D65),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6F8FA),
    onSecondaryContainer = Color(0xFF062E31),
    tertiary = Color(0xFF79530A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF1CA),
    onTertiaryContainer = Color(0xFF3B2600),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF9F7FF),
    onBackground = Color(0xFF21182F),
    surface = Color(0xFFF9F7FF),
    onSurface = Color(0xFF21182F),
    surfaceVariant = Color(0xFFE8E0F4),
    onSurfaceVariant = Color(0xFF514764),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F0FA),
    surfaceContainer = Color(0xFFECE6F7),
    surfaceContainerHigh = Color(0xFFE5DDF3),
    surfaceContainerHighest = Color(0xFFDDD2EF),
    outline = Color(0xFF746889),
    outlineVariant = Color(0xFFC9BFDC),
    inverseSurface = Color(0xFF2B1C48),
    inverseOnSurface = Color(0xFFF2ECFF),
    inversePrimary = Color(0xFFA78BFA),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF130B2B),
    primaryContainer = Color(0xFF4C2A85),
    onPrimaryContainer = Color(0xFFF0E8FF),
    secondary = Color(0xFF28E0E8),
    onSecondary = Color(0xFF0B0619),
    secondaryContainer = Color(0xFF0F535C),
    onSecondaryContainer = Color(0xFFD5FAFB),
    tertiary = Color(0xFFF6B93B),
    onTertiary = Color(0xFF130B2B),
    tertiaryContainer = Color(0xFF5B3F13),
    onTertiaryContainer = Color(0xFFFFF0C1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF130B2B),
    onBackground = Color(0xFFEAE6F7),
    surface = Color(0xFF130B2B),
    onSurface = Color(0xFFEAE6F7),
    surfaceVariant = Color(0xFF3B2B63),
    onSurfaceVariant = Color(0xFFD6CDEA),
    surfaceContainerLowest = Color(0xFF0B0619),
    surfaceContainerLow = Color(0xFF1B1140),
    surfaceContainer = Color(0xFF241755),
    surfaceContainerHigh = Color(0xFF30205E),
    surfaceContainerHighest = Color(0xFF3A286B),
    outline = Color(0xFFA59CC5),
    outlineVariant = Color(0xFF514374),
    inverseSurface = Color(0xFFEAE6F7),
    inverseOnSurface = Color(0xFF130B2B),
    inversePrimary = Color(0xFF4C2A85),
    scrim = Color(0xFF000000),
)

/** Tipografia do sistema (Roboto), com titulos um pouco mais firmes. */
private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun ListaLocalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val semantic = if (darkTheme) SemanticDark else SemanticLight
    CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}

/** Atalho para as cores semanticas dentro de qualquer composable do app. */
val semanticColors: SemanticColors
    @Composable get() = LocalSemanticColors.current
