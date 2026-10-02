@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.listalocal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.listalocal.ui.theme.Space

/** Uma opcao de filtro: valor tecnico, rotulo legivel e quantos contatos cobre. */
data class FilterOption(
    val value: String,
    val label: String,
    val count: Int? = null,
)

/**
 * Grupo de chips de filtro. O leitor de tela ouve o rotulo, a contagem e o
 * estado — a marca de selecao nunca e a unica pista de que algo esta ativo.
 */
@Composable
fun FilterChipGroup(
    options: List<FilterOption>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        options.forEach { option ->
            val isOn = option.value in selected
            FilterChip(
                selected = isOn,
                onClick = { onToggle(option.value) },
                modifier = Modifier.semantics {
                    contentDescription = buildString {
                        append(option.label)
                        option.count?.let { append(", $it contatos") }
                        append(if (isOn) ", selecionado" else ", não selecionado")
                    }
                },
                label = {
                    Text(
                        if (option.count != null) "${option.label} (${option.count})"
                        else option.label,
                    )
                },
                leadingIcon = if (isOn) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
            )
        }
    }
}
