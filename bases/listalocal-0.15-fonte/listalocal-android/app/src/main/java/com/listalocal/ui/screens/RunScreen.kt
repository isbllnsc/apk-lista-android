@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.listalocal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.listalocal.service.ListProgress
import com.listalocal.service.RunPhase
import com.listalocal.service.RunState
import com.listalocal.ui.components.ActionRow
import com.listalocal.ui.components.BadgeTone
import com.listalocal.ui.components.DestructiveActionButton
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.LabeledValue
import com.listalocal.ui.components.ListProgressCard
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.SectionCard
import com.listalocal.ui.components.StatusBadge
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.ui.theme.Sizes
import com.listalocal.ui.theme.Space

/**
 * Etapa 5 — o acompanhamento. Mostra sempre o que esta acontecendo agora, em
 * que lista, e quais acoes existem de verdade neste momento.
 */
@Composable
fun RunScreen(
    run: RunState,
    onConfirmList: () -> Unit,
    onCancelList: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRequestCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val duasColunas = maxWidth >= Sizes.twoColumnBreakpoint
        if (duasColunas) {
            Row(
                Modifier.fillMaxSize().padding(Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    Status(run)
                    Confirmacao(run, onConfirmList, onCancelList)
                    Acoes(run, onPause, onResume, onRequestCancel)
                }
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(run.lists, key = { it.index }) { Lista(it, run.currentListIndex) }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = Sizes.readableMaxWidth).fillMaxSize(),
                    contentPadding = PaddingValues(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    item { Status(run) }
                    item { Confirmacao(run, onConfirmList, onCancelList) }
                    item { Acoes(run, onPause, onResume, onRequestCancel) }
                    items(run.lists, key = { it.index }) { Lista(it, run.currentListIndex) }
                }
            }
        }
    }
}

@Composable
private fun Status(run: RunState) {
    val paused = run.phase == RunPhase.PAUSED
    val problema = run.phase in FASES_DE_PARADA
    val container = when {
        problema -> MaterialTheme.colorScheme.errorContainer
        paused -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        ScreenTitle("Execução")
        SectionCard(container = container) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    faseTexto(run.phase),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("run_phase"),
                )
                StatusBadge(
                    text = when {
                        problema -> "Ação necessária"
                        paused -> "Pausado"
                        run.running -> "Em andamento"
                        else -> "Parado"
                    },
                    tone = when {
                        problema -> BadgeTone.ERROR
                        paused -> BadgeTone.ATTENTION
                        run.running -> BadgeTone.PENDING
                        else -> BadgeTone.NEUTRAL
                    },
                )
            }

            if (run.totalLists > 0) {
                LinearProgressIndicator(
                    progress = { run.listsCreated.toFloat() / run.totalLists },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizes.progressBar)
                        .semantics {
                            contentDescription =
                                "${run.listsCreated} de ${run.totalLists} criações solicitadas"
                        },
                )
                LabeledValue("Criações solicitadas", "${run.listsCreated} de ${run.totalLists}")
                if (run.currentListIndex > 0) {
                    LabeledValue("Lista atual", "Transmissão %03d".format(run.currentListIndex))
                }
                val atual = run.lists.firstOrNull { it.index == run.currentListIndex }
                if (atual != null && atual.total > 0) {
                    val processados = atual.selected + atual.notFound + atual.ambiguous + atual.blocked
                    LabeledValue("Contatos desta lista", "$processados de ${atual.total}")
                }
                val restantes = run.totalLists - run.listsCreated
                if (run.running && restantes > 0) {
                    LabeledValue("Tempo restante", "~${restantes * 13} min (estimativa)")
                }
            }

            if (run.message.isNotEmpty()) {
                Text(
                    run.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (run.whatsAppVersion.isNotEmpty()) {
                Text(
                    "WhatsApp ${run.whatsAppVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (problema) {
            InlineWarning(orientacao(run.phase), tone = WarningTone.ERROR)
        } else if (run.running && !paused) {
            InlineWarning(
                "O WhatsApp fica em primeiro plano enquanto o app monta a lista. " +
                    "Evite tocar na tela até a fase mudar.",
                tone = WarningTone.INFO,
            )
        }
    }
}

@Composable
private fun Confirmacao(run: RunState, onConfirm: () -> Unit, onCancel: () -> Unit) {
    if (!run.needsConfirmation) return
    val atual = run.lists.firstOrNull { it.index == run.currentListIndex }
    SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            "Criar ${atual?.label ?: "esta lista"}?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        LabeledValue("Contatos selecionados", "${atual?.selected ?: 0}")
        if ((atual?.notFound ?: 0) > 0) {
            LabeledValue("Não encontrados", "${atual?.notFound}")
        }
        if ((atual?.ambiguous ?: 0) > 0) {
            LabeledValue("Ambíguos (pulados)", "${atual?.ambiguous}")
        }
        if ((atual?.blocked ?: 0) > 0) {
            LabeledValue("Bloqueados (pulados)", "${atual?.blocked}")
        }
        ActionRow {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .heightIn(min = Sizes.minTouch)
                    .testTag("run_confirm_list"),
            ) { Text("Criar esta lista") }
            DestructiveActionButton("Cancelar operação", onCancel)
        }
    }
}

@Composable
private fun Acoes(
    run: RunState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRequestCancel: () -> Unit,
) {
    if (run.running) {
        ActionRow {
            if (run.phase == RunPhase.PAUSED) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.heightIn(min = Sizes.minTouch).testTag("run_resume"),
                ) { Text("Continuar") }
            } else {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.heightIn(min = Sizes.minTouch).testTag("run_pause"),
                ) { Text("Pausar") }
            }
            DestructiveActionButton("Cancelar", onRequestCancel, testTag = "run_cancel")
        }
    } else {
        PrimaryActionButton("Ver resultado", onRequestCancel, testTag = "run_finished")
    }
}

@Composable
private fun Lista(l: ListProgress, atual: Int) {
    ListProgressCard(
        label = l.label,
        total = l.total,
        selected = l.selected,
        notFound = l.notFound,
        ambiguous = l.ambiguous,
        blocked = l.blocked,
        business = l.business,
        unconfirmed = l.unconfirmed,
        created = l.created,
        inProgress = l.index == atual && !l.created,
    )
}

internal val FASES_DE_PARADA = setOf(
    RunPhase.UI_CHANGED,
    RunPhase.VERSION_UNSUPPORTED,
    RunPhase.PERMISSION_LOST,
    RunPhase.FAILED_SAFE,
)

/** Estado tecnico traduzido para o que a pessoa ve acontecendo. */
internal fun faseTexto(p: RunPhase): String = when (p) {
    RunPhase.IDLE -> "Pronto para iniciar"
    RunPhase.PREPARING -> "Preparando o WhatsApp"
    RunPhase.OPENING_WHATSAPP -> "Abrindo o WhatsApp"
    RunPhase.OPENING_BROADCAST -> "Abrindo nova transmissão"
    RunPhase.SELECTING -> "Selecionando contatos"
    RunPhase.WAITING_CONFIRMATION -> "Aguardando sua confirmação"
    RunPhase.SAVING -> "Criando a lista"
    RunPhase.COMPLETED -> "Lista concluída"
    RunPhase.PAUSED -> "Execução pausada"
    RunPhase.CANCELLED -> "Operação cancelada"
    RunPhase.UI_CHANGED -> "A tela do WhatsApp mudou"
    RunPhase.VERSION_UNSUPPORTED -> "Versão do WhatsApp não reconhecida"
    RunPhase.PERMISSION_LOST -> "Permissão de acessibilidade perdida"
    RunPhase.FAILED_SAFE -> "Parada de segurança"
}

/** O que fazer em cada parada — sempre uma acao concreta. */
internal fun orientacao(p: RunPhase): String = when (p) {
    RunPhase.UI_CHANGED ->
        "Nada foi tocado às cegas. Volte aos contatos e tente de novo; se repetir, " +
            "a versão do WhatsApp precisa ser revalidada antes de continuar."
    RunPhase.VERSION_UNSUPPORTED ->
        "Esta versão do WhatsApp ainda não foi testada com o app. Aguarde uma atualização " +
            "do Lista Local antes de criar listas neste aparelho."
    RunPhase.PERMISSION_LOST ->
        "Reative o Serviço de Acessibilidade na etapa de permissões e inicie novamente."
    RunPhase.FAILED_SAFE ->
        "O app parou por segurança antes de solicitar a lista atual. Confira no " +
            "WhatsApp as listas anteriores antes de recomeçar."
    else -> ""
}

private fun runDeExemplo(
    phase: RunPhase = RunPhase.SELECTING,
    needsConfirmation: Boolean = false,
    running: Boolean = true,
) = RunState(
    phase = phase,
    currentListIndex = 3,
    lists = List(6) { i ->
        ListProgress(
            index = i + 1,
            label = "Transmissão %03d".format(i + 1),
            total = 256,
            selected = if (i < 2) 251 else if (i == 2) 138 else 0,
            notFound = if (i < 3) 4 else 0,
            ambiguous = if (i < 3) 1 else 0,
            created = i < 2,
        )
    },
    message = "",
    needsConfirmation = needsConfirmation,
    whatsAppVersion = "2.26.33.74",
    running = running,
)

@Preview(name = "Execução · selecionando", showBackground = true, heightDp = 900)
@Composable
private fun RunPreview() {
    ListaLocalTheme(darkTheme = false) {
        RunScreen(runDeExemplo(), {}, {}, {}, {}, {})
    }
}

@Preview(name = "Execução · confirmação (escuro)", showBackground = true, heightDp = 900)
@Composable
private fun RunPreviewConfirm() {
    ListaLocalTheme(darkTheme = true) {
        RunScreen(
            runDeExemplo(RunPhase.WAITING_CONFIRMATION, needsConfirmation = true),
            {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Execução · parada segura", showBackground = true, heightDp = 900)
@Composable
private fun RunPreviewFailedSafe() {
    ListaLocalTheme(darkTheme = false) {
        RunScreen(runDeExemplo(RunPhase.FAILED_SAFE, running = false), {}, {}, {}, {}, {})
    }
}
