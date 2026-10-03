package dev.mitul.upibudget.gmail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GmailJsonTest {
    private fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    @Test fun parsesIdList() =
        assertEquals(listOf("a1", "b2"), GmailJson.parseIds("""{"messages":[{"id":"a1","threadId":"t"},{"id":"b2","threadId":"t"}],"resultSizeEstimate":2}"""))

    @Test fun emptyListHasNoMessagesKey() = assertEquals(emptyList<String>(), GmailJson.parseIds("""{"resultSizeEstimate":0}"""))

    @Test fun prefersPlainTextPart() {
        val json = """{"id":"m1","internalDate":"1790000000000","payload":{"mimeType":"multipart/alternative",
          "headers":[{"name":"From","value":"Axis <alerts@axis.bank.in>"},{"name":"Subject","value":"Credit alert"}],
          "parts":[{"mimeType":"text/plain","body":{"data":"${b64("INR 500.00 credited")}"}},
                   {"mimeType":"text/html","body":{"data":"${b64("<p>html version</p>")}"}}]}}"""
        val m = GmailJson.parseMessage(json)
        assertEquals("m1", m.id); assertEquals("Credit alert", m.subject); assertEquals("INR 500.00 credited", m.body)
    }

    @Test fun fallsBackToHtmlAndWalksNestedParts() {
        val json = """{"id":"m2","internalDate":"1790000000000","payload":{"mimeType":"multipart/mixed","headers":[{"name":"Subject","value":"S"}],
          "parts":[{"mimeType":"multipart/alternative","parts":[{"mimeType":"text/html","body":{"data":"${b64("<p>INR&nbsp;9.00 debited</p>")}"}}]}]}}"""
        assertTrue(GmailJson.parseMessage(json).body.contains("INR 9.00 debited"))
    }

    @Test fun singlePartMessage() {
        val json = """{"id":"m3","internalDate":"1790000000000","payload":{"mimeType":"text/plain","headers":[{"name":"Subject","value":"S"}],"body":{"data":"${b64("hello")}"}}}"""
        assertEquals("hello", GmailJson.parseMessage(json).body)
    }
}
