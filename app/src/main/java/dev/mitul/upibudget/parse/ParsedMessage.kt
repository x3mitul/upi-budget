package dev.mitul.upibudget.parse

import dev.mitul.upibudget.core.*
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface ParsedMessage {
    data class Payment(
        val amount: Paise, val direction: Direction, val time: LocalDateTime?, val ref: String?,
        val payee: String, val kind: Kind, val payeeType: PayeeType, val account: String?,
    ) : ParsedMessage
    data class MandateNotice(val merchant: String, val amount: Paise, val dueDate: LocalDate) : ParsedMessage
    data class MandateCreated(val merchant: String, val amount: Paise, val start: LocalDate, val end: LocalDate) : ParsedMessage
    data class RdBooked(val account: String, val amount: Paise, val tenureMonths: Int) : ParsedMessage
    data class MandateRevoked(val merchant: String) : ParsedMessage
    data class Ignored(val reason: String) : ParsedMessage
}
