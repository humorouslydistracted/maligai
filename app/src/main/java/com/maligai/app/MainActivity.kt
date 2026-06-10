package com.maligai.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.Application
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class MaligaiApplication : Application()

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaligaiAppContent(activity = this)
        }
    }
}

@Composable
private fun MaligaiAppContent(activity: FragmentActivity) {
    val setupVm: SetupViewModel = hiltViewModel()
    val settings by setupVm.settings.collectAsStateWithLifecycle()
    MaligaiTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM) {
        if (settings == null) {
            LoadingScreen("Starting Maligai\u2026")
        } else {
            AppRoot(activity = activity, setupVm = setupVm)
        }
    }
}

/** Admin re-locks after this many ms without touch/scroll on an admin screen. */
private const val ADMIN_IDLE_MS = 180_000L // 3 minutes

private enum class Screen {
    BILL, TODAY, SPENDING_QUICK,
    ADMIN_PIN,
    ADMIN_ITEMS, ADMIN_LEDGER, ADMIN_LOANS, ADMIN_SPENDING,
    ADMIN_ANALYSIS, ADMIN_CSV, ADMIN_RECEIPT, ADMIN_PRINTER, ADMIN_SETTINGS,
    ABOUT
}

@Composable
private fun AppRoot(activity: FragmentActivity, setupVm: SetupViewModel) {
    val settings by setupVm.settings.collectAsStateWithLifecycle()
    var authed by remember { mutableStateOf(false) }
    var authTried by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        if (authTried) return@LaunchedEffect
        setupVm.ensureReady()
        val s = settings ?: return@LaunchedEffect
        if (s.biometricUnlockEnabled && deviceSupportsBiometric(activity)) {
            promptBiometric(activity) { ok -> authed = ok; authTried = true }
        } else {
            authed = true
            authTried = true
        }
    }

    when {
        !authed && settings?.biometricUnlockEnabled == true ->
            LockScreen(onRetry = {
                promptBiometric(activity) { ok -> authed = ok }
            }, tried = authTried)
        settings?.setupComplete != true -> SetupFlow(setupVm)
        else -> MainApp()
    }
}

private fun deviceSupportsBiometric(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
}

private fun promptBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
    val manager = BiometricManager.from(activity)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(true)
        return
    }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Maligai")
        .setSubtitle("Unlock to continue")
        .setAllowedAuthenticators(authenticators)
        .build()
    prompt.authenticate(info)
}

@Composable
private fun LoadingScreen(message: String = "Loading\u2026") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            AppText(message)
        }
    }
}

@Composable
private fun LockScreen(onRetry: () -> Unit, tried: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppText("Maligai", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            if (tried) Button(onClick = onRetry) { Text("Unlock") }
            else CircularProgressIndicator()
        }
    }
}

/* ----------------------------------------------------------------- Setup flow */

@Composable
private fun SetupFlow(vm: SetupViewModel) {
    var step by remember { mutableStateOf(0) }
    val selectedLanguage by vm.selectedLanguage.collectAsStateWithLifecycle()
    val enDownloaded by vm.enModelDownloaded.collectAsStateWithLifecycle()
    val regionalDownloaded by vm.regionalModelDownloaded.collectAsStateWithLifecycle()
    val downloading by vm.downloading.collectAsStateWithLifecycle()
    val downloadPhase by vm.downloadPhase.collectAsStateWithLifecycle()
    val downloadError by vm.downloadError.collectAsStateWithLifecycle()

    when (step) {
        0 -> PinSetupStep(onNext = { pin, q, a -> vm.savePin(pin, q, a) { step = 1 } })
        1 -> LanguagePickerStep(
            selected = selectedLanguage,
            onSelect = { vm.selectLanguage(it) },
            onNext = { step = 2 }
        )
        2 -> HandwritingDownloadStep(
            enDownloaded = enDownloaded,
            regionalDownloaded = regionalDownloaded,
            regionalName = ScriptLanguages.displayNameForTag(selectedLanguage?.mlKitTag ?: ScriptLanguages.DEFAULT_TAG),
            downloading = downloading,
            downloadPhase = downloadPhase,
            error = downloadError,
            onDownload = { vm.downloadAllModels { } },
            onNext = { step = 3 }
        )
        else -> {
            LaunchedEffect(Unit) { vm.finishSetup { } }
            LoadingScreen("Finishing setup\u2026")
        }
    }
}

private val securityQuestions = listOf(
    "What is your mother's maiden name?",
    "What is your best friend's name?",
    "What is your favorite sport?",
    "What city were you born in?",
    "What is your pet's name?"
)

@Composable
private fun PinSetupStep(onNext: (String, String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var question by remember { mutableStateOf(securityQuestions.first()) }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        AppText("Set up Maligai", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        VisibleOutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4) pin = it.filter { c -> c.isDigit() } },
            label = { AppText("4-digit Admin PIN", style = MaterialTheme.typography.bodySmall, color = appMutedTextColor()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        VisibleOutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 4) confirm = it.filter { c -> c.isDigit() } },
            label = { AppText("Confirm PIN", style = MaterialTheme.typography.bodySmall, color = appMutedTextColor()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        AppText(
            "Security question",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth()
        )
        securityQuestions.forEach { q ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.RadioButton(selected = question == q, onClick = { question = q })
                AppText(q, modifier = Modifier.weight(1f))
            }
        }
        VisibleOutlinedTextField(
            value = answer,
            onValueChange = { answer = it.filter { c -> c.isLetter() }.lowercase() },
            label = { AppText("Answer (lowercase letters)", style = MaterialTheme.typography.bodySmall, color = appMutedTextColor()) },
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { AppText(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                when {
                    pin.length != 4 -> error = "PIN must be 4 digits"
                    pin != confirm -> error = "PINs do not match"
                    answer.isBlank() -> error = "Enter a security answer"
                    else -> onNext(pin, question, answer)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Next") }
    }
}

@Composable
private fun LanguagePickerStep(
    selected: ScriptLanguage?,
    onSelect: (ScriptLanguage) -> Unit,
    onNext: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        AppText("Choose your shop language", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        AppText(
            "Pick the script you write on the billing canvas. English recognition is always included.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        ScriptLanguages.pickerOptions().forEach { lang ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                androidx.compose.material3.RadioButton(
                    selected = selected?.id == lang.id,
                    onClick = { onSelect(lang) }
                )
                Column(Modifier.weight(1f)) {
                    AppText(lang.displayName, style = MaterialTheme.typography.bodyLarge)
                    AppText(lang.nativeSample, style = MaterialTheme.typography.bodyMedium, color = appMutedTextColor())
                    lang.sharedModelNote?.let {
                        AppText(it, style = MaterialTheme.typography.bodySmall, color = appMutedTextColor())
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNext, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
private fun HandwritingDownloadStep(
    enDownloaded: Boolean,
    regionalDownloaded: Boolean,
    regionalName: String,
    downloading: Boolean,
    downloadPhase: String?,
    error: String?,
    onDownload: () -> Unit,
    onNext: () -> Unit
) {
    val bothReady = enDownloaded && regionalDownloaded

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppText("Download handwriting packs", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        AppText(
            "English and $regionalName models are required (~15\u201320 MB each). " +
                "Needs internet once; afterwards fully offline.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("English")
            AppText(if (enDownloaded) "\u2713" else if (downloading && downloadPhase == "English") "\u2026" else "\u2014")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText(regionalName)
            AppText(if (regionalDownloaded) "\u2713" else if (downloading && downloadPhase != null && downloadPhase != "English") "\u2026" else "\u2014")
        }
        Spacer(Modifier.height(16.dp))
        when {
            downloading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                AppText("Downloading ${downloadPhase ?: ""}\u2026")
            }
            bothReady -> {
                AppText("Both packs ready \u2713", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }
            else -> {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Download now") }
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            AppText(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ModelDownloadStep(
    title: String,
    description: String,
    downloaded: Boolean,
    downloading: Boolean,
    error: String?,
    onDownload: () -> Unit,
    onNext: () -> Unit,
    skipLabel: String = "Skip for now (download later)"
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppText(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        AppText(description)
        Spacer(Modifier.height(24.dp))
        when {
            downloading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                AppText("Downloading\u2026")
            }
            downloaded -> {
                AppText("Downloaded \u2713", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next") }
            }
            else -> {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Download now") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(skipLabel) }
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            AppText(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

/* ----------------------------------------------------------------- Main app shell */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
    printerVm: PrinterViewModel = hiltViewModel(),
    billVm: BillViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.BILL) }
    val connected by printerVm.connected.collectAsStateWithLifecycle()
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(2500)
            statusMessage = null
        }
    }

    fun openBillForEdit(billId: Long) {
        billVm.reopenForEdit(billId) { ok ->
            if (ok) screen = Screen.BILL
            else statusMessage = "Could not open bill for edit"
        }
    }

    // --- Admin session ---
    var adminUnlocked by remember { mutableStateOf(false) }
    var adminLastActiveMs by remember { mutableLongStateOf(0L) }

    // Auto-lock admin after idle period (reset on touch/scroll inside admin pages)
    LaunchedEffect(adminUnlocked) {
        if (adminUnlocked) {
            while (true) {
                delay(5_000L)
                val elapsed = System.currentTimeMillis() - adminLastActiveMs
                if (elapsed > ADMIN_IDLE_MS) {
                    adminUnlocked = false
                    if (screen.name.startsWith("ADMIN") && screen != Screen.ADMIN_PIN) {
                        screen = Screen.ADMIN_PIN
                    }
                    break
                }
            }
        }
    }

    fun touchAdmin() {
        adminLastActiveMs = System.currentTimeMillis()
    }

    fun navigateTo(s: Screen) {
        screen = s
        if (s.name.startsWith("ADMIN") && adminUnlocked) touchAdmin()
        scope.launch { drawerState.close() }
    }

    // Request Bluetooth permissions then reconnect saved printer
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { printerVm.reconnectSaved() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        } else {
            printerVm.reconnectSaved()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // Drawer header
                Text(
                    "Maligai",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                Divider()

                // Public items — no PIN required
                DrawerEntry("New Bill", screen == Screen.BILL) { navigateTo(Screen.BILL) }
                DrawerEntry("Today\u2019s Bills", screen == Screen.TODAY) { navigateTo(Screen.TODAY) }
                DrawerEntry("Shop Spending", screen == Screen.SPENDING_QUICK) { navigateTo(Screen.SPENDING_QUICK) }
                Divider()

                // Admin items — gated behind PIN
                if (!adminUnlocked) {
                    DrawerEntry("Admin", screen == Screen.ADMIN_PIN) { navigateTo(Screen.ADMIN_PIN) }
                } else {
                    DrawerEntry("Items", screen == Screen.ADMIN_ITEMS) { navigateTo(Screen.ADMIN_ITEMS) }
                    DrawerEntry("Ledger", screen == Screen.ADMIN_LEDGER) { navigateTo(Screen.ADMIN_LEDGER) }
                    DrawerEntry("Loans", screen == Screen.ADMIN_LOANS) { navigateTo(Screen.ADMIN_LOANS) }
                    DrawerEntry("Spending", screen == Screen.ADMIN_SPENDING) { navigateTo(Screen.ADMIN_SPENDING) }
                    DrawerEntry("Analysis", screen == Screen.ADMIN_ANALYSIS) { navigateTo(Screen.ADMIN_ANALYSIS) }
                    DrawerEntry("CSV Backup", screen == Screen.ADMIN_CSV) { navigateTo(Screen.ADMIN_CSV) }
                    DrawerEntry("GST \u0026 Receipt", screen == Screen.ADMIN_RECEIPT) { navigateTo(Screen.ADMIN_RECEIPT) }
                    DrawerEntry("Printer", screen == Screen.ADMIN_PRINTER) { navigateTo(Screen.ADMIN_PRINTER) }
                    DrawerEntry("Settings", screen == Screen.ADMIN_SETTINGS) { navigateTo(Screen.ADMIN_SETTINGS) }
                }

                Divider()
                DrawerEntry("About", screen == Screen.ABOUT) { navigateTo(Screen.ABOUT) }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    title = {
                        // Clickable Maligai title — taps Home from any screen
                        Row(
                            modifier = Modifier.clickable {
                                screen = Screen.BILL
                                scope.launch { drawerState.close() }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Maligai",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        // Printer connection status dot
                        Box(
                            Modifier
                                .padding(end = 16.dp)
                                .size(12.dp)
                                .background(
                                    if (connected) Color(0xFF66BB6A) else Color(0xFFE57373),
                                    RoundedCornerShape(6.dp)
                                )
                        )
                    }
                )
            }
        ) { padding ->
            // Touch admin session whenever the user is on an admin screen
            LaunchedEffect(screen) {
                if (screen.name.startsWith("ADMIN") && adminUnlocked) touchAdmin()
            }

            Box(Modifier.padding(padding)) {
                statusMessage?.let { msg ->
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(msg, modifier = Modifier.padding(8.dp))
                    }
                }
                when (screen) {
                    Screen.BILL -> BillScreen()
                    Screen.TODAY -> TodayBillsScreen(
                        onUpdateBill = ::openBillForEdit,
                        onPrintBill = { id ->
                            billVm.printCompletedBill(id) { result ->
                                statusMessage = if (result.ok) "Receipt printed" else "Print: ${result.message}"
                            }
                        }
                    )
                    Screen.SPENDING_QUICK -> QuickSpendingScreen()
                    Screen.ADMIN_PIN -> AdminPinEntry(onUnlock = {
                        adminUnlocked = true
                        adminLastActiveMs = System.currentTimeMillis()
                        screen = Screen.ADMIN_ITEMS
                    })
                    Screen.ADMIN_ITEMS -> AdminActivityZone(::touchAdmin) { ItemsSection() }
                    Screen.ADMIN_LEDGER -> AdminActivityZone(::touchAdmin) {
                        LedgerSection(
                            onUpdateBill = ::openBillForEdit,
                            onPrintBill = { id ->
                                billVm.printCompletedBill(id) { result ->
                                    statusMessage = if (result.ok) "Receipt printed" else "Print: ${result.message}"
                                }
                            }
                        )
                    }
                    Screen.ADMIN_LOANS -> AdminActivityZone(::touchAdmin) { LoansSection() }
                    Screen.ADMIN_SPENDING -> AdminActivityZone(::touchAdmin) { SpendingSection() }
                    Screen.ADMIN_ANALYSIS -> AdminActivityZone(::touchAdmin) { AnalysisSection() }
                    Screen.ADMIN_CSV -> AdminActivityZone(::touchAdmin) { CsvSection() }
                    Screen.ADMIN_RECEIPT -> AdminActivityZone(::touchAdmin) { ReceiptSection() }
                    Screen.ADMIN_PRINTER -> AdminActivityZone(::touchAdmin) { PrinterSection() }
                    Screen.ADMIN_SETTINGS -> AdminActivityZone(::touchAdmin) { SettingsSection() }
                    Screen.ABOUT -> AboutScreen()
                }
            }
        }
    }
}

/** Resets admin idle timer on any touch or scroll inside admin content. */
@Composable
private fun AdminActivityZone(onActivity: () -> Unit, content: @Composable () -> Unit) {
    var lastBump by remember { mutableLongStateOf(0L) }
    fun bump() {
        val now = System.currentTimeMillis()
        if (now - lastBump > 1_000L) {
            lastBump = now
            onActivity()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    bump()
                }
            }
    ) { content() }
}

@Composable
private fun DrawerEntry(label: String, selected: Boolean = false, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

private val sidebarDateFmt = SimpleDateFormat("dd-MM HH:mm", Locale.US)

@Composable
private fun TodayBillsScreen(
    vm: LedgerViewModel = hiltViewModel(),
    onUpdateBill: (Long) -> Unit,
    onPrintBill: (Long) -> Unit
) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val revenue by vm.revenue.collectAsStateWithLifecycle()
    val detailItems by vm.detailItems.collectAsStateWithLifecycle()
    var detailBill by remember { mutableStateOf<Bill?>(null) }
    LaunchedEffect(Unit) { vm.setTab(PeriodTab.TODAY) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today: ${bills.size} bills \u2022 ${formatRs(revenue)}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(bills, key = { it.id }) { bill ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            detailBill = bill
                            vm.loadDetail(bill.id)
                        }
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${bill.name}  ${sidebarDateFmt.format(Date(bill.completedAt ?: bill.createdAt))}" +
                                if (bill.isLoan) "  (kadan)" else ""
                        )
                        Text(formatRs(bill.total), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    detailBill?.let { bill ->
        BillDetailDialog(
            bill = bill,
            items = detailItems,
            onDismiss = { detailBill = null },
            onUpdate = {
                detailBill = null
                onUpdateBill(bill.id)
            },
            onPrint = {
                onPrintBill(bill.id)
            }
        )
    }
}

@Composable
private fun QuickSpendingScreen(vm: SpendingViewModel = hiltViewModel()) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Add Spend") }
        Spacer(Modifier.height(8.dp))
        Text("Recent spending", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(recent, key = { it.id }) { spend ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(spend.name, fontWeight = FontWeight.Medium)
                            Text(
                                sidebarDateFmt.format(Date(spend.spentAt)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(formatRs(spend.amount))
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                Button(onClick = {
                    val a = amount.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && a > 0) { vm.add(name.trim(), a, System.currentTimeMillis()); showAdd = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
            title = { Text("Add Spend") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item / reason") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Amount (\u20B9)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                    )
                }
            }
        )
    }
}

@Composable
private fun AboutScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Maligai", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("India handwriting POS for kirana shops.")
        Text("Version 1.0.1")
        Spacer(Modifier.height(16.dp))
        Text("Offline-first. Your data stays on this device.", style = MaterialTheme.typography.bodyMedium)
    }
}
