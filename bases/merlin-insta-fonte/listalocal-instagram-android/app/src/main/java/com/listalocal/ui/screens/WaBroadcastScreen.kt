package com.listalocal.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Etapa 3 do modo WhatsApp — LISTAS_TRANSMISSAO, Fase 1.
 *
 * Explica o que vai acontecer (criar listas) e oferece o botão para iniciar.
 * A mensagem será definida na próxima etapa, após as listas estarem criadas.
 */
@Composable
fun WaBroadcastScreen(
    onIniciar: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contatosOk: Boolean = true,
    onPedirContatos: () -> Unit = {},
    soPor5521: Boolean = false,
    onSoPor5521Change: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── Cabeçalho ──────────────────────────────────────────────────────────

        Text(
            text = "Criar listas de transmissão",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )

        Text(
            text = "O app vai abrir o WhatsApp e criar automaticamente listas de transmissão com todos os contatos da sua agenda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── Explicação do fluxo ────────────────────────────────────────────────

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "O que acontece agora",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StepRowWa(numero = "1", texto = "O WhatsApp abre automaticamente.")
                StepRowWa(numero = "2", texto = "Listas de transmissão são criadas com os contatos da agenda (até 256 por lista).")
                StepRowWa(numero = "3", texto = "Ao terminar, o app volta para você digitar a mensagem e confirmar o envio.")
            }
        }

        // ── Filtro por DDD 21 ──────────────────────────────────────────────────

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (soPor5521)
                    MaterialTheme.colorScheme.secondaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Switch(
                    checked = soPor5521,
                    onCheckedChange = onSoPor5521Change,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Filtrar por DDD 21 (Rio de Janeiro)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (soPor5521)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (soPor5521)
                            "Somente contatos com número iniciando em +55 21 serão incluídos nas listas."
                        else
                            "Todos os contatos da agenda serão incluídos nas listas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (soPor5521)
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        if (!contatosOk) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    text = "Atenção: A permissão de acesso aos contatos é necessária para ler a agenda do celular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Botão iniciar ──────────────────────────────────────────────────────

        Button(
            onClick = {
                if (!contatosOk) onPedirContatos()
                else onIniciar()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = if (!contatosOk) "Permitir contatos e criar listas" else "Criar listas",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Voltar")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
internal fun StepRowWa(numero: String, texto: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = numero,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = texto,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
    }
}
