package com.listalocal.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listalocal.service.BroadcastListaProgress

/**
 * Etapa 4 do modo WhatsApp — Fase 2 (envio de mensagem pelas listas selecionadas).
 *
 * Exibe todas as listas de transmissão disponíveis (as recém-criadas na Fase 1
 * e as já existentes no WhatsApp descobertas durante o escaneamento com rolagem).
 * Cada lista mostra seu nome (ou "Lista #N") e a quantidade de contatos/destinatários.
 *
 * O usuário pode marcar/desmarcar quais listas receberão a mensagem e clicar em Enviar.
 */
@Composable
fun WaMessageScreen(
    listas: List<BroadcastListaProgress>,
    listasSelecionadas: Set<Int>,
    onToggleLista: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    mensagem: String,
    onMensagemChange: (String) -> Unit,
    onEnviar: () -> Unit,
    onRelerListas: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val temMensagem = mensagem.trim().isNotEmpty()
    val temListasSelecionadas = listasSelecionadas.isNotEmpty()
    val podeEnviar = temMensagem && temListasSelecionadas

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── Cabeçalho ──────────────────────────────────────────────────────────

        Text(
            text = "Enviar mensagem",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )

        // ── Banner de status das listas ────────────────────────────────────────

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp),
                )
                Column {
                    Text(
                        text = "Listas prontas para envio!",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    val novas = listas.count { !it.jaExistia }
                    val existentes = listas.count { it.jaExistia }
                    val resumoTexto = when {
                        novas > 0 && existentes > 0 ->
                            "${listas.size} lista(s) disponível(is): $novas criada(s) agora e $existentes já existente(s)."
                        novas > 0 ->
                            "$novas lista(s) criada(s) prontas para envio."
                        listas.isNotEmpty() ->
                            "${listas.size} lista(s) de transmissão encontradas no WhatsApp."
                        else ->
                            "Nenhuma lista de transmissão encontrada."
                    }
                    Text(
                        text = resumoTexto,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        // ── Seção de seleção das listas de transmissão ─────────────────────────

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Listas de transmissão",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "${listasSelecionadas.size} de ${listas.size} selecionada(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onSelectAll,
                        enabled = listasSelecionadas.size < listas.size,
                    ) {
                        Text("Todas")
                    }
                    TextButton(
                        onClick = onDeselectAll,
                        enabled = listasSelecionadas.isNotEmpty(),
                    ) {
                        Text("Nenhuma")
                    }
                }
            }

            if (listas.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(
                        text = "Nenhuma lista encontrada. Volte à etapa anterior para criar novas listas.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listas.forEach { lista ->
                        val isSelected = listasSelecionadas.contains(lista.index)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleLista(lista.index) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                            border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleLista(lista.index) },
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = lista.label.ifBlank { "Lista #${lista.index}" },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            modifier = Modifier.weight(1f, fill = false),
                                            maxLines = 1,
                                        )

                                        if (lista.jaExistia) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    text = "Existente",
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        } else {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    text = "Criada agora",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                )
                                            }
                                        }
                                    }

                                    // Contagem de contatos e subtítulo
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = when {
                                                lista.subtitulo.isNotBlank() -> lista.subtitulo
                                                lista.selecionados > 0 -> "${lista.selecionados} destinatários"
                                                else -> "Lista de transmissão"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.primary,
                                        )

                                        if (lista.subtitulo.isNotBlank() && !lista.subtitulo.contains("${lista.selecionados}")) {
                                            Text(
                                                text = "• ${lista.subtitulo}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Campo de mensagem ──────────────────────────────────────────────────

        Text(
            text = "Mensagem",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )

        OutlinedTextField(
            value = mensagem,
            onValueChange = onMensagemChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            placeholder = { Text("Digite a mensagem que será enviada para os contatos das listas selecionadas…") },
            maxLines = 12,
            label = { Text("Mensagem") },
        )

        Text(
            text = "${mensagem.length} caracteres",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Botão enviar ───────────────────────────────────────────────────────

        Button(
            onClick = onEnviar,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = podeEnviar,
        ) {
            Text(
                text = if (listasSelecionadas.isNotEmpty()) {
                    "Enviar (${listasSelecionadas.size} lista${if (listasSelecionadas.size > 1) "s" else ""})"
                } else {
                    "Selecione ao menos 1 lista"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedButton(
            onClick = onRelerListas,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = "🗘 Atualizar listas do WhatsApp",
                fontSize = 15.sp,
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

/** Sobrecarga para compatibilidade com chamadas simples. */
@Composable
fun WaMessageScreen(
    numListasCriadas: Int,
    mensagem: String,
    onMensagemChange: (String) -> Unit,
    onEnviar: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listas = remember(numListasCriadas) {
        List(numListasCriadas) { idx ->
            BroadcastListaProgress(
                index = idx + 1,
                label = "Lista #${idx + 1}",
                selecionados = 0,
                criada = true,
            )
        }
    }
    var selecionadas by remember(numListasCriadas) {
        mutableStateOf((1..numListasCriadas).toSet())
    }

    WaMessageScreen(
        listas = listas,
        listasSelecionadas = selecionadas,
        onToggleLista = { id ->
            selecionadas = if (selecionadas.contains(id)) selecionadas - id else selecionadas + id
        },
        onSelectAll = { selecionadas = (1..numListasCriadas).toSet() },
        onDeselectAll = { selecionadas = emptySet() },
        mensagem = mensagem,
        onMensagemChange = onMensagemChange,
        onEnviar = onEnviar,
        onRelerListas = {},
        onBack = onBack,
        modifier = modifier,
    )
}
