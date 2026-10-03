package dev.mitul.upibudget.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.*
import dev.mitul.upibudget.gmail.rememberGmailConnector
import kotlinx.coroutines.launch

private val PALETTE = listOf(0xFFE57373, 0xFF81C784, 0xFF64B5F6, 0xFFFFB74D, 0xFFBA68C8, 0xFF4DB6AC, 0xFFA1887F, 0xFF90A4AE, 0xFFF06292, 0xFFAED581).map { it.toInt() }

@Composable
internal fun Page(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(pal.paper).systemBarsPadding()) {
        Quiet("Back", Modifier.padding(start = 8.dp, top = 8.dp), onClick = onBack)
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) { content() }
    }
}

@Composable
fun SettingsHost(vm: MainViewModel, screen: Screen, go: (Screen) -> Unit) {
    when (screen) {
        Screen.CATEGORIES -> CategoriesPage(vm) { go(Screen.SETTINGS) }
        Screen.KEYWORDS -> KeywordsPage(vm) { go(Screen.SETTINGS) }
        Screen.NAMES -> NamesPage(vm) { go(Screen.SETTINGS) }
        Screen.RECURRING -> RecurringPage(vm) { go(Screen.SETTINGS) }
        Screen.RD -> RdPage(vm) { go(Screen.SETTINGS) }
        Screen.DEBUG -> DebugPage(vm) { go(Screen.SETTINGS) }
        else -> MainSettings(vm, go)
    }
}

@Composable
private fun MainSettings(vm: MainViewModel, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gmail by vm.gmailStatus.collectAsState()
    val account by vm.account.collectAsState()
    var acct by remember(account) { mutableStateOf(account) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val connect = rememberGmailConnector { ok -> Toast.makeText(ctx, if (ok) "Gmail connected" else "Could not connect Gmail", Toast.LENGTH_SHORT).show() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { runCatching { BackupFiles.readText(ctx, uri) }.onSuccess { pendingImport = it }.onFailure { Toast.makeText(ctx, "Could not read that file", Toast.LENGTH_SHORT).show() } }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    Page("Settings", onBack = { go(Screen.DASHBOARD) }) {
        item { Heading("Sorting") }
        item { ListRow("Categories", "Add, rename or remove", onClick = { go(Screen.CATEGORIES) }) }
        item { ListRow("My keyword rules", "Names you always sort the same way", onClick = { go(Screen.KEYWORDS) }) }
        item { ListRow("Learned names", "Payees the app has remembered", onClick = { go(Screen.NAMES) }) }
        item { ListRow("Recurring deposits (RD)", "How much is cut each month, and on which day", onClick = { go(Screen.RD) }) }
        item { ListRow("Recurring payments", "Subscriptions and savings set aside each month", onClick = { go(Screen.RECURRING) }) }
        item { Heading("Access") }
        item {
            val st = remember(tick) { listOf(Permissions.smsGranted(ctx), Permissions.notificationsGranted(ctx), Permissions.listenerGranted(ctx), Permissions.batteryUnrestricted(ctx)) }
            Column {
                PermRow("SMS", st[0]) { Permissions.openAppSettings(ctx) }
                PermRow("Notifications", st[1]) { Permissions.openAppSettings(ctx) }
                PermRow("UPI app notifications", st[2]) { Permissions.openListenerSettings(ctx) }
                PermRow("Runs in background", st[3]) { Permissions.requestBatteryExemption(ctx) }
            }
        }
        item { ListRow("Gmail", gmail, value = "Connect", valueColor = pal.moss, onClick = connect) }
        item { Heading("Bank account") }
        item {
            Soft("The last digits shown in your bank SMS, like XX1234. Leave empty to learn it from the next payment.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(acct, { acct = it }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f))
                Quiet("Save") { vm.setAccount(acct) }
            }
        }
        item { Heading("Your data") }
        item { ListRow("Export a backup", "Saves one file to Downloads", onClick = {
            scope.launch {
                val name = runCatching { BackupFiles.saveToDownloads(ctx, vm.exportBackup()) }.getOrElse { "nothing, ${it.message}" }
                Toast.makeText(ctx, "Saved $name", Toast.LENGTH_LONG).show()
            }
        }) }
        item { ListRow("Import a backup", "Replaces everything on this phone", onClick = { picker.launch(arrayOf("application/json", "*/*")) }) }
        item { ListRow("Re-read my SMS", "Looks at the last 60 days again. Nothing is counted twice.", onClick = {
            scope.launch { val n = runCatching { vm.rereadSms(ctx) }.getOrElse { -1 }; Toast.makeText(ctx, if (n < 0) "Could not read SMS" else "Checked $n messages", Toast.LENGTH_SHORT).show() }
        }) }
        item { ListRow("Debug log", "Raw messages the app could not read, for fixing parsers", onClick = { go(Screen.DEBUG) }) }
    }
    pendingImport?.let { json ->
        AlertDialog(onDismissRequest = { pendingImport = null }, containerColor = pal.paper, title = { Text("Replace everything on this phone?") },
            text = { Text("All payments, categories and rules are replaced by the backup file.") },
            confirmButton = { Quiet("Replace", color = pal.brick) {
                scope.launch {
                    runCatching { vm.importBackup(json) }
                        .onSuccess { Toast.makeText(ctx, "Backup restored", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(ctx, it.message ?: "Could not restore", Toast.LENGTH_LONG).show() }
                }
                pendingImport = null
            } },
            dismissButton = { Quiet("Cancel", color = pal.inkSoft) { pendingImport = null } })
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, fix: () -> Unit) =
    ListRow(label, value = if (ok) "On" else "Turn on", valueColor = if (ok) pal.inkSoft else pal.brick, onClick = if (ok) null else fix)

@Composable
private fun CategoriesPage(vm: MainViewModel, back: () -> Unit) {
    val cats by vm.categories.collectAsState()
    var input by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    var menu by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    val locked = setOf("Uncategorized", "Other")
    Page("Categories", back) {
        item { Quiet("Add a group") { input = "New group" to { n -> vm.addGroup(n, PALETTE[cats.size % PALETTE.size]) } } }
        for (g in cats.filter { it.parentId == null }) {
            item(key = "g${g.id}") {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(Color(g.color), 10); Spacer(Modifier.width(10.dp))
                    Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Quiet("Edit", color = pal.inkSoft) { menu = g }
                }
            }
            items(cats.filter { it.parentId == g.id }, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Quiet("Edit", color = pal.inkSoft) { menu = c }
                }
            }
        }
    }
    menu?.let { c ->
        AlertDialog(onDismissRequest = { menu = null }, containerColor = pal.paper, title = { Text(c.name) },
            text = { Column {
                if (c.parentId == null) ListRow("Add a category here") { menu = null; input = "New category in ${c.name}" to { n -> vm.addSub(n, c.id) } }
                ListRow("Rename") { menu = null; input = "Rename" to { n -> vm.rename(c.id, n) } }
                if (c.parentId == null) ListRow("Change colour") { vm.recolor(c.id, PALETTE[(PALETTE.indexOf(c.color) + 1).mod(PALETTE.size)]); menu = null }
                if (c.name !in locked) ListRow("Delete", value = "", onClick = { menu = null; deleteTarget = c })
            } },
            confirmButton = { Quiet("Close", color = pal.inkSoft) { menu = null } })
    }
    input?.let { (title, action) ->
        var text by remember(title) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { input = null }, containerColor = pal.paper, title = { Text(title) },
            text = { FocusedField(text, { text = it }, onDone = { if (text.isNotBlank()) action(text); input = null }) },
            confirmButton = { Quiet("Save") { if (text.isNotBlank()) action(text); input = null } },
            dismissButton = { Quiet("Cancel", color = pal.inkSoft) { input = null } })
    }
    deleteTarget?.let { c ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, containerColor = pal.paper, title = { Text("Delete ${c.name}?") },
            text = { Text("Its payments and rules move to Other, Uncategorized.") },
            confirmButton = { Quiet("Delete", color = pal.brick) { vm.deleteCategory(c.id); deleteTarget = null } },
            dismissButton = { Quiet("Cancel", color = pal.inkSoft) { deleteTarget = null } })
    }
}

@Composable
private fun KeywordsPage(vm: MainViewModel, back: () -> Unit) {
    val kws by vm.userKeywords.collectAsState()
    val cats by vm.categories.collectAsState()
    LaunchedEffect(Unit) { vm.reloadRules() }
    Page("My keyword rules", back) {
        item { Soft("Long-press a payment on the dashboard to add one. Tap a rule here to remove it.") }
        if (kws.isEmpty()) item { Text("No rules yet.", Modifier.padding(top = 16.dp), color = pal.inkSoft) }
        items(kws, key = { it.id }) { k -> ListRow(prettyName(k.word), cats.firstOrNull { it.id == k.subId }?.name, onClick = { vm.deleteKeyword(k.id) }) }
    }
}

@Composable
private fun NamesPage(vm: MainViewModel, back: () -> Unit) {
    val rules by vm.nameRules.collectAsState()
    val cats by vm.categories.collectAsState()
    LaunchedEffect(Unit) { vm.reloadRules() }
    Page("Learned names", back) {
        item { Soft("Tap a name to forget it. The app will ask about it again next time.") }
        if (rules.isEmpty()) item { Text("Nothing learned yet. Answer a question about a payment and it shows up here.", Modifier.padding(top = 16.dp), color = pal.inkSoft) }
        items(rules.sortedBy { it.payeeNorm }, key = { it.payeeNorm }) { r -> ListRow(prettyName(r.payeeNorm), cats.firstOrNull { it.id == r.subId }?.name, onClick = { vm.deleteNameRule(r.payeeNorm) }) }
    }
}

@Composable
private fun RecurringPage(vm: MainViewModel, back: () -> Unit) {
    val items by vm.recurring.collectAsState()
    var edit by remember { mutableStateOf<RecurringEntity?>(null) }
    val blank = RecurringEntity(merchantNorm = "", amount = 0, frequency = Frequency.MONTHLY, dueDay = 1, nextDue = null, endDate = null, active = true, kind = RecurringKind.SUBSCRIPTION)
    Page("Recurring payments", back) {
        item { Soft("Money for these is set aside at the start of the month, so it never looks spendable.") }
        item { Quiet("Add one") { edit = blank } }
        items(items, key = { it.id }) { r ->
            ListRow(prettyName(r.merchantNorm), "${r.frequency.name.lowercase().replaceFirstChar { it.uppercase() }}, day ${r.dueDay}${if (r.kind == RecurringKind.SAVING) ", savings" else ""}${if (r.active) "" else ", stopped"}",
                value = Money.format(r.amount), valueColor = pal.ink, onClick = { edit = r })
        }
    }
    edit?.let { r ->
        var name by remember(r) { mutableStateOf(r.merchantNorm) }
        var amount by remember(r) { mutableStateOf(if (r.amount == 0L) "" else (r.amount / 100).toString()) }
        var day by remember(r) { mutableStateOf(r.dueDay.toString()) }
        var freq by remember(r) { mutableStateOf(r.frequency) }
        var kind by remember(r) { mutableStateOf(r.kind) }
        var active by remember(r) { mutableStateOf(r.active) }
        AlertDialog(onDismissRequest = { edit = null }, containerColor = pal.paper, title = { Text(if (r.id == 0L) "New recurring payment" else prettyName(r.merchantNorm)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount in ₹") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                OutlinedTextField(day, { day = it }, label = { Text("Day of month") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Frequency.values().forEach { f -> FilterChip(freq == f, { freq = f }, label = { Text(f.name.lowercase().replace('_', ' ')) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { RecurringKind.values().forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text(if (k == RecurringKind.SAVING) "savings" else "subscription") }) } }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Active", Modifier.weight(1f)); Switch(active, { active = it }) }
            } },
            confirmButton = { Quiet("Save") {
                val p = runCatching { Money.parse(amount) }.getOrNull(); val d = day.toIntOrNull()
                if (name.isNotBlank() && p != null && d != null && d in 1..31) {
                    val now = java.time.LocalDate.now()
                    vm.saveRecurring(r.copy(merchantNorm = Normalizer.name(name), amount = p, dueDay = d, frequency = freq, kind = kind, active = active,
                        nextDue = r.nextDue ?: now.withDayOfMonth(minOf(d, now.lengthOfMonth())).toEpochDay()))
                    edit = null
                }
            } },
            dismissButton = { Row {
                if (r.id != 0L) Quiet("Delete", color = pal.brick) { vm.deleteRecurring(r.id); edit = null }
                Quiet("Cancel", color = pal.inkSoft) { edit = null }
            } })
    }
}

@Composable
private fun DebugPage(vm: MainViewModel, back: () -> Unit) {
    val logs by vm.logs.collectAsState()
    val ctx = LocalContext.current
    Page("Debug log", back) {
        item {
            Soft("Raw notification, email and SMS text the app could not read. It holds real details, so edit digits and names before sharing.")
            Row {
                Quiet("Copy all") {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("log", logs.joinToString("\n---\n") { "[${it.tag}] ${it.text}" }))
                    Toast.makeText(ctx, "Copied ${logs.size} entries", Toast.LENGTH_SHORT).show()
                }
                Quiet("Clear", color = pal.inkSoft) { vm.clearLogs() }
            }
        }
        items(logs, key = { it.id }) { l ->
            Column(Modifier.padding(vertical = 8.dp)) { Soft(l.tag); Text(l.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            Hairline()
        }
    }
}
