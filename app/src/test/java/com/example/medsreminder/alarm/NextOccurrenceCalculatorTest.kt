package com.example.medsreminder.alarm

import java.time.Instant
import java.time.ZoneId
import com.example.medsreminder.data.WeekdayMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NextOccurrenceCalculatorTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")

    @Test
    fun usesTodayWhenTimeIsStillAhead() {
        val now = Instant.parse("2026-08-11T05:00:00Z") // 07:00 in Warsaw
        assertEquals(
            Instant.parse("2026-08-11T06:00:00Z"),
            NextOccurrenceCalculator.next(8 * 60, WeekdayMask.ALL, now, warsaw),
        )
    }

    @Test
    fun usesTomorrowWhenTimeHasPassed() {
        val now = Instant.parse("2026-08-11T07:00:00Z") // 09:00 in Warsaw
        assertEquals(
            Instant.parse("2026-08-12T06:00:00Z"),
            NextOccurrenceCalculator.next(8 * 60, WeekdayMask.ALL, now, warsaw),
        )
    }

    @Test
    fun oneSelectedWeekdayWrapsToNextWeekAfterItsTime() {
        val mondayMask = 1
        val now = Instant.parse("2026-08-17T07:00:00Z") // Monday 09:00 in Warsaw
        assertEquals(
            Instant.parse("2026-08-24T06:00:00Z"),
            NextOccurrenceCalculator.next(8 * 60, mondayMask, now, warsaw),
        )
    }

    @Test
    fun multipleSelectedWeekdaysChooseNearestEligibleDate() {
        val thursdayAndSunday = (1 shl 3) or (1 shl 6)
        val now = Instant.parse("2026-08-11T07:00:00Z") // Tuesday
        assertEquals(
            Instant.parse("2026-08-13T06:00:00Z"),
            NextOccurrenceCalculator.next(8 * 60, thursdayAndSunday, now, warsaw),
        )
    }

    @Test
    fun sundayWrapsToMonday() {
        val mondayMask = 1
        val now = Instant.parse("2026-08-16T12:00:00Z") // Sunday
        assertEquals(
            Instant.parse("2026-08-17T06:00:00Z"),
            NextOccurrenceCalculator.next(8 * 60, mondayMask, now, warsaw),
        )
    }

    @Test
    fun dstGapMovesToFirstValidLocalTime() {
        val now = Instant.parse("2026-03-28T12:00:00Z")
        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z"),
            NextOccurrenceCalculator.next(2 * 60 + 30, 1 shl 6, now, warsaw),
        )
    }

    @Test
    fun dstOverlapUsesEarlierOffsetOnly() {
        val now = Instant.parse("2026-10-24T12:00:00Z")
        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z"),
            NextOccurrenceCalculator.next(2 * 60 + 30, 1 shl 6, now, warsaw),
        )
    }

    @Test
    fun dstOverlapDoesNotUseLaterOffsetAfterEarlierOccurrencePassed() {
        val afterEarlierOccurrence = Instant.parse("2026-10-25T00:45:00Z")
        assertEquals(
            Instant.parse("2026-11-01T01:30:00Z"),
            NextOccurrenceCalculator.next(
                2 * 60 + 30,
                1 shl 6,
                afterEarlierOccurrence,
                warsaw,
            ),
        )
    }

    @Test
    fun rejectsEmptyAndUnknownWeekdayBits() {
        val now = Instant.parse("2026-08-11T05:00:00Z")
        assertThrows(IllegalArgumentException::class.java) {
            NextOccurrenceCalculator.next(8 * 60, 0, now, warsaw)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NextOccurrenceCalculator.next(8 * 60, 1 shl 7, now, warsaw)
        }
    }

    @Test
    fun eligibilityUsesTheSuppliedTimezone() {
        val now = Instant.parse("2026-08-17T00:30:00Z")
        val mondayMask = 1
        assertEquals(
            Instant.parse("2026-08-17T08:00:00Z"),
            NextOccurrenceCalculator.next(8 * 60, mondayMask, now, ZoneId.of("UTC")),
        )
        assertEquals(
            Instant.parse("2026-08-17T06:00:00Z"),
            NextOccurrenceCalculator.next(8 * 60, mondayMask, now, warsaw),
        )
    }
}
