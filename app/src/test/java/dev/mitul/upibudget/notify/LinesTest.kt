package dev.mitul.upibudget.notify

import dev.mitul.upibudget.budget.Summary
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.ingest.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class LinesTest {
    private fun s(left: Long, perDay: Long) = Summary(0, 0, 0, 0, 0, 0, 0, left, 10, perDay, false)

    @Test fun statusShowsLeftAndPerDay() = assertEquals("₹4,080 left · ₹291/day", Lines.status(s(408_000, 29_100)))
    @Test fun statusShowsOverBudget() = assertEquals("Over by ₹250", Lines.status(s(-25_000, -2_500)))
    @Test fun statusRoundsToWholeRupees() = assertEquals("Over by ₹94,685 ".trim(), Lines.status(s(-9_468_450, -300_000)))
    @Test fun statusEmptyWithoutMonth() = assertEquals("", Lines.status(null))

    @Test fun debitTitles() {
        val asked = Outcome.Debit(1, 12_000, "RAMESH KIRANA", "Uncategorized", true, emptyList(), null)
        assertEquals("₹120 to Ramesh Kirana", Lines.debitTitle(asked))
        assertEquals("₹250 · Swiggy Ltd · Food Delivery", Lines.debitTitle(asked.copy(amount = 25_000, payee = "Swiggy Ltd", subName = "Food Delivery", asked = false)))
    }

    @Test fun creditTitles() {
        val c = Outcome.Credit(1, 500_000, "PRIYA", null, null)
        assertEquals("₹5,000 received from Priya", Lines.creditTitle(c))
        assertEquals("₹5,000 Payback · Priya", Lines.creditTitle(c.copy(answered = CreditAnswer.PAYBACK)))
        assertEquals("₹5,000 Refund · Priya", Lines.creditTitle(c.copy(answered = CreditAnswer.REFUND)))
    }
}
