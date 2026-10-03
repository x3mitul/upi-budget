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
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AllowanceGuessTest {
    private fun credit(rs: Long, day: Int, answer: CreditAnswer? = null, dir: Direction = Direction.CREDIT, src: Source = Source.SMS) = TxnEntity(
        time = LocalDateTime.of(2026, 9, day, 10, 0).toStore(), amount = rs * 100, direction = dir, payeeRaw = "P", payeeNorm = "P",
        payeeType = PayeeType.P2A, subId = 1, needsSorting = answer == null, creditAs = answer, source = src, ref = null, rawText = "$day$rs", kind = Kind.CREDIT, monthId = 0)

    @Test fun picksBiggestUnansweredCreditAndIgnoresPaybacksDebitsAndManual() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        db.txns().insert(credit(500, 3)); db.txns().insert(credit(8_000, 5)); db.txns().insert(credit(9_000, 6, CreditAnswer.PAYBACK))
        db.txns().insert(credit(20_000, 7, dir = Direction.DEBIT)); db.txns().insert(credit(30_000, 8, src = Source.MANUAL))
        assertEquals(8_000_00L to LocalDate.of(2026, 9, 5), AllowanceGuess.suggest(db))
        db.close()
    }

    @Test fun nullWhenNoCandidate() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        assertNull(AllowanceGuess.suggest(db)); db.close()
    }
}
