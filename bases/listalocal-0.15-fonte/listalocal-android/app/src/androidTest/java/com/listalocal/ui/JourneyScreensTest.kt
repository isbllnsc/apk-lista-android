package com.listalocal.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.listalocal.core.selectors.TargetApp
import com.listalocal.service.ListProgress
import com.listalocal.service.RunPhase
import com.listalocal.service.RunState
import com.listalocal.ui.components.PermissionState
import com.listalocal.ui.screens.ConsentScreen
import com.listalocal.ui.screens.ContactsScreen
import com.listalocal.ui.screens.PermissionsScreen
import com.listalocal.ui.screens.ResultScreen
import com.listalocal.ui.screens.RunScreen
import com.listalocal.ui.theme.ListaLocalTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes da INTERFACE. Exercitam apenas composables sem estado: nenhuma
 * automacao e iniciada e o AccessibilityService nunca e tocado.
 *
 * Rodar com aparelho ou emulador conectado:
 *   gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class JourneyScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun consentimento_temAcaoDeComecar() {
        var clicou = false
        compose.setContent {
            ListaLocalTheme { ConsentScreen(onStart = { clicou = true }) }
        }
        compose.onNodeWithTag("consent_start").assertIsEnabled().performClick()
        assertTrue(clicou)
    }

    @Test
    fun permissoes_bloqueiamAvancoAteEstaremConcedidas() {
        compose.setContent {
            ListaLocalTheme {
                PermissionsScreen(
                    contactsState = PermissionState.GRANTED,
                    accessibilityState = PermissionState.PENDING,
                    onAskContacts = {}, onOpenAccessibility = {}, onContinue = {},
                    installedApps = listOf(TargetApp.WHATSAPP),
                )
            }
        }
        compose.onNodeWithTag("permissions_continue").assertIsNotEnabled()
        compose.onNodeWithTag("perm_accessibility").assertExists()
    }

    @Test
    fun permissoes_liberamAvancoQuandoAmbasConcedidas() {
        compose.setContent {
            ListaLocalTheme {
                PermissionsScreen(
                    contactsState = PermissionState.GRANTED,
                    accessibilityState = PermissionState.GRANTED,
                    onAskContacts = {}, onOpenAccessibility = {}, onContinue = {},
                    installedApps = listOf(TargetApp.WHATSAPP),
                )
            }
        }
        compose.onNodeWithTag("permissions_continue").assertIsEnabled()
    }

    @Test
    fun acessibilidade_exigeAvisoEEscolhaAntesDeAbrirAjustes() {
        var abriuAjustes = false
        compose.setContent {
            ListaLocalTheme {
                PermissionsScreen(
                    contactsState = PermissionState.GRANTED,
                    accessibilityState = PermissionState.PENDING,
                    onAskContacts = {},
                    onOpenAccessibility = { abriuAjustes = true },
                    onContinue = {},
                )
            }
        }

        compose.onNodeWithText("Abrir ajustes de Acessibilidade").performClick()
        compose.onNodeWithText("Antes de ativar Acessibilidade").assertExists()
        assertFalse(abriuAjustes)

        compose.onNodeWithText("Agora não").performClick()
        assertFalse(abriuAjustes)

        compose.onNodeWithText("Abrir ajustes de Acessibilidade").performClick()
        compose.onNodeWithText("Entendi, abrir ajustes").performClick()
        assertTrue(abriuAjustes)
    }

    @Test
    fun contatos_bloqueiamAvancoComMenosDeDoisContatos() {
        compose.setContent {
            ListaLocalTheme {
                ContactsScreen(
                    ui = UiState(validos = 10, filtrados = 1, totalContatos = 10),
                    onToggleDdd = {}, onToggleUf = {}, onToggleRegiao = {},
                    onDddTextChange = {}, onClearFilters = {}, onContinue = {},
                )
            }
        }
        compose.onNodeWithTag("contacts_continue").assertIsNotEnabled()
    }

    @Test
    fun contatos_permiteBuscarEExcluirPessoaSemAlterarAgenda() {
        var excludedId = -1L
        compose.setContent {
            ListaLocalTheme {
                ContactsScreen(
                    ui = UiState(
                        validos = 3, filtrados = 3,
                        exclusionChoices = listOf(
                            ExclusionChoice(1L, "Ana", listOf("+5521999990001")),
                            ExclusionChoice(2L, "Bruno", listOf("+5521999990002")),
                        ),
                    ),
                    onToggleDdd = {}, onToggleUf = {}, onToggleRegiao = {},
                    onDddTextChange = {}, onClearFilters = {}, onContinue = {},
                    onToggleExclusion = { excludedId = it },
                )
            }
        }
        compose.onNodeWithTag("contacts_choose_exclusions").performClick()
        compose.onNodeWithText("Buscar contato").performTextInput("Ana")
        compose.onNodeWithContentDescription("Excluir Ana das listas").performClick()
        assertEquals(1L, excludedId)
    }

    @Test
    fun execucao_mostraFaseEmLinguagemHumana() {
        compose.setContent {
            ListaLocalTheme {
                RunScreen(
                    run = RunState(
                        phase = RunPhase.SELECTING,
                        currentListIndex = 1,
                        lists = listOf(ListProgress(1, "Transmissão 001", 256, selected = 12)),
                        running = true,
                    ),
                    onConfirmList = {}, onCancelList = {}, onPause = {},
                    onResume = {}, onRequestCancel = {},
                )
            }
        }
        compose.onNodeWithTag("run_phase").assertExists()
        compose.onNodeWithText("Selecionando contatos").assertExists()
        compose.onNodeWithTag("run_pause").assertExists()
    }

    @Test
    fun resultado_resumeOQueFoiCriado() {
        compose.setContent {
            ListaLocalTheme {
                ResultScreen(
                    run = RunState(
                        phase = RunPhase.COMPLETED,
                        lists = List(3) {
                            ListProgress(it + 1, "Transmissão %03d".format(it + 1), 256,
                                selected = 250, created = true)
                        },
                        running = false,
                    ),
                    onRestart = {}, onBackToContacts = {},
                )
            }
        }
        compose.onNodeWithTag("result_headline").assertExists()
        compose.onNodeWithTag("result_restart").assertIsEnabled()
    }
}
