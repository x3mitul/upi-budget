package dev.mitul.upibudget.core

/** Display form of a payee: bank SMS shout names in capitals, so title-case those; otherwise keep as written. */
fun prettyName(raw: String): String {
    val s = raw.trim().replace(Regex("\\s+"), " ")
    if (s.isEmpty()) return "Payment"
    if (s != s.uppercase()) return s
    return s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
