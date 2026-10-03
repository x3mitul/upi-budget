package dev.mitul.upibudget.ingest

import dev.mitul.upibudget.core.*
import java.time.Duration
import java.time.LocalDateTime

data class Candidate(
    val id: Long, val time: LocalDateTime, val amount: Paise, val direction: Direction,
    val source: Source, val ref: String?, val rawText: String, val merged: Boolean,
)

object Dedupe {
    const val WINDOW_MINUTES = 10L

    fun findDuplicate(incoming: Candidate, existing: List<Candidate>): Candidate? {
        existing.firstOrNull { e ->
            e.source == incoming.source && ((incoming.ref != null && e.ref == incoming.ref) || e.rawText == incoming.rawText)
        }?.let { return it }
        existing.firstOrNull { e -> e.source != incoming.source && incoming.ref != null && e.ref == incoming.ref }?.let { return it }
        return existing
            .filter { e ->
                e.source != incoming.source && !e.merged && e.amount == incoming.amount && e.direction == incoming.direction &&
                    Math.abs(Duration.between(e.time, incoming.time).toMinutes()) <= WINDOW_MINUTES
            }
            .minByOrNull { Math.abs(Duration.between(it.time, incoming.time).seconds) }
    }
}
