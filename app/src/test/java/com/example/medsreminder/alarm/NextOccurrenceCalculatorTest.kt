package com.example.medsreminder.alarm

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class NextOccurrenceCalculatorTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")

    @Test
    fun usesTodayWhenTimeIsStillAhead() {
        val now = Instant.parse("2026-08-11T05:00:00Z") // 07:00 in Warsaw
        assertEquals(
            Instant.parse("2026-08-11T06:00:00Z"),
            NextOccurrenceCalculator.next(8 * 60, now, warsaw),
        )
    }

    @Test
    fun usesTomorrowWhenTimeHasPassed() {
        val now = Instant.parse("2026-08-11T07:00:00Z") // 09:00 in Warsaw
        assertEquals(
            Instant.parse("2026-08-12T06:00:00Z"),
            NextOccurrenceCalculator.next(8 * 60, now, warsaw),
        )
    }

    @Test
    fun dstGapMovesToFirstValidLocalTime() {
        val now = Instant.parse("2026-03-28T12:00:00Z")
        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z"),
            NextOccurrenceCalculator.next(2 * 60 + 30, now, warsaw),
        )
    }

    @Test
    fun dstOverlapUsesEarlierOffsetOnly() {
        val now = Instant.parse("2026-10-24T12:00:00Z")
        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z"),
            NextOccurrenceCalculator.next(2 * 60 + 30, now, warsaw),
        )
    }
}
