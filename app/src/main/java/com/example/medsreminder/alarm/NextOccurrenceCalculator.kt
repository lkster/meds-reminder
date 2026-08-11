package com.example.medsreminder.alarm

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NextOccurrenceCalculator {
    fun next(minuteOfDay: Int, after: Instant, zoneId: ZoneId): Instant {
        require(minuteOfDay in 0..1439)
        val afterZoned = after.atZone(zoneId)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var candidate = LocalDateTime.of(afterZoned.toLocalDate(), time).atZone(zoneId).toInstant()
        if (!candidate.isAfter(after)) {
            candidate = LocalDateTime.of(afterZoned.toLocalDate().plusDays(1), time)
                .atZone(zoneId)
                .toInstant()
        }
        return candidate
    }
}
