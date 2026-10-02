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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.listalocal.ui.components.JourneyStepper
import com.listalocal.ui.components.ListaLocalTopBar
import com.listalocal.ui.components.PermissionState
import com.listalocal.ui.screens.ConsentScreen
import com.listalocal.ui.screens.ContactsScreen
import com.listalocal.ui.screens.PermissionsScreen
import com.listalocal.ui.screens.PlanScreen
import com.listalocal.ui.screens.ResultScreen
import com.listalocal.ui.screens.RunScreen
import com.listalocal.ui.theme.ListaLocalTheme
import com.listalocal.expiry.ExpiryGate
import com.listalocal.data.OneShotGate
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
    var finished by remember { mutableStateOf(OneShotGate.isConsumed(context)) }
    var expired by remember { mutableStateOf(if (finished) false else ExpiryGate.isExpired(context)) }
    var pendingAtLaunch by remember { mutableStateOf(OneShotGate.isAwaitingVerification(context)) }
    LaunchedEffect(expired, finished) {
        if (finished) {
            vm.finishOneShot()
        } else if (expired) {
            vm.expireNow()
        } else {
            while (true) {
                delay(250)
                if (OneShotGate.isConsumed(context)) {
                    finished = true
                    break
                }
                if (ExpiryGate.isExpired(context)) {
                    expired = true
                    break
                }
            }
        }
    }
    if (finished) {
        FinishedScreen(
            listsCreated = OneShotGate.consumedListsCount(context),
            onOpenAccessibility = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onOpenAppSettings = { context.startActivity(ajustesDoApp(context.packageName)) },
        )
        return
    }
    if (expired) {
        ExpiredScreen(
            onOpenAccessibility = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onOpenAppSettings = { context.startActivity(ajustesDoApp(context.packageName)) },
        )
        return
    }
    if (pendingAtLaunch) {
        PendingVerificationScreen(
            listsRequested = OneShotGate.pendingListsCount(context),
            onFinalize = {
                val saved = OneShotGate.markConsumedFromVerified(context)
                if (saved) {
                    finished = true
                    vm.finishOneShot()
                }
                saved
            },
            onReportMissing = {
                val cleared = OneShotGate.cancelPendingAfterFailedVerification(context)
                if (cleared) {
                    pendingAtLaunch = false
                    vm.goTo(Step.PERMISSIONS)
                }
                cleared
            },
        )
        return
    }

    val ui by vm.ui.collectAsStateWithLifecycle()
    val run by vm.runState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var contatosOk by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var a11yOk by remember { mutableStateOf(Permissions.isAccessibilityEnabled(context)) }
    var contatosNegadoDeVez by remember { mutableStateOf(false) }
    var jaPediuContatos by remember { mutableStateOf(false) }

    val pedirContatos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        contatosOk = concedido
        jaPediuContatos = true
        val activity = context as? ComponentActivity
        // Sem dialogo e sem racional = o Android nao vai mais perguntar: so ajustes resolvem.
        contatosNegadoDeVez = !concedido && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.READ_CONTACTS,
            )
    }

    // Ao voltar dos ajustes do Android, reavalia sem precisar reabrir o app.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (OneShotGate.isConsumed(context)) {
                    finished = true
                    vm.finishOneShot()
                    return@LifecycleEventObserver
                }
                if (ExpiryGate.isExpired(context)) {
                    expired = true
                    vm.expireNow()
                    return@LifecycleEventObserver
                }
                vm.detectarApps()
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
    LaunchedEffect(run.running, ui.step) {
        if (ui.step == Step.RUN && !run.running && run.totalLists > 0) {
            vm.goTo(Step.RESULT)
        }
    }

    var confirmarCancelamento by remember { mutableStateOf(false) }

    val awaitingVerification = OneShotGate.isAwaitingVerification(context)
    BackHandler(enabled = ui.step != Step.CONSENT && !awaitingVerification) {
        when (ui.step) {
            Step.PERMISSIONS -> vm.goTo(Step.CONSENT)
            Step.CONTACTS -> vm.goTo(Step.PERMISSIONS)
            Step.PLAN -> vm.goTo(Step.CONTACTS)
            Step.RUN -> if (run.running) confirmarCancelamento = true else vm.goTo(Step.RESULT)
            Step.RESULT -> vm.goTo(Step.CONTACTS)
            Step.CONSENT -> Unit
        }
    }

    if (confirmarCancelamento) {
        AlertDialog(
            onDismissRequest = { confirmarCancelamento = false },
            title = { Text("Cancelar a operação?") },
            text = {
                Text(
                    "A lista que está sendo montada agora não será criada. " +
                        "As listas já criadas permanecem no WhatsApp.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarCancelamento = false
                    vm.cancelar()
                }) { Text("Cancelar operação") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarCancelamento = false }) { Text("Continuar") }
            },
        )
    }

    val voltar: (() -> Unit)? = when (ui.step) {
        Step.CONSENT -> null
        Step.PERMISSIONS -> { { vm.goTo(Step.CONSENT) } }
        Step.CONTACTS -> { { vm.goTo(Step.PERMISSIONS) } }
        Step.PLAN -> { { vm.goTo(Step.CONTACTS) } }
        Step.RUN -> if (run.running) {
            { confirmarCancelamento = true }
        } else {
            { vm.goTo(Step.RESULT) }
        }
        Step.RESULT -> if (awaitingVerification) null else { { vm.goTo(Step.CONTACTS) } }
    }

    Scaffold(
        topBar = {
            Column {
                ListaLocalTopBar(
                    title = ui.step.titulo,
                    subtitle = "Lista Local",
                    onBack = voltar,
                )
                JourneyStepper(
                    stepIndex = ui.step.ordinal,
                    stepCount = Step.entries.size,
                    stepName = ui.step.titulo,
                )
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
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            when (ui.step) {
                Step.CONSENT -> ConsentScreen(onStart = { vm.goTo(Step.PERMISSIONS) })

                Step.PERMISSIONS -> PermissionsScreen(
                    contactsState = estado(contatosOk, contatosNegadoDeVez, jaPediuContatos),
                    accessibilityState = estado(a11yOk, false, jaPediuContatos = true),
                    onAskContacts = {
                        if (!OneShotGate.isConsumed(context) && !ExpiryGate.isExpired(context))
                            pedirContatos.launch(Manifest.permission.READ_CONTACTS)
                    },
                    onOpenAccessibility = {
                        if (!OneShotGate.isConsumed(context) && !ExpiryGate.isExpired(context))
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onContinue = vm::carregarContatos,
                    contactsPermanentlyDenied = contatosNegadoDeVez,
                    onOpenAppSettings = { context.startActivity(ajustesDoApp(context.packageName)) },
                    installedApps = ui.appsInstalados,
                    selectedApp = ui.appAlvo,
                    onSelectApp = vm::selecionarApp,
                    onCheckCompat = vm::verificarCompatibilidade,
                    checkingCompat = run.checkingCompat,
                    compat = run.compat,
                    onOpenTargetApp = {
                        if (!OneShotGate.isConsumed(context) && !ExpiryGate.isExpired(context)) {
                            context.packageManager
                                .getLaunchIntentForPackage(ui.appAlvo.packageName)
                                ?.also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                ?.let(context::startActivity)
                        }
                    },
                    onOpenOfficialHelp = {
                        if (!OneShotGate.isConsumed(context) && !ExpiryGate.isExpired(context)) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        "https://faq.whatsapp.com/653415899610349/?locale=pt_BR&cms_platform=android",
                                    ),
                                ),
                            )
                        }
                    },
                )

                Step.CONTACTS -> ContactsScreen(
                    ui = ui,
                    onToggleDdd = vm::toggleDdd,
                    onToggleUf = vm::toggleUf,
                    onToggleRegiao = vm::toggleRegiao,
                    onDddTextChange = vm::setDdd,
                    onClearFilters = vm::limparFiltros,
                    onContinue = { vm.goTo(Step.PLAN) },
                    onPrefixoChange = vm::setPrefixoNome,
                    onToggleExclusion = vm::toggleExclusion,
                    onClearExclusions = vm::clearExclusions,
                )

                Step.PLAN -> PlanScreen(
                    ui = ui,
                    accessibilityReady = a11yOk,
                    onToggleConfirmEachList = { confirmarCada -> vm.setAutoConfirm(!confirmarCada) },
                    onStart = vm::iniciar,
                    onBackToContacts = { vm.goTo(Step.CONTACTS) },
                    onToggleTurbo = vm::setTurbo,
                )

                Step.RUN -> RunScreen(
                    run = run,
                    onConfirmList = { vm.confirmar(true) },
                    onCancelList = { vm.confirmar(false) },
                    onPause = vm::pausar,
                    onResume = vm::retomar,
                    onRequestCancel = {
                        if (run.running) confirmarCancelamento = true else vm.goTo(Step.RESULT)
                    },
                )

                Step.RESULT -> ResultScreen(
                    run = run,
                    onRestart = { vm.goTo(Step.CONTACTS) },
                    onBackToContacts = { vm.goTo(Step.CONTACTS) },
                    awaitingVerification = awaitingVerification,
                    onOpenWhatsApp = {
                        context.packageManager.getLaunchIntentForPackage(run.targetApp.packageName)
                            ?.also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            ?.let(context::startActivity)
                    },
                    onFinalize = {
                        val saved = OneShotGate.markConsumedFromVerified(context)
                        if (saved) {
                            finished = true
                            vm.finishOneShot()
                        }
                        saved
                    },
                    onReportMissing = {
                        val cleared = OneShotGate.cancelPendingAfterFailedVerification(context)
                        if (cleared) vm.goTo(Step.CONTACTS)
                        cleared
                    },
                )
            }
        }
    }
}

@Composable
private fun PendingVerificationScreen(
    listsRequested: Int,
    onFinalize: () -> Boolean,
    onReportMissing: () -> Boolean,
) {
    var error by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Confira as listas", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "$listsRequested criação(ões) foram solicitadas. Abra o WhatsApp e confira " +
                "se todas essas listas aparecem antes de encerrar o uso único.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            error = if (onFinalize()) "" else "Não foi possível registrar o encerramento. Tente novamente."
        }) { Text("Confirmei todas; encerrar uso") }
        TextButton(onClick = {
            error = if (onReportMissing()) "" else "Não foi possível liberar a correção. Tente novamente."
        }) { Text("Faltou alguma lista") }
        Text(
            "Se precisar corrigir, confira quais listas já existem no WhatsApp " +
                "para não criá-las em duplicidade.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun FinishedScreen(
    listsCreated: Int,
    onOpenAccessibility: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Uso concluído", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            if (listsCreated > 0) "$listsCreated lista(s) registradas como criadas. " +
                "Esta instalação não pode iniciar outra operação. Confira as listas no WhatsApp."
            else "Esta instalação não pode iniciar outra operação. Confira as listas no WhatsApp.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "O app tenta desligar Acessibilidade ao encerrar. " +
                "Confira e retire as permissões nos ajustes do celular.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenAccessibility) { Text("Abrir Acessibilidade") }
        Button(onClick = onOpenAppSettings) { Text("Abrir permissões do app") }
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
            "Esta versão terminou em 04/10/2026. Ela não lê contatos nem executa a automação.",
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

private fun ajustesDoApp(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
