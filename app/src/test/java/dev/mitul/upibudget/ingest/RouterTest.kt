package dev.mitul.upibudget.ingest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.data.*
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
class RouterTest {
    private lateinit var db: AppDatabase
    private lateinit var router: Router
    private val outcomes = ArrayList<Outcome>()
    private val at = LocalDateTime.of(2026, 10, 3, 10, 0)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        val ing = Ingestor(db) { LocalDate.of(2026, 10, 3) }
        ing.addAllowance(10_000_00, LocalDate.of(2026, 9, 25))
        router = Router(db, ing) { outcomes += it }
    }
    @After fun tearDown() = db.close()

    private val upi = "INR 438.00 debited\nA/c no. XX1234\n03-10-26, 09:32:24\nUPI/P2M/662241432378/\nBlinkit\nNot you? SMS BLOCKUPI\nAxis Bank"
    private suspend fun logs() = db.misc().observeLogs().first()

    @Test fun smsPaymentIsIngestedAndNotified() = runBlocking {
        router.sms("AX-AXISBK-S", upi, at)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes[0] is Outcome.Debit)
        assertTrue(logs().isEmpty())
    }

    @Test fun duplicateSmsDoesNotNotifyTwice() = runBlocking {
        router.sms("AX-AXISBK-S", upi, at); router.sms("AX-AXISBK-S", upi, at)
        assertEquals(1, outcomes.size)
    }

    @Test fun otpIsSilentAndUnlogged() = runBlocking {
        router.sms("AX-AXISBK-S", "123456 is your OTP. - Axis Bank", at)
        assertTrue(outcomes.isEmpty()); assertTrue(logs().isEmpty())
    }

    @Test fun ignoredButMoneyLookingSmsIsLogged() = runBlocking {
        router.sms("AX-AXISBK-S", "Spent INR 500.00 on Axis Bank Credit Card no. XX9999 at AMAZON", at)
        assertTrue(outcomes.isEmpty())
        assertEquals("sms-ignored", logs().single().tag)
    }

    @Test fun unknownShapeSmsIsLoggedAndAsked() = runBlocking {
        router.sms("AX-AXISBK-S", "INR 750.00 debited from A/c XX1234 towards some new thing", at)
        assertEquals("sms-unknown", logs().single().tag)
        assertTrue((outcomes.single() as Outcome.Debit).asked)
    }

    @Test fun watchedNotificationIsAlwaysLoggedAndParsedWhenPossible() = runBlocking {
        router.notification("com.google.android.apps.nbu.paisa.user", "Google Pay", "You paid ₹250 to Swiggy", at)
        router.notification("com.google.android.apps.nbu.paisa.user", "Google Pay", "Weird new format 123", at)
        router.notification("com.whatsapp", "Chat", "hello", at)
        assertEquals(1, outcomes.size)
        val l = logs()
        assertEquals(2, l.size)
        assertTrue(l.any { it.tag == "notif:com.google.android.apps.nbu.paisa.user" && it.text.startsWith("PARSED") })
        assertTrue(l.any { it.text.startsWith("IGNORED") })
    }

    @Test fun emailIsLoggedAndIngested() = runBlocking {
        router.email("m1", "Credit alert", "INR 500.00 has been credited to your A/c no. XX1234.\nUPI/P2A/611111111111/PRIYA SHARMA", at)
        assertTrue(outcomes.single() is Outcome.Credit)
        assertEquals("email", logs().single().tag)
    }

    @Test fun theSameUnreadableSmsIsLoggedOnlyOnce() = runBlocking {
        repeat(5) { router.sms("AX-AXISBK-S", "Spent INR 500.00 on Axis Bank Credit Card no. XX9999 at AMAZON", at) }   // catch-up re-reads the inbox every few minutes
        assertEquals(1, logs().size)
    }

    @Test fun superMoneyIsWatched() = runBlocking {
        router.notification("money.super.payments", "super.money", "You paid ₹30 to Zomato", at)
        assertTrue(outcomes.single() is Outcome.Debit)
    }

    @Test fun moneyLookingNotificationFromAnUnknownAppIsLoggedNotIngested() = runBlocking {
        router.notification("com.some.newupi", "NewPay", "You paid ₹30 to Zomato", at)
        router.notification("com.whatsapp", "Chat", "see you at 5", at)
        assertTrue(outcomes.isEmpty())
        assertEquals("notif-other:com.some.newupi", logs().single().tag)
    }
}
