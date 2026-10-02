package com.listalocal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.listalocal.ui.theme.Radii
import com.listalocal.ui.theme.Sizes
import com.listalocal.ui.theme.Space

/**
 * Cartoes do produto. Um cartao significa "objeto separado": por isso nao ha
 * cartao dentro de cartao, e fundo colorido so aparece onde existe algo a
 * decidir ou um desfecho a comunicar.
 */

/** Cartao neutro de conteudo — a base dos blocos das telas. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.card,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            content = content,
        )
    }
}

/** Numero de destaque com rotulo curto — resumo de contatos e de resultado. */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
    testTag: String? = null,
) {
    val fg = if (emphasis) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Card(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { contentDescription = "$label: $value" },
        shape = Radii.card,
        colors = CardDefaults.cardColors(
            containerColor = if (emphasis) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = fg)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = if (emphasis) fg else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Estado de uma permissao, do ponto de vista de quem opera o app. */
enum class PermissionState { CHECKING, PENDING, GRANTED, ACTION_NEEDED }

/**
 * Cartao de uma permissao: o que e, por que e pedida, como esta agora e o que
 * fazer. Sem justificativa, nao se pede permissao.
 */
@Composable
fun PermissionCard(
    title: String,
    reason: String,
    state: PermissionState,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    fixHint: String? = null,
    testTag: String? = null,
) {
    val (badgeText, badgeTone) = when (state) {
        PermissionState.CHECKING -> "Verificando" to BadgeTone.NEUTRAL
        PermissionState.PENDING -> "Pendente" to BadgeTone.PENDING
        PermissionState.GRANTED -> "Permitido" to BadgeTone.SUCCESS
        PermissionState.ACTION_NEEDED -> "Ação necessária" to BadgeTone.ATTENTION
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        shape = Radii.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(Space.sm))
                StatusBadge(badgeText, badgeTone)
            }
            Text(
                reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state != PermissionState.GRANTED) {
                if (fixHint != null) {
                    Text(
                        fixHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = Sizes.minTouch),
                ) { Text(actionLabel) }
            }
        }
    }
}

/** Uma lista planejada ou em execucao, com progresso e desfechos. */
@Composable
fun ListProgressCard(
    label: String,
    total: Int,
    selected: Int,
    notFound: Int,
    ambiguous: Int,
    blocked: Int,
    created: Boolean,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
    business: Int = 0,
    unconfirmed: Int = 0,
) {
    val processed = selected + notFound + ambiguous + blocked + business + unconfirmed
    val (badgeText, badgeTone) = when {
        created -> "Criar solicitado" to BadgeTone.ATTENTION
        inProgress -> "Em andamento" to BadgeTone.PENDING
        else -> "Aguardando" to BadgeTone.NEUTRAL
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.card,
        colors = CardDefaults.cardColors(
            containerColor = if (inProgress) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                StatusBadge(badgeText, badgeTone)
            }
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { processed.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizes.progressBar)
                        .semantics {
                            contentDescription =
                                "$label: $processed de $total contatos processados"
                        },
                )
            }
            Text(
                buildString {
                    append("$selected de $total selecionados")
                    if (notFound > 0) append(" · $notFound não encontrados")
                    if (ambiguous > 0) append(" · $ambiguous ambíguos")
                    if (blocked > 0) append(" · $blocked bloqueados")
                    if (business > 0) append(" · $business contas comerciais")
                    if (unconfirmed > 0) append(" · $unconfirmed sem confirmação")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Resumo do plano: a resposta rapida de "o que acontece quando eu iniciar". */
@Composable
fun PlanSummaryCard(
    contacts: Int,
    lists: Int,
    filterSummary: String,
    estimate: String,
    confirmEachList: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                "$lists ${if (lists == 1) "lista" else "listas"} · $contacts contatos",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            LabeledValue("Filtro", filterSummary)
            LabeledValue("Tempo estimado", estimate)
            LabeledValue(
                "Confirmação",
                if (confirmEachList) "Você confirma cada lista" else "Cria todas sem parar",
            )
        }
    }
}
