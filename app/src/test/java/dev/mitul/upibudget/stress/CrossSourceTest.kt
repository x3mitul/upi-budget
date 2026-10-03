package dev.mitul.upibudget.stress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.*
import dev.mitul.upibudget.ingest.*
import kotlinx.coroutines.flow.first
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
class CrossSourceTest {
    private lateinit var db: AppDatabase
    private lateinit var router: Router
    private val today = LocalDate.of(2026, 10, 3)
    private val at = LocalDateTime.of(2026, 10, 3, 10, 0)
    private val gpay = "com.google.android.apps.nbu.paisa.user"

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        val ing = Ingestor(db) { today }
        ing.addAllowance(10_000_00, LocalDate.of(2026, 9, 25))
        router = Router(db, ing) { }
    }
    @After fun tearDown() = db.close()

    private fun sms(amt: Int, ref: String, name: String) =
        "INR $amt.00 debited A/c no. XX1234 03-10-26, 10:00:00 UPI/P2M/$ref/ $name Not you? SMS BLOCKUPI Axis Bank"
    private suspend fun count() = db.txns().all().count { it.kind != Kind.CREDIT || it.source != Source.MANUAL }

    @Test fun twoEqualPaymentsEachSeenByTwoSourcesStayTwoInAnyOrder() = runBlocking {
        val orders = listOf(
            listOf("sA", "nA", "sB", "nB"), listOf("sA", "sB", "nA", "nB"), listOf("nA", "nB", "sA", "sB"), listOf("nA", "sA", "nB", "sB"), listOf("sB", "nA", "nB", "sA"),
        )
        for ((i, order) in orders.withIndex()) {
            db.txns().deleteAll()
            for (e in order) when (e) {
                "sA" -> router.sms("AX-AXISBK-S", sms(20, "1111$i", "Zomato"), at)
                "sB" -> router.sms("AX-AXISBK-S", sms(20, "2222$i", "Zomato"), at.plusMinutes(1))
                "nA" -> router.notification(gpay, "Google Pay", "You paid ₹20.00 to Zomato", at.plusSeconds(20))
                "nB" -> router.notification(gpay, "Google Pay", "You paid ₹20.00 to Zomato ", at.plusMinutes(1).plusSeconds(20))   // trailing space: different text
            }
            assertEquals("order $order", 2, db.txns().all().size)
        }
    }

    @Test fun sameCreditFromSmsEmailAndNotificationIsOne() = runBlocking {
        router.notification(gpay, "Google Pay", "Received ₹500 from Priya Sharma", at)
        router.sms("AX-AXISBK-S", "INR 500.00 credited A/c no. XX1234 03-10-26, 10:00:30 UPI/P2A/611111111111/ PRIYA SHARMA Axis Bank", at.plusSeconds(30))
        router.email("m1", "Credit alert", "INR 500.00 has been credited to your A/c no. XX1234.\nUPI/P2A/611111111111/PRIYA SHARMA", at.plusMinutes(2))
        val credits = db.txns().all().filter { it.direction == Direction.CREDIT && it.source != Source.MANUAL }
        assertEquals(1, credits.size)
        assertEquals(Source.SMS, credits[0].source)
    }

    @Test fun repeatedIdenticalNotificationUpdatesAreOnePayment() = runBlocking {
        repeat(5) { router.notification(gpay, "Google Pay", "You paid ₹99 to Blinkit", at.plusSeconds(it.toLong())) }
        assertEquals(1, db.txns().all().count { it.direction == Direction.DEBIT })
    }

    @Test fun aBrokenDatabaseNeverCrashesTheRouter() = runBlocking {
        db.categories().delete(db.categories().subIdByName("Uncategorized")!!)   // sabotage: ingest needs this row
        router.sms("AX-AXISBK-S", sms(10, "999999999999", "Blinkit"), at)
        assertTrue(db.misc().observeLogs().first().any { it.tag == "error" })
    }
}
