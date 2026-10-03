package dev.mitul.upibudget.sms

import android.content.Context
import android.net.Uri
import dev.mitul.upibudget.ingest.Router
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class SmsRow(val address: String, val body: String, val at: LocalDateTime)

object SmsImporter {
    /** Axis SMS from the last [days] days, oldest first. Needs READ_SMS. */
    fun read(ctx: Context, days: Int = 60): List<SmsRow> {
        val since = System.currentTimeMillis() - days * 86_400_000L
        val rows = ArrayList<SmsRow>()
        ctx.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"),
            "date >= ? AND address LIKE ?", arrayOf(since.toString(), "%AXISBK%"), "date ASC")?.use { c ->
            while (c.moveToNext()) {
                rows += SmsRow(c.getString(0) ?: "", c.getString(1) ?: "",
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(2)), ZoneId.systemDefault()))
            }
        }
        return rows
    }

    /** Feeds rows through the normal pipeline. Returns how many rows were processed. */
    suspend fun import(rows: List<SmsRow>, router: Router): Int {
        rows.forEach { router.sms(it.address, it.body, it.at) }
        return rows.size
    }
}
