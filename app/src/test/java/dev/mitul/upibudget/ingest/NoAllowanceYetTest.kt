package dev.mitul.upibudget.ingest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoAllowanceYetTest {
    @Test fun appWorksWithoutAnAllowanceAndStartsTheMonthWhenMoneyArrives() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        var today = LocalDate.of(2026, 10, 3)
        val ing = Ingestor(db) { today }
        val router = Router(db, ing) { }
        val repo = BudgetRepo(db) { today }

        assertNull("no month, no budget, and no crash", repo.current())
        router.sms("AX-AXISBK-S", "INR 100.00 debited A/c no. XX1234 03-10-26, 09:00:00 UPI/P2M/660000000001/ Blinkit Not you? SMS BLOCKUPI Axis Bank", LocalDateTime.of(2026, 10, 3, 9, 0))
        assertEquals(1, db.txns().all().size)                       // recorded, just not counted yet

        today = LocalDate.of(2026, 10, 5)
        router.sms("AX-AXISBK-S", "INR 20,000.00 credited A/c no. XX1234 05-10-26, 10:00:00 UPI/P2A/660000000002/ MOM Axis Bank", LocalDateTime.of(2026, 10, 5, 10, 0))
        val credit = db.txns().all().first { it.direction == Direction.CREDIT }
        assertNull(credit.creditAs)                                  // asked, not guessed
        ing.answerCredit(credit.id, CreditAnswer.ALLOWANCE)

        val s = repo.current()!!.summary
        assertEquals(20_000_00L, s.income)
        assertEquals(0L, s.spent)                                    // the Oct 3 payment predates the month
        router.sms("AX-AXISBK-S", "INR 50.00 debited A/c no. XX1234 05-10-26, 11:00:00 UPI/P2M/660000000003/ Zomato Not you? SMS BLOCKUPI Axis Bank", LocalDateTime.of(2026, 10, 5, 11, 0))
        assertEquals(20_000_00L - 50_00L, repo.current()!!.summary.left)
        db.close()
    }
}
