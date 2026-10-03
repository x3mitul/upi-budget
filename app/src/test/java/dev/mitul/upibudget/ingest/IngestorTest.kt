package dev.mitul.upibudget.ingest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.data.*
import dev.mitul.upibudget.parse.ParsedMessage
import dev.mitul.upibudget.parse.SmsParser
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
class IngestorTest {
    private lateinit var db: AppDatabase
    private lateinit var ing: Ingestor
    private var today = LocalDate.of(2026, 10, 3)
    private val now get() = today.atTime(10, 0)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        ing = Ingestor(db) { today }
        ing.addAllowance(10_000_00, LocalDate.of(2026, 9, 25))
    }
    @After fun tearDown() = db.close()

    private fun upi(amt: String, ref: String, payee: String, acct: String = "XX1234", type: String = "P2M", date: String = "03-10-26, 10:00:00") =
        "INR $amt debited\nA/c no. $acct\n$date\nUPI/$type/$ref/\n$payee\nNot you? SMS BLOCKUPI\nCust ID to 919900000000\nAxis Bank"
    private fun credit(amt: String, ref: String, payee: String) =
        "INR $amt credited\nA/c no. XX1234\n03-10-26, 10:00:00\nUPI/P2A/$ref/\n$payee\nAxis Bank"

    private suspend fun sms(body: String, at: LocalDateTime = now) =
        ing.handle(SmsParser.parse("AX-AXISBK-S", body, at), Source.SMS, body, at)
    private suspend fun sub(name: String) = db.categories().subIdByName(name)!!
    private suspend fun budget() = BudgetRepo(db) { today }.current()!!.summary

    @Test fun knownBrandIsCategorizedSilently() = runBlocking {
        val o = sms(upi("438.00", "111111111111", "Blinkit")) as Outcome.Debit
        assertFalse(o.asked)
        assertEquals("Quick Commerce", o.subName)
        assertEquals(10_000_00L - 438_00L, budget().left)
        assertEquals(10_000_00L - 438_00L, o.summary!!.left)
    }

    @Test fun unknownPayeeIsAskedOnceThenRemembered() = runBlocking {
        val first = sms(upi("120.00", "111111111111", "RAMESH ZXQ")) as Outcome.Debit
        assertTrue(first.asked)
        ing.teach(first.txnId, sub("Kirana & General Store"))
        val second = sms(upi("80.00", "222222222222", "Ramesh  Zxq")) as Outcome.Debit   // different case/spacing
        assertFalse(second.asked)
        assertEquals("Kirana & General Store", second.subName)
    }

    @Test fun teachAlsoFixesOtherPendingPaymentsToSameName() = runBlocking {
        val a = sms(upi("10.00", "111111111111", "QWERTY ABC")) as Outcome.Debit
        sms(upi("11.00", "222222222222", "QWERTY ABC"), now.plusMinutes(30))
        ing.teach(a.txnId, sub("Food Delivery"))
        assertTrue(db.txns().all().none { it.needsSorting })
    }

    @Test fun personWithNoMatchGuessesFriendsFirst() = runBlocking {
        val o = sms(upi("50.00", "111111111111", "ZZQ PERSON", type = "P2A")) as Outcome.Debit
        assertEquals(sub("Friends & Family"), o.guesses.first())
    }

    @Test fun sameSmsTwiceCountsOnce() = runBlocking {
        val body = upi("438.00", "111111111111", "Blinkit")
        sms(body); assertEquals(Outcome.Skipped, sms(body))
        assertEquals(2, db.txns().all().size)   // allowance + 1 payment
    }

    @Test fun notificationThenSmsMergesAndSmsWins() = runBlocking {
        val n = ParsedMessage.Payment(43_800, Direction.DEBIT, null, null, "Blinkit", Kind.UPI, PayeeType.P2M, null)
        ing.handle(n, Source.NOTIFICATION, "Paid ₹438 to Blinkit", now.minusMinutes(1))
        assertEquals(Outcome.Skipped, sms(upi("438.00", "111111111111", "Blinkit")))
        val t = db.txns().all().first { it.kind == Kind.UPI }
        assertEquals(Source.SMS, t.source); assertEquals("111111111111", t.ref); assertTrue(t.merged)
    }

    @Test fun twoGenuinePaymentsOfSameAmountStayTwo() = runBlocking {
        sms(upi("20.00", "111111111111", "Blinkit"), now)
        sms(upi("20.00", "222222222222", "Blinkit", date = "03-10-26, 10:01:00"), now.plusMinutes(1))
        assertEquals(2, db.txns().all().count { it.kind == Kind.UPI })
    }

    @Test fun otherAccountIsIgnoredAfterFirstIsLearned() = runBlocking {
        sms(upi("10.00", "111111111111", "Blinkit"))
        assertEquals(Outcome.Skipped, sms(upi("10.00", "222222222222", "Blinkit", acct = "XX9999")))
        assertEquals("XX1234", db.misc().setting("account"))
    }

    @Test fun nonTransactionAndOtherSenderAreSkipped() = runBlocking {
        assertEquals(Outcome.Skipped, sms("123456 is your OTP for login. - Axis Bank"))
        assertEquals(1, db.txns().all().size)   // only the allowance
    }

    @Test fun unparseableMoneySmsBecomesUnknownAndAsked() = runBlocking {
        val o = sms("INR 750.00 debited from A/c XX1234 towards some new thing - Axis Bank") as Outcome.Debit
        assertTrue(o.asked)
        assertTrue(db.txns().all().first { it.kind == Kind.UNKNOWN }.needsSorting)
    }

    @Test fun creditAskedThenRememberedPerSender() = runBlocking {
        val first = sms(credit("500.00", "611111111111", "PRIYA SHARMA")) as Outcome.Credit
        assertNull(first.answered)
        ing.answerCredit(first.txnId, CreditAnswer.PAYBACK)
        val second = sms(credit("300.00", "622222222222", "Priya  Sharma"), now.plusHours(1)) as Outcome.Credit
        assertEquals(CreditAnswer.PAYBACK, second.answered)
        assertEquals(10_000_00L + 500_00L + 300_00L, budget().left)
    }

    @Test fun merchantCreditIsARefund() = runBlocking {
        val c = sms(credit("200.00", "611111111111", "Swiggy Ltd")) as Outcome.Credit
        assertEquals(CreditAnswer.REFUND, c.answered)
    }

    @Test fun ignoredCreditCountsForNothing() = runBlocking {
        val c = sms(credit("900.00", "611111111111", "MY OTHER BANK")) as Outcome.Credit
        ing.answerCredit(c.txnId, CreditAnswer.IGNORE)
        assertEquals(10_000_00L, budget().left)
    }

    @Test fun secondAllowanceWithinTwentyDaysJoinsCurrentMonth() = runBlocking {
        val c = sms(credit("5,000.00", "611111111111", "MOM")) as Outcome.Credit
        ing.answerCredit(c.txnId, CreditAnswer.ALLOWANCE)
        assertEquals(1, db.months().all().size)
        assertEquals(15_000_00L, budget().income)
    }

    @Test fun allowanceAfterTwentyDaysStartsNewMonthWithCarryOver() = runBlocking {
        sms(upi("2,000.00", "111111111111", "Blinkit"))                      // month 1: 10000 - 2000 = 8000 left
        today = LocalDate.of(2026, 10, 20)
        val c = ing.handle(SmsParser.parse("AX-AXISBK-S", credit("5,000.00", "611111111111", "MOM").replace("03-10-26", "20-10-26"), now),
            Source.SMS, "c", now) as Outcome.Credit
        ing.answerCredit(c.txnId, CreditAnswer.ALLOWANCE)
        assertEquals(2, db.months().all().size)
        val s = budget()
        assertEquals(8_000_00L, s.carryOver)
        assertEquals(13_000_00L, s.left)
    }

    @Test fun overspendingCarriesOverNegative() = runBlocking {
        sms(upi("12,000.00", "111111111111", "Blinkit"))                     // -2000 left
        today = LocalDate.of(2026, 10, 20)
        val c = ing.handle(SmsParser.parse("AX-AXISBK-S", credit("5,000.00", "611111111111", "MOM").replace("03-10-26", "20-10-26"), now),
            Source.SMS, "c2", now) as Outcome.Credit
        ing.answerCredit(c.txnId, CreditAnswer.ALLOWANCE)
        assertEquals(-2_000_00L, budget().carryOver)
        assertEquals(3_000_00L, budget().left)
    }

    @Test fun autopayDebitLearnsRecurringAndNoticeReservesNextOne() = runBlocking {
        sms("For the upcoming mandate set for 30-09-26, INR 199.00 will be debited from your A/c towards NETFLIX COM for Upi Mandate, Axis Bank")
        assertEquals(199_00L, budget().reserved)
        sms("Your A/c has been debited towards NETFLIX COM for INR 199.00 on 30-09-26. abc@upi - Axis Bank", LocalDateTime.of(2026, 9, 30, 4, 0))
        assertEquals(0L, budget().reserved)
        assertEquals(10_000_00L - 199_00L, budget().left)
        assertEquals(1, db.recurring().all().size)
    }

    @Test fun unknownAutopayCreatesMonthlyRecurring() = runBlocking {
        sms("Your A/c has been debited towards Some Gym for INR 999.00 on 28-09-26. abc@upi - Axis Bank", LocalDateTime.of(2026, 9, 28, 4, 0))
        val r = db.recurring().all().single()
        assertEquals(Frequency.MONTHLY, r.frequency); assertEquals(28, r.dueDay); assertEquals(RecurringKind.SUBSCRIPTION, r.kind)
    }

    @Test fun mandateCreatedAsksFrequencyAndYearlyReservesOnlyWhenDue() = runBlocking {
        val o = sms("Your UPI ASPRESENTED mandate has been successfully created towards Google Play from 05-10-26 to 31-12-36 for INR 1999.00 - Axis Bank") as Outcome.AskFrequency
        assertEquals(1999_00L, o.amount)
        ing.setFrequency(o.recurringId, Frequency.YEARLY)
        assertEquals(1999_00L, budget().reserved)     // due 5 Oct, inside this month's window
        ing.setFrequency(o.recurringId, Frequency.MONTHLY)
        assertEquals(1999_00L, budget().reserved)
    }

    @Test fun stoppedRecurringReservesNothing() = runBlocking {
        sms("For the upcoming mandate set for 30-09-26, INR 199.00 will be debited from your A/c towards NETFLIX COM for Upi Mandate")
        ing.stopRecurring(db.recurring().all().single().id)
        assertEquals(0L, budget().reserved)
    }

    @Test fun rdBookedReservesThenRealInstallmentCountsAsSaved() = runBlocking {
        sms("Your RD no. X1234 is booked for INR 5000 for a tenure of 15M 0D at 6.45%. Nomination Registered: Y, Auto Renewal: N - Axis Bank")
        assertEquals(5_000_00L, budget().reserved)
        sms("INR 5000.00 debited towards RD no. X1234 on 03-10-26 - Axis Bank")
        val s = budget()
        assertEquals(5_000_00L, s.saved); assertEquals(0L, s.reserved); assertEquals(5_000_00L, s.left)
    }

    @Test fun missedChargeIsReportedOnceThenReleased() = runBlocking {
        sms("For the upcoming mandate set for 30-09-26, INR 199.00 will be debited from your A/c towards NETFLIX COM for Upi Mandate")
        today = LocalDate.of(2026, 10, 8)
        val repo = BudgetRepo(db) { today }
        assertEquals(1, repo.missedCharges().size)
        assertEquals(0, repo.missedCharges().size)
        assertEquals(0L, repo.current()!!.summary.reserved)
    }

    @Test fun txnsBeforeFirstAllowanceAreAssignedByReassign() = runBlocking {
        db.txns().deleteAll(); db.months().deleteAll()
        sms(upi("100.00", "111111111111", "Blinkit"), LocalDateTime.of(2026, 9, 27, 10, 0))
        assertEquals(0L, db.txns().all().single().monthId)
        ing.addAllowance(5_000_00, LocalDate.of(2026, 9, 25))
        ing.reassignMonths()
        assertNotEquals(0L, db.txns().all().first { it.kind == Kind.UPI }.monthId)
        assertEquals(5_000_00L - 100_00L, budget().left)
    }
}
