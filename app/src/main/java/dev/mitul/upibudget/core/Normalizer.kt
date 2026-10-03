package dev.mitul.upibudget.core

import java.util.Locale

object Normalizer {
    private val separators = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    /** Uppercase, punctuation -> space, collapse spaces, trim. */
    fun name(raw: String): String = separators.replace(raw.uppercase(Locale.ROOT), " ").trim()

    /** True if [keyword] appears in [normalized] as a whole-word sequence. Both must already be normalized. */
    fun hasWords(normalized: String, keyword: String): Boolean =
        keyword.isNotEmpty() && " $normalized ".contains(" $keyword ")
}
