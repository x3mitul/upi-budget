package dev.mitul.upibudget.categorize

import dev.mitul.upibudget.categorize.CategoryResult.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {
    private val food = 1L; private val groc = 2L; private val cab = 3L; private val misc = 4L; private val user = 5L
    private fun kw(word: String, sub: Long, weak: Boolean = false, order: Int = 0) = KeywordEntry(word, sub, weak, order)

    private val builtin = listOf(
        kw("SWIGGY", food, order = 0), kw("SWIGGY INSTAMART", groc, order = 1),
        kw("OLA", cab, order = 2), kw("STORE", groc, weak = true, order = 3),
        kw("KIRANA", groc, order = 4), kw("LTD", misc, weak = true, order = 5),
    )
    private val c = Categorizer(mapOf("RAMESH KIRANA" to food), listOf(kw("PIZZA", user)), builtin)

    @Test fun exactNameRuleWinsOverEverything() =
        assertEquals(Known(food, Via.NAME_RULE), c.categorize("RAMESH KIRANA"))

    @Test fun userKeywordBeatsBuiltin() =
        assertEquals(Known(user, Via.USER_KEYWORD), c.categorize("SWIGGY PIZZA"))

    @Test fun longestKeywordWins() =
        assertEquals(Known(groc, Via.BUILTIN), c.categorize("SWIGGY INSTAMART"))

    @Test fun wholeWordsOnly() =
        assertEquals(Unknown, c.categorize("COCA COLA"))

    @Test fun strongBeatsWeakEvenIfWeakIsLonger() =
        assertEquals(Known(cab, Via.BUILTIN), c.categorize("OLA STORE"))

    @Test fun weakOnlyGivesHint() =
        assertEquals(Hint(groc), c.categorize("VERMA STORE"))

    @Test fun nothingMatchesIsUnknown() =
        assertEquals(Unknown, c.categorize("ANIL MEHRA"))

    @Test fun tieGoesToEarlierOrder() {
        val t = Categorizer(emptyMap(), emptyList(), listOf(kw("ABC", 1, order = 7), kw("XYZ", 2, order = 3)))
        assertEquals(Known(2, Via.BUILTIN), t.categorize("ABC XYZ"))
    }

    @Test fun bestGuessUsesKeywordHitThenMostUsed() {
        assertEquals(listOf(groc, food), c.bestGuess("VERMA STORE", mostUsed = listOf(food, cab)))
        assertEquals(listOf(cab, food), c.bestGuess("ANIL MEHRA", mostUsed = listOf(cab, food, groc)))
        assertEquals(listOf(food), c.bestGuess("UNKNOWN NAME", mostUsed = listOf(food), count = 1))
    }
}
