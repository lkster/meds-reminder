package com.example.medsreminder.alarm

import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal suspend fun AppDatabase.ensureFutureBase(
    reminderTimeId: Long,
    minuteOfDay: Int,
    weekdayMask: Int,
    nowMillis: Long,
    zoneId: ZoneId,
): AlarmOccurrenceEntity {
    val occurrenceDao = occurrenceDao()
    occurrenceDao.getFutureBase(reminderTimeId, nowMillis)?.let { return it }
    val next = AlarmOccurrenceEntity(
        id = UUID.randomUUID().toString(),
        reminderTimeId = reminderTimeId,
        kind = OccurrenceKind.BASE,
        scheduledAtEpochMillis = NextOccurrenceCalculator.next(
            minuteOfDay,
            weekdayMask,
            Instant.ofEpochMilli(nowMillis),
            zoneId,
        ).toEpochMilli(),
        status = OccurrenceStatus.SCHEDULED,
    )
    occurrenceDao.insert(next)
    return occurrenceDao.getFutureBase(reminderTimeId, nowMillis) ?: next
}
