package dev.mitul.upibudget.gmail

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.data.*
import dev.mitul.upibudget.ingest.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GmailSyncTest {
    private lateinit var db: AppDatabase
    private lateinit var router: Router
    private val at = LocalDateTime.of(2026, 10, 3, 10, 0)

    private class Fake(val msgs: Map<String, GmailMessage>, val failOn: Set<String> = emptySet()) : GmailApi {
        var queries = ArrayList<String>()
        override suspend fun listIds(query: String): List<String> { queries += query; return msgs.keys.toList() }
        override suspend fun get(id: String): GmailMessage { if (id in failOn) error("boom"); return msgs.getValue(id) }
    }
    private fun credit(id: String) = GmailMessage(id, "Credit alert", "INR 500.00 has been credited to your A/c no. XX1234.\nUPI/P2A/61111111111$id/PRIYA SHARMA", at)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        val ing = Ingestor(db) { LocalDate.of(2026, 10, 3) }
        ing.addAllowance(10_000_00, LocalDate.of(2026, 9, 25))
        router = Router(db, ing) { }
    }
    @After fun tearDown() = db.close()

    @Test fun processesEachMessageOnce() = runBlocking {
        val api = Fake(mapOf("1" to credit("1"), "2" to credit("2")))
        assertEquals(2, GmailSync.run(api, db, router))
        assertEquals(0, GmailSync.run(api, db, router))
        assertEquals(GmailSync.QUERY, api.queries.first())
        assertTrue(db.misc().seenEmail("1"))
    }

    @Test fun oneFailingMessageDoesNotBlockOthersAndIsRetriedLater() = runBlocking {
        val api = Fake(mapOf("1" to credit("1"), "2" to credit("2")), failOn = setOf("1"))
        assertEquals(1, GmailSync.run(api, db, router))
        assertFalse(db.misc().seenEmail("1")); assertTrue(db.misc().seenEmail("2"))
    }
}
