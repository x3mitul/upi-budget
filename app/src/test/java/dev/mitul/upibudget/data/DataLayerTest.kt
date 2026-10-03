package dev.mitul.upibudget.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mitul.upibudget.core.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataLayerTest {
    private lateinit var db: AppDatabase
    private val json = File("src/main/assets/categories.json").readText()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private fun txn(sub: Long, payee: String = "SOME SHOP") = TxnEntity(
        time = 0, amount = 100, direction = Direction.DEBIT, payeeRaw = payee, payeeNorm = payee, payeeType = PayeeType.P2M,
        subId = sub, needsSorting = false, creditAs = null, source = Source.SMS, ref = null, rawText = "x", kind = Kind.UPI, monthId = 1)

    @Test fun seedsCategoriesAndKeywordsOnce() = runBlocking {
        val seeder = Seeder(db)
        seeder.seed(json)
        val cats = db.categories().all()
        assertEquals(21, cats.count { it.parentId == null })
        assertEquals(109, cats.count { it.parentId != null })
        assertTrue(db.rules().allKeywords().size > 2000)
        val n = db.rules().allKeywords().size
        seeder.seed(json)
        assertEquals(n, db.rules().allKeywords().size)
        assertEquals(cats.size, db.categories().count())
        val savingsSub = cats.first { it.name == "Recurring Deposit" }
        assertTrue(savingsSub.isSavings)
        assertFalse(cats.first { it.name == "Food Delivery" }.isSavings)
    }

    @Test fun reseedKeepsUserKeywords() = runBlocking {
        val seeder = Seeder(db); seeder.seed(json)
        val food = db.categories().subIdByName("Food Delivery")!!
        db.rules().insertKeyword(KeywordRuleEntity(word = "MYSHOP", subId = food, weak = false, builtin = false, ord = 0))
        seeder.seed(json.replace("SWIGGY", "SWIGGYX"))   // changed asset -> different hash -> reload built-ins
        assertTrue(db.rules().allKeywords().any { it.word == "MYSHOP" && !it.builtin })
        assertTrue(db.rules().allKeywords().any { it.word == "SWIGGYX" && it.builtin })
    }

    @Test fun deletingCategoryMovesTxnsAndRulesToUncategorized() = runBlocking {
        val seeder = Seeder(db); seeder.seed(json)
        val unc = seeder.uncategorizedId()
        val food = db.categories().subIdByName("Food Delivery")!!
        db.txns().insert(txn(food))
        db.rules().upsertNameRule(NameRuleEntity("RAMESH", food))
        db.rules().insertKeyword(KeywordRuleEntity(word = "MYFOOD", subId = food, weak = false, builtin = false, ord = 0))
        CategoryRepo(db).deleteCategory(food)
        assertNull(db.categories().all().firstOrNull { it.id == food })
        assertEquals(unc, db.txns().all().single().subId)
        assertEquals(unc, db.rules().allNameRules().single().subId)
        assertEquals(unc, db.rules().allKeywords().first { it.word == "MYFOOD" }.subId)
        assertTrue(db.rules().allKeywords().none { it.builtin && it.subId == food })
    }

    @Test fun deletingGroupDeletesItsSubsAndMovesTheirTxns() = runBlocking {
        val seeder = Seeder(db); seeder.seed(json)
        val unc = seeder.uncategorizedId()
        val food = db.categories().subIdByName("Food Delivery")!!
        val group = db.categories().all().first { it.id == food }.parentId!!
        db.txns().insert(txn(food))
        CategoryRepo(db).deleteCategory(group)
        assertTrue(db.categories().all().none { it.id == group || it.parentId == group })
        assertEquals(unc, db.txns().all().single().subId)
    }

    @Test fun uncategorizedCannotBeDeleted() = runBlocking {
        val seeder = Seeder(db); seeder.seed(json)
        val unc = seeder.uncategorizedId()
        CategoryRepo(db).deleteCategory(unc)
        assertNotNull(db.categories().all().firstOrNull { it.id == unc })
    }

    @Test fun mostUsedSubsOrdersByCount() = runBlocking {
        db.txns().insert(txn(5)); db.txns().insert(txn(5)); db.txns().insert(txn(7))
        assertEquals(listOf(5L, 7L), db.txns().mostUsedSubs(2))
    }
}
