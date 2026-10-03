package dev.mitul.upibudget.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.data.*
import dev.mitul.upibudget.ingest.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsImporterTest {
    @Test fun importsInOrderAndDedupesRepeats() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        val ing = Ingestor(db) { LocalDate.of(2026, 10, 3) }
        val router = Router(db, ing) { }
        fun body(ref: String, payee: String, time: String) =
            "INR 100.00 debited\nA/c no. XX1234\n$time\nUPI/P2M/$ref/\n$payee\nNot you? SMS BLOCKUPI\nAxis Bank"
        val rows = listOf(
            SmsRow("AX-AXISBK-S", body("111111111111", "Blinkit", "01-10-26, 09:00:00"), LocalDateTime.of(2026, 10, 1, 9, 0)),
            SmsRow("AX-AXISBK-S", body("111111111111", "Blinkit", "01-10-26, 09:00:00"), LocalDateTime.of(2026, 10, 1, 9, 0)),
            SmsRow("AX-AXISBK-S", body("222222222222", "Zomato", "02-10-26, 09:00:00"), LocalDateTime.of(2026, 10, 2, 9, 0)),
            SmsRow("VK-OTHER", "INR 5.00 debited", LocalDateTime.of(2026, 10, 2, 9, 0)),
        )
        assertEquals(4, SmsImporter.import(rows, router))
        assertEquals(2, db.txns().all().size)
        db.close()
    }
}
