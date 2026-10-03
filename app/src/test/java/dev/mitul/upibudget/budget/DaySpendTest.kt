package dev.mitul.upibudget.budget

import dev.mitul.upibudget.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DaySpendTest {
    private val start = LocalDate.of(2026, 9, 25)
    private val today = LocalDate.of(2026, 10, 3)
    private fun tx(d: LocalDate, rs: Long, dir: Direction = Direction.DEBIT, saving: Boolean = false) =
        TxLite(d.atTime(12, 0), rs * 100, dir, Kind.UPI, "X", saving, null)

    @Test fun thirtyBarsWithTodayAndFutureFlagged() {
        val b = DaySpend.bars(emptyList(), start, today)
        assertEquals(30, b.size)
        assertEquals(start, b[0].date)
        assertTrue(b[8].isToday); assertFalse(b[7].isToday)
        assertTrue(b[9].isFuture); assertFalse(b[8].isFuture)
    }

    @Test fun sumsOnlySpendingDebitsPerDay() {
        val b = DaySpend.bars(listOf(tx(start, 100), tx(start, 50), tx(start, 999, Direction.CREDIT), tx(start, 700, saving = true),
            tx(start.plusDays(1), 20)), start, today)
        assertEquals(150_00L, b[0].spent); assertEquals(20_00L, b[1].spent); assertEquals(0L, b[2].spent)
    }

    @Test fun transactionsOutsideTheWindowAreIgnored() {
        val b = DaySpend.bars(listOf(tx(start.minusDays(1), 500), tx(start.plusDays(40), 500)), start, today)
        assertEquals(0L, b.sumOf { it.spent })
    }

    @Test fun overdueMonthHasNoFutureDays() {
        val b = DaySpend.bars(emptyList(), start, start.plusDays(60))
        assertTrue(b.none { it.isFuture || it.isToday })
    }
}
