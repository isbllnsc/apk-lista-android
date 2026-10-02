@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.listalocal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.listalocal.service.Desfecho
import com.listalocal.service.ListProgress
import com.listalocal.service.Modo
import com.listalocal.service.PersonResult
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
 * Etapa 5 — o acompanhamento. Mostra o que esta acontecendo agora, com quem,
 * e quais acoes existem de verdade neste momento.
 */
@Composable
fun RunScreen(
    run: RunState,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRequestCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = Sizes.readableMaxWidth).fillMaxSize(),
            contentPadding = PaddingValues(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item { Status(run) }
            item { Confirmacao(run, onConfirm, onDecline) }
            item { Acoes(run, onPause, onResume, onRequestCancel) }
            items(run.lists, key = { it.index }) { Lote(it, run) }
        }
    }
}

@Composable
private fun Status(run: RunState) {
    val paused = run.phase == RunPhase.PAUSED || run.phase == RunPhase.RESTRICTED
    val problema = run.phase in FASES_DE_PARADA || run.phase == RunPhase.RESTRICTED
    val container = when {
        problema -> MaterialTheme.colorScheme.errorContainer
        paused -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        ScreenTitle("Execução", run.origem?.let { "${run.modo.titulo} · ${it.titulo}" } ?: run.modo.titulo)
        SectionCard(container = container) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // O leitor de tela anuncia cada mudanca de fase e de contagem sem o dono tocar em nada.
                Text(
                    faseTexto(run.phase, run.isWa), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("run_phase").semantics { liveRegion = LiveRegionMode.Polite },
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
            if (run.origem != null) {
                // Lista ao vivo: o total so se sabe no fim.
                if (run.running && !paused) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(Sizes.progressBar))
                }
                // "Enviar para ate X pessoas": "12 de 50".
                run.limite?.let { limite ->
                    LabeledValue("Pessoas", "${run.enviados} de $limite")
                }
                Text(
                    remember(run.results) { progresso(run.results, dm = true) },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("run_progress").semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else if (run.totalPessoas > 0) {
                LinearProgressIndicator(
                    progress = { run.feitos.toFloat() / run.totalPessoas },
                    modifier = Modifier.fillMaxWidth().height(Sizes.progressBar)
                        .semantics { contentDescription = "${run.feitos} de ${run.totalPessoas} pessoas" },
                )
                LabeledValue("Pessoas", "${run.feitos} de ${run.totalPessoas}")
            }
            // Para WA: conta vazia não é exibida; número de telefone não leva @
            if (!run.isWa) run.conta?.let { LabeledValue("Enviando como", "@$it") }
            if (run.atual.isNotEmpty()) {
                val prefixo = if (run.isWa) "" else "@"
                LabeledValue(if (run.phase == RunPhase.WAITING_INTERVAL) "Próxima" else "Agora", "$prefixo${run.atual}")
            }
            if (run.origem == null && run.results.isNotEmpty()) LabeledValue("Até aqui", remember(run.results) { resumoDesfechos(run.results) })
            if (run.message.isNotEmpty()) {
                Text(run.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (run.appVersionWa.isNotEmpty()) {
                Text("WhatsApp ${run.appVersionWa}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (run.appVersion.isNotEmpty()) {
                Text("Instagram ${run.appVersion}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            run.phase == RunPhase.RESTRICTED -> InlineWarning(
                if (run.isWa) run.message.ifEmpty { "Operação pausada. Desbloqueie o celular e toque em Continuar." }
                else "O Instagram mostrou um aviso de restrição. Veja o aviso no Instagram antes de continuar; " +
                    "insistir pode levar a conta a ser desativada.",
                tone = WarningTone.ERROR,
            )
            problema -> InlineWarning(orientacao(run.phase, run.isWa), tone = WarningTone.ERROR)
            run.running && !paused -> InlineWarning(
                if (run.isWa) "Evite tocar no celular. Pausar e Parar ficam por cima do WhatsApp."
                else "Evite tocar no celular. Pausar e Parar ficam por cima do Instagram.",
                tone = WarningTone.INFO,
            )
        }
    }
}

@Composable
private fun Confirmacao(run: RunState, onConfirm: () -> Unit, onDecline: () -> Unit) {
    if (!run.needsConfirmation) return
    SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            run.message.ifEmpty { "Seguir?" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        LabeledValue("Até aqui", remember(run.results, run.modo) { progresso(run.results, run.modo == Modo.DM) })
        ActionRow {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = Sizes.minTouch).testTag("run_confirm"),
            ) { Text(if (run.modo == Modo.DM) "Seguir" else "Tocar em Concluir") }
            DestructiveActionButton("Parar aqui", onDecline)
        }
    }
}

@Composable
private fun Acoes(run: RunState, onPause: () -> Unit, onResume: () -> Unit, onRequestCancel: () -> Unit) {
    if (run.running) {
        ActionRow {
            if (run.phase == RunPhase.PAUSED || run.phase == RunPhase.RESTRICTED) {
                Button(onClick = onResume, modifier = Modifier.heightIn(min = Sizes.minTouch).testTag("run_resume")) {
                    Text("Continuar")
                }
            } else {
                OutlinedButton(onClick = onPause, modifier = Modifier.heightIn(min = Sizes.minTouch).testTag("run_pause")) {
                    Text("Pausar")
                }
            }
            DestructiveActionButton("Parar", onRequestCancel, testTag = "run_cancel")
        }
    } else {
        PrimaryActionButton("Ver resultado", onRequestCancel, testTag = "run_finished")
    }
}

@Composable
private fun Lote(l: ListProgress, run: RunState) {
    // So refaz a conta quando chega um resultado, nao a cada troca de fase.
    val (feitos, resumo) = remember(run.results, l.index, l.total) {
        val deste = run.results.filter { it.lote == l.index }
        deste.size to if (deste.isEmpty()) "${l.total} pessoas" else resumoDesfechos(deste)
    }
    ListProgressCard(
        label = l.label,
        total = l.total,
        processed = feitos,
        summary = resumo,
        done = l.done,
        inProgress = l.index == run.currentListIndex && !l.done && run.running,
    )
}

/**
 * "12 enviados · 3 pulados · 1 falha". Pulado = nada foi aberto para enviar
 * (lista do dono, ja recebeu, pediu para parar, sem @); sem confirmacao conta
 * como falha.
 */
internal fun progresso(results: List<PersonResult>, dm: Boolean): String {
    val ok = results.count { if (dm) it.desfecho == Desfecho.ENVIADO else !it.desfecho.falha && it.desfecho != Desfecho.PULADO }
    val falhas = results.count { it.desfecho.falha }
    val pulados = results.size - ok - falhas
    fun n(q: Int, um: String, varios: String) = "$q ${if (q == 1) um else varios}"
    return listOf(
        if (dm) n(ok, "enviado", "enviados") else n(ok, "na lista", "na lista"),
        n(pulados, "pulado", "pulados"),
        n(falhas, "falha", "falhas"),
    ).joinToString(" · ")
}

/** "12 enviadas · 1 sem confirmação · 2 falhas", na ordem do enum. */
internal fun resumoDesfechos(results: List<PersonResult>): String =
    Desfecho.entries.mapNotNull { d ->
        val n = results.count { it.desfecho == d }
        if (n == 0) null else "$n ${d.rotulo.lowercase()}"
    }.joinToString(" · ").ifEmpty { "nenhuma pessoa ainda" }

internal val FASES_DE_PARADA = setOf(
    RunPhase.UI_CHANGED,
    RunPhase.VERSION_UNSUPPORTED,
    RunPhase.PERMISSION_LOST,
    RunPhase.FAILED_SAFE,
)

/** Estado tecnico traduzido para o que a pessoa ve acontecendo. */
internal fun faseTexto(p: RunPhase, isWa: Boolean = false): String = when (p) {
    RunPhase.IDLE -> "Pronto para iniciar"
    RunPhase.PREPARING -> "Preparando"
    RunPhase.OPENING_APP -> if (isWa) "Abrindo o WhatsApp" else "Abrindo o Instagram"
    RunPhase.OPENING_SCREEN -> if (isWa) "Abrindo a lista no WhatsApp" else "Abrindo a lista no Instagram"
    RunPhase.READING_LIST -> if (isWa) "Lendo os contatos" else "Lendo a lista no Instagram"
    RunPhase.WORKING -> "Trabalhando"
    RunPhase.WAITING_INTERVAL -> "Intervalo entre envios"
    RunPhase.WAITING_CONFIRMATION -> "Aguardando sua confirmação"
    RunPhase.SAVING -> "Gravando a lista"
    RunPhase.COMPLETED -> "Concluído"
    RunPhase.PAUSED -> "Execução pausada"
    RunPhase.RESTRICTED -> if (isWa) "Operação pausada" else "Aviso de restrição do Instagram"
    RunPhase.CANCELLED -> "Operação parada"
    RunPhase.UI_CHANGED -> if (isWa) "A tela do WhatsApp mudou" else "A tela do Instagram mudou"
    RunPhase.VERSION_UNSUPPORTED -> if (isWa) "WhatsApp não conferido" else "Instagram não conferido"
    RunPhase.PERMISSION_LOST -> "Permissão de acessibilidade perdida"
    RunPhase.FAILED_SAFE -> "Parada de segurança"
    RunPhase.OPENING_BROADCAST -> "Abrindo seletor de transmissão"
    RunPhase.SELECTING -> "Selecionando contatos"
    RunPhase.LISTS_CREATED -> "Listas criadas! Volte ao app para enviar a mensagem."
    RunPhase.SENDING_PHASE -> "Enviando pelas listas"
}

/** O que fazer em cada parada — sempre uma acao concreta. */
internal fun orientacao(p: RunPhase, isWa: Boolean = false): String = when (p) {
    RunPhase.UI_CHANGED ->
        if (isWa) "Nada foi tocado às cegas. Confira o WhatsApp; se repetir, esta versão precisa ser recalibrada."
        else "Nada foi tocado às cegas. Confira o Instagram de novo; se repetir, esta versão precisa ser recalibrada."
    RunPhase.VERSION_UNSUPPORTED ->
        if (isWa) "Volte à etapa Permissões e confira o WhatsApp nesta versão."
        else "Volte à etapa Permissões e confira o Instagram para este modo nesta versão."
    RunPhase.PERMISSION_LOST ->
        "Reative o Serviço de Acessibilidade na etapa de permissões e inicie novamente."
    RunPhase.FAILED_SAFE ->
        "O app parou por segurança. Quem ficou sem confirmação não recebe de novo; confira o resultado."
    else -> ""
}

@Preview(name = "Execução", showBackground = true, heightDp = 900)
@Composable
private fun RunPreview() {
    ListaLocalTheme(darkTheme = false) {
        RunScreen(
            RunState(
                phase = RunPhase.WORKING, running = true, atual = "ana.souza", currentListIndex = 1,
                lists = listOf(ListProgress(1, "Lote 001", 50), ListProgress(2, "Lote 002", 20)),
                results = listOf(PersonResult("bia", "Bia", 1, Desfecho.ENVIADO, "")),
            ),
            {}, {}, {}, {}, {},
        )
    }
}
