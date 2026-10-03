package dev.mitul.upibudget.core

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    /** "1,25,000.00", "Rs.500.5", "INR 5000", "₹1,234" -> paise. Throws NumberFormatException if no digits. */
    fun parse(text: String): Paise {
        val cleaned = text.filter { it.isDigit() || it == '.' }.trim('.')
        if (cleaned.none { it.isDigit() }) throw NumberFormatException("no amount in '$text'")
        return try {
            BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            throw NumberFormatException("amount too large in '$text'")
        }
    }

    /** 123450 -> "₹1,234.50", 1234500 -> "₹12,345" (Indian grouping, paise only when non-zero). */
    fun format(p: Paise): String {
        val abs = kotlin.math.abs(p)
        val s = (abs / 100).toString()
        val grouped = if (s.length <= 3) s else {
            s.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + s.takeLast(3)
        }
        val paise = (abs % 100).toInt()
        val frac = if (paise != 0) "." + paise.toString().padStart(2, '0') else ""
        return (if (p < 0) "-" else "") + "₹" + grouped + frac
    }

    /** Calm display for headline numbers: nearest whole rupee. */
    fun whole(p: Paise): String = format(Math.floorDiv(p + if (p >= 0) 50 else -50, 100L) * 100 + 0)
}
