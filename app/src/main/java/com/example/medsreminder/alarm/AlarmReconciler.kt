package com.example.medsreminder.alarm

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class ReconciliationMode {
    ROUTINE,
    WALL_CLOCK_CHANGED,
    REBOOT,
    PACKAGE_REPLACED,
    EXACT_PERMISSION_RESTORED,
}

class AlarmReconciler(
    context: Context,
    private val database: AppDatabase = AppDatabase.get(context),
    private val scheduler: AlarmScheduler = AlarmScheduler(context),
) {
    suspend fun reconcile(
        mode: ReconciliationMode,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        scheduler.cleanUpLegacyM0Alarm()
        val obsoleteIds = mutableListOf<String>()
        val desired = database.withTransaction {
            val occurrenceDao = database.occurrenceDao()
            val medicationDao = database.medicationDao()
            val strictRecovery = mode == ReconciliationMode.REBOOT ||
                mode == ReconciliationMode.PACKAGE_REPLACED ||
                mode == ReconciliationMode.EXACT_PERMISSION_RESTORED
            val expiryCutoff = if (strictRecovery) nowMillis else {
                AlarmScheduler.routineExpiryCutoff(nowMillis)
            }

            obsoleteIds += occurrenceDao.getPastScheduledIds(expiryCutoff)
            occurrenceDao.expireScheduledThrough(expiryCutoff, nowMillis)

            if (strictRecovery) {
                occurrenceDao.expireAllRinging(nowMillis)
            } else if (occurrenceDao.presentedRingingCount() == 0) {
                // A persisted queue is valid only while one occurrence owns the presentation.
                // This clears an orphaned receiver claim without timing out occurrences queued
                // behind an actively presented alarm.
                occurrenceDao.expireUnpresentedRingingThrough(expiryCutoff, nowMillis)
            }

            if (mode == ReconciliationMode.WALL_CLOCK_CHANGED) {
                obsoleteIds += occurrenceDao.getFutureBaseIds(nowMillis)
                occurrenceDao.deleteFutureBases(nowMillis)
            }

            medicationDao.getEnabledReminderTimes().forEach { reminder ->
                ensureFutureBase(
                    reminderTimeId = reminder.reminderTimeId,
                    minuteOfDay = reminder.minuteOfDay,
                    nowMillis = nowMillis,
                    zoneId = zoneId,
                )
            }
            occurrenceDao.getFutureScheduled(nowMillis)
        }

        obsoleteIds.distinct().forEach(scheduler::cancelOccurrence)
        if (scheduler.projectionReady()) {
            val medicationDao = database.medicationDao()
            desired.forEach { occurrence ->
                val medicationId = medicationDao.getMedicationIdForReminder(occurrence.reminderTimeId)
                if (medicationId != null) {
                    scheduler.registerOccurrence(occurrence, medicationId)
                        .onFailure { Log.e(TAG, "Unable to project occurrence ${occurrence.id}", it) }
                }
            }
        }
    }

    suspend fun ensureFutureBase(
        reminderTimeId: Long,
        minuteOfDay: Int,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): AlarmOccurrenceEntity {
        val occurrenceDao = database.occurrenceDao()
        occurrenceDao.getFutureBase(reminderTimeId, nowMillis)?.let { return it }
        val next = AlarmOccurrenceEntity(
            id = UUID.randomUUID().toString(),
            reminderTimeId = reminderTimeId,
            kind = OccurrenceKind.BASE,
            scheduledAtEpochMillis = NextOccurrenceCalculator.next(
                minuteOfDay,
                Instant.ofEpochMilli(nowMillis),
                zoneId,
            ).toEpochMilli(),
            status = OccurrenceStatus.SCHEDULED,
        )
        occurrenceDao.insert(next)
        return occurrenceDao.getFutureBase(reminderTimeId, nowMillis) ?: next
    }

    companion object {
        private const val TAG = "AlarmReconciler"
    }
}
