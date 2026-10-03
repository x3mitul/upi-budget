package dev.mitul.upibudget.parse

import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.parse.ParsedMessage.*
import java.time.LocalDateTime

object EmailParser {
    private val upiInfo = Regex("UPI/(P2A|P2M)/(\\d+)/([^\\n/<]+)")
    private val acct = Regex("A/c\\s+(?:no\\.?\\s*)?(X+\\d+)", RegexOption.IGNORE_CASE)

    fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<(script|style).*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</tr>|</div>|</li>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("[ \\t]+"), " ").replace(Regex("\\s*\\n\\s*"), "\n").trim()

    fun parse(subject: String, bodyText: String, receivedAt: LocalDateTime): ParsedMessage {
        val text = "$subject\n$bodyText"
        val m = SmsParser.parse("AXISBK", text, receivedAt)
        if (m !is Payment || m.payee.isNotEmpty()) return m
        val u = upiInfo.find(text)
        val type = when (u?.groupValues?.get(1)) { "P2M" -> PayeeType.P2M; "P2A" -> PayeeType.P2A; else -> PayeeType.UNKNOWN }
        return m.copy(
            payee = u?.groupValues?.get(3)?.trim() ?: "", ref = u?.groupValues?.get(2), payeeType = type,
            account = m.account ?: acct.find(text)?.groupValues?.get(1),
        )
    }
}
