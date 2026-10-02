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
                StepRowWa(numero = "2", texto = "Listas de transmissão são criadas com todos os contatos da agenda (até 256 por lista).")
                StepRowWa(numero = "3", texto = "Ao terminar, o app volta para você digitar a mensagem e confirmar o envio.")
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
