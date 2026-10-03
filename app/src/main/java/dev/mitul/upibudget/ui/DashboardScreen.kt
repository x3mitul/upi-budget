package dev.mitul.upibudget.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mitul.upibudget.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TxnRef(val id: Long)

private val shortDay = DateTimeFormatter.ofPattern("d MMM")
private val longDay = DateTimeFormatter.ofPattern("EEE d MMM")

private fun dayLabel(d: LocalDate, today: LocalDate) = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(longDay)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: MainViewModel, onOpenSettings: () -> Unit, openTxnId: Long?, onTxnHandled: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val cats by vm.categories.collectAsState()
    var pick by remember { mutableStateOf<TxnRef?>(null) }
    var credit by remember { mutableStateOf<Long?>(null) }
    var keywordFor by remember { mutableStateOf<TxnRow?>(null) }
    var showAllowance by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    LaunchedEffect(openTxnId) {
        val id = openTxnId ?: return@LaunchedEffect
        vm.txn(id)?.let { if (it.direction == Direction.CREDIT) credit = it.id else pick = TxnRef(it.id) }
        onTxnHandled()
    }

    val u = ui
    Column(Modifier.fillMaxSize().background(pal.paper).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(u?.monthStart?.let { "${it.format(shortDay)} – ${it.plusDays(29).format(shortDay)}" } ?: "Budget",
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Quiet("Allowance") { showAllowance = true }
            Quiet("Settings", onClick = onOpenSettings)
        }
        if (u == null) return@Column
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item(key = "hero") { Hero(u) }
            if (u.toSortCount > 0) item(key = "sort") {
                Column(Modifier.padding(top = 20.dp)) {
                    Hairline()
                    Row(Modifier.fillMaxWidth().clickable { showSort = true }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Dot(pal.marigold, 10); Spacer(Modifier.width(12.dp))
                        Text(if (u.toSortCount == 1) "1 payment needs a category" else "${u.toSortCount} payments need a category",
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("Sort", color = pal.moss, style = MaterialTheme.typography.labelLarge)
                    }
                    Hairline()
                }
            }
            if (u.groups.isNotEmpty()) {
                item(key = "where") { Heading("Where it went"); Breakdown(u) }
            }
            if (u.recent.isNotEmpty()) {
                item(key = "recent-h") { Heading("Recent") }
                val grouped = u.recent.groupBy { it.txn.time.toLocalDateTime().toLocalDate() }
                grouped.forEach { (day, rows) ->
                    item(key = "d$day") { Soft(dayLabel(day, today), Modifier.padding(top = 12.dp, bottom = 2.dp)) }
                    items(rows, key = { "t${it.txn.id}" }) { r ->
                        val isCredit = r.txn.direction == Direction.CREDIT
                        Row(Modifier.fillMaxWidth().combinedClickable(
                            onClick = { if (isCredit) credit = r.txn.id else pick = TxnRef(r.txn.id) },
                            onLongClick = { if (!isCredit) keywordFor = r },
                        ).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(prettyName(r.txn.payeeRaw), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Soft(if (isCredit) (r.txn.creditAs?.let { if (it == CreditAnswer.SAVINGS) "From savings" else it.name.lowercase().replaceFirstChar { c -> c.uppercase() } } ?: "Needs an answer")
                                     else if (r.txn.needsSorting) "Needs a category" else r.subName)
                            }
                            Text((if (isCredit) "+" else "") + Money.format(r.txn.amount), style = MaterialTheme.typography.bodyLarge,
                                color = if (isCredit) pal.moss else pal.ink)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showSort) SortSheet(vm) { showSort = false }
    pick?.let { t -> CategoryPickerSheet(cats, onPick = { vm.teach(t.id, it); pick = null }, onDismiss = { pick = null }) }
    credit?.let { id ->
        ModalBottomSheet(onDismissRequest = { credit = null }, containerColor = pal.paper, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("What is this money?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                listOf(CreditAnswer.ALLOWANCE to "Allowance", CreditAnswer.PAYBACK to "Payback from someone", CreditAnswer.SAVINGS to "Money back from savings (RD, FD, SIP)", CreditAnswer.REFUND to "Refund", CreditAnswer.IGNORE to "Ignore it")
                    .forEach { (a, label) -> ListRow(label, onClick = { vm.answerCredit(id, a); credit = null }) }
            }
        }
    }
    keywordFor?.let { r ->
        var word by remember(r) { mutableStateOf(Normalizer.name(r.txn.payeeRaw).split(" ").firstOrNull().orEmpty()) }
        var choosing by remember(r) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { keywordFor = null }, containerColor = pal.paper, title = { Text("Always sort names containing…") },
            text = { FocusedField(word, { word = it }, onDone = { choosing = true }) },
            confirmButton = { Quiet("Choose category") { choosing = true } },
            dismissButton = { Quiet("Cancel", color = pal.inkSoft) { keywordFor = null } })
        if (choosing) CategoryPickerSheet(cats, onPick = { vm.addKeyword(word, it); keywordFor = null }, onDismiss = { choosing = false })
    }
    if (showAllowance) AllowanceDialog(onDismiss = { showAllowance = false }, onSave = { amt, d -> vm.addAllowance(amt, d); showAllowance = false })
}

@Composable
private fun Hero(u: DashboardUi) {
    val s = u.summary
    Column(Modifier.padding(top = 24.dp)) {
        if (s == null) {
            Text("Waiting for your allowance", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text("When money arrives you will be asked if it is your allowance, and the month starts that day. You can also tap Allowance above.", color = pal.inkSoft, style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        val over = s.left < 0
        Soft(if (over) "Over budget by" else "Left this month")
        val big = Money.whole(kotlin.math.abs(s.left))
        Text(big,
            style = when { big.length > 12 -> MaterialTheme.typography.displaySmall; big.length > 9 -> MaterialTheme.typography.displayMedium; else -> MaterialTheme.typography.displayLarge }, color = if (over) pal.brick else pal.ink, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                s.overdue -> "This month has ended. Add the next allowance when it arrives."
                over -> "Nothing to spend for the next ${s.daysRemaining} days."
                else -> "${Money.whole(s.perDay)} a day for ${s.daysRemaining} more days"
            }, style = MaterialTheme.typography.bodyLarge, color = if (over || s.overdue) pal.brick else pal.inkSoft)
        Spacer(Modifier.height(20.dp))
        DayStrip(u.bars, u.pace)
        Spacer(Modifier.height(16.dp))
        if (s.saved > 0) Line(pal.moss, "${Money.format(s.saved)} saved")
        if (s.reserved > 0) {
            var open by remember { mutableStateOf(false) }
            Column(Modifier.clickable { open = !open }) {
                Line(pal.marigold, "${Money.format(s.reserved)} set aside for upcoming payments")
                if (open) u.reserved.forEach { (name, amt) -> Soft("${name.lowercase().replaceFirstChar { it.uppercase() }}  ${Money.format(amt)}", Modifier.padding(start = 18.dp, top = 2.dp)) }
            }
        }
        if (s.carryOver != 0L) Line(pal.inkSoft, (if (s.carryOver > 0) "${Money.format(s.carryOver)} carried over" else "${Money.format(-s.carryOver)} overspent last month, taken off this one"))
    }
}

@Composable
private fun Line(dot: Color, text: String) =
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(dot, 8); Spacer(Modifier.width(10.dp)); Text(text, style = MaterialTheme.typography.bodyMedium)
    }

@Composable
private fun Breakdown(u: DashboardUi) {
    val total = u.groups.sumOf { it.total }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        u.groups.forEach { g -> Box(Modifier.weight(g.total.toFloat() / total).fillMaxHeight().background(Color(g.color))) }
    }
    Spacer(Modifier.height(8.dp))
    u.groups.forEach { g ->
        var open by remember { mutableStateOf(false) }
        val last = u.lastGroups.firstOrNull { it.groupId == g.groupId }?.total
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Color(g.color), 10); Spacer(Modifier.width(12.dp))
                Text(g.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(Money.format(g.total), style = MaterialTheme.typography.bodyLarge)
            }
            if (last != null && last != g.total) {
                val diff = g.total - last
                Soft(if (diff > 0) "${Money.format(diff)} more than last month" else "${Money.format(-diff)} less than last month", Modifier.padding(start = 22.dp))
            }
            if (open) g.subs.forEach { sub ->
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 4.dp)) {
                    Text(sub.name, style = MaterialTheme.typography.bodyMedium, color = pal.inkSoft, modifier = Modifier.weight(1f))
                    Text(Money.format(sub.total), style = MaterialTheme.typography.bodyMedium, color = pal.inkSoft)
                }
            }
        }
    }
}

@Composable
fun AllowanceDialog(onDismiss: () -> Unit, onSave: (Paise, LocalDate) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val save = {
        val p = runCatching { Money.parse(amount) }.getOrNull(); val d = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
        if (p != null && d != null && p > 0) onSave(p, d)
    }
    AlertDialog(onDismissRequest = onDismiss, containerColor = pal.paper, title = { Text("Allowance received") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusedField(amount, { amount = it }, label = "Amount in ₹", onDone = save)
            OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, shape = MaterialTheme.shapes.medium)
        } },
        confirmButton = { Quiet("Save") { save() } },
        dismissButton = { Quiet("Cancel", color = pal.inkSoft, onClick = onDismiss) })
}
