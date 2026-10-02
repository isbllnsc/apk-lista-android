@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.listalocal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.listalocal.ui.theme.Radii
import com.listalocal.ui.theme.Sizes
import com.listalocal.ui.theme.Space
import com.listalocal.ui.theme.semanticColors

/**
 * Blocos de base compartilhados por todas as telas: moldura, titulos, acoes e
 * avisos. Sao stateless: recebem texto e callbacks, nunca conhecem o ViewModel.
 */

/**
 * Moldura padrao de uma tela: rolagem vertical, respiro consistente e largura
 * de leitura limitada (em tablet o conteudo centraliza em vez de esticar).
 */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Sizes.readableMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
            content = content,
        )
    }
}

/** Titulo de tela. Um por tela — a hierarquia depende disso (e o leitor de tela pula por titulos). */
@Composable
fun ScreenTitle(text: String, support: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (support != null) {
            Text(
                support,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Titulo de bloco dentro de uma tela. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/**
 * Titulo de um passo numerado da tela: o numero num circulo e o titulo. O
 * leitor de tela ouve um titulo so, "Passo 1 de 3. De onde vêm as pessoas".
 */
@Composable
fun PassoTitulo(numero: Int, total: Int, texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Sizes.passo)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text("$numero", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Text(
            texto,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Passo $numero de $total. $texto"
            },
        )
    }
}

/**
 * Caixa de marcar com o texto ao lado: a linha inteira e o alvo de toque, e o
 * leitor de tela le a caixa e o texto juntos (so a caixa ficava sem nome).
 */
@Composable
fun CaixaDeMarcar(texto: String, marcado: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .toggleable(value = marcado, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = marcado, onCheckedChange = null)
        Spacer(Modifier.width(Space.sm))
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Acao principal: uma por tela, largura total, alvo de toque generoso. */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.primaryAction)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) { Text(text, style = MaterialTheme.typography.titleSmall) }
}

/** Acao secundaria: mesma largura, menos peso visual. */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) { Text(text) }
}

/** Acao destrutiva: cor de erro e, na tela, sempre atras de confirmacao. */
@Composable
fun DestructiveActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = Sizes.minTouch)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
        ),
    ) { Text(text) }
}

/** Linha de acoes que quebra em telas estreitas ou com fonte ampliada. */
@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}

enum class WarningTone { INFO, ATTENTION, ERROR }

/**
 * Aviso em linha. A cor reforca, mas nunca carrega sozinha a informacao:
 * o texto sempre diz o que aconteceu e o que fazer.
 */
@Composable
fun InlineWarning(
    text: String,
    modifier: Modifier = Modifier,
    tone: WarningTone = WarningTone.INFO,
) {
    val sem = semanticColors
    val container = when (tone) {
        WarningTone.INFO -> sem.infoContainer
        WarningTone.ATTENTION -> sem.warningContainer
        WarningTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (tone) {
        WarningTone.INFO -> sem.onInfoContainer
        WarningTone.ATTENTION -> sem.onWarningContainer
        WarningTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, shape = Radii.field, modifier = modifier.fillMaxWidth()) {
        Text(
            text,
            color = onContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(Space.md),
        )
    }
}

enum class BadgeTone { NEUTRAL, PENDING, SUCCESS, ATTENTION, ERROR }

/** Selo compacto de estado. O texto e a informacao; a cor e o reforco. */
@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val sem = semanticColors
    val (bg, fg) = when (tone) {
        BadgeTone.NEUTRAL -> sem.neutralContainer to sem.onNeutralContainer
        BadgeTone.PENDING -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        BadgeTone.SUCCESS -> sem.successContainer to sem.onSuccessContainer
        BadgeTone.ATTENTION -> sem.warningContainer to sem.onWarningContainer
        BadgeTone.ERROR -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = bg, shape = Radii.badge, modifier = modifier) {
        Text(
            text,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
        )
    }
}

/** Estado de carregamento com explicacao — nunca uma roda solta na tela. */
@Composable
fun LoadingState(title: String, modifier: Modifier = Modifier, support: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.padding(Space.xl),
        ) {
            CircularProgressIndicator(Modifier.clearAndSetSemantics { })
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (support != null) {
                Text(
                    support,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Par rotulo/valor alinhado, usado em resumos e revisoes. O leitor de tela le os dois juntos. */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Space.md),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}
