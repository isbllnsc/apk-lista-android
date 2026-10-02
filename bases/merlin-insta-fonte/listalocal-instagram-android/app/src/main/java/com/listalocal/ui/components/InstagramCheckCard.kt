package com.listalocal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.listalocal.service.CompatCheck
import com.listalocal.service.Modo
import com.listalocal.ui.theme.Radii
import com.listalocal.ui.theme.Sizes
import com.listalocal.ui.theme.Space

/**
 * "Conferir o Instagram": prova, na tela deste aparelho e nesta versao, que o
 * app encontra o que cada modo precisa. O app nao promete: ele confere e
 * mostra o que achou. Nada e escrito, enviado ou marcado na conferencia.
 */
@Composable
fun InstagramCheckCard(
    version: String?,
    compat: Map<Modo, CompatCheck>,
    checking: Modo?,
    accessibilityReady: Boolean,
    onCheck: (Modo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("instagram_card"),
        shape = Radii.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text("Conferir o Instagram", style = MaterialTheme.typography.titleMedium)
            if (version == null) {
                Text(
                    "O Instagram não foi encontrado neste aparelho. Instale-o para continuar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            Text(
                "Instagram $version. O app abre o Instagram, vai até a tela de cada modo e volta. " +
                    "Não escreve, não envia e não marca ninguém.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Modo.entries.forEach { modo ->
                ModoLinha(modo, compat[modo], version, checking, accessibilityReady, onCheck)
            }
            if (!accessibilityReady) {
                Text(
                    "Ative o Serviço de Acessibilidade acima para poder conferir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModoLinha(
    modo: Modo,
    check: CompatCheck?,
    version: String,
    checking: Modo?,
    accessibilityReady: Boolean,
    onCheck: (Modo) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(top = Space.sm)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(modo.titulo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            check?.let {
                val vale = it.valeParaVersao(version)
                StatusBadge(
                    text = when {
                        vale -> "Liberado"
                        it.ok -> "Confira de novo"
                        else -> "Bloqueado"
                    },
                    tone = if (vale) BadgeTone.SUCCESS else BadgeTone.ERROR,
                )
            }
        }
        if (checking == modo) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CircularProgressIndicator(Modifier.heightIn(max = 20.dp))
                Text("Conferindo no Instagram…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            FilledTonalButton(
                onClick = { onCheck(modo) },
                enabled = accessibilityReady && checking == null,
                modifier = Modifier.heightIn(min = Sizes.minTouch).testTag("check_${modo.name.lowercase()}"),
            ) { Text("Conferir este modo") }
        }
        check?.let {
            it.conta?.let { c -> LabeledValue("Conta aberta", "@$c") }
            when {
                it.valeParaVersao(version) -> if (modo == Modo.DM) Text(
                    // A conferência do DM não abre conversa: campo e Enviar são conferidos
                    // a cada pessoa, antes de escrever. "Liberado" não cobre essa tela.
                    "A tela da conversa (campo e Enviar) é conferida a cada pessoa, na hora do envio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                it.ok -> InlineWarning(
                    "O Instagram mudou de versão desde a conferência (${it.version}). Confira de novo.",
                    tone = WarningTone.ATTENTION,
                )
                else -> InlineWarning(
                    "Não dá para operar neste modo: ${it.failure ?: "faltou ${it.faltaram.joinToString()}"}. " +
                        "Agir aqui seria adivinhação, então o app não vai tentar.",
                    tone = WarningTone.ERROR,
                )
            }
        }
    }
}
