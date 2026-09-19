package com.example.medsreminder.ui

import com.example.medsreminder.data.HistoryOccurrence
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import java.time.Instant
import java.time.LocalDate
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
    fun mappingPreservesResolvedTimestampIncludingNull() {
        listOf(
            OccurrenceStatus.TAKEN,
            OccurrenceStatus.SKIPPED,
            OccurrenceStatus.TIMED_OUT,
        ).forEach { status ->
            assertEquals(
                2_000L,
                occurrence(status = status, resolvedAtEpochMillis = 2_000L)
                    .toHistoryItem().resolvedAtEpochMillis,
            )
        }
        assertEquals(
            null,
            occurrence(status = OccurrenceStatus.SKIPPED, resolvedAtEpochMillis = null)
                .toHistoryItem().resolvedAtEpochMillis,
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

    @Test
    fun groupsByScheduledLocalDateWithoutChangingRoomOrder() {
        val zone = ZoneId.of("Europe/Warsaw")
        val newer = LocalDateTime.of(2026, 9, 18, 8, 0).atZone(zone).toInstant().toEpochMilli()
        val older = LocalDateTime.of(2026, 9, 17, 20, 0).atZone(zone).toInstant().toEpochMilli()
        val groups = groupHistoryByScheduledDate(
            listOf(
                HistoryItem("newer", "Medicine", newer, null, HistoryOutcome.TAKEN, false),
                HistoryItem("older", "Medicine", older, null, HistoryOutcome.SKIPPED, false),
            ),
            zone,
        )

        assertEquals(listOf(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 17)), groups.map { it.date })
        assertEquals(listOf("newer", "older"), groups.flatMap { it.items }.map { it.id })
    }

    @Test
    fun resultLabelsDescribeActualTerminalTransitionsAndCrossMidnightDate() {
        val zone = ZoneId.of("Europe/Warsaw")
        val scheduled = LocalDateTime.of(2026, 9, 18, 23, 55).atZone(zone).toInstant().toEpochMilli()
        val resolved = LocalDateTime.of(2026, 9, 19, 0, 5).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            "Taken at Sat, 19 Sep 2026 \u2022 00:05",
            formatHistoryResult(HistoryItem("taken", "Medicine", scheduled, resolved, HistoryOutcome.TAKEN, false), zone),
        )
        assertEquals(
            "Skipped at 00:05",
            formatHistoryResult(HistoryItem("skip", "Medicine", resolved, resolved, HistoryOutcome.SKIPPED, false), zone),
        )
        assertEquals(
            "Timed out at 00:05",
            formatHistoryResult(HistoryItem("timeout", "Medicine", resolved, resolved, HistoryOutcome.NO_RESPONSE, false), zone),
        )
        assertEquals(
            null,
            formatHistoryResult(HistoryItem("unknown", "Medicine", scheduled, null, HistoryOutcome.TAKEN, false), zone),
        )
    }

    private fun occurrence(
        kind: OccurrenceKind = OccurrenceKind.BASE,
        status: OccurrenceStatus,
        resolvedAtEpochMillis: Long? = 2_000L,
    ) = HistoryOccurrence(
        occurrenceId = "occurrence",
        medicationId = 1L,
        medicationName = "Medicine",
        kind = kind,
        status = status,
        scheduledAtEpochMillis = 1_000L,
        resolvedAtEpochMillis = resolvedAtEpochMillis,
    )
}
