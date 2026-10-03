package dev.mitul.upibudget.stress

import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.gmail.GmailJson
import dev.mitul.upibudget.parse.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class FuzzTest {
    private val at = LocalDateTime.of(2026, 10, 3, 10, 0)
    private val tokens = listOf(
        "INR", "Rs.", "₹", "debited", "credited", "A/c", "no.", "XX1234", "UPI/P2M/", "UPI/P2A/", "662241432378/", "Not you?", "Axis Bank",
        "99-99-26,", "13-09-26,", "09:32:24", "99:99:99", "31-02-26", "00-00-00", "1,25,000.00", "99999999999999999999999", "0.0000001", ".", "..", "-5",
        "\n", "\r\n", "\t", "  ", "/", "//", "Blinkit", "SUNIL  SURNAME", "रमेश", "😀", "mandate", "has been successfully created towards", "for INR",
        "from", "to", "upcoming mandate set for", "will be debited from your A/c towards", "for Upi Mandate", "RD no.", "is booked for INR", "tenure of", "M",
        "OTP", "failed", "reversed", "1e10", "NaN", "\u0000", "(", ")", "[", "\\", "$", "^", "*", "+", "?", "{", "}", "|",
    )
    private fun junk(r: Random): String = (0 until r.nextInt(1, 40)).joinToString(if (r.nextBoolean()) " " else "") { tokens[r.nextInt(tokens.size)] }

    @Test fun smsParserNeverThrowsOnGarbage() {
        val r = Random(1)
        repeat(30_000) {
            val s = junk(r)
            try { SmsParser.parse("AX-AXISBK-S", s, at) } catch (e: Throwable) { fail("SmsParser threw ${e::class.simpleName} on: ${s.take(200)}") }
        }
    }

    @Test fun smsParserNeverThrowsOnMutatedRealFormats() {
        val r = Random(2)
        val real = listOf(
            "INR 438.00 debited\nA/c no. XX1234\n13-09-26, 09:32:24\nUPI/P2M/662241432378/\nBlinkit\nNot you? SMS BLOCKUPI\nCust ID to 919900000000\nAxis Bank",
            "Your A/c has been debited towards Google Play for INR 1999.00 on 25-09-26. abc@upi - Axis Bank",
            "For the upcoming mandate set for 30-09-26, INR 199.00 will be debited from your A/c towards NETFLIX COM for Upi Mandate, Axis Bank",
            "Your UPI ASPRESENTED mandate has been successfully created towards Google Play from 25-09-26 to 31-12-36 for INR 1999.00 - Axis Bank",
            "Your RD no. X1234 is booked for INR 5000 for a tenure of 15M 0D at 6.45%. Nomination Registered: Y, Auto Renewal: N - Axis Bank",
        )
        repeat(30_000) {
            val sb = StringBuilder(real[r.nextInt(real.size)])
            repeat(r.nextInt(1, 5)) {
                val p = r.nextInt(sb.length + 1)
                when (r.nextInt(3)) { 0 -> sb.insert(p, tokens[r.nextInt(tokens.size)]); 1 -> if (p < sb.length) sb.deleteCharAt(p); else -> if (p + 3 < sb.length) sb.replace(p, p + 3, tokens[r.nextInt(tokens.size)]) }
            }
            val s = sb.toString()
            try { SmsParser.parse("AX-AXISBK-S", s, at) } catch (e: Throwable) { fail("threw ${e::class.simpleName} on: ${s.take(300)}") }
        }
    }

    @Test fun notificationAndEmailParsersNeverThrow() {
        val r = Random(3)
        repeat(20_000) {
            val a = junk(r); val b = junk(r)
            try {
                NotificationParser.parse("com.google.android.apps.nbu.paisa.user", a, b)
                EmailParser.parse(a, b, at)
                EmailParser.htmlToText(a + "<" + b)
            } catch (e: Throwable) { fail("threw ${e::class.simpleName} on: ${a.take(100)} | ${b.take(100)}") }
        }
    }

    @Test fun moneyParseOnlyThrowsNumberFormat() {
        val r = Random(4)
        repeat(20_000) {
            val s = junk(r)
            try { Money.parse(s) } catch (e: NumberFormatException) { } catch (e: Throwable) { fail("Money.parse threw ${e::class.simpleName} on: ${s.take(100)}") }
        }
    }

    @Test fun normalizerAndPrettyNameNeverThrow() {
        val r = Random(5)
        repeat(20_000) { val s = junk(r); Normalizer.name(s); prettyName(s); Money.format(r.nextLong()) }
    }

    @Test fun gmailJsonGarbageOnlyThrowsCatchableExceptions() {
        val r = Random(6)
        repeat(5_000) {
            val s = junk(r)
            try { GmailJson.parseMessage(s) } catch (e: Exception) { }
            try { GmailJson.parseIds(s) } catch (e: Exception) { }
        }
    }
}
