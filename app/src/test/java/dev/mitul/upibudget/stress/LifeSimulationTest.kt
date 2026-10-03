package dev.mitul.upibudget.stress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.budget.*
import dev.mitul.upibudget.core.*
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
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LifeSimulationTest {
    private lateinit var db: AppDatabase
    private lateinit var ing: Ingestor
    private lateinit var router: Router
    private var today = LocalDate.of(2026, 6, 25)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { Seeder(db).seed(File("src/main/assets/categories.json").readText()) }
        ing = Ingestor(db) { today }
        router = Router(db, ing) { }
    }
    @After fun tearDown() = db.close()

    private fun fmt(d: LocalDate) = "%02d-%02d-%02d".format(d.dayOfMonth, d.monthValue, d.year % 100)
    private fun amt(p: Long) = "${p / 100}.${(p % 100).toString().padStart(2, '0')}"

    @Test fun fourMonthsOfLifeBalanceExactly() = runBlocking {
        val r = Random(99)
        var ref = 100_000_000_000L
        val names = listOf("Blinkit", "Swiggy Ltd", "Zomato", "Delhi Metro", "RAMESH KUMAR", "Sunil  S", "Amazon Pay", "Apollo Pharmacy", "Cafe Coffee Day", "MYSTERY SHOP")
        var allowances = 0L; var paybacks = 0L; var debits = 0L
        val firstDay = today
        ing.addAllowance(30_000_00, today); allowances += 30_000_00
        var d = today
        val end = today.plusDays(125)
        while (d.isBefore(end)) {
            today = d
            val at = d.atTime(r.nextInt(8, 22), r.nextInt(0, 59))
            repeat(r.nextInt(0, 5)) {
                val a = r.nextLong(1_000, 80_000)
                router.sms("AX-AXISBK-S", "INR ${amt(a)} debited A/c no. XX1234 ${fmt(d)}, ${"%02d:%02d:%02d".format(at.hour, at.minute, r.nextInt(60))} UPI/P2M/${ref++}/ ${names.random(r)} Not you? SMS BLOCKUPI Axis Bank", at)
                debits += a
            }
            if (r.nextInt(12) == 0) { // someone pays back; the user answers right away
                val a = r.nextLong(5_000, 200_000)
                router.sms("AX-AXISBK-S", "INR ${amt(a)} credited A/c no. XX1234 ${fmt(d)}, 12:00:00 UPI/P2A/${ref++}/ FRIEND ${r.nextInt(3)} Axis Bank", at)
                val t = db.txns().all().first { it.ref == (ref - 1).toString() }
                if (t.creditAs == null) ing.answerCredit(t.id, CreditAnswer.PAYBACK) else assertEquals(CreditAnswer.PAYBACK, t.creditAs)   // remembered sender is answered by itself
                paybacks += a
            }
            if (d.dayOfMonth == 3) { router.sms("AX-AXISBK-S", "Your A/c has been debited towards NETFLIX COM for INR 199.00 on ${fmt(d)}. h${ref++}@upi - Axis Bank", at); debits += 19_900 }
            if (d.dayOfMonth == 7) { router.sms("AX-AXISBK-S", "INR 5000.00 debited towards RD no. X1234 on ${fmt(d)} - Axis Bank", at); debits += 500_000 }
            if (d.dayOfMonth == 25 && d != firstDay) {
                val a = r.nextLong(25_000_00, 35_000_00)
                router.sms("AX-AXISBK-S", "INR ${amt(a)} credited A/c no. XX1234 ${fmt(d)}, 09:00:00 UPI/P2A/${ref++}/ MOM Axis Bank", at)
                val t = db.txns().all().first { it.direction == Direction.CREDIT && it.ref == (ref - 1).toString() }
                if (t.creditAs == null) ing.answerCredit(t.id, CreditAnswer.ALLOWANCE) else assertEquals(CreditAnswer.ALLOWANCE, t.creditAs)
                allowances += a
            }
            d = d.plusDays(1)
        }
        today = end
        val cur = BudgetRepo(db) { today }.current()!!
        val s = cur.summary
        // Nothing may leak between months: all money in minus all money out equals what is left plus what is set aside.
        assertEquals("allowances+paybacks-debits must equal left+reserved", allowances + paybacks - debits, s.left + s.reserved)
        val months = db.months().all()
        println("months=${months.size} left=${Money.format(s.left)} reserved=${Money.format(s.reserved)} carry=${Money.format(s.carryOver)}")
        assertEquals(5, months.size)
        // every allowed transaction belongs to a month
        assertTrue(db.txns().all().none { it.monthId == 0L })
        // the carry chain is monotone-consistent: month k+1 carry == month k closing (without reservations)
        val ms = months.map { it.toInfo() }
        for (i in 1 until ms.size) {
            val prev = BudgetRepo(db) { today }.txsOf(ms[i - 1].id)
            assertEquals(BudgetEngine.carryOverOf(ms[i - 1], prev), ms[i].carryOver)
        }
    }

    @Test fun answeringAnOldCreditCountsInTheCurrentMonth() = runBlocking {
        ing.addAllowance(10_000_00, LocalDate.of(2026, 6, 25))
        router.sms("AX-AXISBK-S", "INR 500.00 credited A/c no. XX1234 ${fmt(LocalDate.of(2026, 6, 28))}, 10:00:00 UPI/P2A/611111111111/ FRIEND A Axis Bank", LocalDate.of(2026, 6, 28).atTime(10, 0))
        today = LocalDate.of(2026, 7, 26)
        ing.addAllowance(10_000_00, today)                 // month 2 begins; the credit above is still unanswered
        val before = BudgetRepo(db) { today }.current()!!.summary.left
        ing.answerCredit(db.txns().all().first { it.ref == "611111111111" }.id, CreditAnswer.PAYBACK)
        assertEquals(before + 500_00, BudgetRepo(db) { today }.current()!!.summary.left)
    }
}
