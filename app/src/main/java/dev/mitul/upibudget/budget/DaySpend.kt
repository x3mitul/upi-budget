package dev.mitul.upibudget.budget

import dev.mitul.upibudget.core.Direction
import dev.mitul.upibudget.core.Paise
import java.time.LocalDate

data class DayBar(val date: LocalDate, val spent: Paise, val isToday: Boolean, val isFuture: Boolean)

object DaySpend {
    /** One bar per day of the budget month: spending debits only (savings and credits excluded). */
    fun bars(txs: List<TxLite>, start: LocalDate, today: LocalDate, days: Int = BudgetEngine.MONTH_DAYS): List<DayBar> {
        val perDay = txs.filter { it.direction == Direction.DEBIT && !it.isSaving }
            .groupBy { it.time.toLocalDate() }.mapValues { (_, l) -> l.sumOf { it.amount } }
        return (0 until days).map { i ->
            val d = start.plusDays(i.toLong())
            DayBar(d, perDay[d] ?: 0L, d == today, d.isAfter(today))
        }
    }
}
