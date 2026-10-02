package com.listalocal.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.listalocal.expiry.ExpiryGate
import com.listalocal.service.Modo
import com.listalocal.service.RunPhase
import com.listalocal.ui.components.JourneyStepper
import com.listalocal.ui.components.ListaLocalTopBar
import com.listalocal.ui.components.PermissionState
import com.listalocal.ui.screens.ConsentScreen
import com.listalocal.ui.screens.PermissionsScreen
import com.listalocal.ui.screens.PlanScreen
import com.listalocal.ui.screens.RecipientsScreen
import com.listalocal.ui.screens.ResultScreen
import com.listalocal.ui.screens.RunScreen
import com.listalocal.ui.screens.WaBroadcastScreen
import com.listalocal.ui.screens.WaMessageScreen
import com.listalocal.core.contacts.Plataforma
import com.listalocal.ui.theme.ListaLocalTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 (targetSdk 35) impoe edge-to-edge; os insets sao tratados no Scaffold.
        enableEdgeToEdge()
        setContent {
            ListaLocalTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var expired by remember { mutableStateOf(ExpiryGate.isExpired(context)) }
    LaunchedEffect(expired) {
        if (expired) {
            vm.expireNow()
        } else {
            while (true) {
                delay(250)
                if (ExpiryGate.isExpired(context)) { expired = true; break }
            }
        }
    }
    if (expired) {
        ExpiredScreen(
            onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            onOpenAppSettings = { context.startActivity(ajustesDoApp(context.packageName)) },
        )
        return
    }

    val ui by vm.ui.collectAsStateWithLifecycle()
    val run by vm.runState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var a11yOk by remember { mutableStateOf(Permissions.isAccessibilityEnabled(context)) }
    var contatosOk by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var contatosNegadoDeVez by remember { mutableStateOf(false) }
    var jaPediuContatos by remember { mutableStateOf(false) }

    val pedirContatos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        contatosOk = concedido
        jaPediuContatos = true
        val activity = context as? ComponentActivity
        contatosNegadoDeVez = !concedido && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.READ_CONTACTS,
            )
    }

    // A planilha do resultado pelo seletor do Android: sem permissao de armazenamento.
    val salvarResultado = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportarResultado)
    }

    // Ao voltar dos ajustes do Android (ou do Instagram), reavalia sem reabrir o app.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (ExpiryGate.isExpired(context)) {
                    expired = true
                    vm.expireNow()
                    return@LifecycleEventObserver
                }
                vm.detectarInstagram()
                a11yOk = Permissions.isAccessibilityEnabled(context)
                contatosOk = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CONTACTS,
                ) == PackageManager.PERMISSION_GRANTED
                if (contatosOk) contatosNegadoDeVez = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A tela nao pode apagar durante a execucao: tela bloqueada derruba a automacao.
    LaunchedEffect(run.running) { view.keepScreenOn = run.running }

    // Quando a automacao termina (por qualquer motivo), a jornada segue para o resultado.
    LaunchedEffect(run.running, ui.step, run.phase) {
        when {
            // Fase 1 concluída: navega para a tela de mensagem
            ui.step == Step.RUN && !run.running && run.phase == RunPhase.LISTS_CREATED ->
                vm.goTo(Step.WA_MESSAGE)
            // Qualquer outra conclusão: vai para resultado
            ui.step == Step.RUN && !run.running && run.phase != RunPhase.IDLE ->
                vm.goTo(Step.RESULT)
        }
    }

    var confirmarParada by remember { mutableStateOf(false) }

    BackHandler(enabled = ui.step != Step.CONSENT) {
        when (ui.step) {
            Step.PERMISSIONS   -> vm.goTo(Step.CONSENT)
            Step.RECIPIENTS    -> vm.goTo(Step.PERMISSIONS)
            Step.WA_BROADCAST  -> vm.goTo(Step.PERMISSIONS)
            Step.WA_MESSAGE    -> vm.goTo(Step.WA_BROADCAST)  // volta para Etapa 3 (não recria listas)
            Step.PLAN          -> vm.goTo(Step.RECIPIENTS)
            Step.RUN           -> if (run.running) confirmarParada = true else vm.goTo(Step.RESULT)
            Step.RESULT        -> vm.novaOperacao()
            Step.CONSENT       -> Unit
        }
    }

    if (confirmarParada) {
        AlertDialog(
            onDismissRequest = { confirmarParada = false },
            title = { Text("Parar a operação?") },
            text = {
                Text(
                    "A pessoa em andamento termina ou fica como falha antes do envio. " +
                        "Quem já recebeu continua registrado e não recebe de novo.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarParada = false; vm.cancelar() }) { Text("Parar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarParada = false }) { Text("Continuar") }
            },
        )
    }

    val voltar: (() -> Unit)? = when (ui.step) {
        Step.CONSENT       -> null
        Step.PERMISSIONS   -> { { vm.goTo(Step.CONSENT) } }
        Step.RECIPIENTS    -> { { vm.goTo(Step.PERMISSIONS) } }
        Step.WA_BROADCAST  -> { { vm.goTo(Step.PERMISSIONS) } }
        Step.WA_MESSAGE    -> { { vm.goTo(Step.WA_BROADCAST) } }
        Step.PLAN          -> { { vm.goTo(Step.RECIPIENTS) } }
        Step.RUN           -> if (run.running) { { confirmarParada = true } } else { { vm.goTo(Step.RESULT) } }
        Step.RESULT        -> { { vm.novaOperacao() } }
    }

    Scaffold(
        topBar = {
            Column {
                ListaLocalTopBar(title = ui.step.titulo, subtitle = "Merlin", onBack = voltar)
                // Stepper: WA tem 4 etapas (inclui WA_MESSAGE); Instagram usa o enum menos etapas WA
                val isWa = ui.plataforma == Plataforma.WHATSAPP
                val waSteps  = listOf(Step.CONSENT, Step.PERMISSIONS, Step.WA_BROADCAST, Step.WA_MESSAGE)
                val igSteps  = Step.entries.filter { it != Step.WA_BROADCAST && it != Step.WA_MESSAGE }
                val stepList = if (isWa) waSteps else igSteps
                val stepIdx  = stepList.indexOfFirst { it == ui.step }.coerceAtLeast(0)
                JourneyStepper(stepIndex = stepIdx, stepCount = stepList.size, stepName = ui.step.titulo)
                if (ExpiryGate.enabled) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            "Versão com prazo: funciona até 04/10/2026. " +
                                "A partir de 05/10/2026, o acesso será encerrado.",
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().imePadding()) {
            when (ui.step) {
                Step.CONSENT -> ConsentScreen(
                    onStart = { vm.goTo(Step.PERMISSIONS) },
                    plataforma = ui.plataforma,
                    onPlataforma = vm::setPlataforma,
                    aceitouRiscos = ui.aceitouRiscos,
                    onAceitarRiscos = vm::aceitarRiscos,
                )

                Step.PERMISSIONS -> PermissionsScreen(
                    accessibilityState = if (a11yOk) PermissionState.GRANTED else PermissionState.PENDING,
                    onOpenAccessibility = {
                        if (!ExpiryGate.isExpired(context)) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    },
                    // WhatsApp vai direto para a criação de listas; Instagram vai para Destinatários
                    onContinue = {
                        if (ui.plataforma == Plataforma.WHATSAPP) vm.goTo(Step.WA_BROADCAST)
                        else vm.goTo(Step.RECIPIENTS)
                    },
                    onOpenAppSettings = { context.startActivity(ajustesDoApp(context.packageName)) },
                    plataforma = ui.plataforma,
                    contactsState = estado(contatosOk, contatosNegadoDeVez, jaPediuContatos),
                    onAskContacts = { pedirContatos.launch(Manifest.permission.READ_CONTACTS) },
                    contactsPermanentlyDenied = contatosNegadoDeVez,
                    instagramVersion = ui.versaoInstagram,
                    whatsappVersion = run.appVersionWa.takeIf { it.isNotEmpty() },
                    compat = run.compat,
                    compatWa = run.compatWa,
                    checking = run.checkingCompat,
                    onCheck = vm::verificarCompatibilidade,
                    onCheckWa = vm::verificarCompatibilidadeWa,
                )

                Step.WA_BROADCAST -> WaBroadcastScreen(
                    onIniciar = {
                        if (!contatosOk) {
                            pedirContatos.launch(Manifest.permission.READ_CONTACTS)
                        } else {
                            vm.iniciarBroadcastWa()
                        }
                    },
                    onRelerListas = vm::relerListasWa,
                    onBack = { vm.goTo(Step.PERMISSIONS) },
                    contatosOk = contatosOk,
                    onPedirContatos = { pedirContatos.launch(Manifest.permission.READ_CONTACTS) },
                    soPor5521 = ui.soPor5521,
                    onSoPor5521Change = vm::toggleSoPor5521,
                )

                Step.WA_MESSAGE -> {
                    // Mostra todas as listas: as criadas agora E as já existentes (detectadas via scan)
                    val todasListas = run.broadcastListas
                    val selecionadas = if (ui.listasSelecionadasWa.isNotEmpty()) {
                        ui.listasSelecionadasWa
                    } else {
                        todasListas.map { it.index }.toSet()
                    }

                    WaMessageScreen(
                        listas = todasListas,
                        listasSelecionadas = selecionadas,
                        onToggleLista = vm::toggleListaWa,
                        onSelectAll = { vm.selectAllListasWa(todasListas.map { it.index }.toSet()) },
                        onDeselectAll = vm::deselectAllListasWa,
                        mensagem = ui.mensagemWa,
                        onMensagemChange = vm::setMensagemWa,
                        onEnviar = vm::enviarMensagemWa,
                        onRelerListas = vm::relerListasWa,
                        onBack = { vm.goTo(Step.WA_BROADCAST) },
                    )
                }

                Step.RECIPIENTS -> RecipientsScreen(
                    ui = ui,
                    plataforma = ui.plataforma,
                    onOrigem = vm::setOrigem,
                    onPularComerciais = vm::setPularComerciais,
                    onNaoEnviar = vm::setNaoEnviar,
                    onSoPara = vm::setSoPara,
                    retomarLiberado = a11yOk && when (ui.plataforma) {
                        Plataforma.WHATSAPP ->
                            run.compatWa[Modo.DM]?.valeParaVersao(run.appVersionWa.takeIf { it.isNotEmpty() }) == true
                        else ->
                            run.compat[Modo.DM]?.valeParaVersao(ui.versaoInstagram) == true
                    },
                    onRetomar = vm::retomarSalva,
                    onDescartar = vm::descartarSalva,
                    onContinue = { vm.goTo(Step.PLAN) },
                )

                Step.PLAN -> PlanScreen(
                    ui = ui,
                    compat = run.compat,
                    compatWa = run.compatWa,
                    appVersionWa = run.appVersionWa.takeIf { it.isNotEmpty() },
                    plataforma = ui.plataforma,
                    accessibilityReady = a11yOk,
                    onModo = vm::setModo,
                    onMensagem = vm::setMensagem,
                    onVelocidade = vm::setVelocidade,
                    onConfirmarCadaLote = { confirmar -> vm.setAutoConfirm(!confirmar) },
                    onBaseLegal = vm::setBaseLegal,
                    onBaseLegalOutro = vm::setBaseLegalOutro,
                    onConfirmouSeguidores = vm::setConfirmou,
                    onStart = vm::iniciar,
                    onBack = { vm.goTo(Step.RECIPIENTS) },
                    trocandoConta = run.trocandoConta,
                    onTrocarConta = vm::trocarConta,
                    onMensagemN = vm::setMensagemN,
                    onModoMensagens = vm::setModoMensagens,
                    onLimite = vm::setLimite,
                    onAPartirDe = vm::setAPartirDe,
                    onPularJaRecebeu = vm::setPularJaRecebeu,
                    onPararAs = vm::setPararAs,
                )

                Step.RUN -> RunScreen(
                    run = run,
                    onConfirm = { vm.confirmar(true) },
                    onDecline = { vm.confirmar(false) },
                    onPause = vm::pausar,
                    onResume = vm::retomar,
                    onRequestCancel = { if (run.running) confirmarParada = true else vm.goTo(Step.RESULT) },
                )

                Step.RESULT -> ResultScreen(
                    run = run,
                    onNewOperation = vm::novaOperacao,
                    onExport = { salvarResultado.launch("Lista-Local-Instagram-resultado.csv") },
                    onResume = vm::retomarSalva.takeIf { run.origem != null && a11yOk },
                )
            }
        }
    }
}

@Composable
private fun ExpiredScreen(
    onOpenAccessibility: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Prazo encerrado", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Esta versão terminou em 04/10/2026. Ela não executa a automação.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Se a permissão ainda aparecer no celular, abra os ajustes abaixo para removê-la.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenAccessibility) { Text("Abrir Acessibilidade") }
        Button(onClick = onOpenAppSettings) { Text("Abrir permissões do app") }
    }
}

private fun ajustesDoApp(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** Traduz o par (concedida, negada de vez) no estado que a tela de permissoes mostra. */
private fun estado(
    granted: Boolean,
    permanentlyDenied: Boolean,
    jaPediuContatos: Boolean,
): PermissionState = when {
    granted -> PermissionState.GRANTED
    permanentlyDenied -> PermissionState.ACTION_NEEDED
    jaPediuContatos -> PermissionState.PENDING
    else -> PermissionState.PENDING
}
