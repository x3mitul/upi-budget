package dev.mitul.upibudget.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PrettyNameTest {
    @Test fun shoutedBankNamesBecomeTitleCase() = assertEquals("Priya Sharma", prettyName("PRIYA  SHARMA"))
    @Test fun mixedCaseIsKept() = assertEquals("Swiggy Ltd", prettyName("Swiggy Ltd"))
    @Test fun blankBecomesPayment() = assertEquals("Payment", prettyName("  "))
    @Test fun nonLettersSurvive() = assertEquals("7 Eleven", prettyName("7 ELEVEN"))
}
