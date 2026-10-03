package dev.mitul.upibudget.gmail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

interface GmailApi {
    suspend fun listIds(query: String): List<String>
    suspend fun get(id: String): GmailMessage
}

/** Read-only Gmail REST calls. The only network code in the app. */
class HttpGmailApi(private val token: () -> String) : GmailApi {
    private val base = "https://gmail.googleapis.com/gmail/v1/users/me/messages"

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.setRequestProperty("Authorization", "Bearer ${token()}")
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            if (c.responseCode != 200) error("Gmail HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    override suspend fun listIds(query: String) =
        GmailJson.parseIds(fetch("$base?maxResults=50&q=${URLEncoder.encode(query, "UTF-8")}"))

    override suspend fun get(id: String) = GmailJson.parseMessage(fetch("$base/$id?format=full"))
}
