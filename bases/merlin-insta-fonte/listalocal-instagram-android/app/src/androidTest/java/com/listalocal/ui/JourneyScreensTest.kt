package com.listalocal.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.listalocal.core.followers.Audience
import com.listalocal.core.selectors.SelectorProfile
import com.listalocal.service.Campanha
import com.listalocal.service.CompatCheck
import com.listalocal.service.Desfecho
import com.listalocal.service.ListProgress
import com.listalocal.service.Modo
import com.listalocal.service.Origem
import com.listalocal.service.PersonResult
import com.listalocal.service.RunPhase
import com.listalocal.service.RunState
import com.listalocal.service.Velocidade
import com.listalocal.ui.components.PermissionState
import com.listalocal.ui.screens.ConsentScreen
import com.listalocal.ui.screens.PermissionsScreen
import com.listalocal.ui.screens.PlanScreen
import com.listalocal.ui.screens.RecipientsScreen
import com.listalocal.ui.screens.ResultScreen
import com.listalocal.ui.screens.RunScreen
import com.listalocal.ui.theme.ListaLocalTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private val versao = "448.0.0.52.84"
    private val dmOk = mapOf(
        Modo.DM to CompatCheck(Modo.DM, versao, "minhaconta", SelectorProfile.DM_KEYS.associateWith { true }),
    )
    private val pronto = UiState(versaoInstagram = versao, contaInstagram = "minhaconta")

    @Test
    fun consentimento_soComecaDepoisDeAceitarOsRiscos() {
        var aceitou = false
        compose.setContent {
            ListaLocalTheme { ConsentScreen(onStart = {}, aceitouRiscos = false, onAceitarRiscos = { aceitou = it }) }
        }
        compose.onNodeWithTag("consent_start").assertIsNotEnabled()
        compose.onNodeWithText("Entendi os riscos e quero usar mesmo assim.").performClick()
        assertTrue(aceitou)
    }

    @Test
    fun permissoes_bloqueiamAvancoSemConferencia() {
        compose.setContent {
            ListaLocalTheme {
                PermissionsScreen(
                    accessibilityState = PermissionState.GRANTED,
                    onOpenAccessibility = {}, onContinue = {},
                    instagramVersion = versao,
                )
            }
        }
        compose.onNodeWithTag("permissions_continue").assertIsNotEnabled()
    }

    @Test
    fun permissoes_liberamComAcessibilidadeEUmModoConferido() {
        compose.setContent {
            ListaLocalTheme {
                PermissionsScreen(
                    accessibilityState = PermissionState.GRANTED,
                    onOpenAccessibility = {}, onContinue = {},
                    instagramVersion = versao, compat = dmOk,
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
                    accessibilityState = PermissionState.PENDING,
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

    private fun destinatarios(ui: UiState, retomar: () -> Unit = {}) = compose.setContent {
        ListaLocalTheme {
            RecipientsScreen(
                ui = ui, onOrigem = {}, onPularComerciais = {}, onNaoEnviar = {}, onSoPara = {},
                retomarLiberado = true, onRetomar = retomar, onDescartar = {}, onContinue = {},
            )
        }
    }

    @Test
    fun destinatarios_semInstagramConferidoNaoAvancam() {
        destinatarios(pronto.copy(contaInstagram = null))
        compose.onNodeWithTag("recipients_continue").assertIsNotEnabled()
    }

    @Test
    fun destinatarios_semArquivoEComOrigemAoVivo() {
        destinatarios(pronto)
        compose.onNodeWithText("Seus seguidores").assertExists()
        compose.onNodeWithText("Conversas do Direct").assertExists()
        compose.onNodeWithText("Importar CSV de seguidores").assertDoesNotExist()
        compose.onNodeWithTag("recipients_continue").assertIsEnabled()
    }

    @Test
    fun destinatarios_textoQueNaoEArrobaBloqueia() {
        destinatarios(pronto.copy(naoEnviarTexto = "ana silva!", naoEnviar = Audience.arrobas("ana silva!")))
        compose.onNodeWithTag("recipients_continue").assertIsNotEnabled()
    }

    @Test
    fun destinatarios_retomaDoPontoSalvo() {
        var retomou = false
        val salva = Campanha(
            conta = "minhaconta", origem = Origem.SEGUIDORES, mensagens = listOf("Oi"), velocidade = Velocidade.RAPIDO,
            inicio = 1L, ultimo = "bia",
        )
        destinatarios(
            pronto.copy(salva = salva, salvaFeitos = listOf(PersonResult("ana", "ana", 0, Desfecho.ENVIADO, ""))),
        ) { retomou = true }
        compose.onNodeWithText("@bia").assertExists()
        compose.onNodeWithText("1 enviado · 0 pulados · 0 falhas").assertExists()
        compose.onNodeWithTag("recipients_resume").performClick()
        assertTrue(retomou)
    }

    @Test
    fun plano_naoIniciaSemBaseLegalEConfirmacao() {
        compose.setContent {
            ListaLocalTheme {
                PlanScreen(
                    ui = pronto.copy(mensagem = "Oi"),
                    compat = dmOk, accessibilityReady = true,
                    onModo = {}, onMensagem = {}, onVelocidade = {}, onConfirmarCadaLote = {},
                    onBaseLegal = {}, onBaseLegalOutro = {}, onConfirmouSeguidores = {}, onStart = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("plan_start").assertIsNotEnabled()
    }

    @Test
    fun plano_iniciaComTudoConferido() {
        compose.setContent {
            ListaLocalTheme {
                PlanScreen(
                    ui = pronto.copy(mensagem = "Oi", baseLegal = BASES_LEGAIS.first(), confirmouSeguidores = true),
                    compat = dmOk, accessibilityReady = true,
                    onModo = {}, onMensagem = {}, onVelocidade = {}, onConfirmarCadaLote = {},
                    onBaseLegal = {}, onBaseLegalOutro = {}, onConfirmouSeguidores = {}, onStart = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("plan_start").assertIsEnabled()
        // Velocidade sem segundos na tela.
        compose.onNodeWithText("Muito rápido").assertExists()
        compose.onNodeWithText("Normal · 30 s").assertDoesNotExist()
    }

    @Test
    fun execucao_mostraFaseEPausar() {
        compose.setContent {
            ListaLocalTheme {
                RunScreen(
                    run = RunState(
                        phase = RunPhase.WORKING, running = true, atual = "ana", currentListIndex = 1,
                        lists = listOf(ListProgress(1, "Lote 001", 2)),
                    ),
                    onConfirm = {}, onDecline = {}, onPause = {}, onResume = {}, onRequestCancel = {},
                )
            }
        }
        compose.onNodeWithTag("run_phase").assertExists()
        compose.onNodeWithText("Trabalhando").assertExists()
        compose.onNodeWithTag("run_pause").assertExists()
    }

    @Test
    fun execucao_aoVivoMostraEnviadosPuladosEFalhas() {
        compose.setContent {
            ListaLocalTheme {
                RunScreen(
                    run = RunState(
                        phase = RunPhase.WORKING, running = true, origem = Origem.SEGUIDORES, atual = "caio",
                        results = listOf(
                            PersonResult("ana", "Ana", 0, Desfecho.ENVIADO, ""),
                            PersonResult("bia", "Bia", 0, Desfecho.ENVIADO, ""),
                            PersonResult("duda", "Duda", 0, Desfecho.PULADO, "na lista Não enviar para"),
                            PersonResult("edu", "Edu", 0, Desfecho.INCERTO, "sem evidência"),
                        ),
                    ),
                    onConfirm = {}, onDecline = {}, onPause = {}, onResume = {}, onRequestCancel = {},
                )
            }
        }
        compose.onNodeWithTag("run_progress").assertExists()
        compose.onNodeWithText("2 enviados · 1 pulado · 1 falha").assertExists()
        compose.onNodeWithTag("run_pause").assertExists()
    }

    @Test
    fun resultado_mostraFalhasEExportar() {
        compose.setContent {
            ListaLocalTheme {
                ResultScreen(
                    run = RunState(
                        phase = RunPhase.COMPLETED,
                        results = listOf(PersonResult("ana", "Ana", 1, Desfecho.INCERTO, "sem evidência")),
                    ),
                    onNewOperation = {}, onExport = {},
                )
            }
        }
        compose.onNodeWithTag("result_headline").assertExists()
        compose.onNodeWithTag("result_export").assertIsEnabled()
    }
}
