package dev.mitul.upibudget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.RecurringEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy")

private fun ordinal(d: Int) = d.toString() + when { d in 11..13 -> "th"; d % 10 == 1 -> "st"; d % 10 == 2 -> "nd"; d % 10 == 3 -> "rd"; else -> "th" }

private fun rdTitle(norm: String) = if (norm.startsWith("RD ")) "RD " + norm.removePrefix("RD ") else prettyName(norm)

/** Where you tell the app about each recurring deposit: how much is cut and on which day. That money is set aside up front, so it never looks spendable. */
@Composable
fun RdPage(vm: MainViewModel, back: () -> Unit) {
    val all by vm.recurring.collectAsState()
    val rds = all.filter { it.kind == RecurringKind.SAVING }
    var edit by remember { mutableStateOf<RecurringEntity?>(null) }
    val blank = RecurringEntity(merchantNorm = "", amount = 0, frequency = Frequency.MONTHLY, dueDay = 1, nextDue = null, endDate = null, active = true, kind = RecurringKind.SAVING)
    val perMonth = rds.filter { it.active }.sumOf { it.amount }
    Page("Recurring deposits", back) {
        item {
            Soft("Money for these is set aside at the start of each month, so it never shows as spendable. When the bank cuts it, the app swaps the set-aside amount for the real payment.")
            Spacer(Modifier.height(12.dp))
            if (rds.isNotEmpty()) Text("${Money.format(perMonth)} goes into RDs each month", style = MaterialTheme.typography.titleMedium)
            Quiet("Add a recurring deposit") { edit = blank }
        }
        if (rds.isEmpty()) item { Text("No RDs yet. When Axis texts that an RD was booked, it is added here by itself.", Modifier.padding(top = 8.dp), color = pal.inkSoft) }
        items(rds, key = { it.id }) { r ->
            val until = r.endDate?.toLocalDate()?.format(monthYear)?.let { ", until $it" } ?: ""
            ListRow(rdTitle(r.merchantNorm), "Cut on the ${ordinal(r.dueDay)} of each month$until${if (r.active) "" else ", stopped"}",
                value = Money.format(r.amount), valueColor = if (r.active) pal.ink else pal.inkSoft, onClick = { edit = r })
        }
    }
    edit?.let { r ->
        var name by remember(r) { mutableStateOf(r.merchantNorm) }
        var amount by remember(r) { mutableStateOf(if (r.amount == 0L) "" else (r.amount / 100).toString()) }
        var day by remember(r) { mutableStateOf(if (r.id == 0L) "" else r.dueDay.toString()) }
        var months by remember(r) { mutableStateOf(r.endDate?.toLocalDate()?.let { java.time.temporal.ChronoUnit.MONTHS.between(LocalDate.now(), it).coerceAtLeast(0).toString() } ?: "") }
        var active by remember(r) { mutableStateOf(r.active) }
        var error by remember(r) { mutableStateOf("") }
        val save = {
            val p = runCatching { Money.parse(amount) }.getOrNull(); val d = day.trim().toIntOrNull(); val m = months.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
            when {
                p == null || p <= 0 -> error = "Enter how much is cut each month."
                d == null || d !in 1..31 -> error = "Enter the day of the month it is cut, 1 to 31."
                months.isNotBlank() && (m == null || m < 0) -> error = "Months left must be a number, or leave it empty."
                else -> {
                    val now = LocalDate.now()
                    vm.saveRecurring(r.copy(
                        merchantNorm = Normalizer.name(name).let { n -> if (n.isEmpty()) "MY RD" else if (n.startsWith("RD ")) n else "RD $n" }, amount = p, dueDay = d, frequency = Frequency.MONTHLY, kind = RecurringKind.SAVING, active = active,
                        nextDue = r.nextDue ?: now.withDayOfMonth(minOf(d, now.lengthOfMonth())).toEpochDay(), endDate = m?.let { now.plusMonths(it.toLong()).toEpochDay() },
                    ))
                    edit = null
                }
            }
        }
        AlertDialog(onDismissRequest = { edit = null }, containerColor = pal.paper, title = { Text(if (r.id == 0L) "New recurring deposit" else rdTitle(r.merchantNorm)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusedField(amount, { amount = it }, label = "Cut each month, in ₹")
                OutlinedTextField(day, { day = it }, label = { Text("Day of the month it is cut") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                OutlinedTextField(months, { months = it }, label = { Text("Months left (optional)") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                OutlinedTextField(name, { name = it }, label = { Text("RD number, like X4321 (optional)") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                Soft("The RD number helps match the bank's SMS. Without it, a cut of the same amount is matched instead.")
                if (r.id != 0L) Row(verticalAlignment = Alignment.CenterVertically) { Text("Still running", Modifier.weight(1f)); Switch(active, { active = it }) }
                if (error.isNotEmpty()) Text(error, color = pal.brick, style = MaterialTheme.typography.bodyMedium)
            } },
            confirmButton = { Quiet("Save") { save() } },
            dismissButton = { Row {
                if (r.id != 0L) Quiet("Delete", color = pal.brick) { vm.deleteRecurring(r.id); edit = null }
                Quiet("Cancel", color = pal.inkSoft) { edit = null }
            } })
    }
}
