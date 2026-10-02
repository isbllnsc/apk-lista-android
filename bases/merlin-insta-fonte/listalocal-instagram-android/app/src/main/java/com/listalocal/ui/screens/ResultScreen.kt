package com.listalocal.ui.screens

/* © 2026 João Victor Girão — jvictorgirao@poli.ufrj.br. Marca de autoria; não remover. */
import com.listalocal.ui.components.MarcaAutor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.listalocal.service.Desfecho
import com.listalocal.service.Modo
import com.listalocal.service.PersonResult
import com.listalocal.service.RunPhase
import com.listalocal.service.RunState
import com.listalocal.ui.components.BadgeTone
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.LabeledValue
import com.listalocal.ui.components.MetricCard
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenColumn
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.SecondaryActionButton
import com.listalocal.ui.components.SectionCard
import com.listalocal.ui.components.StatusBadge
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.ui.theme.Space

/** Quantas pessoas a lista desenha; a planilha traz todas. */
private const val PESSOAS_VISIVEIS = 300

/**
 * Etapa 6 — o fechamento: quantas receberam, quais falharam e por que, e o
 * proximo passo real. Sem confirmacao conta como falha e nao se repete.
 */
@Composable
fun ResultScreen(
    run: RunState,
    onNewOperation: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    /** Modo C parado antes do fim da lista: retomar do ponto salvo. */
    onResume: (() -> Unit)? = null,
) {
    var detalhes by rememberSaveable { mutableStateOf(false) }
    val dm = run.modo == Modo.DM
    val bons = run.results.count { if (dm) it.desfecho == Desfecho.ENVIADO else !it.desfecho.falha && it.desfecho != Desfecho.PULADO }
    val pulados = run.results.count { !it.desfecho.falha } - bons
    val falhas = run.results.filter { it.desfecho.falha }
    val incertos = run.contar(Desfecho.INCERTO)
    val parou = run.phase != RunPhase.COMPLETED

    ScreenColumn(modifier) {
        ScreenTitle("Resultado", if (dm) "Mensagem direta" else "Amigos Próximos")

        SectionCard(
            container = if (!parou && falhas.isEmpty()) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (run.origem != null) progresso(run.results, dm) else "${run.feitos} de ${run.totalPessoas} pessoas",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("result_headline"),
                )
                StatusBadge(
                    when {
                        parou -> "Interrompido"
                        falhas.isEmpty() -> "Concluído"
                        else -> "Concluído com falhas"
                    },
                    when {
                        parou -> BadgeTone.ERROR
                        falhas.isEmpty() -> BadgeTone.SUCCESS
                        else -> BadgeTone.ATTENTION
                    },
                )
            }
            if (run.message.isNotEmpty()) {
                Text(run.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            MetricCard(if (dm) "enviadas" else "na lista", "$bons", Modifier.weight(1f), emphasis = true)
            MetricCard("puladas", "$pulados", Modifier.weight(1f))
            MetricCard("falhas", "${falhas.size}", Modifier.weight(1f))
        }

        if (incertos > 0) {
            InlineWarning(
                "$incertos pessoa(s) sem confirmação: o toque em Enviar aconteceu, mas a mensagem não " +
                    "apareceu como enviada no prazo. Contam como falha e o app não manda de novo. Confira à mão.",
                tone = WarningTone.ATTENTION,
            )
        }
        if (!dm && !parou) {
            InlineWarning("Agora publique um story e escolha Amigos Próximos.", tone = WarningTone.INFO)
        }

        if (falhas.isNotEmpty()) {
            SectionCard {
                Text("Por que falhou", style = MaterialTheme.typography.titleSmall)
                Desfecho.entries.filter { it.falha }.forEach { d ->
                    val n = falhas.count { it.desfecho == d }
                    if (n > 0) LabeledValue(d.rotulo, "$n")
                }
            }
        }

        if (onResume != null && parou) {
            PrimaryActionButton("Retomar de onde parou", onResume, testTag = "result_resume")
        }
        PrimaryActionButton("Salvar planilha do resultado", onExport, testTag = "result_export")
        SecondaryActionButton("Nova operação", onNewOperation, testTag = "result_restart")
        MarcaAutor()

        TextButton(onClick = { detalhes = !detalhes }) {
            Text(if (detalhes) "Ocultar pessoa a pessoa" else "Ver pessoa a pessoa")
        }
        AnimatedVisibility(visible = detalhes) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                run.results.take(PESSOAS_VISIVEIS).forEach { r -> Pessoa(r) }
                if (run.results.size > PESSOAS_VISIVEIS) {
                    Text("… e mais ${run.results.size - PESSOAS_VISIVEIS}. A planilha traz todas.",
                        style = MaterialTheme.typography.bodySmall)
                }
                LabeledValue("Fase final", run.phase.name)
                if (run.appVersion.isNotEmpty()) LabeledValue("Instagram", run.appVersion)
            }
        }
    }
}

@Composable
private fun Pessoa(r: PersonResult) {
    Column {
        LabeledValue(if (r.username.isEmpty()) r.name else "@${r.username}", r.desfecho.rotulo)
        if (r.motivo.isNotEmpty()) {
            Text(r.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}

@Preview(name = "Resultado", showBackground = true, heightDp = 1000)
@Composable
private fun ResultPreview() {
    ListaLocalTheme(darkTheme = false) {
        ResultScreen(
            RunState(
                phase = RunPhase.COMPLETED,
                results = listOf(
                    PersonResult("ana", "Ana", 1, Desfecho.ENVIADO, ""),
                    PersonResult("bia", "Bia", 1, Desfecho.INCERTO, "sem evidência de envio no prazo"),
                ),
            ),
            {}, {},
        )
    }
}
