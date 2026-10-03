package dev.mitul.upibudget.ingest

import dev.mitul.upibudget.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class DedupeTest {
    private val t0 = LocalDateTime.of(2026, 9, 13, 9, 32)
    private fun c(id: Long, min: Long = 0, amt: Long = 43_800, src: Source = Source.SMS, ref: String? = "R1",
                  raw: String = "raw$id", dir: Direction = Direction.DEBIT, merged: Boolean = false) =
        Candidate(id, t0.plusMinutes(min), amt, dir, src, ref, raw, merged)

    @Test fun sameSourceSameRef() = assertEquals(1L, Dedupe.findDuplicate(c(0, ref = "R1"), listOf(c(1)))?.id)
    @Test fun sameSourceIdenticalText() =
        assertEquals(1L, Dedupe.findDuplicate(c(0, ref = null, raw = "same"), listOf(c(1, ref = null, raw = "same")))?.id)
    @Test fun sameSourceDifferentRefIsTwoPayments() =
        assertNull(Dedupe.findDuplicate(c(0, 1, ref = "R2"), listOf(c(1, ref = "R1"))))
    @Test fun crossSourceSameRef() =
        assertEquals(1L, Dedupe.findDuplicate(c(0, 500, src = Source.GMAIL, ref = "R1"), listOf(c(1)))?.id)
    @Test fun crossSourceWithinWindowByAmount() =
        assertEquals(1L, Dedupe.findDuplicate(c(0, 3, src = Source.NOTIFICATION, ref = null), listOf(c(1)))?.id)
    @Test fun crossSourceOutsideWindow() =
        assertNull(Dedupe.findDuplicate(c(0, 11, src = Source.NOTIFICATION, ref = null), listOf(c(1))))
    @Test fun crossSourceDifferentAmountOrDirection() {
        assertNull(Dedupe.findDuplicate(c(0, 1, amt = 100, src = Source.NOTIFICATION, ref = null), listOf(c(1))))
        assertNull(Dedupe.findDuplicate(c(0, 1, src = Source.NOTIFICATION, ref = null, dir = Direction.CREDIT), listOf(c(1))))
    }
    @Test fun oneToOneAlreadyMergedIsNotReused() =
        assertNull(Dedupe.findDuplicate(c(0, 1, src = Source.NOTIFICATION, ref = null), listOf(c(1, merged = true))))
    @Test fun picksClosestInTime() =
        assertEquals(2L, Dedupe.findDuplicate(c(0, 5, src = Source.NOTIFICATION, ref = null), listOf(c(1, 0, ref = "A"), c(2, 4, ref = "B")))?.id)
}
