package com.example.medsreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.medsreminder.MainActivity

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val store = AlarmStore(context)

    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    fun schedule(triggerAtMillis: Long): Result<Unit> = runCatching {
        require(triggerAtMillis > System.currentTimeMillis()) {
            "Alarm time must be in the future"
        }
        check(canScheduleExactAlarms()) {
            "Exact alarm access is not granted"
        }

        store.save(triggerAtMillis)
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, alarmDetailsPendingIntent()),
                alarmPendingIntent(),
            )
        } catch (error: Throwable) {
            store.clear()
            throw error
        }
    }

    fun cancel() {
        alarmManager.cancel(alarmPendingIntent())
        store.clear()
    }

    fun rescheduleStoredFutureAlarm(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val triggerAtMillis = store.scheduledAtMillis() ?: return false
        if (triggerAtMillis <= nowMillis) {
            store.clear()
            return false
        }
        return schedule(triggerAtMillis).isSuccess
    }

    private fun alarmPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alarmDetailsPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        DETAILS_REQUEST_CODE,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val ALARM_REQUEST_CODE = 100
        private const val DETAILS_REQUEST_CODE = 101
    }
}

