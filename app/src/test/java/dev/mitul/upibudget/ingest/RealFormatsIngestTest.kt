package dev.mitul.upibudget.ingest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.*
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

/** Message shapes seen in a real Axis inbox (digits and names changed). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealFormatsIngestTest {
    private lateinit var db: AppDatabase
    private lateinit var router: Router
    private val today = LocalDate.of(2026, 10, 26)
    private val at = LocalDateTime.of(2026, 10, 25, 4, 5)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        val ing = Ingestor(db) { today }
        ing.addAllowance(30_000_00, LocalDate.of(2026, 10, 24))
        router = Router(db, ing) { }
    }
    @After fun tearDown() = db.close()
    private suspend fun s() = BudgetRepo(db) { today }.current()!!.summary

    @Test fun mandateNoticeIsAReservationNotASpend() = runBlocking {
        router.sms("AX-AXISBK-S", "For the upcoming mandate set for 03-11-26, INR 129.00 will be debited from your A/c towards YouTube for GOOGLE, 123456789012. To stop execution, pause mandate - Axis Bank", at)
        assertEquals(0, db.txns().all().count { it.direction == Direction.DEBIT })
        assertEquals("YOUTUBE", db.recurring().all().single().merchantNorm)
    }

    @Test fun rdInstallmentInMbbFormatCountsAsSavedAndSettlesTheReservation() = runBlocking {
        router.sms("AX-AXISBK-S", "Your RD no. X4321 is booked for INR 5000 for a tenure of 15M 0D at 6.45% - Axis Bank", at.minusDays(1))
        assertEquals(5_000_00L, s().reserved)
        router.sms("AX-AXISBK-S", "Debit INR 5000.00\nAxis Bank A/c XX1234\n25-10-26 04:05:06\nMBB-RD/123456789012/MIT\nWhatsApp BAL to 919900000000\nNot You? SMS BLOCKALL CustID to 919900000000", at)
        val x = s()
        assertEquals(5_000_00L, x.saved); assertEquals(0L, x.reserved); assertEquals(25_000_00L, x.left)
    }

    @Test fun revokedMandateStopsReservingMoney() = runBlocking {
        router.sms("AX-AXISBK-S", "For the upcoming mandate set for 30-10-26, INR 1999.00 will be debited from your A/c towards Google Play for Upi Mandate, Axis Bank", at)
        assertEquals(1_999_00L, s().reserved)
        router.sms("AX-AXISBK-S", "Your UPI mandate has been successfully revoked towards Google Play for INR 1999.00 - Axis Bank", at)
        assertEquals(0L, s().reserved)
    }

    @Test fun upiLiteTopUpIsSpendingInWalletTopUp() = runBlocking {
        router.sms("AX-AXISBK-S", "UPI LITE top-up on UPI App amounting to INR 200.00 has been successful. Ref no. 123456789012 - Axis Bank", at)
        val t = db.txns().all().first { it.payeeNorm == "UPI LITE" }
        assertEquals("Wallet Top-up", db.categories().all().first { it.id == t.subId }.name)
        assertFalse(t.needsSorting)
        assertEquals(200_00L, s().spent)
    }

    @Test fun rereadingFixesEarlierMisreadMessages() = runBlocking {
        val text = "For the upcoming mandate set for 03-11-26, INR 129.00 will be debited from your A/c towards YouTube for GOOGLE, 123456789012. To stop execution, pause mandate - Axis Bank"
        // what an older version stored for this message: an unknown debit with the identical raw text
        db.txns().insert(TxnEntity(time = at.toStore(), amount = 12_900, direction = Direction.DEBIT, payeeRaw = "", payeeNorm = "", payeeType = PayeeType.UNKNOWN,
            subId = db.categories().subIdByName("Uncategorized")!!, needsSorting = true, creditAs = null, source = Source.SMS, ref = null, rawText = text, kind = Kind.UNKNOWN, monthId = 1))
        router.sms("AX-AXISBK-S", text, at)
        assertEquals("identical text is skipped as a duplicate, so the wrong record would stay", 1, db.txns().all().count { it.kind == Kind.UNKNOWN })
        db.txns().deleteUnreadSms()
        router.sms("AX-AXISBK-S", text, at)
        assertEquals(0, db.txns().all().count { it.kind == Kind.UNKNOWN })
        assertEquals(1, db.recurring().all().size)
    }
}
