package dev.mitul.upibudget.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryRepoTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CategoryRepo
    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        Seeder(db).seed(File("src/main/assets/categories.json").readText())
        repo = CategoryRepo(db)
    }
    @After fun tearDown() = db.close()

    @Test fun customGroupAndSubAreCreatedAndUsable() = runBlocking {
        val g = repo.addGroup("Hobbies", 0xFF123456.toInt())
        val s = repo.addSub("Guitar", g)
        val sub = db.categories().all().first { it.id == s }
        assertEquals(g, sub.parentId); assertEquals(0xFF123456.toInt(), sub.color); assertFalse(sub.isSavings); assertFalse(sub.builtin)
    }

    @Test fun subInSavingsGroupIsSavings() = runBlocking {
        val sg = db.categories().all().first { it.parentId == null && it.isSavings }.id
        val id = repo.addSub("Chit fund", sg)
        assertTrue(db.categories().all().first { it.id == id }.isSavings)
    }

    @Test fun renameAndRecolor() = runBlocking {
        val g = repo.addGroup("A", 1); val s = repo.addSub("B", g)
        repo.rename(s, "B2"); repo.recolor(g, 7)
        val all = db.categories().all()
        assertEquals("B2", all.first { it.id == s }.name)
        assertEquals(7, all.first { it.id == s }.color)      // sub follows group colour
    }

    @Test fun blankNamesAreRejected() = runBlocking {
        try { repo.addGroup("  ", 1); fail() } catch (e: IllegalArgumentException) { }
    }
}
