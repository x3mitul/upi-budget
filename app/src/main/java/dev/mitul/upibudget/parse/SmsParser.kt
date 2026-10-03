package dev.mitul.upibudget.parse

import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.parse.ParsedMessage.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object SmsParser {
    private val opt = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private const val AMT = "([\\d,]+(?:\\.\\d+)?)"
    private const val DATE = "(\\d{2}-\\d{2}-\\d{2})"

    private val upiRe = Regex("INR\\s+$AMT\\s+(debited|credited)\\s+A/c\\s+no\\.?\\s+(\\w+)\\s+$DATE,\\s*(\\d{2}:\\d{2}:\\d{2})\\s+UPI/(P2M|P2A)/(\\d+)/\\s*(.*?)\\s*(?:Not you\\?|Axis Bank\\s*$|$)", opt)
    private val autopayRe = Regex("A/c has been debited towards\\s+(.+?)\\s+for INR\\s+$AMT\\s+on\\s+$DATE", opt)
    private val noticeRe = Regex("upcoming mandate set for\\s+$DATE,\\s*INR\\s+$AMT\\s+will be debited from your A/c towards\\s+(.+?)\\s+for\\s+(?:Upi Mandate|[^,.]+,)", opt)
    private val createdRe = Regex("mandate has been successfully created towards\\s+(.+?)\\s+from\\s+$DATE\\s+to\\s+$DATE\\s+for INR\\s+$AMT", opt)
    private val revokedRe = Regex("mandate has been successfully revoked towards\\s+(.+?)\\s+for INR", opt)
    private val liteRe = Regex("UPI LITE top-up.*?INR\\s+$AMT.*?successful.*?Ref no\\.?\\s*(\\d+)", opt)
    // "Debit INR 5000.00 / Axis Bank A/c XX1234 / 25-09-26 04:05:06 / MBB-RD/<ref>/MIT": net-banking style debits, RD installments among them.
    private val mbbRe = Regex("(Debit|Credit)\\s+INR\\s+$AMT\\s+Axis Bank A/c\\s+(\\w+)\\s+$DATE\\s+(\\d{2}:\\d{2}:\\d{2})\\s+([A-Za-z0-9-]+)/(\\d+)", opt)
    private val rdBookedRe = Regex("Your RD no\\.?\\s*(\\w+)\\s+is booked for INR\\s+$AMT\\s+for a tenure of\\s+(\\d+)\\s*M", opt)
    private val rdDebitRe = Regex("RD no\\.?\\s*(\\w+)", opt)
    private val anyAmountRe = Regex("(?:INR|Rs\\.?|₹)\\s*$AMT", opt)
    private val acctRe = Regex("A/c\\s+(?:no\\.?\\s*)?(X+\\d+)", opt)
    private val ignoreRe = Regex("\\bOTP\\b|reversed|failed|declined|unsuccessful|collect request|credit card|card no|apply now|pre-approved|offer", opt)
    private val moneyWords = Regex("\\b(debited|credited)\\b", opt)

    /** Never throws: anything that looks like money but has an impossible date or amount becomes Ignored (and is logged by the Router). */
    fun parse(sender: String, body: String, receivedAt: LocalDateTime): ParsedMessage =
        try { parseUnsafe(sender, body, receivedAt) } catch (e: RuntimeException) { Ignored("unparseable: ${e::class.simpleName}") }

    private fun parseUnsafe(sender: String, body: String, receivedAt: LocalDateTime): ParsedMessage {
        if (!sender.uppercase().contains("AXISBK")) return Ignored("sender")
        // Structured formats first: they are specific, so a promo/OTP word elsewhere cannot hide them.
        upiRe.find(body)?.let { m ->
            val (amt, dir, acct, date, time, type, ref, payee) = m.destructured
            val debit = dir.equals("debited", true)
            return Payment(
                Money.parse(amt), if (debit) Direction.DEBIT else Direction.CREDIT,
                LocalDateTime.of(d(date), LocalTime.parse(time)), ref, payee,
                if (debit) Kind.UPI else Kind.CREDIT,
                if (type == "P2M") PayeeType.P2M else PayeeType.P2A, acct,
            )
        }
        noticeRe.find(body)?.destructured?.let { (date, amt, merchant) ->
            return MandateNotice(merchant, Money.parse(amt), d(date))
        }
        createdRe.find(body)?.destructured?.let { (merchant, s, e, amt) ->
            return MandateCreated(merchant, Money.parse(amt), d(s), d(e))
        }
        revokedRe.find(body)?.destructured?.let { (merchant) -> return MandateRevoked(merchant) }
        liteRe.find(body)?.destructured?.let { (amt, ref) ->
            return Payment(Money.parse(amt), Direction.DEBIT, receivedAt, ref, "UPI LITE", Kind.UPI, PayeeType.P2M, null)
        }
        mbbRe.find(body)?.destructured?.let { (dir, amt, acct, date, time, desc, ref) ->
            val debit = dir.equals("debit", true)
            val rd = desc.contains("RD", true)
            return Payment(Money.parse(amt), if (debit) Direction.DEBIT else Direction.CREDIT, LocalDateTime.of(d(date), LocalTime.parse(time)), ref,
                if (rd) "RD" else desc, if (rd) Kind.RD else if (debit) Kind.UNKNOWN else Kind.CREDIT, PayeeType.UNKNOWN, acct)
        }
        rdBookedRe.find(body)?.let { m ->
            val (acct, amt, months) = m.destructured
            return RdBooked(acct, Money.parse(amt), months.toInt())
        }
        if (ignoreRe.containsMatchIn(body)) return Ignored("not a transaction")
        autopayRe.find(body)?.let { m ->
            val (merchant, amt, date) = m.destructured
            return Payment(Money.parse(amt), Direction.DEBIT, LocalDateTime.of(d(date), receivedAt.toLocalTime()), null,
                merchant, Kind.AUTOPAY, PayeeType.P2M, null)
        }
        val dirWord = moneyWords.find(body)?.value?.lowercase() ?: return Ignored("no debit/credit word")
        val amt = anyAmountRe.find(body)?.groupValues?.get(1) ?: return Ignored("no amount")
        val direction = if (dirWord == "debited") Direction.DEBIT else Direction.CREDIT
        val rd = rdDebitRe.find(body)?.groupValues?.get(1)
        return Payment(
            Money.parse(amt), direction, receivedAt, null,
            if (rd != null) "RD $rd" else "",
            if (rd != null) Kind.RD else if (direction == Direction.CREDIT) Kind.CREDIT else Kind.UNKNOWN,
            PayeeType.UNKNOWN, acctRe.find(body)?.groupValues?.get(1),
        )
    }

    private fun d(s: String): LocalDate =
        LocalDate.of(2000 + s.substring(6, 8).toInt(), s.substring(3, 5).toInt(), s.substring(0, 2).toInt())
}
