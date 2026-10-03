package dev.mitul.upibudget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.CategoryEntity
import dev.mitul.upibudget.data.TxnEntity
import java.time.format.DateTimeFormatter

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")

/** Works through payments that need an answer, one at a time. Answering moves to the next. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SortSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val list by vm.toSort.collectAsState()
    val cats by vm.categories.collectAsState()
    var skipped by remember { mutableStateOf(setOf<Long>()) }
    var picking by remember { mutableStateOf(false) }
    val current = list.firstOrNull { it.id !in skipped }
    LaunchedEffect(current) { if (current == null) onDismiss() }
    if (current == null) return
    var guesses by remember(current.id) { mutableStateOf<List<CategoryEntity>>(emptyList()) }
    LaunchedEffect(current.id) { if (current.direction == Direction.DEBIT) guesses = vm.guessesFor(current) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = pal.paper, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Soft("${list.count { it.id !in skipped }} left to sort")
            Text(prettyName(current.payeeRaw), style = MaterialTheme.typography.headlineSmall)
            Soft("${Money.format(current.amount)} · ${current.time.toLocalDateTime().format(dayFmt)}")
            Spacer(Modifier.height(16.dp))
            if (current.direction == Direction.CREDIT) {
                Text("What is this money?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CreditAnswer.ALLOWANCE to "Allowance", CreditAnswer.PAYBACK to "Payback", CreditAnswer.SAVINGS to "From savings", CreditAnswer.REFUND to "Refund", CreditAnswer.IGNORE to "Ignore")
                        .forEach { (a, label) -> AnswerChip(label) { vm.answerCredit(current.id, a) } }
                }
            } else {
                Text("Which category?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    guesses.forEach { c -> AnswerChip(c.name) { vm.teach(current.id, c.id) } }
                    AnswerChip("Other…", strong = false) { picking = true }
                }
            }
            Spacer(Modifier.height(8.dp))
            Quiet("Skip for now") { skipped = skipped + current.id }
        }
    }
    if (picking) CategoryPickerSheet(cats, onPick = { vm.teach(current.id, it); picking = false }, onDismiss = { picking = false })
}

@Composable
fun AnswerChip(label: String, strong: Boolean = true, onClick: () -> Unit) =
    if (strong) FilledTonalButton(onClick, shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = pal.mist, contentColor = pal.ink)) { Text(label, style = MaterialTheme.typography.labelLarge) }
    else OutlinedButton(onClick, shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, pal.mist)) { Text(label, style = MaterialTheme.typography.labelLarge, color = pal.inkSoft) }
