package dev.mitul.upibudget.budget

import dev.mitul.upibudget.core.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class MonthInfo(val id: Long, val start: LocalDate, val carryOver: Paise) {
    val end: LocalDate get() = start.plusDays(BudgetEngine.MONTH_DAYS.toLong())
}

data class TxLite(
    val time: LocalDateTime, val amount: Paise, val direction: Direction, val kind: Kind,
    val payeeNorm: String, val isSaving: Boolean, val creditAs: CreditAnswer?,
)

data class Summary(
    val carryOver: Paise, val allowance: Paise, val paybacks: Paise, val income: Paise, val spent: Paise,
    val saved: Paise, val reserved: Paise, val left: Paise, val daysRemaining: Int, val perDay: Paise, val overdue: Boolean,
)

object BudgetEngine {
    const val MONTH_DAYS = 30
    const val NEW_MONTH_AFTER_DAYS = 20
    const val MISSED_AFTER_DAYS = 7

    private fun allowance(txs: List<TxLite>) =
        txs.filter { it.direction == Direction.CREDIT && it.creditAs == CreditAnswer.ALLOWANCE }.sumOf { it.amount }
    private fun paybacks(txs: List<TxLite>) =
        txs.filter { it.direction == Direction.CREDIT && (it.creditAs == CreditAnswer.PAYBACK || it.creditAs == CreditAnswer.REFUND || it.creditAs == CreditAnswer.SAVINGS) }.sumOf { it.amount }
    private fun spent(txs: List<TxLite>) = txs.filter { it.direction == Direction.DEBIT && !it.isSaving }.sumOf { it.amount }
    private fun saved(txs: List<TxLite>) = txs.filter { it.direction == Direction.DEBIT && it.isSaving }.sumOf { it.amount }

    fun summarize(month: MonthInfo, txs: List<TxLite>, recurring: List<RecurringLite>, today: LocalDate): Summary {
        val allowance = allowance(txs); val paybacks = paybacks(txs)
        val spent = spent(txs); val saved = saved(txs)
        val reserved = recurring.sumOf { Recurring.reservation(it, month, txs, today) }
        val income = month.carryOver + allowance + paybacks
        val left = income - spent - saved - reserved
        val overdue = !today.isBefore(month.end)
        val days = if (overdue) 1 else ChronoUnit.DAYS.between(today, month.end).toInt().coerceIn(1, MONTH_DAYS)
        return Summary(month.carryOver, allowance, paybacks, income, spent, saved, reserved, left, days, left / days, overdue)
    }

    /** What flows into the next month: everything except forward-looking reservations. */
    fun carryOverOf(month: MonthInfo, txs: List<TxLite>): Paise =
        month.carryOver + allowance(txs) + paybacks(txs) - spent(txs) - saved(txs)

    fun shouldStartNewMonth(current: MonthInfo?, today: LocalDate): Boolean =
        current == null || !today.isBefore(current.start.plusDays(NEW_MONTH_AFTER_DAYS.toLong()))

    fun missedDue(item: RecurringLite, month: MonthInfo, txs: List<TxLite>, today: LocalDate): LocalDate? {
        if (!item.active) return null
        val due = Recurring.dueDate(item, month.start, month.end) ?: return null
        val debited = txs.any { Recurring.isDebitOf(item, it) }
        return due.takeIf { !debited && today.isAfter(it.plusDays(MISSED_AFTER_DAYS.toLong())) }
    }
}
