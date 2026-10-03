package dev.mitul.upibudget.parse

import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.parse.ParsedMessage.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class EmailParserTest {
    private val at = LocalDateTime.of(2026, 10, 2, 10, 5)

    @Test fun htmlToTextStripsTagsAndEntities() {
        val t = EmailParser.htmlToText("<html><style>p{color:red}</style><body><p>Dear Customer,</p><p>INR&nbsp;500.00 credited &amp; done</p></body></html>")
        assertTrue(t.contains("INR 500.00 credited & done"))
        assertFalse(t.contains("<"))
        assertFalse(t.contains("color"))
    }

    @Test fun creditEmailWithUpiTransactionInfo() {
        val m = EmailParser.parse("Alert: Amount credited to your A/c XX1234",
            "Dear Customer,\nINR 500.00 has been credited to your A/c no. XX1234.\nTransaction Info: UPI/P2A/611111111111/PRIYA SHARMA\nAxis Bank", at) as Payment
        assertEquals(Direction.CREDIT, m.direction); assertEquals(50_000L, m.amount)
        assertEquals("PRIYA SHARMA", m.payee); assertEquals("611111111111", m.ref); assertEquals(PayeeType.P2A, m.payeeType)
        assertEquals("XX1234", m.account)
    }

    @Test fun smsShapedEmailIsParsedLikeSms() {
        val m = EmailParser.parse("Debit alert", "INR 438.00 debited\nA/c no. XX1234\n13-09-26, 09:32:24\nUPI/P2M/662241432378/\nBlinkit\nAxis Bank", at) as Payment
        assertEquals("Blinkit", m.payee); assertEquals(Direction.DEBIT, m.direction)
    }

    @Test fun marketingAndNonMoneyEmailsAreIgnored() {
        assertTrue(EmailParser.parse("Pre-approved offer for you", "Apply now and get INR 5,00,000 loan", at) is Ignored)
        assertTrue(EmailParser.parse("Your statement is ready", "Please find attached", at) is Ignored)
    }
}
