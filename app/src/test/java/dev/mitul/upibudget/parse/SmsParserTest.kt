package dev.mitul.upibudget.parse

import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.parse.ParsedMessage.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SmsParserTest {
    private val now = LocalDateTime.of(2026, 9, 13, 9, 33)
    private fun parse(body: String, sender: String = "AX-AXISBK-S") = SmsParser.parse(sender, body, now)

    private val upiDebit = "INR 438.00 debited\nA/c no. XX1234\n13-09-26, 09:32:24\nUPI/P2M/662241432378/\nBlinkit\nNot you? SMS BLOCKUPI\nCust ID to 919900000000\nAxis Bank"

    @Test fun upiMerchantDebit() {
        val p = parse(upiDebit) as Payment
        assertEquals(43800L, p.amount)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(LocalDateTime.of(2026, 9, 13, 9, 32, 24), p.time)
        assertEquals("662241432378", p.ref)
        assertEquals("Blinkit", p.payee)
        assertEquals(Kind.UPI, p.kind)
        assertEquals(PayeeType.P2M, p.payeeType)
        assertEquals("XX1234", p.account)
    }

    @Test fun upiPersonDebitKeepsDoubleSpaceName() {
        val p = parse("INR 288.00 debited\nA/c no. XX1234\n14-09-26, 08:57:23\nUPI/P2A/662356647951/\nSUNIL  SURNAME\nNot you? SMS BLOCKUPI\nCust ID to 919900000000\nAxis Bank") as Payment
        assertEquals(PayeeType.P2A, p.payeeType)
        assertEquals("SUNIL  SURNAME", p.payee)
    }

    @Test fun bigIndianFormattedAmount() {
        val p = parse(upiDebit.replace("INR 438.00", "INR 1,25,000.00")) as Payment
        assertEquals(12_500_000L, p.amount)
    }

    @Test fun amountWithoutDecimals() {
        assertEquals(500_000L, (parse(upiDebit.replace("INR 438.00", "INR 5000")) as Payment).amount)
    }

    @Test fun autopayDebit() {
        val p = parse("Your A/c has been debited towards Google Play for INR 1999.00 on 25-09-26. 01a0d96f11af70e8b785f8904898a86f@supermoney - Axis Bank") as Payment
        assertEquals(Kind.AUTOPAY, p.kind)
        assertEquals("Google Play", p.payee)
        assertEquals(199_900L, p.amount)
        assertEquals(LocalDate.of(2026, 9, 25), p.time!!.toLocalDate())
    }

    @Test fun autopayNotice() {
        val m = parse("For the upcoming mandate set for 30-09-26, INR 199.00 will be debited from your A/c towards NETFLIX COM for Upi Mandate, Axis Bank") as MandateNotice
        assertEquals("NETFLIX COM", m.merchant)
        assertEquals(19_900L, m.amount)
        assertEquals(LocalDate.of(2026, 9, 30), m.dueDate)
    }

    @Test fun mandateCreated() {
        val m = parse("Your UPI ASPRESENTED mandate has been successfully created towards Google Play from 25-09-26 to 31-12-36 for INR 1999.00 - Axis Bank") as MandateCreated
        assertEquals("Google Play", m.merchant)
        assertEquals(199_900L, m.amount)
        assertEquals(LocalDate.of(2026, 9, 25), m.start)
        assertEquals(LocalDate.of(2036, 12, 31), m.end)
    }

    @Test fun rdBooked() {
        val m = parse("Your RD no. X1234 is booked for INR 5000 for a tenure of 15M 0D at 6.45%. Nomination Registered: Y, Auto Renewal: N - Axis Bank") as RdBooked
        assertEquals("X1234", m.account)
        assertEquals(500_000L, m.amount)
        assertEquals(15, m.tenureMonths)
    }

    @Test fun creditIsParsedAsCredit() {
        val p = parse("INR 5,000.00 credited\nA/c no. XX1234\n02-10-26, 10:00:00\nUPI/P2A/611111111111/\nPRIYA SHARMA\nAxis Bank") as Payment
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals(500_000L, p.amount)
        assertEquals("PRIYA SHARMA", p.payee)
    }

    @Test fun unknownDebitShapeIsKeptNotDropped() {
        val p = parse("INR 750.00 debited from A/c XX1234 towards some new thing - Axis Bank") as Payment
        assertEquals(Kind.UNKNOWN, p.kind)
        assertEquals(75_000L, p.amount)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("", p.payee)
        assertEquals(now, p.time)
    }

    @Test fun nonTransactionsAreIgnored() {
        assertTrue(parse("123456 is your OTP for login. Do not share. - Axis Bank") is Ignored)
        assertTrue(parse("INR 100.00 debited UPI payment failed, amount reversed. Axis Bank") is Ignored)
        assertTrue(parse("Get a pre-approved loan up to INR 5,00,000. Apply now - Axis Bank") is Ignored)
        assertTrue(parse(upiDebit, sender = "VK-SOMEBNK-S") is Ignored)
        assertTrue(parse("Spent INR 500.00 on Axis Bank Credit Card no. XX9999 at AMAZON. Avl limit INR 1,00,000") is Ignored)
    }

    @Test fun payeeNamedLikeTheBankIsKeptWhole() {
        val p = parse("INR 5000.00 debited\nA/c no. XX1234\n13-09-26, 09:32:24\nUPI/P2M/662241432378/\nAxis Bank Credit Card Payment\nNot you? SMS BLOCKUPI\nCust ID to 919900000000\nAxis Bank") as Payment
        assertEquals("Axis Bank Credit Card Payment", p.payee)
    }

    @Test fun payeeWithSlashesAndDotsIsKept() {
        val p = parse("INR 10.00 debited\nA/c no. XX1234\n13-09-26, 09:32:24\nUPI/P2M/662241432378/\nM/S A.B. TRADERS & CO.\nNot you? SMS BLOCKUPI\nAxis Bank") as Payment
        assertEquals("M/S A.B. TRADERS & CO.", p.payee)
    }

    // ---- formats seen in the real inbox (all digits and names changed) ----
    @Test fun upcomingMandateNoticeWithReferenceInsteadOfUpiMandate() {
        val m = parse("For the upcoming mandate set for 03-11-26, INR 129.00 will be debited from your A/c towards YouTube for GOOGLE, 123456789012. To stop execution, pause mandate - Axis Bank") as MandateNotice
        assertEquals("YouTube", m.merchant); assertEquals(12_900L, m.amount); assertEquals(LocalDate.of(2026, 11, 3), m.dueDate)
    }

    @Test fun rdInstallmentDebitInMbbFormat() {
        val p = parse("Debit INR 5000.00\nAxis Bank A/c XX1234\n25-09-26 04:05:06\nMBB-RD/123456789012/MIT\nWhatsApp BAL to 919900000000\nNot You? SMS BLOCKALL CustID to 919900000000") as Payment
        assertEquals(500_000L, p.amount); assertEquals(Direction.DEBIT, p.direction); assertEquals(Kind.RD, p.kind)
        assertEquals("RD", p.payee); assertEquals("XX1234", p.account); assertEquals("123456789012", p.ref)
        assertEquals(LocalDateTime.of(2026, 9, 25, 4, 5, 6), p.time)
    }

    @Test fun upiLiteTopUpIsMoneyOut() {
        val p = parse("UPI LITE top-up on UPI App amounting to INR 200.00 has been successful. Ref no. 123456789012 - Axis Bank") as Payment
        assertEquals(20_000L, p.amount); assertEquals(Direction.DEBIT, p.direction); assertEquals("UPI LITE", p.payee); assertEquals("123456789012", p.ref)
    }

    @Test fun revokedMandate() {
        val m = parse("Your UPI mandate has been successfully revoked towards Google Play for INR 1999.00 - Axis Bank") as MandateRevoked
        assertEquals("Google Play", m.merchant)
    }

    @Test fun rdPenaltyNoticeIsStillIgnored() =
        assertTrue(parse("W.e.f. 01-10-2026, penalty for delayed RD instalments is revised from INR 10 to INR 20 per INR 1000/month. Recoverable at maturity/closure. T&C apply - Axis Bank") is Ignored)
}
