package com.example.medsreminder.ui

import com.example.medsreminder.data.HistoryOccurrence
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryModelsTest {
    @Test
    fun qualifyingSnoozeMapsToNoResponseWithoutReconstructingAJourney() {
        val item = occurrence(
            kind = OccurrenceKind.SNOOZE,
            status = OccurrenceStatus.TIMED_OUT,
        ).toHistoryItem()

        assertEquals(HistoryOutcome.NO_RESPONSE, item.outcome)
        assertTrue(item.afterSnooze)
    }

    @Test
    fun qualifyingBaseMapsToTakenAndSkipped() {
        assertEquals(
            HistoryOutcome.TAKEN,
            occurrence(status = OccurrenceStatus.TAKEN).toHistoryItem().outcome,
        )
        assertEquals(
            HistoryOutcome.SKIPPED,
            occurrence(status = OccurrenceStatus.SKIPPED).toHistoryItem().outcome,
        )
    }

    @Test
    fun nonQualifyingStatusCannotBecomeAHistoryItem() {
        val result = runCatching {
            occurrence(status = OccurrenceStatus.EXPIRED).toHistoryItem()
        }

        assertTrue(result.isFailure)
        assertFalse(result.getOrNull()?.afterSnooze == true)
    }

    @Test
    fun scheduledInstantMapsUsingTheSuppliedTimezone() {
        val instant = Instant.parse("2026-08-17T06:00:00Z").toEpochMilli()

        assertEquals(
            LocalDateTime.of(2026, 8, 17, 6, 0),
            historyLocalDateTime(instant, ZoneId.of("UTC")),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 17, 8, 0),
            historyLocalDateTime(instant, ZoneId.of("Europe/Warsaw")),
        )
    }

    private fun occurrence(
        kind: OccurrenceKind = OccurrenceKind.BASE,
        status: OccurrenceStatus,
    ) = HistoryOccurrence(
        occurrenceId = "occurrence",
        medicationId = 1L,
        medicationName = "Medicine",
        kind = kind,
        status = status,
        scheduledAtEpochMillis = 1_000L,
        resolvedAtEpochMillis = 2_000L,
    )
}
