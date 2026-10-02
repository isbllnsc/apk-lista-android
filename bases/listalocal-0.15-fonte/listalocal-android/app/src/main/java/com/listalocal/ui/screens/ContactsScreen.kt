@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.listalocal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.listalocal.core.region.Regions
import com.listalocal.ui.ExclusionChoice
import com.listalocal.ui.UiState
import com.listalocal.ui.components.FilterChipGroup
import com.listalocal.ui.components.FilterOption
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.LoadingState
import com.listalocal.ui.components.MetricCard
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.SecondaryActionButton
import com.listalocal.ui.components.SectionTitle
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.ui.theme.Radii
import com.listalocal.ui.theme.Sizes
import com.listalocal.ui.theme.Space

/** DDDs que a operacao usa com mais frequencia, oferecidos como atalho. */
private val DDDS_DESTAQUE = listOf("21", "22", "24")

/**
 * Etapa 3 — o que existe na agenda, o que os filtros deixam passar e quantas
 * listas isso vira. Cada mudanca de filtro atualiza os numeros na hora.
 */
@Composable
fun ContactsScreen(
    ui: UiState,
    onToggleDdd: (String) -> Unit,
    onToggleUf: (String) -> Unit,
    onToggleRegiao: (String) -> Unit,
    onDddTextChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    onPrefixoChange: (String) -> Unit = {},
    onToggleExclusion: (Long) -> Unit = {},
    onClearExclusions: () -> Unit = {},
) {
    if (ui.loading) {
        LoadingState(
            title = "Lendo os contatos do aparelho",
            modifier = modifier,
            support = "Agendas com muitos contatos podem levar alguns segundos.",
        )
        return
    }

    var mostrarExclusoes by rememberSaveable { mutableStateOf(false) }

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
                    Cabecalho(ui)
                    Resumo(ui)
                    SelecaoExclusoes(ui) { mostrarExclusoes = true }
                    Acoes(ui, onClearFilters, onContinue)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) { Filtros(ui, onToggleDdd, onToggleUf, onToggleRegiao, onDddTextChange, onPrefixoChange) }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = Sizes.readableMaxWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    Cabecalho(ui)
                    Resumo(ui)
                    SelecaoExclusoes(ui) { mostrarExclusoes = true }
                    Filtros(ui, onToggleDdd, onToggleUf, onToggleRegiao, onDddTextChange, onPrefixoChange)
                    Acoes(ui, onClearFilters, onContinue)
                }
            }
        }
    }

    if (mostrarExclusoes) {
        ExclusionsSheet(
            choices = ui.exclusionChoices,
            excludedNumbers = ui.excludedNumbers,
            onToggleExclusion = onToggleExclusion,
            onClearExclusions = onClearExclusions,
            onDismiss = { mostrarExclusoes = false },
        )
    }
}

@Composable
private fun SelecaoExclusoes(ui: UiState, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        SectionTitle("Escolha quem fica fora")
        Text(
            if (ui.excludedNumbers == 0) "Nenhum número excluído das listas."
            else "${ui.excludedNumbers} número(s) excluído(s) das listas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryActionButton(
            text = "Escolher quem fica fora",
            onClick = onOpen,
            enabled = ui.exclusionChoices.isNotEmpty(),
            testTag = "contacts_choose_exclusions",
        )
        Text(
            "Excluir aqui não apaga ninguém da agenda do celular.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExclusionsSheet(
    choices: List<ExclusionChoice>,
    excludedNumbers: Int,
    onToggleExclusion: (Long) -> Unit,
    onClearExclusions: () -> Unit,
    onDismiss: () -> Unit,
) {
    var busca by rememberSaveable { mutableStateOf("") }
    val encontrados = remember(choices, busca) {
        val termo = busca.trim()
        if (termo.isEmpty()) choices else choices.filter { contato ->
            contato.displayName.contains(termo, ignoreCase = true) ||
                contato.numbers.any { it.contains(termo, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text("Escolher quem fica fora", style = MaterialTheme.typography.titleLarge)
            Text(
                "Toque em Excluir para impedir que todos os números desse contato entrem nas listas. " +
                    "Isso não apaga o contato da agenda; vale só para esta operação.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (excludedNumbers == 0) "Nenhum número excluído."
                else "$excludedNumbers número(s) excluído(s) das listas.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                label = { Text("Buscar contato") },
                placeholder = { Text("Nome ou número") },
                singleLine = true,
                shape = Radii.field,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${encontrados.size} de ${choices.size} contato(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items = encontrados, key = { it.contactId }) { contato ->
                    ExclusionRow(contato, onToggleExclusion)
                    HorizontalDivider()
                }
            }
            if (choices.any { it.excluded }) {
                SecondaryActionButton(
                    text = "Reincluir todos",
                    onClick = onClearExclusions,
                    testTag = "contacts_clear_exclusions",
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Concluir")
            }
        }
    }
}

@Composable
private fun ExclusionRow(contato: ExclusionChoice, onToggleExclusion: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                contato.displayName.ifBlank { "Contato sem nome" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                contato.numbers.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (contato.excluded) "Fora das listas" else "Pode entrar nas listas",
                style = MaterialTheme.typography.labelSmall,
                color = if (contato.excluded) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { onToggleExclusion(contato.contactId) },
            modifier = Modifier.semantics {
                contentDescription =
                    "${if (contato.excluded) "Reincluir" else "Excluir"} ${contato.displayName} das listas"
            },
        ) {
            Text(if (contato.excluded) "Reincluir" else "Excluir")
        }
    }
}

@Composable
private fun Cabecalho(ui: UiState) {
    ScreenTitle(
        "Contatos",
        if (ui.temFiltro) "Filtro ativo: ${ui.filtroResumo}" else "Nenhum filtro: a agenda inteira.",
    )
}

@Composable
private fun Resumo(ui: UiState) {
    val listas = ui.lotes.size
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            MetricCard(
                label = "vão para as listas",
                value = "${ui.filtrados}",
                emphasis = true,
                modifier = Modifier.weight(1f),
                testTag = "metric_filtrados",
            )
            MetricCard(
                label = if (listas == 1) "lista será criada" else "listas serão criadas",
                value = "$listas",
                emphasis = true,
                modifier = Modifier.weight(1f),
                testTag = "metric_listas",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            MetricCard("contatos na agenda", "${ui.totalContatos}", Modifier.weight(1f))
            MetricCard("números válidos", "${ui.validos}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            MetricCard("repetidos removidos", "${ui.duplicados}", Modifier.weight(1f))
            MetricCard("sem número válido", "${ui.semTelefoneValido}", Modifier.weight(1f))
        }
    }

    when {
        ui.validos == 0 -> InlineWarning(
            "Nenhum número válido na agenda. Verifique a permissão de Contatos e se os " +
                "contatos têm telefone salvo.",
            tone = WarningTone.ERROR,
        )
        ui.filtrados < UiState.MIN_DESTINATARIOS -> InlineWarning(
            "O filtro atual deixou ${ui.filtrados} contato(s). Uma lista de transmissão precisa " +
                "de pelo menos ${UiState.MIN_DESTINATARIOS}. Amplie ou limpe os filtros para continuar.",
            tone = WarningTone.ATTENTION,
        )
    }
}

@Composable
private fun Filtros(
    ui: UiState,
    onToggleDdd: (String) -> Unit,
    onToggleUf: (String) -> Unit,
    onToggleRegiao: (String) -> Unit,
    onDddTextChange: (String) -> Unit,
    onPrefixoChange: (String) -> Unit = {},
) {
    var avancados by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        SectionTitle("Nome começa com")
        OutlinedTextField(
            value = ui.prefixoNome,
            onValueChange = onPrefixoChange,
            label = { Text("Prefixos do nome") },
            placeholder = { Text("ex.: LC, LF") },
            supportingText = { Text("Só contatos cujo nome começa por um destes. Separe por vírgula.") },
            singleLine = true,
            shape = Radii.field,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        SectionTitle("Filtrar por DDD")
        FilterChipGroup(
            options = DDDS_DESTAQUE.map {
                FilterOption(it, "DDD $it", ui.porDdd[it] ?: 0)
            },
            selected = ui.dddSelecionados,
            onToggle = onToggleDdd,
        )
        Text(
            "Pode marcar mais de um: 21, 22 e 24 juntos, por exemplo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    TextButton(onClick = { avancados = !avancados }) {
        Text(if (avancados) "Ocultar filtros avançados" else "Filtros avançados (UF, região, outros DDDs)")
    }

    AnimatedVisibility(visible = avancados) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionTitle("Região")
                FilterChipGroup(
                    options = Regions.REGIOES.map {
                        FilterOption(it, it, ui.porRegiao[it] ?: 0)
                    },
                    selected = ui.regiaoSelecionadas,
                    onToggle = onToggleRegiao,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                SectionTitle("Estado (UF)")
                val ufs = ui.porUf.keys.filter { it.length == 2 && it != "??" }.sorted()
                if (ufs.isEmpty()) {
                    Text(
                        "Nenhum estado identificado nos números da agenda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FilterChipGroup(
                        options = ufs.map { FilterOption(it, it, ui.porUf[it]) },
                        selected = ui.ufSelecionadas,
                        onToggle = onToggleUf,
                    )
                }
            }
            OutlinedTextField(
                value = ui.dddTexto,
                onValueChange = onDddTextChange,
                label = { Text("Outros DDDs") },
                placeholder = { Text("ex.: 11, 27, 31") },
                supportingText = { Text("Separe por vírgula. Soma-se aos DDDs marcados acima.") },
                singleLine = true,
                shape = Radii.field,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Acoes(ui: UiState, onClearFilters: () -> Unit, onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        PrimaryActionButton(
            text = "Revisar plano",
            onClick = onContinue,
            enabled = ui.podeAvancar,
            testTag = "contacts_continue",
        )
        if (ui.temFiltro) {
            SecondaryActionButton("Limpar filtros", onClearFilters)
        }
    }
}

@Preview(name = "Contatos · claro", showBackground = true, heightDp = 900)
@Composable
private fun ContactsPreview() {
    ListaLocalTheme(darkTheme = false) {
        ContactsScreen(
            ui = amostra(),
            onToggleDdd = {}, onToggleUf = {}, onToggleRegiao = {},
            onDddTextChange = {}, onClearFilters = {}, onContinue = {},
        )
    }
}

@Preview(name = "Contatos · filtro vazio (escuro)", showBackground = true, heightDp = 900)
@Composable
private fun ContactsPreviewEmptyFilter() {
    ListaLocalTheme(darkTheme = true) {
        ContactsScreen(
            ui = amostra().copy(filtrados = 1, lotes = emptyList(), dddSelecionados = setOf("22")),
            onToggleDdd = {}, onToggleUf = {}, onToggleRegiao = {},
            onDddTextChange = {}, onClearFilters = {}, onContinue = {},
        )
    }
}

/** Estado de exemplo para os previews — nunca usado em producao. */
internal fun amostra(): UiState = UiState(
    totalContatos = 4210,
    telefonesLidos = 5120,
    validos = 3980,
    duplicados = 812,
    semTelefoneValido = 328,
    filtrados = 3980,
    porUf = mapOf("RJ" to 2100, "SP" to 1200, "MG" to 680),
    porRegiao = mapOf("Sudeste" to 3980),
    porDdd = mapOf("21" to 1500, "22" to 400, "24" to 200, "11" to 900),
)
