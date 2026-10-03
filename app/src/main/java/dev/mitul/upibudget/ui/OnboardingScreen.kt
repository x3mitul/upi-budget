package dev.mitul.upibudget.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.TxnEntity
import dev.mitul.upibudget.gmail.rememberGmailConnector
import kotlinx.coroutines.launch
import java.time.LocalDate

private class StepSpec(
    val title: String, val body: String, val done: Boolean? = null, val doneText: String = "Done", val todoText: String = "Not done yet",
    val action: String? = null, val skip: String? = null, val run: () -> Unit = {},
)

@Composable
fun OnboardingScreen(vm: MainViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(0) }
    val sms = remember(tick) { Permissions.smsGranted(ctx) }
    val notif = remember(tick) { Permissions.notificationsGranted(ctx) }
    val listener = remember(tick) { Permissions.listenerGranted(ctx) }
    val battery = remember(tick) { Permissions.batteryUnrestricted(ctx) }
    var gmailOk by remember { mutableStateOf<Boolean?>(null) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    var guess by remember { mutableStateOf<TxnEntity?>(null) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var error by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.importBackup(dev.mitul.upibudget.data.BackupFiles.readText(ctx, uri)) }
                .onSuccess { onDone() }
                .onFailure { android.widget.Toast.makeText(ctx, it.message ?: "Could not restore that file", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    val connect = rememberGmailConnector { ok -> gmailOk = ok }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }

    val steps = buildList {
        add(StepSpec("Your spending, without the bookkeeping",
            "UPI Budget reads your Axis Bank messages and UPI app notifications on this phone, sorts each payment, and tells you what is left. Your data stays on the phone."))
        add(StepSpec("Allow restricted settings",
            "Android blocks SMS and notification access for apps installed outside the Play Store until you allow it. Open app settings, tap the ⋮ menu at the top right, then choose Allow restricted settings.",
            action = "Open app settings", skip = "I have done this") { Permissions.openAppSettings(ctx) })
        add(StepSpec("See payments as they happen", "Reading bank SMS is how the app knows about a payment within seconds.",
            done = sms, doneText = "SMS access allowed", todoText = "SMS access needed", action = "Allow SMS", skip = "Not now") {
            smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
        })
        if (Build.VERSION.SDK_INT >= 33) add(StepSpec("Show what is left after each payment",
            "A short notification after every payment, and a question only when the app cannot tell what a payment was.",
            done = notif, doneText = "Notifications allowed", todoText = "Notifications needed", action = "Allow notifications", skip = "Not now") {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        })
        add(StepSpec("Catch payments from UPI apps",
            "Lets the app read payment notifications from GPay, PhonePe and Paytm. Find UPI Budget in the list and switch it on.",
            done = listener, doneText = "Notification access on", todoText = "Notification access off", action = "Open notification access", skip = "Not now") {
            Permissions.openListenerSettings(ctx)
        })
        add(StepSpec("Keep it running",
            "Some phones stop background apps. On vivo and iQOO: Settings, Battery, Background power consumption, UPI Budget, Allow high background power consumption. Then Settings, Apps, Autostart, UPI Budget on.",
            done = battery, doneText = "Battery limits removed", todoText = "Battery limits still on", action = "Remove battery limits", skip = "Not now") {
            Permissions.requestBatteryExemption(ctx)
        })
        add(StepSpec("Add Gmail for small credits",
            "Axis emails credits under ₹10,000 instead of texting them. Connecting Gmail lets the app read those emails, read-only. Google shows an unverified app notice once: choose Advanced, then Continue.",
            done = gmailOk, doneText = "Gmail connected", todoText = "Could not connect, you can try again in Settings", action = "Connect Gmail", skip = "Skip") { connect() })
        add(StepSpec("Bring in the last 60 days",
            "Reads your recent Axis SMS so the dashboard starts with real history. No notifications for these.",
            done = importMsg?.startsWith("Imported"), doneText = importMsg ?: "", todoText = importMsg ?: "Not imported yet",
            action = if (sms) "Import SMS" else null, skip = "Skip") {
            importMsg = "Importing…"
            scope.launch { importMsg = "Imported ${vm.runImport(ctx)} messages"; guess = vm.allowanceGuess() }
        })
        add(StepSpec("Your allowance", ""))
    }
    val last = step == steps.lastIndex
    val s = steps[step]
    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().background(pal.paper).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.indices.forEach { i ->
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) pal.moss else pal.mist))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(s.title, style = MaterialTheme.typography.headlineMedium)
            if (s.body.isNotEmpty()) Text(s.body, style = MaterialTheme.typography.bodyLarge, color = pal.inkSoft)
            s.done?.let { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (d) pal.moss else pal.marigold, 10); Spacer(Modifier.width(10.dp))
                    Text(if (d) s.doneText else s.todoText, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (last) {
                val g = guess
                if (g != null) {
                    Text("Found ${Money.format(g.amount)} on ${g.time.toLocalDateTime().toLocalDate()}. Is that your allowance?", style = MaterialTheme.typography.bodyLarge, color = pal.inkSoft)
                    Primary("Yes, use ${Money.format(g.amount)}") { scope.launch { vm.finishOnboarding(null, null, g.id); onDone() } }
                } else {
                    Text("No need to enter it now. When money arrives, the app asks whether it is your allowance, and the month starts from that day. Allowances can differ in amount and date every time.",
                        style = MaterialTheme.typography.bodyLarge, color = pal.inkSoft)
                }
                if (manual) {
                    FocusedField(amount, { amount = it }, label = "Amount in ₹", modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it }, label = { Text("Date received (YYYY-MM-DD)") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
                    if (error.isNotEmpty()) Text(error, color = pal.brick, style = MaterialTheme.typography.bodyMedium)
                    Primary("Save allowance") {
                        val p = runCatching { Money.parse(amount) }.getOrNull(); val d = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
                        if (p == null || d == null || p <= 0) error = "Enter an amount and a date like 2026-10-03."
                        else scope.launch { vm.finishOnboarding(p, d, null); onDone() }
                    }
                } else Quiet("Enter it now instead", color = pal.inkSoft) { manual = true }
            }
        }
        if (last) {
            if (!manual && guess == null) Primary("Finish setup") { scope.launch { vm.finishOnboarding(null, null, null); onDone() } }
            else if (guess != null && !manual) Quiet("Skip, I will tell it when money arrives", Modifier.align(Alignment.CenterHorizontally), color = pal.inkSoft) { scope.launch { vm.finishOnboarding(null, null, null); onDone() } }
        } else {
            val needsAction = s.action != null && s.done != true
            Primary(when { step == 0 -> "Get started"; needsAction -> s.action!!; else -> "Continue" }) {
                if (needsAction) s.run() else step++
            }
            if (step == 0) Quiet("Restore from a backup", Modifier.align(Alignment.CenterHorizontally), color = pal.inkSoft) { restore.launch(arrayOf("application/json", "*/*")) }
            if (needsAction || (s.skip != null && s.done != true)) Quiet(s.skip ?: "Skip", Modifier.align(Alignment.CenterHorizontally), color = pal.inkSoft) { step++ }
        }
    }
}
