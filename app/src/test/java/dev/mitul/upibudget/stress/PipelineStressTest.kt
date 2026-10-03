package dev.mitul.upibudget.stress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.budget.*
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.*
import dev.mitul.upibudget.ingest.*
import dev.mitul.upibudget.sms.SmsImporter
import dev.mitul.upibudget.sms.SmsRow
import kotlinx.coroutines.*
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
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PipelineStressTest {
    private lateinit var db: AppDatabase
    private lateinit var ing: Ingestor
    private lateinit var router: Router
    private val today = LocalDate.of(2026, 10, 3)
    private val notified = java.util.concurrent.atomic.AtomicInteger()

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        ing = Ingestor(db) { today }
        ing.addAllowance(1_00_000_00, LocalDate.of(2026, 9, 25))
        router = Router(db, ing) { notified.incrementAndGet() }
    }
    @After fun tearDown() = db.close()

    private fun upi(amt: Long, ref: String, payee: String, type: String = "P2M", acct: String = "XX1234", date: LocalDate = today, time: String = "10:00:00") =
        "INR ${amt / 100}.${(amt % 100).toString().padStart(2, '0')} debited A/c no. $acct ${"%02d-%02d-%02d".format(date.dayOfMonth, date.monthValue, date.year % 100)}, $time UPI/$type/$ref/ $payee Not you? SMS BLOCKUPI Axis Bank"
    private fun cred(amt: Long, ref: String, payee: String, date: LocalDate = today) =
        "INR ${amt / 100}.${(amt % 100).toString().padStart(2, '0')} credited A/c no. XX1234 ${"%02d-%02d-%02d".format(date.dayOfMonth, date.monthValue, date.year % 100)}, 10:00:00 UPI/P2A/$ref/ $payee Axis Bank"

    private val brands = listOf("Blinkit", "Swiggy Ltd", "Zomato", "Uber India", "Delhi Metro", "Amazon Pay", "Netflix", "BigBasket", "Croma", "Decathlon", "Haldirams", "Apollo Pharmacy")
    private val people = listOf("RAMESH KUMAR", "Sunil  Surya", "priya sharma", "A", "ZZ QQ", "रमेश", "O'Brien & Sons", "X  Y  Z", "12345", "Mr. K. Das")

    @Test fun randomEventsKeepTheBooksBalanced() = runBlocking {
        val r = Random(7)
        var expectedDebits = 0L
        val sent = ArrayList<String>()
        var refCounter = 600_000_000_000L
        val unique = HashSet<String>()
        repeat(1500) { i ->
            val time = today.atTime(r.nextInt(0, 23), r.nextInt(0, 59), r.nextInt(0, 59))
            val body: String? = when (r.nextInt(12)) {
                0, 1, 2 -> { val a = r.nextLong(100, 500_000); val b = upi(a, (refCounter++).toString(), brands.random(r), date = today.minusDays(r.nextLong(0, 8)), time = "%02d:%02d:%02d".format(time.hour, time.minute, time.second)); expectedDebits += a; b }
                3, 4 -> { val a = r.nextLong(100, 500_000); val b = upi(a, (refCounter++).toString(), people.random(r), if (r.nextBoolean()) "P2A" else "P2M", date = today.minusDays(r.nextLong(0, 8)), time = "%02d:%02d:%02d".format(time.hour, time.minute, time.second)); expectedDebits += a; b }
                5 -> if (sent.isNotEmpty()) sent.random(r) else null                                       // exact resend
                6 -> cred(r.nextLong(100, 1_000_000), (refCounter++).toString(), (brands + people).random(r))
                7 -> "For the upcoming mandate set for 30-10-26, INR ${r.nextInt(50, 2000)}.00 will be debited from your A/c towards ${listOf("NETFLIX COM", "SPOTIFY", "GOOGLE PLAY").random(r)} for Upi Mandate"
                8 -> "INR ${r.nextInt(1, 999)}.00 debited from A/c XX1234 mystery ${r.nextInt()}".also { /* unknown shape: debit counted */ expectedDebits += Money.parse("${it.substringAfter("INR ").substringBefore(" debited")}") }
                9 -> upi(r.nextLong(100, 9999), (refCounter++).toString(), brands.random(r), acct = "XX9999")                 // other account: ignored
                10 -> "${r.nextInt(100000, 999999)} is your OTP. Do not share."
                else -> null
            }
            if (body != null) { val isNew = unique.add(body); if (!isNew && body.contains("debited")) { /* resend of a debit: must not count again */ } ; router.sms("AX-AXISBK-S", body, time); if (isNew) sent += body }
            if (i % 7 == 0) db.txns().all().filter { it.needsSorting }.randomOrNull(r)?.let {
                if (it.direction == Direction.CREDIT) ing.answerCredit(it.id, CreditAnswer.values().random(r))
                else ing.teach(it.id, db.categories().all().filter { c -> c.parentId != null }.random(r).id)
            }
        }
        val all = db.txns().all()
        val debits = all.filter { it.direction == Direction.DEBIT }
        assertEquals("debit total must equal sum of unique debits sent", expectedDebits, debits.sumOf { it.amount })
        assertEquals("no duplicate refs", debits.mapNotNull { it.ref }.size, debits.mapNotNull { it.ref }.toSet().size)
        assertTrue(all.none { it.amount <= 0 })
        // independent recomputation of the headline number
        val cur = BudgetRepo(db) { today }.current()!!
        val cats = db.categories().all().associateBy { it.id }
        val month = all.filter { it.monthId == cur.month.id.toLong() }
        val spent = month.filter { it.direction == Direction.DEBIT && cats[it.subId]?.isSavings != true }.sumOf { it.amount }
        val saved = month.filter { it.direction == Direction.DEBIT && cats[it.subId]?.isSavings == true }.sumOf { it.amount }
        val income = cur.month.carryOver + month.filter { it.direction == Direction.CREDIT && it.creditAs == CreditAnswer.ALLOWANCE }.sumOf { it.amount } +
            month.filter { it.direction == Direction.CREDIT && (it.creditAs == CreditAnswer.PAYBACK || it.creditAs == CreditAnswer.REFUND || it.creditAs == CreditAnswer.SAVINGS) }.sumOf { it.amount }
        assertEquals(spent, cur.summary.spent); assertEquals(saved, cur.summary.saved); assertEquals(income, cur.summary.income)
        assertEquals(income - spent - saved - cur.summary.reserved, cur.summary.left)
        assertTrue(cur.summary.reserved >= 0 && cur.summary.daysRemaining in 1..30)
    }

    @Test fun parallelIngestionIsSafeAndExact() = runBlocking {
        val bodies = (0 until 300).map { upi(10_000L + it, (700_000_000_000L + it).toString(), brands[it % brands.size], time = "10:%02d:%02d".format(it / 60 % 60, it % 60)) }
        coroutineScope {
            (bodies + bodies + bodies).shuffled(Random(9)).map { b -> async(Dispatchers.Default) { router.sms("AX-AXISBK-S", b, today.atTime(10, 0)) } }.awaitAll()
        }
        val debits = db.txns().all().filter { it.direction == Direction.DEBIT }
        assertEquals(300, debits.size)
        assertEquals((0 until 300).sumOf { 10_000L + it }, debits.sumOf { it.amount })
        assertEquals(300, notified.get())
    }

    @Test fun fiveThousandMessageImportIsFastEnough() = runBlocking {
        val rows = (0 until 5000).map { i ->
            val d = today.minusDays((i % 55).toLong())
            SmsRow("AX-AXISBK-S", upi(5_000L + i, (800_000_000_000L + i).toString(), (brands + people)[i % 22], date = d, time = "%02d:%02d:%02d".format(i % 24, i % 60, (i * 7) % 60)), d.atTime(10, 0))
        }
        val t0 = System.nanoTime()
        SmsImporter.import(rows, Router(db, ing) { })
        val secs = (System.nanoTime() - t0) / 1e9
        println("import of 5000 messages took %.1fs".format(secs))
        assertEquals(5000, db.txns().all().count { it.direction == Direction.DEBIT })
        assertTrue("import too slow: $secs s", secs < 120)
    }

    @Test fun manyAllowancesInAnyOrderAreNeverLost() = runBlocking {
        val r = Random(11)
        val dates = (0 until 40).map { LocalDate.of(2026, 1, 1).plusDays(r.nextLong(0, 270)) }
        var total = 0L
        for (d in dates) { val a = r.nextLong(1_000, 10_000_000); total += a; ing.addAllowance(a, d) }
        val months = db.months().all()
        val counted = months.sumOf { m -> BudgetRepo(db) { today }.txsOf(m.id).filter { it.creditAs == CreditAnswer.ALLOWANCE }.sumOf { it.amount } }
        // the setUp allowance (1,00,000) is part of the total too
        assertEquals("every allowance must land in some month", total + 1_00_000_00L, counted)
    }

    @Test fun oldCreditAnsweredAsAllowanceAfterwardsStillCounts() = runBlocking {
        val body = cred(500_000, "611111111111", "MOM", date = LocalDate.of(2026, 9, 5))
        router.sms("AX-AXISBK-S", body, LocalDateTime.of(2026, 9, 5, 10, 0))
        val t = db.txns().all().first { it.ref == "611111111111" }
        ing.answerCredit(t.id, CreditAnswer.ALLOWANCE)
        assertEquals(1_00_000_00L + 500_000L, BudgetRepo(db) { today }.current()!!.summary.allowance)
    }
}
