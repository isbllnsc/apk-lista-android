package com.listalocal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.listalocal.core.selectors.TargetApp
import com.listalocal.service.CompatCheck
import com.listalocal.service.RecoveryAction
import com.listalocal.ui.theme.Radii
import com.listalocal.ui.theme.Sizes
import com.listalocal.ui.theme.Space

/**
 * Escolha do aplicativo alvo e prova de que ele funciona NESTE aparelho.
 *
 * O WhatsApp e o WhatsApp Business tem identificadores de tela diferentes e,
 * dependendo da versao, telas diferentes. Por isso o app nao promete: ele
 * verifica e mostra o que encontrou.
 */
@Composable
fun TargetAppCard(
    installed: List<TargetApp>,
    selected: TargetApp,
    onSelect: (TargetApp) -> Unit,
    onCheck: () -> Unit,
    checking: Boolean,
    compat: CompatCheck?,
    modifier: Modifier = Modifier,
    accessibilityReady: Boolean = true,
    onOpenTargetApp: () -> Unit = {},
    onOpenOfficialHelp: () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("target_app_card"),
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
                Text("Aplicativo", style = MaterialTheme.typography.titleMedium)
                compat?.let {
                    StatusBadge(
                        text = if (it.ok) "Compatível" else "Incompatível",
                        tone = if (it.ok) BadgeTone.SUCCESS else BadgeTone.ERROR,
                    )
                }
            }

            when {
                installed.isEmpty() -> Text(
                    "Nenhum WhatsApp encontrado neste aparelho. Instale o WhatsApp ou o " +
                        "WhatsApp Business para continuar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                installed.size == 1 -> Text(
                    "Vamos operar sobre o ${installed.first().label}, o único instalado aqui.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {
                    Text(
                        "Os dois estão instalados. Escolha em qual as listas serão criadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.selectableGroup()) {
                        installed.forEach { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Sizes.minTouch)
                                    .semantics { contentDescription = app.label },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = app == selected,
                                    onClick = { onSelect(app) },
                                )
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            if (installed.isNotEmpty()) {
                Text(
                    "Antes de criar listas, o app confere na tela do ${selected.label} se " +
                        "encontra tudo de que precisa. Leva alguns segundos e não seleciona " +
                        "ninguém.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (checking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        CircularProgressIndicator(Modifier.heightIn(max = 20.dp))
                        Text(
                            "Verificando no ${selected.label}…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onCheck,
                        enabled = accessibilityReady,
                        modifier = Modifier
                            .heightIn(min = Sizes.minTouch)
                            .testTag("check_compat"),
                    ) { Text("Verificar compatibilidade") }
                    if (!accessibilityReady) {
                        Text(
                            "Ative o Serviço de Acessibilidade acima para poder verificar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                compat?.let {
                    CompatResultado(
                        compat = it,
                        onOpenTargetApp = onOpenTargetApp,
                        onOpenOfficialHelp = onOpenOfficialHelp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompatResultado(
    compat: CompatCheck,
    onOpenTargetApp: () -> Unit,
    onOpenOfficialHelp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        LabeledValue("Aplicativo", "${compat.app.label} ${compat.version}")
        compat.quota?.let { q ->
            LabeledValue(
                "Transmissões este mês",
                buildString {
                    q.enviadas?.let { append("$it enviadas") }
                    q.restantes?.let { if (isNotEmpty()) append(" · "); append("$it restantes") }
                    q.periodo?.let { append(" ($it)") }
                },
            )
        }
        when {
            compat.ok -> InlineWarning(
                "Tudo que o app precisa foi encontrado nesta versão. Pode criar as listas.",
                tone = WarningTone.INFO,
            )
            compat.failure != null -> InlineWarning(
                "Não dá para operar aqui: ${compat.failure}.",
                tone = WarningTone.ERROR,
            )
            compat.missing.isNotEmpty() -> InlineWarning(
                "Não encontrei na tela: ${compat.missing.joinToString(", ")}. " +
                    "Criar listas aqui seria adivinhação, então o app não vai tentar.",
                tone = WarningTone.ERROR,
            )
        }

        if (compat.recommendedActions.isNotEmpty()) {
            Text(
                "Como tentar liberar",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Space.sm),
            )
            compat.recommendedActions.forEachIndexed { index, action ->
                Text(
                    "${index + 1}. ${recoveryText(action)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onOpenTargetApp,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch),
            ) {
                Text("Abrir WhatsApp Business")
            }
            TextButton(
                onClick = onOpenOfficialHelp,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.minTouch),
            ) {
                Text("Ver ajuda oficial do WhatsApp")
            }
        }
    }
}

private fun recoveryText(action: RecoveryAction): String = when (action) {
    RecoveryAction.CHECK_BUSINESS_PLATFORM ->
        "Se este número estiver conectado à Plataforma do WhatsApp Business (API ou " +
            "Meta Business Suite), as transmissões comerciais ficam indisponíveis: " +
            "desconecte-o ou use outro número."
    RecoveryAction.RETRY_COMPATIBILITY ->
        "Feche e abra o Business; depois volte aqui e verifique novamente."
    RecoveryAction.TRY_ANOTHER_BUSINESS_ACCOUNT ->
        "Se continuar ausente, teste outro número Business."
    RecoveryAction.USE_STANDARD_WHATSAPP ->
        "Se a operação permitir, use o WhatsApp comum para listas clássicas."
}
