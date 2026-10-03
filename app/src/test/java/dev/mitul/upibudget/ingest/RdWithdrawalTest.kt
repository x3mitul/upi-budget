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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RdWithdrawalTest {
    private lateinit var db: AppDatabase
    private lateinit var router: Router
    private val today = LocalDate.of(2026, 9, 26)
    private val at = LocalDateTime.of(2026, 9, 25, 11, 0)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        val ing = Ingestor(db) { today }
        ing.addAllowance(30_000_00, LocalDate.of(2026, 9, 25))
        router = Router(db, ing) { }
        router.sms("AX-AXISBK-S", "Your RD no. X4321 is booked for INR 5000 for a tenure of 15M 0D at 6.45%. Nomination Registered: Y - Axis Bank", at.minusDays(40))
    }
    @After fun tearDown() = db.close()
    private suspend fun left() = BudgetRepo(db) { today }.current()!!.summary

    @Test fun breakingAnRdAndStartingANewOneNetsTenThousand() = runBlocking {
        router.sms("AX-AXISBK-S", "INR 15000.00 credited to A/c XX1234 on 25-09-26 towards RD no. X4321 closure - Axis Bank", at)
        router.sms("AX-AXISBK-S", "Your RD no. X9999 is booked for INR 5000 for a tenure of 12M 0D at 6.45% - Axis Bank", at.plusMinutes(1))
        router.sms("AX-AXISBK-S", "INR 5000.00 debited towards RD no. X9999 on 25-09-26 - Axis Bank", at.plusMinutes(2))
        val s = left()
        assertEquals("allowance 30,000 + 15,000 back from the old RD - 5,000 into the new RD", 30_000_00L + 10_000_00L, s.left)
        assertEquals(5_000_00L, s.saved)
        assertEquals("closed RD must stop reserving money", 0L, s.reserved)
        val credit = db.txns().all().first { it.direction == Direction.CREDIT && it.source != Source.MANUAL }
        assertEquals(CreditAnswer.SAVINGS, credit.creditAs)
        assertFalse(db.recurring().all().first { it.merchantNorm == "RD X4321" }.active)
        assertTrue(db.recurring().all().first { it.merchantNorm == "RD X9999" }.active)
    }

    @Test fun savingsAnswerCanAlsoBeChosenByHand() = runBlocking {
        router.sms("AX-AXISBK-S", "INR 15000.00 credited UPI/P2A from SOMEONE", at)   // unknown shape: asked
        val t = db.txns().all().first { it.direction == Direction.CREDIT && it.source != Source.MANUAL }
        val before = left().left
        Ingestor(db) { today }.answerCredit(t.id, CreditAnswer.SAVINGS)
        assertEquals(before + 15_000_00, left().left)
    }
}
