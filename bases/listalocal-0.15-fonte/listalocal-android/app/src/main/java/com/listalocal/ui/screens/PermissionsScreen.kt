package com.listalocal.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
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
import com.listalocal.core.selectors.TargetApp
import com.listalocal.service.CompatCheck
import com.listalocal.ui.components.InlineWarning
import com.listalocal.ui.components.PermissionCard
import com.listalocal.ui.components.PermissionState
import com.listalocal.ui.components.PrimaryActionButton
import com.listalocal.ui.components.ScreenColumn
import com.listalocal.ui.components.ScreenTitle
import com.listalocal.ui.components.TargetAppCard
import com.listalocal.ui.components.WarningTone
import com.listalocal.ui.theme.ListaLocalTheme

/**
 * Etapa 2 — as duas autorizacoes que o app realmente usa, cada uma com motivo,
 * estado e correcao. O app nao pede notificacoes: nao existe permissao de
 * notificacao no manifesto e nada aqui envia notificacao.
 */
@Composable
fun PermissionsScreen(
    contactsState: PermissionState,
    accessibilityState: PermissionState,
    onAskContacts: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    contactsPermanentlyDenied: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
    installedApps: List<TargetApp> = emptyList(),
    selectedApp: TargetApp = TargetApp.WHATSAPP,
    onSelectApp: (TargetApp) -> Unit = {},
    onCheckCompat: () -> Unit = {},
    checkingCompat: Boolean = false,
    compat: CompatCheck? = null,
    onOpenTargetApp: () -> Unit = {},
    onOpenOfficialHelp: () -> Unit = {},
) {
    var mostrarAvisoAcessibilidade by rememberSaveable { mutableStateOf(false) }
    val permissoesOk = contactsState == PermissionState.GRANTED &&
        accessibilityState == PermissionState.GRANTED
    // Perfil so validado no WhatsApp comum: no Business, exigimos a prova na tela.
    val precisaVerificar = selectedApp != TargetApp.WHATSAPP
    val appOk = installedApps.isNotEmpty() && (!precisaVerificar || compat?.ok == true)
    val pronto = permissoesOk && appOk

    ScreenColumn(modifier) {
        ScreenTitle(
            "Permissões",
            "Contatos e Acessibilidade são decisões separadas. Você pode revogá-las.",
        )

        PermissionCard(
            title = "Contatos",
            reason = "Para ler os números da agenda e organizar as listas. " +
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

        PermissionCard(
            title = "Serviço de Acessibilidade",
            reason = "Lê a interface do WhatsApp para localizar controles e tocar por você " +
                "ao montar as listas. Leia o aviso antes de decidir.",
            state = accessibilityState,
            actionLabel = "Abrir ajustes de Acessibilidade",
            onAction = { mostrarAvisoAcessibilidade = true },
            fixHint = "Em Acessibilidade, procure Lista Local e ative o serviço.",
            testTag = "perm_accessibility",
        )

        TargetAppCard(
            installed = installedApps,
            selected = selectedApp,
            onSelect = onSelectApp,
            onCheck = onCheckCompat,
            checking = checkingCompat,
            compat = compat,
            accessibilityReady = accessibilityState == PermissionState.GRANTED,
            onOpenTargetApp = onOpenTargetApp,
            onOpenOfficialHelp = onOpenOfficialHelp,
        )

        if (accessibilityState != PermissionState.GRANTED) {
            InlineWarning(
                "Android 13 ou mais novo: se a opção aparecer esmaecida, abra Ajustes › Apps › " +
                    "Lista Local › menu (⋮) › Permitir configurações restritas e tente de novo.",
                tone = WarningTone.INFO,
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
                textoDoQueFalta(
                    contatos = contactsState,
                    acessibilidade = accessibilityState,
                    semApp = installedApps.isEmpty(),
                    faltaVerificar = precisaVerificar && compat == null,
                    incompativel = precisaVerificar && compat != null && !compat.ok,
                    app = selectedApp,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = onOpenAppSettings) {
            Text("Abrir ajustes do app")
        }
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
                    Text("O Lista Local lê os controles exibidos no WhatsApp e no WhatsApp Business, " +
                        "incluindo nomes e números visíveis, para encontrar contatos e tocar nos " +
                        "botões que criam a lista iniciada por você.")
                    Text("O Android alerta que um serviço de Acessibilidade pode ler o conteúdo " +
                        "da tela e interagir com apps em seu nome. Esta versão limita a automação " +
                        "aos dois apps do WhatsApp.")
                    Text("O Lista Local processa esses dados no aparelho e não tem permissão " +
                        "de Internet para enviá-los. Você pode desligar o serviço em " +
                        "Configurações > Acessibilidade a qualquer momento.")
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

private fun textoDoQueFalta(
    contatos: PermissionState,
    acessibilidade: PermissionState,
    semApp: Boolean,
    faltaVerificar: Boolean,
    incompativel: Boolean,
    app: TargetApp,
): String {
    // Ja verificamos e nao serve: dizer "falta verificar" seria mandar o
    // operador repetir um teste que ja deu resposta.
    if (incompativel) {
        return "A transmissão não apareceu nesta conta do ${app.label}. Siga os passos " +
            "acima e verifique de novo. O app só avança quando encontra o fluxo correto."
    }
    val falta = buildList {
        if (contatos != PermissionState.GRANTED) add("a permissão de Contatos")
        if (acessibilidade != PermissionState.GRANTED) add("o Serviço de Acessibilidade")
        if (semApp) add("ter o WhatsApp ou o WhatsApp Business instalado")
        if (faltaVerificar) add("verificar a compatibilidade com o ${app.label}")
    }
    if (falta.isEmpty()) return ""
    return "Para continuar, falta ${falta.joinToString(" e ")}."
}

@Preview(name = "Permissões · pendentes", showBackground = true)
@Composable
private fun PermissionsPreviewPending() {
    ListaLocalTheme(darkTheme = false) {
        PermissionsScreen(
            contactsState = PermissionState.PENDING,
            accessibilityState = PermissionState.PENDING,
            onAskContacts = {}, onOpenAccessibility = {}, onContinue = {},
        )
    }
}

@Preview(name = "Permissões · uma pendente (escuro)", showBackground = true)
@Composable
private fun PermissionsPreviewPartialDark() {
    ListaLocalTheme(darkTheme = true) {
        PermissionsScreen(
            contactsState = PermissionState.GRANTED,
            accessibilityState = PermissionState.ACTION_NEEDED,
            onAskContacts = {}, onOpenAccessibility = {}, onContinue = {},
        )
    }
}
