package dev.mitul.upibudget.budget

import dev.mitul.upibudget.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BudgetEngineTest {
    private val start = LocalDate.of(2026, 9, 25)
    private fun month(carry: Paise = 0) = MonthInfo(1, start, carry)
    private val t = LocalDateTime.of(2026, 9, 26, 12, 0)

    private fun allowance(rs: Long) = TxLite(t, rs * 100, Direction.CREDIT, Kind.CREDIT, "PRIYA", false, CreditAnswer.ALLOWANCE)
    private fun spend(rs: Long, payee: String = "BLINKIT", kind: Kind = Kind.UPI, saving: Boolean = false) =
        TxLite(t, rs * 100, Direction.DEBIT, kind, payee, saving, null)
    private fun netflix(due: Int = 30) = RecurringLite(1, "NETFLIX COM", 19_900, Frequency.MONTHLY, due, null, null, true, RecurringKind.SUBSCRIPTION)
    private fun rd() = RecurringLite(2, "RD X1234", 500_000, Frequency.MONTHLY, 28, null, LocalDate.of(2027, 12, 28), true, RecurringKind.SAVING)
    private val today = LocalDate.of(2026, 10, 3)

    @Test fun leftSubtractsSpentSavedAndReserved() {
        val s = BudgetEngine.summarize(month(), listOf(allowance(20_000), spend(2_000), spend(3_000, "RD X1234", Kind.RD, saving = true)),
            listOf(netflix(), rd().copy(merchantNorm = "RD X9999")), today)
        assertEquals(2_000_00L, s.spent)
        assertEquals(3_000_00L, s.saved)
        assertEquals(19_900L + 500_000L, s.reserved)
        assertEquals(20_000_00L - 2_000_00L - 3_000_00L - 19_900L - 500_000L, s.left)
        assertEquals(22, s.daysRemaining)      // Oct 3 -> Oct 25
        assertFalse(s.overdue)
    }

    @Test fun realDebitReplacesReservation() {
        val s = BudgetEngine.summarize(month(), listOf(allowance(1_000), spend(2, "NETFLIX COM", Kind.AUTOPAY)), listOf(netflix()), today)
        assertEquals(0L, s.reserved)
        assertEquals(1_000_00L - 2_00L, s.left)
    }

    @Test fun reservationReleasedMoreThanSevenDaysAfterDue() {
        val m = month()
        assertEquals(19_900L, BudgetEngine.summarize(m, listOf(allowance(1_000)), listOf(netflix()), LocalDate.of(2026, 10, 7)).reserved)
        assertEquals(0L, BudgetEngine.summarize(m, listOf(allowance(1_000)), listOf(netflix()), LocalDate.of(2026, 10, 8)).reserved)
        assertEquals(LocalDate.of(2026, 9, 30), BudgetEngine.missedDue(netflix(), m, emptyList(), LocalDate.of(2026, 10, 8)))
        assertNull(BudgetEngine.missedDue(netflix(), m, emptyList(), LocalDate.of(2026, 10, 7)))
    }

    @Test fun yearlyOnlyReservesInItsMonth() {
        val y = netflix().copy(frequency = Frequency.YEARLY, nextDue = LocalDate.of(2026, 10, 10))
        assertEquals(19_900L, BudgetEngine.summarize(month(), emptyList(), listOf(y), today).reserved)
        val far = y.copy(nextDue = LocalDate.of(2027, 3, 1))
        assertEquals(0L, BudgetEngine.summarize(month(), emptyList(), listOf(far), today).reserved)
    }

    @Test fun inactiveOrEndedItemsReserveNothing() {
        assertEquals(0L, BudgetEngine.summarize(month(), emptyList(), listOf(netflix().copy(active = false)), today).reserved)
        assertEquals(0L, BudgetEngine.summarize(month(), emptyList(), listOf(netflix().copy(end = LocalDate.of(2026, 9, 1))), today).reserved)
    }

    @Test fun dueDayBeyondMonthLengthClampsToLastDay() {
        val d = Recurring.dueDate(netflix(31), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 3))
        assertEquals(LocalDate.of(2026, 2, 28), d)
    }

    @Test fun paybacksAndRefundsAddIgnoredAndUnansweredDont() {
        val txs = listOf(allowance(1_000),
            allowance(0).copy(amount = 200_00, creditAs = CreditAnswer.PAYBACK),
            allowance(0).copy(amount = 50_00, creditAs = CreditAnswer.REFUND),
            allowance(0).copy(amount = 999_00, creditAs = CreditAnswer.IGNORE),
            allowance(0).copy(amount = 777_00, creditAs = null))
        val s = BudgetEngine.summarize(month(), txs, emptyList(), today)
        assertEquals(1_250_00L, s.income)
        assertEquals(1_250_00L, s.left)
    }

    @Test fun negativeCarryOverEatsIntoAllowance() {
        val s = BudgetEngine.summarize(month(carry = -300_00), listOf(allowance(1_000)), emptyList(), today)
        assertEquals(700_00L, s.left)
        assertEquals(-300_00L, s.carryOver)
    }

    @Test fun overBudgetIsNegativeAndPerDayNegative() {
        val s = BudgetEngine.summarize(month(), listOf(allowance(100), spend(300)), emptyList(), today)
        assertEquals(-200_00L, s.left)
        assertTrue(s.perDay < 0)
    }

    @Test fun overdueMonthKeepsWorkingWithOneDay() {
        val s = BudgetEngine.summarize(month(), listOf(allowance(1_000), spend(100)), emptyList(), LocalDate.of(2026, 12, 1))
        assertTrue(s.overdue)
        assertEquals(1, s.daysRemaining)
        assertEquals(900_00L, s.perDay)
    }

    @Test fun lastDayOfMonthHasOneDay() {
        assertEquals(1, BudgetEngine.summarize(month(), emptyList(), emptyList(), LocalDate.of(2026, 10, 24)).daysRemaining)
    }

    @Test fun carryOverExcludesReservations() {
        val c = BudgetEngine.carryOverOf(month(carry = 100_00), listOf(allowance(1_000), spend(400), spend(100, saving = true)))
        assertEquals(600_00L, c)
    }

    @Test fun newMonthStartsWhenNoneOrTwentyDaysOld() {
        val m = month()
        assertTrue(BudgetEngine.shouldStartNewMonth(null, today))
        assertFalse(BudgetEngine.shouldStartNewMonth(m, LocalDate.of(2026, 10, 14)))   // 19 days
        assertTrue(BudgetEngine.shouldStartNewMonth(m, LocalDate.of(2026, 10, 15)))    // 20 days
    }

    @Test fun matchesIsWholeWordEitherDirection() {
        assertTrue(Recurring.matches("NETFLIX COM", "NETFLIX COM"))
        assertTrue(Recurring.matches("NETFLIX", "NETFLIX COM"))
        assertFalse(Recurring.matches("GOOGLE PLAY", "GOOGLE ASIA PACIFIC"))
    }

    @Test fun rdEnteredByHandIsReplacedByTheRealInstallmentOfTheSameAmount() {
        val mine = rd().copy(merchantNorm = "MY HDFC RD")          // the user's own label does not match the bank's "RD X4321"
        val withDebit = listOf(allowance(1_000), spend(5_000, "RD X4321", Kind.RD, saving = true))
        assertEquals(0L, BudgetEngine.summarize(month(), withDebit, listOf(mine), today).reserved)
        val otherAmount = listOf(allowance(1_000), spend(4_000, "RD X4321", Kind.RD, saving = true))
        assertEquals(500_000L, BudgetEngine.summarize(month(), otherAmount, listOf(mine), today).reserved)
        assertNull(BudgetEngine.missedDue(mine, month(), withDebit, LocalDate.of(2026, 10, 20)))
    }
}
