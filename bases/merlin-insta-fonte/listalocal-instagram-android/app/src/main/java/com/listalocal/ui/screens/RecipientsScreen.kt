@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.listalocal.ui.screens

/* © 2026 João Victor Girão — jvictorgirao@poli.ufrj.br. Marca de autoria; não remover. */
import com.listalocal.ui.components.MarcaAutor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.listalocal.core.contacts.Plataforma
import com.listalocal.core.followers.Audience
import com.listalocal.core.followers.FollowerImport
import com.listalocal.service.Origem
import com.listalocal.ui.UiState
import com.listalocal.ui.components.ActionRow
import com.listalocal.ui.components.CaixaDeMarcar
import com.listalocal.ui.components.ContatoPickerDialog
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.LabeledValue
import com.listalocal.ui.components.PassoTitulo
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenColumn
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.SecondaryActionButton
import com.listalocal.ui.components.SectionCard
import com.listalocal.ui.components.SectionTitle
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.ui.theme.Radii

/**
 * Etapa 3 — destinatários. Exibe seção diferente conforme a plataforma:
 * - Instagram: origem (Seguidores / Conversas) + campos de @
 * - WhatsApp: origem fixa na agenda + campos de números de telefone
 */
@Composable
fun RecipientsScreen(
    ui: UiState,
    onOrigem: (Origem) -> Unit,
    onPularComerciais: (Boolean) -> Unit,
    onNaoEnviar: (String) -> Unit,
    onSoPara: (String) -> Unit,
    retomarLiberado: Boolean,
    onRetomar: () -> Unit,
    onDescartar: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    plataforma: Plataforma = Plataforma.INSTAGRAM,
) {
    val isWa = plataforma == Plataforma.WHATSAPP

    ScreenColumn(modifier) {
        ScreenTitle(
            "Destinatários",
            if (isWa) "Os destinatários vêm dos contatos do WhatsApp."
            else ui.contaInstagram?.let { "Conta no Instagram: @$it" }
                ?: "Antes, confira o Instagram na etapa Permissões.",
        )

        ui.salva?.let { c ->
            SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                SectionTitle("A última operação parou no meio")
                LabeledValue("Origem", c.origem.titulo)
                LabeledValue("Até aqui", progresso(ui.salvaFeitos, dm = true))
                c.ultimo?.let {
                    LabeledValue(
                        "Parou em",
                        if (!isWa && FollowerImport.username(it) == it) "@$it" else it,
                    )
                }
                Text(
                    "Retomar segue de onde parou, com a mesma mensagem. Quem já recebeu não recebe de novo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (!retomarLiberado) {
                    InlineWarning(
                        if (isWa) "Para retomar, confira o WhatsApp (mensagem direta) na etapa Permissões."
                        else "Para retomar, confira o Instagram (mensagem direta) na etapa Permissões.",
                        tone = WarningTone.ERROR,
                    )
                }
                ActionRow {
                    PrimaryActionButton(
                        "Retomar de onde parou", onRetomar, enabled = retomarLiberado,
                        testTag = "recipients_resume",
                    )
                    SecondaryActionButton("Começar outra", onDescartar, testTag = "recipients_discard")
                }
            }
        }

        // ── Seção 1: origem ───────────────────────────────────────────────────
        SectionCard {
            PassoTitulo(1, PASSOS, "De onde vêm as pessoas")

            if (isWa) {
                // WhatsApp: origem fixa — números na agenda que têm WhatsApp
                Text(
                    "Os destinatários são os contatos da agenda que têm número de WhatsApp válido " +
                        "(formato E.164, ex: +5521999990000). O app abre cada conversa pelo link wa.me/.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Instagram: escolher origem
                Column(Modifier.selectableGroup()) {
                    Origem.entries.forEach { o ->
                        Opcao(o.titulo, descricaoOrigem(o), ui.origem == o, true) { onOrigem(o) }
                    }
                }
                if (ui.origem == Origem.CONVERSAS) {
                    CaixaDeMarcar(
                        "Pular conversas comerciais", ui.pularComerciais, onPularComerciais,
                        Modifier.testTag("recipients_skip_business"),
                    )
                }
                Text(
                    "O app lê a lista na tela do Instagram, uma tela por vez. Guarda só quem recebeu, " +
                        "quem falhou e onde parou.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Seção 2: não enviar para ──────────────────────────────────────────
        if (isWa) {
            CampoTexto(
                passo = 2,
                titulo = "Não enviar para",
                apoio = "Estes números nunca recebem.",
                label = "Números separados por espaço, vírgula ou linha",
                placeholder = "+5521999990000, +5511988880000",
                texto = ui.naoEnviarTexto,
                onChange = onNaoEnviar,
                testTag = "recipients_exclude",
            )
            CampoTexto(
                passo = 3,
                titulo = "Só para estes números (opcional)",
                apoio = "Vazio = todos da agenda com WhatsApp.",
                label = "Números separados por espaço, vírgula ou linha",
                placeholder = "+5521999990000",
                texto = ui.soParaTexto,
                onChange = onSoPara,
                testTag = "recipients_only",
            )
        } else {
            CampoArrobas(
                passo = 2,
                titulo = "Não enviar para",
                apoio = "Estes @ nunca recebem.",
                texto = ui.naoEnviarTexto,
                lidos = ui.naoEnviar,
                onChange = onNaoEnviar,
                testTag = "recipients_exclude",
            )
            CampoArrobas(
                passo = 3,
                titulo = "Só para estes @ (opcional)",
                apoio = "Vazio = todos da origem. Amigos Próximos usa esta lista.",
                texto = ui.soParaTexto,
                lidos = ui.soPara,
                onChange = onSoPara,
                testTag = "recipients_only",
            )
        }

        if (ui.arrobasInvalidos.isNotEmpty()) {
            InlineWarning(
                "Não é @: ${ui.arrobasInvalidos.take(5).joinToString()}. Corrija ou apague para seguir.",
                tone = WarningTone.ERROR,
            )
        }

        PrimaryActionButton(
            text = "Revisar plano",
            onClick = onContinue,
            enabled = ui.podeAvancar,
            testTag = "recipients_continue",
        )
        MarcaAutor()
    }
}

private const val PASSOS = 3

@Composable
private fun CampoArrobas(
    passo: Int,
    titulo: String,
    apoio: String,
    texto: String,
    lidos: Audience.Arrobas,
    onChange: (String) -> Unit,
    testTag: String,
) {
    SectionCard {
        PassoTitulo(passo, PASSOS, titulo)
        OutlinedTextField(
            value = texto,
            onValueChange = onChange,
            label = { Text("@ separados por espaço, vírgula ou linha") },
            placeholder = { Text("@ana, @bia") },
            supportingText = {
                Text(
                    buildString {
                        append(if (lidos.validos.size == 1) "1 @" else "${lidos.validos.size} @")
                        if (lidos.invalidos.isNotEmpty()) append(" · ${lidos.invalidos.size} não é @")
                        append(". $apoio")
                    },
                )
            },
            isError = lidos.invalidos.isNotEmpty(),
            minLines = 2,
            shape = Radii.field,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
    }
}

@Composable
private fun CampoTexto(
    passo: Int,
    titulo: String,
    apoio: String,
    label: String,
    placeholder: String,
    texto: String,
    onChange: (String) -> Unit,
    testTag: String,
) {
    // Números já presentes no campo, para marcar como "já adicionado" no picker
    val jaAdicionados = remember(texto) {
        texto.split(Regex("[\\s,;\n]+"))
            .map { it.trim() }
            .filter { it.startsWith("+") && it.length >= 8 }
            .toSet()
    }
    var mostrarPicker by remember { mutableStateOf(false) }

    if (mostrarPicker) {
        ContatoPickerDialog(
            jaAdicionados = jaAdicionados,
            onConfirmar = { novos ->
                // Acrescenta os números novos ao texto existente
                val sufixo = novos.joinToString(", ")
                val novoTexto = if (texto.isBlank()) sufixo else "${texto.trimEnd(',', ' ')}, $sufixo"
                onChange(novoTexto)
                mostrarPicker = false
            },
            onDismiss = { mostrarPicker = false },
        )
    }

    SectionCard {
        PassoTitulo(passo, PASSOS, titulo)
        OutlinedTextField(
            value = texto,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            supportingText = { Text(apoio) },
            minLines = 2,
            shape = Radii.field,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { mostrarPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
            )
            Spacer(Modifier.fillMaxWidth(0.03f))
            Text("Adicionar da agenda")
        }
    }
}

private fun descricaoOrigem(o: Origem) = when (o) {
    Origem.SEGUIDORES -> "Perfil › Seguidores, do começo ao fim da lista."
    Origem.CONVERSAS -> "Suas conversas 1:1 no Direct. Sem grupos e sem pedidos."
}

@Preview(name = "Destinatários · Instagram", showBackground = true, heightDp = 1100)
@Composable
private fun RecipientsPreviewInstagram() {
    ListaLocalTheme(darkTheme = false) {
        RecipientsScreen(
            ui = UiState(contaInstagram = "minhaconta", naoEnviarTexto = "@ana, bia"),
            plataforma = Plataforma.INSTAGRAM,
            onOrigem = {}, onPularComerciais = {}, onNaoEnviar = {}, onSoPara = {},
            retomarLiberado = true, onRetomar = {}, onDescartar = {}, onContinue = {},
        )
    }
}

@Preview(name = "Destinatários · WhatsApp", showBackground = true, heightDp = 900)
@Composable
private fun RecipientsPreviewWhatsApp() {
    ListaLocalTheme(darkTheme = false) {
        RecipientsScreen(
            ui = UiState(plataforma = Plataforma.WHATSAPP),
            plataforma = Plataforma.WHATSAPP,
            onOrigem = {}, onPularComerciais = {}, onNaoEnviar = {}, onSoPara = {},
            retomarLiberado = true, onRetomar = {}, onDescartar = {}, onContinue = {},
        )
    }
}
