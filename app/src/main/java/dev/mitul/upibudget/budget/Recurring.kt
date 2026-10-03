package dev.mitul.upibudget.budget

import dev.mitul.upibudget.core.*
import java.time.LocalDate

data class RecurringLite(
    val id: Long, val merchantNorm: String, val amount: Paise, val frequency: Frequency, val dueDay: Int,
    val nextDue: LocalDate?, val end: LocalDate?, val active: Boolean, val kind: RecurringKind,
)

object Recurring {
    fun matches(merchantNorm: String, payeeNorm: String): Boolean =
        merchantNorm.isNotEmpty() && payeeNorm.isNotEmpty() &&
            (merchantNorm == payeeNorm || Normalizer.hasWords(payeeNorm, merchantNorm) || Normalizer.hasWords(merchantNorm, payeeNorm))

    fun dueDate(item: RecurringLite, start: LocalDate, endExclusive: LocalDate): LocalDate? =
        when (item.frequency) {
            Frequency.MONTHLY -> generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(endExclusive) }
                .firstOrNull { it.dayOfMonth == minOf(item.dueDay, it.lengthOfMonth()) }
            Frequency.YEARLY, Frequency.ONE_TIME -> item.nextDue?.takeIf { !it.isBefore(start) && it.isBefore(endExclusive) }
        }

    /** A real debit settles an item when it names the same merchant, or (for an RD typed in by hand) is an RD installment of the same amount. */
    fun isDebitOf(item: RecurringLite, tx: TxLite): Boolean =
        tx.direction == Direction.DEBIT && (tx.kind == Kind.AUTOPAY || tx.kind == Kind.RD) &&
            (matches(item.merchantNorm, tx.payeeNorm) || (tx.kind == Kind.RD && item.kind == RecurringKind.SAVING && tx.amount == item.amount))

    private fun debited(item: RecurringLite, txs: List<TxLite>) = txs.any { isDebitOf(item, it) }

    fun reservation(item: RecurringLite, month: MonthInfo, txs: List<TxLite>, today: LocalDate): Paise {
        if (!item.active) return 0
        if (item.end != null && item.end.isBefore(month.start)) return 0
        val due = dueDate(item, month.start, month.end) ?: return 0
        if (debited(item, txs)) return 0
        if (today.isAfter(due.plusDays(BudgetEngine.MISSED_AFTER_DAYS.toLong()))) return 0
        return item.amount
    }
}
