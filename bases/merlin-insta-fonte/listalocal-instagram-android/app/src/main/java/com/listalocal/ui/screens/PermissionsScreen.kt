package com.listalocal.ui.screens

/* © 2026 João Victor Girão — jvictorgirao@poli.ufrj.br. Marca de autoria; não remover. */
import com.listalocal.ui.components.MarcaAutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.listalocal.core.contacts.Plataforma
import com.listalocal.service.CompatCheck
import com.listalocal.service.Modo
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.InstagramCheckCard
import com.listalocal.ui.components.PermissionCard
import com.listalocal.ui.components.PermissionState
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenColumn
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.components.WhatsAppCheckCard
import com.listalocal.ui.theme.ListaLocalTheme

/**
 * Etapa 2 — a única autorização que o app usa (Acessibilidade), com aviso e
 * escolha explícita antes dos ajustes, e "Conferir o Instagram" ou
 * "Conferir o WhatsApp" conforme a plataforma escolhida na Etapa 1.
 */
@Composable
fun PermissionsScreen(
    accessibilityState: PermissionState,
    onOpenAccessibility: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    plataforma: Plataforma = Plataforma.INSTAGRAM,
    onOpenAppSettings: () -> Unit = {},
    contactsState: PermissionState = PermissionState.GRANTED,
    onAskContacts: () -> Unit = {},
    contactsPermanentlyDenied: Boolean = false,
    instagramVersion: String? = null,
    whatsappVersion: String? = null,
    compat: Map<Modo, CompatCheck> = emptyMap(),
    compatWa: Map<Modo, CompatCheck> = emptyMap(),
    checking: Modo? = null,
    onCheck: (Modo) -> Unit = {},
    onCheckWa: (Modo) -> Unit = {},
) {
    var mostrarAvisoAcessibilidade by rememberSaveable { mutableStateOf(false) }
    val a11yOk = accessibilityState == PermissionState.GRANTED
    val contatosOk = plataforma != Plataforma.WHATSAPP || contactsState == PermissionState.GRANTED

    // "Pronto" depende da plataforma selecionada
    val appVersion = if (plataforma == Plataforma.WHATSAPP) whatsappVersion else instagramVersion
    val compatAtual = if (plataforma == Plataforma.WHATSAPP) compatWa else compat
    val algumModo = compatAtual.values.any { it.valeParaVersao(appVersion) }
    val pronto = a11yOk && contatosOk && algumModo

    val nomeApp = plataforma.titulo  // "Instagram" ou "WhatsApp"

    ScreenColumn(modifier) {
        ScreenTitle(
            "Permissões",
            if (plataforma == Plataforma.WHATSAPP) {
                "Contatos e Acessibilidade são decisões separadas. Você pode revogá-las quando quiser."
            } else {
                "Só a Acessibilidade é pedida. Você pode revogá-la quando quiser."
            },
        )

        // No WhatsApp, precisamos ler a agenda para formar as listas de transmissão
        if (plataforma == Plataforma.WHATSAPP) {
            PermissionCard(
                title = "Contatos",
                reason = "Para ler os números da agenda e organizar as listas de transmissão no WhatsApp. " +
                    "Somente leitura: o app não altera nem cria contatos.",
                state = contactsState,
                actionLabel = if (contactsPermanentlyDenied) "Abrir ajustes do app"
                else "Permitir acesso aos contatos",
                onAction = if (contactsPermanentlyDenied) onOpenAppSettings else onAskContacts,
                fixHint = if (contactsPermanentlyDenied) {
                    "A permissão foi negada mais de uma vez. Ative em Ajustes › Permissões › Contatos."
                } else null,
                testTag = "perm_contacts",
            )
        }

        PermissionCard(
            title = "Serviço de Acessibilidade",
            reason = "Lê a tela do $nomeApp para achar a conversa, conferir o destinatário e tocar em " +
                "Enviar por você. Leia o aviso antes de decidir.",
            state = accessibilityState,
            actionLabel = "Abrir ajustes de Acessibilidade",
            onAction = { mostrarAvisoAcessibilidade = true },
            fixHint = "Em Acessibilidade, procure Lista Local e ative o serviço.",
            testTag = "perm_accessibility",
        )

        if (!a11yOk) {
            InlineWarning(
                "Android 13 ou mais novo: se a opção aparecer esmaecida, abra Ajustes › Apps › " +
                    "Lista Local › menu (⋮) › Permitir configurações restritas e tente de novo.",
                tone = WarningTone.INFO,
            )
        }

        // Card de conferência específico da plataforma
        if (plataforma == Plataforma.WHATSAPP) {
            WhatsAppCheckCard(
                version = whatsappVersion,
                compat = compatWa,
                checking = checking,
                accessibilityReady = a11yOk,
                onCheck = onCheckWa,
            )
        } else {
            InstagramCheckCard(
                version = instagramVersion,
                compat = compat,
                checking = checking,
                accessibilityReady = a11yOk,
                onCheck = onCheck,
            )
        }

        PrimaryActionButton(
            text = "Continuar",
            onClick = onContinue,
            enabled = pronto,
            testTag = "permissions_continue",
        )

        if (!pronto) {
            Text(
                when {
                    appVersion == null -> "Para continuar, instale o $nomeApp."
                    !a11yOk -> "Para continuar, ative o Serviço de Acessibilidade."
                    plataforma == Plataforma.WHATSAPP && !contatosOk -> "Para continuar, permita o acesso aos contatos."
                    else -> "Para continuar, confira o $nomeApp."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = onOpenAppSettings) {
            Text("Abrir ajustes do app")
        }
        MarcaAutor()
    }

    if (mostrarAvisoAcessibilidade) {
        AlertDialog(
            onDismissRequest = { mostrarAvisoAcessibilidade = false },
            title = { Text("Antes de ativar Acessibilidade") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("O Lista Local lê o que aparece no $nomeApp, incluindo nomes, identificadores " +
                        "e mensagens visíveis nas conversas que ele abre, para conferir a pessoa certa " +
                        "e tocar nos botões da operação iniciada por você.")
                    Text("O Android alerta que um serviço de Acessibilidade pode ler o conteúdo " +
                        "da tela e interagir com apps em seu nome. Esta versão limita a automação " +
                        "ao $nomeApp.")
                    Text("O app processa esses dados no aparelho e não tem permissão de Internet " +
                        "para enviá-los. Você pode desligar o serviço em Configurações > " +
                        "Acessibilidade a qualquer momento.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    mostrarAvisoAcessibilidade = false
                    onOpenAccessibility()
                }) { Text("Entendi, abrir ajustes") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarAvisoAcessibilidade = false }) {
                    Text("Agora não")
                }
            },
        )
    }
}

@Preview(name = "Permissões · Instagram · pendente", showBackground = true)
@Composable
private fun PermissionsPreviewInstagram() {
    ListaLocalTheme(darkTheme = false) {
        PermissionsScreen(
            accessibilityState = PermissionState.PENDING,
            onOpenAccessibility = {}, onContinue = {},
            plataforma = Plataforma.INSTAGRAM,
            instagramVersion = "448.0.0.52.84",
        )
    }
}

@Preview(name = "Permissões · WhatsApp · pendente", showBackground = true)
@Composable
private fun PermissionsPreviewWhatsApp() {
    ListaLocalTheme(darkTheme = false) {
        PermissionsScreen(
            accessibilityState = PermissionState.PENDING,
            onOpenAccessibility = {}, onContinue = {},
            plataforma = Plataforma.WHATSAPP,
            whatsappVersion = "2.24.10.82",
        )
    }
}
