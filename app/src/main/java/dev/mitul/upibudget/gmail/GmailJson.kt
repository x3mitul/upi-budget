package dev.mitul.upibudget.gmail

import dev.mitul.upibudget.parse.EmailParser
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

data class GmailMessage(val id: String, val subject: String, val body: String, val at: LocalDateTime)

object GmailJson {
    fun parseIds(json: String): List<String> {
        val arr = JSONObject(json).optJSONArray("messages") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
    }

    fun parseMessage(json: String): GmailMessage {
        val o = JSONObject(json)
        val payload = o.getJSONObject("payload")
        val headers = payload.optJSONArray("headers") ?: JSONArray()
        val subject = (0 until headers.length()).map { headers.getJSONObject(it) }
            .firstOrNull { it.getString("name").equals("Subject", true) }?.getString("value").orEmpty()
        val plain = findPart(payload, "text/plain")
        val body = if (plain != null) decode(plain) else findPart(payload, "text/html")?.let { EmailParser.htmlToText(decode(it)) }.orEmpty()
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(o.optString("internalDate", "0").toLong()), ZoneId.systemDefault())
        return GmailMessage(o.getString("id"), subject, body, at)
    }

    private fun findPart(p: JSONObject, mime: String): String? {
        if (p.optString("mimeType") == mime) p.optJSONObject("body")?.optString("data")?.takeIf { it.isNotEmpty() }?.let { return it }
        val parts = p.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) findPart(parts.getJSONObject(i), mime)?.let { return it }
        return null
    }

    private fun decode(data: String) = String(Base64.getUrlDecoder().decode(data), Charsets.UTF_8)
}
