package dev.mitul.upibudget.ui

import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BreakdownTest {
    private val cats = listOf(
        CategoryEntity(1, "Food & Dining", null, 0xFF0000, false, true, 0),
        CategoryEntity(2, "Food Delivery", 1, 0xFF0000, false, true, 1),
        CategoryEntity(3, "Restaurants", 1, 0xFF0000, false, true, 2),
        CategoryEntity(4, "Savings & Investments", null, 0x00FF00, true, true, 3),
        CategoryEntity(5, "Recurring Deposit", 4, 0x00FF00, true, true, 4),
        CategoryEntity(6, "Transport", null, 0x0000FF, false, true, 5),
        CategoryEntity(7, "Metro", 6, 0x0000FF, false, true, 6),
    )
    private fun tx(sub: Long, rs: Long, dir: Direction = Direction.DEBIT) = TxnEntity(time = 0, amount = rs * 100, direction = dir, payeeRaw = "x",
        payeeNorm = "X", payeeType = PayeeType.P2M, subId = sub, needsSorting = false, creditAs = null, source = Source.SMS, ref = null,
        rawText = "", kind = Kind.UPI, monthId = 1)

    @Test fun groupsSubsAndSortsLargestFirst() {
        val g = Breakdown.byGroup(listOf(tx(2, 100), tx(3, 50), tx(7, 400), tx(2, 20)), cats)
        assertEquals(listOf("Transport", "Food & Dining"), g.map { it.name })
        assertEquals(170_00L, g[1].total)
        assertEquals(listOf("Food Delivery", "Restaurants"), g[1].subs.map { it.name })
        assertEquals(120_00L, g[1].subs[0].total)
    }

    @Test fun savingsAndCreditsAreExcluded() {
        val g = Breakdown.byGroup(listOf(tx(5, 5000), tx(2, 100, Direction.CREDIT), tx(7, 10)), cats)
        assertEquals(listOf("Transport"), g.map { it.name })
    }

    @Test fun emptyInputGivesEmptyList() = assertEquals(emptyList<GroupTotal>(), Breakdown.byGroup(emptyList(), cats))
}
