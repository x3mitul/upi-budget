package dev.mitul.upibudget.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.core.*
import dev.mitul.upibudget.ingest.*
import dev.mitul.upibudget.parse.SmsParser
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
class BackupTest {
    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val today = LocalDate.of(2026, 10, 3)

    @Test fun exportThenImportIntoEmptyDbGivesSameBudget() = runBlocking {
        val a = newDb()
        Seeder(a).seed(File("src/main/assets/categories.json").readText())
        val ing = Ingestor(a) { today }
        ing.addAllowance(10_000_00, LocalDate.of(2026, 9, 25))
        val at = LocalDateTime.of(2026, 10, 2, 9, 0)
        val body = "INR 120.00 debited\nA/c no. XX1234\n02-10-26, 09:00:00\nUPI/P2M/111111111111/\nRAMESH ZXQ\nAxis Bank"
        val o = ing.handle(SmsParser.parse("AX-AXISBK-S", body, at), Source.SMS, body, at) as Outcome.Debit
        ing.teach(o.txnId, a.categories().subIdByName("Kirana & General Store")!!)
        ing.handle(SmsParser.parse("AX-AXISBK-S", "For the upcoming mandate set for 30-10-26, INR 199.00 will be debited from your A/c towards NETFLIX COM for Upi Mandate", at),
            Source.SMS, "n", at)
        val json = Backup.export(a)

        val b = newDb()
        Backup.import(b, json)
        assertEquals(a.txns().all(), b.txns().all())
        assertEquals(a.categories().all(), b.categories().all())
        assertEquals(a.rules().allNameRules(), b.rules().allNameRules())
        assertEquals(a.recurring().all(), b.recurring().all())
        assertEquals(a.months().all(), b.months().all())
        assertEquals(BudgetRepo(a) { today }.current()!!.summary, BudgetRepo(b) { today }.current()!!.summary)
        a.close(); b.close()
    }

    @Test fun importReplacesExistingDataAndRejectsGarbage() = runBlocking {
        val a = newDb(); Seeder(a).seed(File("src/main/assets/categories.json").readText())
        val json = Backup.export(a)
        val b = newDb()
        Seeder(b).seed(File("src/main/assets/categories.json").readText())
        b.rules().upsertNameRule(NameRuleEntity("OLD", 1))
        Backup.import(b, json)
        assertTrue(b.rules().allNameRules().isEmpty())
        try { Backup.import(b, """{"hello":1}"""); fail("expected rejection") } catch (e: IllegalArgumentException) { }
        assertEquals(a.categories().count(), b.categories().count())     // unchanged by the rejected import
        a.close(); b.close()
    }

    @Test fun debugLogIsNotExported() = runBlocking {
        val a = newDb()
        a.misc().addLog(DebugLogEntity(time = 0, tag = "x", text = "secret raw sms"))
        assertFalse(Backup.export(a).contains("secret raw sms"))
        a.close()
    }
}
