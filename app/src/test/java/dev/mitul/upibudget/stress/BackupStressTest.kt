package dev.mitul.upibudget.stress

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupStressTest {
    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val json = File("src/main/assets/categories.json").readText()

    @Test fun tenThousandTxnsWithAwkwardTextRoundTrip() = runBlocking {
        val a = newDb(); Seeder(a).seed(json)
        val nasty = listOf("O'Brien \"Sons\"", "line1\nline2", "tab\there", "emoji 😀 रमेश", "back\\slash", "{\"json\":true}", "'; DROP TABLE txn; --", "", "  ", "%_[]")
        repeat(10_000) { i ->
            a.txns().insert(TxnEntity(time = 1_700_000_000L + i, amount = i + 1L, direction = if (i % 4 == 0) Direction.CREDIT else Direction.DEBIT,
                payeeRaw = nasty[i % nasty.size], payeeNorm = Normalizer.name(nasty[i % nasty.size]), payeeType = PayeeType.values()[i % 3], subId = 1L + i % 50,
                needsSorting = i % 3 == 0, creditAs = if (i % 4 == 0) CreditAnswer.values()[i % 4] else null, source = Source.values()[i % 4],
                ref = if (i % 5 == 0) null else "R$i", rawText = nasty[(i + 3) % nasty.size] + i, kind = Kind.values()[i % 5], monthId = 1L + i % 3, merged = i % 7 == 0))
            a.rules().upsertNameRule(NameRuleEntity("NAME $i", 1L + i % 50))
        }
        val t0 = System.nanoTime()
        val exported = Backup.export(a)
        val b = newDb()
        Backup.import(b, exported)
        println("backup of 10k txns: ${exported.length / 1024} KB, %.1fs".format((System.nanoTime() - t0) / 1e9))
        assertEquals(a.txns().all(), b.txns().all())
        assertEquals(a.rules().allNameRules().sortedBy { it.payeeNorm }, b.rules().allNameRules().sortedBy { it.payeeNorm })
        // a second export of the restored DB must be byte-identical: nothing drifts across restores
        assertEquals(exported.replace(Regex("\\{\"key\":\"seed_hash\",\"value\":\"-?\\d+\"\\}"), ""), Backup.export(b))  // seed_hash is deliberately not restored
        a.close(); b.close()
    }

    @Test fun corruptedOrTruncatedBackupsAreRejectedWithoutTouchingData() = runBlocking {
        val a = newDb(); Seeder(a).seed(json)
        val good = Backup.export(a)
        val before = a.categories().count()
        val broken = listOf("", "null", "[]", good.take(good.length / 2), good.replace("\"tables\"", "\"tabl\""), good.replace("\"app\":\"upibudget\"", "\"app\":\"other\""),
            """{"app":"upibudget","version":1,"tables":{"txn":[{"nonexistent_column":1}]}}""")
        for (b in broken) {
            try { Backup.import(a, b); fail("accepted: ${b.take(60)}") } catch (e: IllegalArgumentException) { } catch (e: Exception) { /* sqlite rejection also leaves data due to transaction */ }
            assertEquals("data changed by rejected import of ${b.take(40)}", before, a.categories().count())
        }
        a.close()
    }
}
