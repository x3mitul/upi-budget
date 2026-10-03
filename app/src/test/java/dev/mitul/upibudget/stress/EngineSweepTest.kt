package dev.mitul.upibudget.stress

import dev.mitul.upibudget.budget.*
import dev.mitul.upibudget.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class EngineSweepTest {
    private fun item(r: Random, id: Long) = RecurringLite(
        id, "M$id", r.nextLong(1, 5_000_000), Frequency.values().random(r), r.nextInt(1, 32),
        LocalDate.of(2024, 1, 1).plusDays(r.nextLong(0, 1500)), if (r.nextInt(3) == 0) LocalDate.of(2024, 1, 1).plusDays(r.nextLong(0, 1500)) else null,
        r.nextInt(5) != 0, RecurringKind.values().random(r),
    )

    @Test fun everyMonthStartAndTodayCombinationIsSane() {
        val r = Random(21)
        var cases = 0
        for (startOffset in 0 until 800 step 3) {
            val start = LocalDate.of(2024, 1, 1).plusDays(startOffset.toLong())
            val month = MonthInfo(1, start, r.nextLong(-5_000_000, 5_000_000))
            val rec = (1..6).map { item(r, it.toLong()) }
            val txs = (0 until r.nextInt(0, 30)).map {
                TxLite(start.plusDays(r.nextLong(-3, 40)).atTime(12, 0), r.nextLong(1, 1_000_000), Direction.values().random(r), Kind.values().random(r),
                    "M${r.nextInt(1, 8)}", r.nextBoolean(), CreditAnswer.values().toList().plus(null).random(r))
            }
            for (todayOff in -5..75 step 2) {
                val today = start.plusDays(todayOff.toLong())
                val s = BudgetEngine.summarize(month, txs, rec, today)
                assertTrue("days ${s.daysRemaining}", s.daysRemaining in 1..30)
                assertTrue("reserved ${s.reserved}", s.reserved >= 0)
                assertEquals(s.income - s.spent - s.saved - s.reserved, s.left)
                assertEquals(s.left / s.daysRemaining, s.perDay)
                assertEquals(todayOff >= 30, s.overdue)
                rec.forEach { it -> BudgetEngine.missedDue(it, month, txs, today) }
                DaySpend.bars(txs, start, today).also { assertEquals(30, it.size); assertTrue(it.count { b -> b.isToday } <= 1) }
                cases++
            }
        }
        println("engine sweep cases: $cases")
    }

    @Test fun leapDayAndYearEndDueDates() {
        val m = MonthInfo(1, LocalDate.of(2027, 12, 20), 0)
        val monthly31 = RecurringLite(1, "A", 100, Frequency.MONTHLY, 31, null, null, true, RecurringKind.SUBSCRIPTION)
        assertEquals(LocalDate.of(2027, 12, 31), Recurring.dueDate(monthly31, m.start, m.end))
        val feb = MonthInfo(2, LocalDate.of(2028, 2, 10), 0)
        assertEquals(LocalDate.of(2028, 2, 29), Recurring.dueDate(monthly31, feb.start, feb.end))
        val yearly = RecurringLite(2, "B", 100, Frequency.YEARLY, 1, LocalDate.of(2028, 1, 2), null, true, RecurringKind.SUBSCRIPTION)
        assertEquals(100L, Recurring.reservation(yearly, m, emptyList(), LocalDate.of(2027, 12, 25)))
    }

    @Test fun extremeAmountsDoNotOverflowFormatting() {
        assertTrue(Money.format(Long.MAX_VALUE / 2).startsWith("₹"))
        assertEquals("₹0", Money.format(0))
        assertEquals("-₹1", Money.format(-100))
        assertEquals("₹1,00,00,00,000", Money.whole(100_00_00_00_000))
    }
}
