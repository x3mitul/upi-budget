package dev.mitul.upibudget.categorize

import dev.mitul.upibudget.core.Normalizer

data class KeywordEntry(val word: String, val subId: Long, val weak: Boolean, val order: Int)

enum class Via { NAME_RULE, USER_KEYWORD, BUILTIN }

sealed interface CategoryResult {
    data class Known(val subId: Long, val via: Via) : CategoryResult
    data class Hint(val subId: Long) : CategoryResult
    data object Unknown : CategoryResult
}

class Categorizer(
    private val nameRules: Map<String, Long>,
    private val userKeywords: List<KeywordEntry>,
    private val builtin: List<KeywordEntry>,
) {
    private val strength = compareBy<KeywordEntry>({ it.weak }, { -it.word.length }, { it.order })

    private fun best(list: List<KeywordEntry>, normalized: String): KeywordEntry? =
        list.filter { Normalizer.hasWords(normalized, it.word) }.minWithOrNull(strength)

    fun categorize(normalized: String): CategoryResult {
        nameRules[normalized]?.let { return CategoryResult.Known(it, Via.NAME_RULE) }
        best(userKeywords, normalized)?.let { return CategoryResult.Known(it.subId, Via.USER_KEYWORD) }
        val b = best(builtin, normalized) ?: return CategoryResult.Unknown
        return if (b.weak) CategoryResult.Hint(b.subId) else CategoryResult.Known(b.subId, Via.BUILTIN)
    }

    /** Category ids to offer as the first buttons: keyword hit (if any), then the user's most-used. */
    fun bestGuess(normalized: String, mostUsed: List<Long>, count: Int = 2): List<Long> {
        val hit = when (val r = categorize(normalized)) {
            is CategoryResult.Known -> r.subId
            is CategoryResult.Hint -> r.subId
            CategoryResult.Unknown -> null
        }
        return (listOfNotNull(hit) + mostUsed).distinct().take(count)
    }
}
