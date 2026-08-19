package com.example.medsreminder.alarm

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import com.example.medsreminder.data.WeekdayMask

object NextOccurrenceCalculator {
    fun next(minuteOfDay: Int, weekdayMask: Int, after: Instant, zoneId: ZoneId): Instant {
        require(minuteOfDay in 0..1439)
        WeekdayMask.requireValid(weekdayMask)
        val afterZoned = after.atZone(zoneId)
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        for (dayOffset in 0L..7L) {
            val date = afterZoned.toLocalDate().plusDays(dayOffset)
            if (!WeekdayMask.contains(weekdayMask, date.dayOfWeek)) continue
            val candidate = LocalDateTime.of(date, time).atZone(zoneId).toInstant()
            if (candidate.isAfter(after)) return candidate
        }
        error("A valid weekday mask must produce a next occurrence within seven days")
    }
}
