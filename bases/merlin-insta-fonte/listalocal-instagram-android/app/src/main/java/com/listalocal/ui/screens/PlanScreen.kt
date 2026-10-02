@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

/* © 2026 João Victor Girão — jvictorgirao@poli.ufrj.br. Marca de autoria; não remover. */
package com.listalocal.ui.screens

import com.listalocal.core.contacts.Plataforma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.listalocal.service.CompatCheck
import com.listalocal.service.Modo
import com.listalocal.service.ModoMensagens
import com.listalocal.service.Origem
import com.listalocal.service.Velocidade
import com.listalocal.ui.BASES_LEGAIS
import com.listalocal.ui.UiState
import com.listalocal.ui.components.CaixaDeMarcar
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.LabeledValue
import com.listalocal.ui.components.PassoTitulo
import com.listalocal.ui.components.PlanSummaryCard
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenColumn
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.SecondaryActionButton
import com.listalocal.ui.components.SectionCard
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.ui.theme.Radii
import com.listalocal.ui.theme.Sizes
import com.listalocal.ui.theme.Space

/**
 * Etapa 4 — a revisao: modo, texto, velocidade, base legal e a confirmacao de
 * quem recebe. Nada comeca sem as duas ultimas. O modo C nao tem lotes: le a
 * lista ao vivo e so para com Pausar, Parar ou o fim da lista.
 */
@Composable
fun PlanScreen(
    ui: UiState,
    compat: Map<Modo, CompatCheck>,
    accessibilityReady: Boolean,
    onModo: (Modo) -> Unit,
    onMensagem: (String) -> Unit,
    onVelocidade: (Velocidade) -> Unit,
    onConfirmarCadaLote: (Boolean) -> Unit,
    onBaseLegal: (String) -> Unit,
    onBaseLegalOutro: (String) -> Unit,
    onConfirmouSeguidores: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    plataforma: Plataforma = Plataforma.INSTAGRAM,
    compatWa: Map<Modo, CompatCheck> = emptyMap(),
    appVersionWa: String? = null,
    trocandoConta: Boolean = false,
    onTrocarConta: () -> Unit = {},
    /** Mensagens 2 e 3 (a 1 e [onMensagem]). */
    onMensagemN: (Int, String) -> Unit = { _, _ -> },
    onModoMensagens: (ModoMensagens) -> Unit = {},
    onLimite: (String) -> Unit = {},
    onAPartirDe: (String) -> Unit = {},
    onPularJaRecebeu: (Boolean) -> Unit = {},
    onPararAs: (String) -> Unit = {},
) {
    val isWa = plataforma == Plataforma.WHATSAPP
    val nomeApp = if (isWa) "WhatsApp" else "Instagram"
    var mostrarResumo by remember { mutableStateOf(false) }
    val dm = ui.modo == Modo.DM
    val liberado = if (isWa) true  // WA: verificado na etapa 2 (ConferirWa)
        else compat[ui.modo]?.valeParaVersao(ui.versaoInstagram) == true
    val resumo = resumoDoPlano(ui, isWa)
    val passos = if (dm) 5 else 3

    ScreenColumn(modifier) {
        ScreenTitle("Plano", "Revise antes de abrir o $nomeApp.")

        PlanSummaryCard(
            title = when {
                isWa -> "Contatos da agenda · ao vivo"
                dm -> "${ui.origem.titulo} · ao vivo"
                else -> "${ui.plano.total} pessoas · ${ui.modo.titulo}"
            },
            rows = resumo,
        )

        SectionCard {
            PassoTitulo(1, passos, "O que fazer")
            Column(Modifier.selectableGroup()) {
                // WhatsApp: apenas modo DM (sem Amigos Próximos)
                val modos = if (isWa) listOf(Modo.DM) else Modo.entries
                modos.forEach { m ->
                    val ok = if (isWa) true else compat[m]?.valeParaVersao(ui.versaoInstagram) == true
                    Opcao(
                        texto = m.titulo,
                        apoio = if (ok) descricaoModo(m) else "Confira o $nomeApp para este modo na etapa Permissões.",
                        selecionado = ui.modo == m,
                        habilitado = ok,
                        onClick = { onModo(m) },
                    )
                }
            }
        }

        if (dm) {
            SectionCard {
                PassoTitulo(2, passos, "Mensagens")
                Text(
                    "Até 3. A 2 e a 3 são opcionais; em branco, ficam de fora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(ui.mensagem, ui.mensagem2, ui.mensagem3).forEachIndexed { i, texto ->
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { if (i == 0) onMensagem(it) else onMensagemN(i + 1, it) },
                        label = { Text(if (i == 0) "Mensagem 1" else "Mensagem ${i + 1} (opcional)") },
                        supportingText = { Text("${texto.length}/${UiState.MAX_MENSAGEM}") },
                        isError = texto.length > UiState.MAX_MENSAGEM,
                        minLines = if (i == 0) 4 else 2,
                        shape = Radii.field,
                        modifier = Modifier.fillMaxWidth().testTag("plan_message_${i + 1}"),
                    )
                }
                if (ui.mensagens.size > 1) {
                    Column(Modifier.selectableGroup()) {
                        Opcao(
                            "Revezar", "Cada pessoa recebe UMA delas, em turnos: 1, 2, 3, 1…",
                            ui.modoMensagens == ModoMensagens.REVEZAR, true,
                        ) { onModoMensagens(ModoMensagens.REVEZAR) }
                        Opcao(
                            "Sequência",
                            "Cada pessoa recebe todas, na ordem. A seguinte só sai depois da anterior " +
                                "confirmada; se uma falhar, as outras não saem.",
                            ui.modoMensagens == ModoMensagens.SEQUENCIA, true,
                        ) { onModoMensagens(ModoMensagens.SEQUENCIA) }
                    }
                }
                Text(
                    "Dica: diga como parar (\"responda PARE\"). Quem pedir não recebe de novo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard {
                PassoTitulo(3, passos, "Velocidade")
                Text(
                    "Tempo fixo entre um envio e o próximo, igual para todos. Sem sorteio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.selectableGroup()) {
                    Velocidade.entries.forEach { v ->
                        Opcao(v.rotulo, null, ui.velocidade == v, true) { onVelocidade(v) }
                    }
                }
            }
        } else {
            SectionCard {
                PassoTitulo(2, passos, "Conferência")
                Chave(
                    "Confirmar antes de Concluir",
                    if (!ui.autoConfirm) "Recomendado. O app para antes de gravar a lista e espera você."
                    else "Desligado: toca em Concluir sozinho no fim.",
                    !ui.autoConfirm,
                ) { onConfirmarCadaLote(it) }
                Text(
                    "Marca os @ de \"Só para estes @\". Quem pediu para parar sai da lista. " +
                        "O app nunca toca em \"Limpar tudo\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (dm) {
            SectionCard {
                PassoTitulo(4, passos, "Até onde")
                OutlinedTextField(
                    value = ui.limiteTexto, onValueChange = onLimite,
                    label = { Text("Enviar para até quantas pessoas") },
                    supportingText = {
                        Text(if (ui.limiteTexto.isBlank()) "Em branco: sem limite. Pulados não contam." else "Pulados não contam.")
                    },
                    isError = ui.limiteTexto.isNotBlank() && ui.limite == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, shape = Radii.field,
                    modifier = Modifier.fillMaxWidth().testTag("plan_limit"),
                )
                if (!isWa) OutlinedTextField(
                    value = ui.aPartirDeTexto, onValueChange = onAPartirDe,
                    label = { Text("Começar a partir de @ (opcional)") },
                    supportingText = { Text("Quem vem antes na lista fica de fora.") },
                    isError = ui.aPartirDeTexto.isNotBlank() && ui.aPartirDe == null,
                    singleLine = true, shape = Radii.field,
                    modifier = Modifier.fillMaxWidth().testTag("plan_start_from"),
                )
                OutlinedTextField(
                    value = ui.pararAsTexto, onValueChange = onPararAs,
                    label = { Text("Parar às (HH:MM, opcional)") },
                    supportingText = { Text("Para sozinho nessa hora; dá para retomar depois.") },
                    isError = ui.pararAsTexto.isNotBlank() && ui.pararAs == null,
                    singleLine = true, shape = Radii.field,
                    modifier = Modifier.fillMaxWidth().testTag("plan_stop_at"),
                )
                Chave(
                    "Pular quem já recebeu desta conta",
                    if (ui.pularJaRecebeu) {
                        if (isWa) "Recomendado. Quem já recebeu via WhatsApp fica de fora."
                        else "Recomendado. Quem já recebeu de @${ui.contaInstagram.orEmpty()} fica de fora."
                    } else {
                        "Desligado: quem já recebeu pode receber de novo. Quem ficou sem confirmação ou pediu para parar, nunca."
                    },
                    ui.pularJaRecebeu, onPularJaRecebeu,
                )
            }
        }

        SectionCard {
            PassoTitulo(passos, passos, "Por que você está enviando?")
            Text(
                "A LGPD pede o motivo. Ele vai na planilha do resultado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                BASES_LEGAIS.forEach { b -> Opcao(b, null, ui.baseLegal == b, true) { onBaseLegal(b) } }
                Opcao("Outro motivo", null, ui.baseLegal == UiState.OUTRA, true) { onBaseLegal(UiState.OUTRA) }
            }
            if (ui.baseLegal == UiState.OUTRA) {
                OutlinedTextField(
                    value = ui.baseLegalOutro, onValueChange = onBaseLegalOutro,
                    label = { Text("Escreva o motivo") }, shape = Radii.field, modifier = Modifier.fillMaxWidth(),
                )
            }
            CaixaDeMarcar(
                when {
                    isWa ->
                        "Confirmo que estas pessoas têm WhatsApp e esperam este contato."
                    dm && ui.origem == Origem.CONVERSAS ->
                        "Confirmo que estas pessoas já conversam com @${ui.contaInstagram.orEmpty()} e esperam este contato."
                    else ->
                        "Confirmo que estas pessoas seguem @${ui.contaInstagram.orEmpty()} e esperam este contato."
                },
                ui.confirmouSeguidores,
                onConfirmouSeguidores,
            )
        }

        if (!dm && ui.plano.total == 0) {
            InlineWarning(
                "Escreva pelo menos um @ em \"Só para estes @\" (etapa Destinatários).",
                tone = WarningTone.ATTENTION,
            )
        }
        if (!accessibilityReady) {
            InlineWarning(
                "A Acessibilidade está desligada. Ative na etapa Permissões.",
                tone = WarningTone.ERROR,
            )
        }
        InlineWarning(
            "Deixe o celular desbloqueado e na tomada. Pausar e Parar ficam por cima do $nomeApp.",
            tone = WarningTone.INFO,
        )

        if (!isWa) ContaDaOperacao(ui.contaInstagram, dm, trocandoConta, onTrocarConta)
        PrimaryActionButton(
            text = "Iniciar",
            onClick = { mostrarResumo = true },
            enabled = accessibilityReady && liberado && ui.podeIniciar,
            testTag = "plan_start",
        )
        SecondaryActionButton("Voltar aos destinatários", onBack)
    }

    if (mostrarResumo) {
        // Aberto por inteiro e com rolagem: com 3 mensagens o resumo passa da tela e o botão Iniciar sumia (teste real 26/09).
        ModalBottomSheet(onDismissRequest = { mostrarResumo = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text("Confirmar e iniciar", style = MaterialTheme.typography.titleLarge)
                if (dm) {
                    Text(ui.linhaDoEnvio, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("plan_summary_line"))
                    Text(
                        "Uma pessoa por vez, de ${ui.origem.titulo.lowercase()}. Ficam de fora: " +
                            (if (ui.pularJaRecebeu) "quem já recebeu, " else "") + "quem pediu para parar e " +
                            "\"Não enviar para\". Nenhum grupo recebe.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ui.mensagens.forEachIndexed { i, m ->
                        SectionCard {
                            if (ui.mensagens.size > 1) Text("Mensagem ${i + 1}", style = MaterialTheme.typography.labelMedium)
                            Text(m, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                resumo.forEach { (k, v) -> LabeledValue(k, v) }
                LabeledValue("Base legal", ui.baseLegalFinal)
                if (!isWa) ContaDaOperacao(ui.contaInstagram, dm, trocandoConta, onTrocarConta)
                InlineWarning(
                    "O $nomeApp abre na frente e o app toca por você. Evite mexer no celular.",
                    tone = WarningTone.ATTENTION,
                )
                PrimaryActionButton(
                    text = "Iniciar agora",
                    onClick = { mostrarResumo = false; onStart() },
                    testTag = "plan_confirm_start",
                )
                SecondaryActionButton("Voltar", onClick = { mostrarResumo = false })
            }
        }
    }
}

/**
 * A conta que vai enviar, antes de confirmar. O dono tem mais de uma conta:
 * trocar abre o seletor de contas do proprio Instagram (nenhuma senha passa
 * pelo app) e o app confere a conta escolhida sozinho. Ao iniciar, a conta
 * aberta e lida de novo.
 */
@Composable
private fun ContaDaOperacao(conta: String?, dm: Boolean, trocando: Boolean, onTrocar: () -> Unit) {
    Text(
        conta?.let { if (dm) "Enviando como @$it" else "Lista de Amigos Próximos de @$it" }
            ?: "Conta do Instagram ainda não conferida",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.testTag("plan_account"),
    )
    SecondaryActionButton(
        if (trocando) "Escolha a conta no Instagram…" else "Trocar de conta",
        onTrocar, enabled = !trocando, testTag = "plan_switch_account",
    )
}

private fun descricaoModo(m: Modo) = when (m) {
    Modo.DM -> "Uma conversa 1:1 por pessoa, aberta pelo @."
    Modo.AMIGOS_PROXIMOS -> "Monta a lista para um story. Não envia mensagem."
    Modo.LISTAS_TRANSMISSAO -> "Fase 1: cria listas da agenda. Fase 2: envia mensagem pelas listas."
}

private fun resumoDoPlano(ui: UiState, isWa: Boolean = false): List<Pair<String, String>> =
    if (ui.modo == Modo.DM) {
        listOf(
            "Origem" to if (isWa) "Contatos da agenda" else ui.origem.titulo,
            "Velocidade" to ui.velocidade.rotulo,
            "Mensagens" to if (ui.mensagens.size <= 1) "1" else "${ui.mensagens.size} ${ui.modoMensagens.rotulo}",
            "Até" to (ui.limite?.let { "$it ${if (it == 1) "pessoa" else "pessoas"}" } ?: "sem limite"),
            "Não enviar para" to if (isWa) numeros(ui.naoEnviar.validos.size) else arrobas(ui.naoEnviar.validos.size),
            "Só para" to if (ui.soPara.validos.isEmpty()) "todos da origem"
                else if (isWa) numeros(ui.soPara.validos.size) else arrobas(ui.soPara.validos.size),
        ) + listOfNotNull(
            ("Conversas comerciais" to "puladas").takeIf { ui.origem == Origem.CONVERSAS && ui.pularComerciais },
            ui.aPartirDe?.let { "A partir de" to "@$it" },
            ui.pararAs?.let { "Parar às" to "%02d:%02d".format(it / 60, it % 60) },
            ("Quem já recebeu" to "pode receber de novo").takeIf { !ui.pularJaRecebeu },
        )
    } else {
        listOf(
            "Pessoas a marcar" to "${ui.plano.total}",
            "Ficam de fora" to "${ui.plano.pulados.values.sum()}",
            "Tempo estimado" to "cerca de ${maxOf(1, ui.plano.total * 4 / 60)} min",
        )
    }

private fun arrobas(n: Int) = if (n == 1) "1 @" else "$n @"
private fun numeros(n: Int) = if (n == 1) "1 número" else "$n números"

@Composable
internal fun Opcao(texto: String, apoio: String?, selecionado: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .selectable(selected = selecionado, enabled = habilitado, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selecionado, onClick = null, enabled = habilitado)
        Spacer(Modifier.width(Space.sm))
        Column {
            Text(texto, style = MaterialTheme.typography.bodyLarge)
            if (apoio != null) {
                Text(apoio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Chave(titulo: String, apoio: String, ligado: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(apoio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.md))
        Switch(checked = ligado, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = titulo })
    }
}

@Preview(name = "Plano", showBackground = true, heightDp = 1400)
@Composable
private fun PlanPreview() {
    ListaLocalTheme(darkTheme = false) {
        PlanScreen(
            ui = UiState(contaInstagram = "minhaconta", mensagem = "Oi! Sábado tem evento."),
            compat = emptyMap(), accessibilityReady = true,
            onModo = {}, onMensagem = {}, onVelocidade = {}, onConfirmarCadaLote = {},
            onBaseLegal = {}, onBaseLegalOutro = {}, onConfirmouSeguidores = {}, onStart = {}, onBack = {},
        )
    }
}
