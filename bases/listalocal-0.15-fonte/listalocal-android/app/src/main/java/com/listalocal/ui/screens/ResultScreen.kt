package com.listalocal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.listalocal.service.ListProgress
import com.listalocal.service.RunPhase
import com.listalocal.service.RunState
import com.listalocal.ui.components.BadgeTone
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.LabeledValue
import com.listalocal.ui.components.ListProgressCard
import com.listalocal.ui.components.MetricCard
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenColumn
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.SecondaryActionButton
import com.listalocal.ui.components.SectionCard
import com.listalocal.ui.components.SectionTitle
import com.listalocal.ui.components.StatusBadge
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.ui.theme.Space

/** Como a operacao terminou, do ponto de vista de quem operou. */
enum class Outcome(val titulo: String, val tone: BadgeTone) {
    CONCLUIDO("Concluído", BadgeTone.SUCCESS),
    CONCLUIDO_COM_AVISOS("Concluído com avisos", BadgeTone.ATTENTION),
    CANCELADO("Cancelado", BadgeTone.NEUTRAL),
    PERMISSAO_PERDIDA("Interrompido: permissão perdida", BadgeTone.ERROR),
    VERSAO_NAO_SUPORTADA("Interrompido: versão do WhatsApp não suportada", BadgeTone.ERROR),
    TELA_DESCONHECIDA("Interrompido: tela do WhatsApp não reconhecida", BadgeTone.ERROR),
    PARADA_SEGURA("Interrompido por segurança", BadgeTone.ERROR),
}

/**
 * Etapa 6 — o fechamento. Diz o que foi feito, o que ficou de fora e qual e o
 * proximo passo real. Codigo tecnico so aparece em "Ver detalhes".
 */
@Composable
fun ResultScreen(
    run: RunState,
    onRestart: () -> Unit,
    onBackToContacts: () -> Unit,
    modifier: Modifier = Modifier,
    awaitingVerification: Boolean = false,
    onFinalize: () -> Boolean = { false },
    onOpenWhatsApp: () -> Unit = {},
    onReportMissing: () -> Boolean = { false },
) {
    var detalhes by rememberSaveable { mutableStateOf(false) }
    var finalizationError by rememberSaveable { mutableStateOf(false) }
    var missingDialog by rememberSaveable { mutableStateOf(false) }
    var missingError by rememberSaveable { mutableStateOf(false) }
    val outcome = outcomeDe(run)
    val selecionados = run.lists.sumOf { it.selected }
    val naoEncontrados = run.lists.sumOf { it.notFound }
    val ambiguos = run.lists.sumOf { it.ambiguous }
    val bloqueados = run.lists.sumOf { it.blocked }
    val interrompidas = run.lists.count { !it.created && it.selected > 0 }

    ScreenColumn(modifier) {
        ScreenTitle("Resultado", resumoDe(outcome, run))

        SectionCard(
            container = when (outcome) {
                Outcome.CONCLUIDO -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${run.listsCreated} de ${run.totalLists} criações solicitadas",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("result_headline"),
                )
                StatusBadge(
                    if (awaitingVerification) "A conferir" else outcome.titulo,
                    if (awaitingVerification) BadgeTone.ATTENTION else outcome.tone,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            MetricCard("criações solicitadas", "${run.listsCreated}", Modifier.weight(1f), emphasis = true)
            MetricCard("contatos incluídos", "$selecionados", Modifier.weight(1f), emphasis = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            MetricCard("não encontrados", "$naoEncontrados", Modifier.weight(1f))
            MetricCard("pulados", "${ambiguos + bloqueados}", Modifier.weight(1f))
        }

        if (outcome != Outcome.CONCLUIDO) {
            InlineWarning(
                orientacaoFinal(outcome, interrompidas),
                tone = if (outcome == Outcome.CANCELADO) WarningTone.INFO else WarningTone.ERROR,
            )
        }

        if (ambiguos + bloqueados + naoEncontrados > 0) {
            SectionCard {
                Text("O que ficou de fora", style = MaterialTheme.typography.titleSmall)
                if (naoEncontrados > 0) {
                    LabeledValue("Sem WhatsApp ou número diferente", "$naoEncontrados")
                }
                if (ambiguos > 0) {
                    LabeledValue("Dois contatos iguais (ambíguo)", "$ambiguos")
                }
                if (bloqueados > 0) {
                    LabeledValue("Contatos bloqueados", "$bloqueados")
                }
                Text(
                    "Esses contatos não entraram em nenhuma lista. Você pode adicioná-los à mão " +
                        "no WhatsApp, se quiser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (awaitingVerification) {
            InlineWarning(
                "O toque em Criar não confirma que o WhatsApp salvou a lista. " +
                    "Abra o WhatsApp, confira se todas as ${run.totalLists} listas aparecem " +
                    "e só então encerre o uso.",
                tone = WarningTone.ATTENTION,
            )
            SecondaryActionButton("Abrir WhatsApp para conferir", onOpenWhatsApp)
            PrimaryActionButton(
                text = "Confirmei todas as listas; encerrar uso",
                onClick = { finalizationError = !onFinalize() },
                testTag = "result_finalize_one_shot",
            )
            SecondaryActionButton("Faltou alguma lista", { missingDialog = true })
            if (finalizationError) {
                InlineWarning(
                    "Não foi possível registrar o encerramento no celular. " +
                        "Mantenha o app aberto e tente confirmar novamente.",
                    tone = WarningTone.ERROR,
                )
            }
            if (missingError) {
                InlineWarning("Não foi possível liberar a correção. Tente novamente.",
                    tone = WarningTone.ERROR)
            }
        } else {
            PrimaryActionButton(
                text = "Voltar ao planejamento",
                onClick = onRestart,
                testTag = "result_restart",
            )
            SecondaryActionButton("Voltar aos contatos", onBackToContacts)
        }

        TextButton(onClick = { detalhes = !detalhes }) {
            Text(if (detalhes) "Ocultar detalhes" else "Ver detalhes")
        }

        AnimatedVisibility(visible = detalhes) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionCard(container = MaterialTheme.colorScheme.surfaceContainer) {
                    Text("Detalhes técnicos", style = MaterialTheme.typography.titleSmall)
                    LabeledValue("Fase final", run.phase.name)
                    if (run.message.isNotEmpty()) LabeledValue("Mensagem", run.message)
                    if (run.whatsAppVersion.isNotEmpty()) {
                        LabeledValue("Versão do WhatsApp", run.whatsAppVersion)
                    }
                }
                SectionTitle("Lista a lista")
                run.lists.forEach { l ->
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
                        inProgress = false,
                    )
                }
            }
        }
    }
    if (missingDialog) {
        AlertDialog(
            onDismissRequest = { missingDialog = false },
            title = { Text("Faltou alguma lista?") },
            text = { Text("Você poderá voltar ao planejamento. As listas já existentes " +
                "continuam no WhatsApp; confira quais faltam para não duplicá-las.") },
            confirmButton = {
                TextButton(onClick = {
                    missingError = !onReportMissing()
                    if (!missingError) missingDialog = false
                }) { Text("Voltar ao planejamento") }
            },
            dismissButton = { TextButton(onClick = { missingDialog = false }) { Text("Continuar conferindo") } },
        )
    }
}

internal fun outcomeDe(run: RunState): Outcome = when (run.phase) {
    RunPhase.PERMISSION_LOST -> Outcome.PERMISSAO_PERDIDA
    RunPhase.VERSION_UNSUPPORTED -> Outcome.VERSAO_NAO_SUPORTADA
    RunPhase.UI_CHANGED -> Outcome.TELA_DESCONHECIDA
    RunPhase.FAILED_SAFE -> Outcome.PARADA_SEGURA
    RunPhase.CANCELLED -> Outcome.CANCELADO
    else -> {
        val faltou = run.listsCreated < run.totalLists
        val problemas = run.lists.sumOf { it.notFound + it.ambiguous + it.blocked } > 0
        if (faltou || problemas) Outcome.CONCLUIDO_COM_AVISOS else Outcome.CONCLUIDO
    }
}

private fun resumoDe(outcome: Outcome, run: RunState): String = when (outcome) {
    Outcome.CONCLUIDO ->
        "As criações foram solicitadas ao WhatsApp. Confira se todas as listas aparecem. Nenhuma mensagem foi enviada."
    Outcome.CONCLUIDO_COM_AVISOS ->
        "Confira as listas no WhatsApp. Alguns contatos ficaram de fora."
    Outcome.CANCELADO ->
        "Você cancelou a operação. Confira no WhatsApp as ${run.listsCreated} lista(s) solicitadas."
    else ->
        "A operação parou antes do fim. Confira no WhatsApp as ${run.listsCreated} lista(s) solicitadas."
}

private fun orientacaoFinal(outcome: Outcome, interrompidas: Int): String = when (outcome) {
    Outcome.CONCLUIDO_COM_AVISOS ->
        "Confira as listas no WhatsApp. Os contatos que ficaram de fora estão detalhados abaixo."
    Outcome.CANCELADO ->
        "Nada foi perdido. Para continuar de onde parou, ajuste o filtro na etapa de contatos " +
            "e comece uma nova operação."
    Outcome.PERMISSAO_PERDIDA ->
        "O Serviço de Acessibilidade foi desligado durante a execução. Reative-o nas permissões " +
            "e inicie uma nova operação."
    Outcome.VERSAO_NAO_SUPORTADA ->
        "Esta versão do WhatsApp ainda não foi validada. Não crie listas neste aparelho até " +
            "atualizar o Lista Local."
    Outcome.TELA_DESCONHECIDA, Outcome.PARADA_SEGURA ->
        "O app parou sem tocar em nada que não reconhecia" +
            (if (interrompidas > 0) ". A criação da lista em andamento não foi solicitada" else "") +
            ". Recomece a partir dos contatos que ainda faltam."
    Outcome.CONCLUIDO -> ""
}

private fun runFinal(phase: RunPhase, criadas: Int, total: Int = 6) = RunState(
    phase = phase,
    currentListIndex = criadas,
    lists = List(total) { i ->
        ListProgress(
            index = i + 1,
            label = "Transmissão %03d".format(i + 1),
            total = 256,
            selected = if (i < criadas) 249 else 0,
            notFound = if (i < criadas) 5 else 0,
            ambiguous = if (i < criadas) 2 else 0,
            created = i < criadas,
        )
    },
    whatsAppVersion = "2.26.33.74",
    running = false,
)

@Preview(name = "Resultado · concluído com avisos", showBackground = true, heightDp = 1000)
@Composable
private fun ResultPreview() {
    ListaLocalTheme(darkTheme = false) {
        ResultScreen(runFinal(RunPhase.COMPLETED, 6), {}, {})
    }
}

@Preview(name = "Resultado · parada segura (escuro)", showBackground = true, heightDp = 1000)
@Composable
private fun ResultPreviewFailed() {
    ListaLocalTheme(darkTheme = true) {
        ResultScreen(runFinal(RunPhase.FAILED_SAFE, 2), {}, {})
    }
}
