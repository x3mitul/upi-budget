package dev.mitul.upibudget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test fun parsesCommonAxisAndAppFormats() {
        assertEquals(43800L, Money.parse("438.00"))
        assertEquals(12500000L, Money.parse("1,25,000.00"))
        assertEquals(500000L, Money.parse("5000"))
        assertEquals(50L, Money.parse("0.5"))
        assertEquals(50050L, Money.parse("Rs.500.5"))
        assertEquals(123400L, Money.parse("₹1,234"))
        assertEquals(1999_00L, Money.parse("INR 1999.00"))
    }

    @Test fun rejectsTextWithNoNumber() {
        assertThrows(NumberFormatException::class.java) { Money.parse("INR") }
    }

    @Test fun formatsWithIndianGrouping() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(99900))
        assertEquals("₹1,000", Money.format(100000))
        assertEquals("₹12,345", Money.format(1234500))
        assertEquals("₹12,34,567", Money.format(123456700))
        assertEquals("₹1,234.50", Money.format(123450))
        assertEquals("-₹250", Money.format(-25000))
    }

    @Test fun wholeRoundsToNearestRupee() {
        assertEquals("₹1,13,156", Money.whole(11315550))
        assertEquals("₹302", Money.whole(30249))
        assertEquals("-₹251", Money.whole(-25050))
    }
}
