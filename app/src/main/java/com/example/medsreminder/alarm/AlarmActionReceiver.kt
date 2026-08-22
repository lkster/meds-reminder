package com.example.medsreminder.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.OccurrenceDetails
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_ACTIONS) {
            Log.w(TAG, "Ignoring unexpected action: ${intent.action}")
            return
        }
        val occurrenceId = occurrenceId(intent)
        if (occurrenceId == null) {
            Log.w(TAG, "Ignoring alarm action without occurrence identity")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_TAKEN -> resolve(context, occurrenceId, OccurrenceStatus.TAKEN)
                    ACTION_SKIP -> resolve(context, occurrenceId, OccurrenceStatus.SKIPPED)
                    ACTION_SNOOZE -> snooze(context, occurrenceId)
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Alarm action failed for $occurrenceId", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun resolve(context: Context, occurrenceId: String, status: OccurrenceStatus) {
        val database = AppDatabase.get(context)
        if (database.occurrenceDao().transition(
                occurrenceId,
                OccurrenceStatus.RINGING,
                status,
                System.currentTimeMillis(),
            ) == 1
        ) {
            AlarmRingingService.synchronizeWithPersistedQueue(context, database)
        }
    }

    private suspend fun snooze(context: Context, occurrenceId: String) {
        val database = AppDatabase.get(context)
        val dao = database.occurrenceDao()
        val original = dao.getDetails(occurrenceId) ?: return
        if (original.status != OccurrenceStatus.RINGING) return

        val scheduler = AlarmScheduler(context)
        if (!scheduler.canScheduleExactAlarms()) return

        val snoozeMinutes = AlarmPreferences.read(context).snoozeMinutes
        val actionNowMillis = System.currentTimeMillis()
        val snooze = createSnoozeOccurrence(
            original = original,
            nowMillis = actionNowMillis,
            snoozeMinutes = snoozeMinutes,
            snoozeId = UUID.randomUUID().toString(),
        )
        val registration = scheduler.registerOccurrence(snooze, original.medicationId)
        if (registration.isFailure) {
            Log.e(TAG, "Unable to pre-register snooze", registration.exceptionOrNull())
            return
        }

        val committed = runCatching {
            dao.commitSnooze(occurrenceId, snooze, actionNowMillis)
        }.getOrElse {
            Log.e(TAG, "Unable to commit snooze", it)
            false
        }

        if (!committed) {
            runCatching { scheduler.cancelOccurrence(snooze.id) }
                .onFailure { Log.e(TAG, "Unable to cancel orphaned snooze", it) }
            return
        }
        AlarmRingingService.synchronizeWithPersistedQueue(context, database)
    }

    companion object {
        const val ACTION_TAKEN = "com.example.medsreminder.action.TAKEN"
        const val ACTION_SNOOZE = "com.example.medsreminder.action.SNOOZE"
        const val ACTION_SKIP = "com.example.medsreminder.action.SKIP"

        private const val TAG = "AlarmActionReceiver"
        private val ALLOWED_ACTIONS = setOf(ACTION_TAKEN, ACTION_SNOOZE, ACTION_SKIP)

        fun pendingIntent(context: Context, action: String, occurrenceId: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, AlarmActionReceiver::class.java).apply {
                    this.action = action
                    data = Uri.parse("medsreminder://alarm-action/${action.substringAfterLast('.')}/$occurrenceId")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun send(context: Context, action: String, occurrenceId: String) {
            context.sendBroadcast(
                Intent(context, AlarmActionReceiver::class.java).apply {
                    this.action = action
                    data = Uri.parse("medsreminder://alarm-action/${action.substringAfterLast('.')}/$occurrenceId")
                },
            )
        }

        private fun occurrenceId(intent: Intent): String? {
            val uri = intent.data ?: return null
            if (uri.scheme != "medsreminder" || uri.host != "alarm-action") return null
            return uri.pathSegments.takeIf { it.size == 2 }?.get(1)
        }
    }
}

internal fun createSnoozeOccurrence(
    original: OccurrenceDetails,
    nowMillis: Long,
    snoozeMinutes: Int,
    snoozeId: String,
): AlarmOccurrenceEntity {
    require(snoozeMinutes in AlarmPreferences.ALLOWED_SNOOZE_MINUTES)
    return AlarmOccurrenceEntity(
        id = snoozeId,
        reminderTimeId = original.reminderTimeId,
        kind = OccurrenceKind.SNOOZE,
        scheduledAtEpochMillis = nowMillis + snoozeMinutes * 60_000L,
        status = OccurrenceStatus.SCHEDULED,
    )
}
