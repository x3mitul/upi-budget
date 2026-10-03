package dev.mitul.upibudget.parse

import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.parse.ParsedMessage.*
import org.junit.Assert.*
import org.junit.Test

class NotificationParserTest {
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private fun p(text: String, title: String = "Google Pay", pkg: String = gpay) = NotificationParser.parse(pkg, title, text)

    @Test fun debitYouPaidTo() {
        val m = p("You paid ₹250.00 to Swiggy") as Payment
        assertEquals(25_000L, m.amount); assertEquals(Direction.DEBIT, m.direction); assertEquals("Swiggy", m.payee)
    }
    @Test fun debitPaymentOfSuccessful() =
        assertEquals("Ramesh Kirana", (p("Payment of ₹120 to Ramesh Kirana successful") as Payment).payee)
    @Test fun creditReceivedFrom() {
        val m = p("Received ₹500 from Priya Sharma") as Payment
        assertEquals(Direction.CREDIT, m.direction); assertEquals(Kind.CREDIT, m.kind); assertEquals("Priya Sharma", m.payee)
    }
    @Test fun creditNamePaidYouInTitle() {
        val m = p("₹1,000", title = "Priya Sharma paid you ₹1,000") as Payment
        assertEquals(100_000L, m.amount); assertEquals("Priya Sharma", m.payee)
    }
    @Test fun collectRequestsOtpAndOffersAreIgnored() {
        assertTrue(p("Priya requested ₹500 from you") is Ignored)
        assertTrue(p("Use code 123456 as OTP") is Ignored)
        assertTrue(p("Get ₹50 cashback offer on your next payment") is Ignored)
    }
    @Test fun otherPackagesAreIgnored() = assertTrue(p("You paid ₹250 to Swiggy", pkg = "com.whatsapp") is Ignored)
    @Test fun garbageNeverThrows() = assertTrue(p("") is Ignored)
}
