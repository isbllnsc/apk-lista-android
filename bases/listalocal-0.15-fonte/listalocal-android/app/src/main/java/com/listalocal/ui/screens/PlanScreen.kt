@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.listalocal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.listalocal.core.contacts.Batch
import com.listalocal.core.contacts.NormalizedNumber
import com.listalocal.ui.UiState
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.LabeledValue
import com.listalocal.ui.components.ListProgressCard
import com.listalocal.ui.components.PlanSummaryCard
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenColumn
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.SecondaryActionButton
import com.listalocal.ui.components.SectionCard
import com.listalocal.ui.components.SectionTitle
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.ui.theme.Space

/** Quantas listas mostramos antes de resumir o restante. */
private const val LISTAS_VISIVEIS = 8

/**
 * Etapa 4 — a revisao. Responde, sem rolar a tela: quantos contatos, quais
 * filtros, quantas listas, quanto tempo e se havera confirmacao.
 */
@Composable
fun PlanScreen(
    ui: UiState,
    accessibilityReady: Boolean,
    onToggleConfirmEachList: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBackToContacts: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleTurbo: (Boolean) -> Unit = {},
) {
    val turboDisponivel = ui.prefixos().isNotEmpty() && ui.excludedNumbers == 0
    var mostrarResumo by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val confirmarCada = !ui.autoConfirm
    val estimativa = estimativaTexto(ui.lotes.size)

    ScreenColumn(modifier) {
        ScreenTitle("Plano", "Revise antes de abrir o WhatsApp.")

        PlanSummaryCard(
            contacts = ui.filtrados,
            lists = ui.lotes.size,
            filterSummary = ui.filtroResumo,
            estimate = estimativa,
            confirmEachList = confirmarCada,
        )

        if (ui.excludedNumbers > 0) {
            InlineWarning(
                "${ui.excludedNumbers} número(s) marcado(s) para ficar fora. " +
                    "O modo turbo fica desligado para respeitar essas exclusões.",
                tone = WarningTone.INFO,
            )
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Confirmar cada lista antes de criar",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (confirmarCada) {
                            "Recomendado. O app para antes de criar cada lista e espera você."
                        } else {
                            "Desligado: cria todas as listas seguidas, sem parar."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Space.md))
                Switch(
                    checked = confirmarCada,
                    onCheckedChange = onToggleConfirmEachList,
                    modifier = Modifier.semantics {
                        contentDescription = "Confirmar cada lista antes de criar"
                    },
                )
            }
        }

        if (turboDisponivel) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Modo turbo (por prefixo)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (ui.turbo) {
                                "Digita o prefixo uma vez e marca os contatos do lote na lista, rolando. " +
                                    "Muito mais rápido; cada toque é confirmado pela contagem do WhatsApp."
                            } else {
                                "Desligado: procura cada contato pelo nome, um a um."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Space.md))
                    Switch(
                        checked = ui.turbo,
                        onCheckedChange = onToggleTurbo,
                        modifier = Modifier.semantics { contentDescription = "Modo turbo por prefixo" },
                    )
                }
            }
        }

        SectionTitle("As listas que serão criadas")
        ui.lotes.take(LISTAS_VISIVEIS).forEach { lote ->
            ListProgressCard(
                label = lote.label,
                total = lote.numbers.size,
                selected = 0, notFound = 0, ambiguous = 0, blocked = 0,
                created = false, inProgress = false,
            )
        }
        if (ui.lotes.size > LISTAS_VISIVEIS) {
            Text(
                "… e mais ${ui.lotes.size - LISTAS_VISIVEIS} lista(s) com a mesma divisão.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!accessibilityReady) {
            InlineWarning(
                "O Serviço de Acessibilidade está desligado. Volte à etapa de permissões e " +
                    "ative-o para poder iniciar.",
                tone = WarningTone.ERROR,
            )
        }

        InlineWarning(
            "Crie a primeira lista, confira no WhatsApp e siga. Pare ao primeiro aviso do " +
                "WhatsApp. A tela do aparelho fica ligada durante a execução.",
            tone = WarningTone.INFO,
        )

        PrimaryActionButton(
            text = "Iniciar criação",
            onClick = { mostrarResumo = true },
            enabled = accessibilityReady && ui.lotes.isNotEmpty(),
            testTag = "plan_start",
        )
        SecondaryActionButton("Voltar aos contatos", onBackToContacts)
    }

    if (mostrarResumo) {
        ModalBottomSheet(
            onDismissRequest = { mostrarResumo = false },
            sheetState = sheetState,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg)
                    .padding(bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text("Confirmar e iniciar", style = MaterialTheme.typography.titleLarge)
                LabeledValue("Contatos", "${ui.filtrados}")
                LabeledValue("Números excluídos", "${ui.excludedNumbers}")
                LabeledValue("Listas", "${ui.lotes.size}")
                LabeledValue("Filtro", ui.filtroResumo)
                LabeledValue(
                    "Confirmação",
                    if (confirmarCada) "Você confirma cada lista" else "Cria todas sem parar",
                )
                LabeledValue("Tempo estimado", estimativa)
                InlineWarning(
                    "O WhatsApp será aberto em primeiro plano e o app vai tocar na tela por você. " +
                        "Evite mexer no aparelho enquanto uma lista estiver sendo montada.",
                    tone = WarningTone.ATTENTION,
                )
                PrimaryActionButton(
                    text = "Iniciar criação",
                    onClick = { mostrarResumo = false; onStart() },
                    testTag = "plan_confirm_start",
                )
                SecondaryActionButton("Voltar", onClick = { mostrarResumo = false })
            }
        }
    }
}

/**
 * Estimativa honesta: ~13 min por lista de 256, medidos no spike do Android.
 * Listas menores contam proporcionalmente.
 */
private fun estimativaTexto(listas: Int): String {
    if (listas <= 0) return "—"
    val minutos = listas * 13
    return when {
        minutos < 60 -> "cerca de $minutos min"
        else -> "cerca de ${minutos / 60} h ${minutos % 60} min"
    }
}

private fun lotesDeExemplo(): List<Batch> {
    val numero = NormalizedNumber("+5521999999999", 0L, "Contato de exemplo")
    return List(16) { i ->
        Batch(i + 1, List(if (i == 15) 116 else 256) { numero })
    }
}

@Preview(name = "Plano · claro", showBackground = true, heightDp = 1000)
@Composable
private fun PlanPreview() {
    ListaLocalTheme(darkTheme = false) {
        PlanScreen(
            ui = amostra().copy(lotes = lotesDeExemplo(), filtrados = 3956),
            accessibilityReady = true,
            onToggleConfirmEachList = {}, onStart = {}, onBackToContacts = {},
        )
    }
}

@Preview(name = "Plano · acessibilidade off (escuro)", showBackground = true, heightDp = 1000)
@Composable
private fun PlanPreviewBlocked() {
    ListaLocalTheme(darkTheme = true) {
        PlanScreen(
            ui = amostra().copy(lotes = lotesDeExemplo(), filtrados = 3956),
            accessibilityReady = false,
            onToggleConfirmEachList = {}, onStart = {}, onBackToContacts = {},
        )
    }
}
