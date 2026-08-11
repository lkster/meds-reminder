package com.example.medsreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.OccurrenceDetails
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) {
            Log.w(TAG, "Ignoring unexpected action: ${intent.action}")
            return
        }
        val occurrenceId = AlarmScheduler.occurrenceId(intent)
        if (occurrenceId == null) {
            Log.w(TAG, "Ignoring alarm without a valid occurrence identity")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliver(context.applicationContext, occurrenceId)
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to deliver occurrence $occurrenceId", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliver(context: Context, occurrenceId: String) {
        val database = AppDatabase.get(context)
        val scheduler = AlarmScheduler(context)
        val reconciler = AlarmReconciler(context, database, scheduler)
        val now = System.currentTimeMillis()
        val initial = database.occurrenceDao().getDetails(occurrenceId) ?: return
        if (initial.status != OccurrenceStatus.SCHEDULED) return

        val tooEarly = initial.scheduledAtEpochMillis > now + EARLY_TOLERANCE_MILLIS
        val materiallyLate = !tooEarly &&
            !AlarmScheduler.isWithinDeliveryGrace(initial.scheduledAtEpochMillis, now)
        val canPresent = scheduler.requiredPresentationReady() && scheduler.canScheduleExactAlarms()
        val shouldRing = !tooEarly && !materiallyLate && canPresent

        var claimed: OccurrenceDetails? = null
        var nextBase: AlarmOccurrenceEntity? = null
        database.withTransaction {
            val dao = database.occurrenceDao()
            val current = dao.getDetails(occurrenceId) ?: return@withTransaction
            if (current.status != OccurrenceStatus.SCHEDULED) return@withTransaction
            if (!shouldRing) {
                if (!tooEarly && dao.transition(
                        occurrenceId,
                        OccurrenceStatus.SCHEDULED,
                        OccurrenceStatus.EXPIRED,
                        now,
                    ) == 1 && current.kind == OccurrenceKind.BASE
                ) {
                    nextBase = reconciler.ensureFutureBase(
                        current.reminderTimeId,
                        current.minuteOfDay,
                        now,
                    )
                }
                return@withTransaction
            }

            if (dao.transition(
                    occurrenceId,
                    OccurrenceStatus.SCHEDULED,
                    OccurrenceStatus.RINGING,
                    null,
                ) != 1
            ) return@withTransaction

            claimed = dao.getDetails(occurrenceId)
            if (current.kind == OccurrenceKind.BASE) {
                nextBase = reconciler.ensureFutureBase(
                    current.reminderTimeId,
                    current.minuteOfDay,
                    now,
                )
            }
        }

        claimed?.let { occurrence ->
            // If another occurrence already owns the session, keep presenting it. The newly
            // claimed occurrence remains queued in Room and will follow sequentially.
            val sessionOccurrence = database.occurrenceDao().getCurrentRinging() ?: occurrence
            try {
                context.startForegroundService(AlarmRingingService.startIntent(context, sessionOccurrence))
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to start systemExempted ringing service", error)
                AlarmRingingService.postFallbackNotification(context, sessionOccurrence, error)
            }
        }

        nextBase?.let { next ->
            if (scheduler.projectionReady()) {
                val medicationId = database.medicationDao().getMedicationIdForReminder(next.reminderTimeId)
                if (medicationId != null) {
                    scheduler.registerOccurrence(next, medicationId)
                        .onFailure { Log.e(TAG, "Unable to register the next BASE occurrence", it) }
                }
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.example.medsreminder.action.FIRE_OCCURRENCE"
        private const val EARLY_TOLERANCE_MILLIS = 1_000L
        private const val TAG = "AlarmReceiver"
    }
}
