package dev.mitul.upibudget.parse

import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.parse.ParsedMessage.*

/** Formats are best guesses until real samples arrive via the debug log (plan Task 18). */
object NotificationParser {
    val PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp", // PhonePe, Paytm, BHIM
        "com.dreamplug.androidapp", "com.axis.mobile",               // CRED, Axis Mobile
        "money.super.payments",                                      // super.money
    )
    private const val AMT = "(?:₹|Rs\\.?|INR)\\s*([\\d,]+(?:\\.\\d+)?)"
    private const val END = "(?:\\s+(?:on|in|via|to|using|from|successful|successfully)\\b|[.!]|$)"
    private val i = RegexOption.IGNORE_CASE
    private val noise = Regex("\\brequest(?:ed|s)?\\b|\\bOTP\\b|\\boffers?\\b|\\breminder\\b", i)
    private val credit1 = Regex("(?:received|credited)\\s+$AMT\\s+from\\s+(.+?)$END", i)       // amt, name
    private val credit2 = Regex("^(.+?)\\s+(?:paid|sent)\\s+you\\s+$AMT", i)                   // name, amt
    private val debit1 = Regex("(?:paid|sent|payment of)\\s+$AMT\\s+to\\s+(.+?)$END", i)       // amt, name
    private val debit2 = Regex("$AMT\\s+(?:paid|sent)\\s+to\\s+(.+?)$END", i)                  // amt, name

    fun parse(pkg: String, title: String, text: String): ParsedMessage =
        try { parseUnsafe(pkg, title, text) } catch (e: RuntimeException) { Ignored("unparseable: ${e::class.simpleName}") }

    private fun parseUnsafe(pkg: String, title: String, text: String): ParsedMessage {
        if (pkg !in PACKAGES) return Ignored("package")
        if (noise.containsMatchIn("$title $text")) return Ignored("noise")
        for (s in listOf(text, title, "$title $text")) {
            credit1.find(s)?.let { return pay(it.groupValues[1], it.groupValues[2], Direction.CREDIT) }
            credit2.find(s)?.let { return pay(it.groupValues[2], it.groupValues[1], Direction.CREDIT) }
            debit1.find(s)?.let { return pay(it.groupValues[1], it.groupValues[2], Direction.DEBIT) }
            debit2.find(s)?.let { return pay(it.groupValues[1], it.groupValues[2], Direction.DEBIT) }
        }
        return Ignored("no match")
    }

    private fun pay(amount: String, name: String, dir: Direction) = Payment(
        Money.parse(amount), dir, null, null, name.trim(), if (dir == Direction.DEBIT) Kind.UPI else Kind.CREDIT, PayeeType.UNKNOWN, null,
    )
}
