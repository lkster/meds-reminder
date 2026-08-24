package com.example.medsreminder.alarm

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.medsreminder.data.AppDatabase
import java.time.ZoneId

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
            val strictScheduledRecovery = mode == ReconciliationMode.REBOOT ||
                mode == ReconciliationMode.PACKAGE_REPLACED ||
                mode == ReconciliationMode.EXACT_PERMISSION_RESTORED
            val expiryCutoff = if (strictScheduledRecovery) nowMillis else {
                AlarmScheduler.routineExpiryCutoff(nowMillis)
            }

            obsoleteIds += occurrenceDao.getPastScheduledIds(expiryCutoff)
            occurrenceDao.expireScheduledThrough(expiryCutoff, nowMillis)

            if (mode == ReconciliationMode.REBOOT) {
                occurrenceDao.expireAllRinging(nowMillis)
            } else if (occurrenceDao.presentedRingingCount() == 0) {
                // A persisted queue is valid only while one occurrence owns the presentation.
                // This clears an orphaned receiver claim without timing out occurrences queued
                // behind an actively presented alarm.
                occurrenceDao.expireUnpresentedRingingThrough(
                    AlarmScheduler.routineExpiryCutoff(nowMillis),
                    nowMillis,
                )
            }

            if (mode == ReconciliationMode.WALL_CLOCK_CHANGED) {
                obsoleteIds += occurrenceDao.getFutureBaseIds(nowMillis)
                occurrenceDao.deleteFutureBases(nowMillis)
            }

            medicationDao.getEnabledReminderTimes().forEach { reminder ->
                ensureFutureBase(
                    reminderTimeId = reminder.reminderTimeId,
                    minuteOfDay = reminder.minuteOfDay,
                    weekdayMask = reminder.weekdayMask,
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
        weekdayMask: Int,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) = database.ensureFutureBase(reminderTimeId, minuteOfDay, weekdayMask, nowMillis, zoneId)

    companion object {
        private const val TAG = "AlarmReconciler"
    }
}
