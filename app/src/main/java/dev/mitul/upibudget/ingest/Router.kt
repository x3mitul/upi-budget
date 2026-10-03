package dev.mitul.upibudget.ingest

import dev.mitul.upibudget.core.Kind
import dev.mitul.upibudget.core.Source
import dev.mitul.upibudget.core.toStore
import dev.mitul.upibudget.data.AppDatabase
import dev.mitul.upibudget.data.DebugLogEntity
import dev.mitul.upibudget.parse.*
import java.time.LocalDateTime

class Router(private val db: AppDatabase, private val ingestor: Ingestor, private val notify: suspend (Outcome) -> Unit) {
    private val otherMoney = Regex("(?:₹|Rs\\.?|INR)\\s*\\d.*\\b(?:paid|sent|received|debited|credited)\\b|\\b(?:paid|sent|received|debited|credited)\\b.*(?:₹|Rs\\.?|INR)\\s*\\d", RegexOption.IGNORE_CASE)
    private val money = Regex("(?:INR|Rs\\.?)\\s*\\d", RegexOption.IGNORE_CASE)

    private suspend fun log(tag: String, text: String, at: LocalDateTime) {
        val t = text.take(600)
        if (db.misc().hasLog(tag, t)) return          // the catch-up re-reads the inbox often; keep each sample once
        db.misc().addLog(DebugLogEntity(time = at.toStore(), tag = tag, text = t))
        db.misc().pruneLogs(300)
    }

    private suspend fun run(p: ParsedMessage, source: Source, raw: String, at: LocalDateTime) {
        try {
            val o = ingestor.handle(p, source, raw, at)
            if (o != Outcome.Skipped) notify(o)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // A bad message or a database hiccup must never take the app down: keep the raw text so it can be fixed or re-read.
            log("error", "${e::class.simpleName}: ${e.message}\n$raw", at)
        }
    }

    suspend fun sms(sender: String, body: String, at: LocalDateTime) {
        val p = SmsParser.parse(sender, body, at)
        if (p is ParsedMessage.Ignored && sender.uppercase().contains("AXISBK") && money.containsMatchIn(body)) log("sms-ignored", body, at)
        if (p is ParsedMessage.Payment && p.kind == Kind.UNKNOWN) log("sms-unknown", body, at)
        run(p, Source.SMS, body, at)
    }

    suspend fun notification(pkg: String, title: String, text: String, at: LocalDateTime) {
        if (pkg !in NotificationParser.PACKAGES) {
            // Learn which other app might be a payment app: keep (never ingest) money-looking text so its package can be added.
            if (otherMoney.containsMatchIn("$title $text")) log("notif-other:$pkg", "$title | $text", at)
            return
        }
        val p = NotificationParser.parse(pkg, title, text)
        log("notif:$pkg", (if (p is ParsedMessage.Ignored) "IGNORED " else "PARSED ") + "$title | $text", at)
        run(p, Source.NOTIFICATION, "$pkg|$title|$text", at)
    }

    suspend fun email(messageId: String, subject: String, body: String, at: LocalDateTime) {
        log("email", "$subject\n${body.take(500)}", at)
        run(EmailParser.parse(subject, body, at), Source.GMAIL, "$messageId|$subject", at)
    }
}
