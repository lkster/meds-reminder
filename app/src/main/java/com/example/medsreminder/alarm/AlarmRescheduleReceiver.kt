package com.example.medsreminder.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_ACTIONS) {
            Log.w(TAG, "Ignoring unexpected action: ${intent.action}")
            return
        }

        val scheduler = AlarmScheduler(context)
        if (!scheduler.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot restore alarm because exact alarm access is unavailable")
            return
        }

        if (scheduler.rescheduleStoredFutureAlarm()) {
            Log.i(TAG, "Restored future alarm after ${intent.action}")
        }
    }

    companion object {
        private const val TAG = "AlarmReschedule"
        private val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

