package com.example.medsreminder.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_ACTIONS) {
            Log.w(TAG, "Ignoring unexpected action: ${intent.action}")
            return
        }

        context.stopService(Intent(context, AlarmRingingService::class.java))
        context.getSystemService(NotificationManager::class.java)
            .cancel(AlarmRingingService.NOTIFICATION_ID)

        val scheduler = AlarmScheduler(context)
        try {
            when (intent.action) {
                ACTION_TAKEN, ACTION_SKIP -> scheduler.cancel()
                ACTION_SNOOZE -> {
                    scheduler.cancel()
                    val result = scheduler.schedule(System.currentTimeMillis() + SNOOZE_MILLIS)
                    if (result.isFailure) {
                        Log.e(TAG, "Unable to schedule snooze", result.exceptionOrNull())
                    }
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Alarm action cleanup failed for ${intent.action}", error)
        }

    }

    companion object {
        const val ACTION_TAKEN = "com.example.medsreminder.action.TAKEN"
        const val ACTION_SNOOZE = "com.example.medsreminder.action.SNOOZE"
        const val ACTION_SKIP = "com.example.medsreminder.action.SKIP"

        private const val SNOOZE_MILLIS = 5 * 60 * 1000L
        private const val TAG = "AlarmActionReceiver"
        private val ALLOWED_ACTIONS = setOf(ACTION_TAKEN, ACTION_SNOOZE, ACTION_SKIP)

        fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, AlarmActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun send(context: Context, action: String) {
            context.sendBroadcast(
                Intent(context, AlarmActionReceiver::class.java).setAction(action),
            )
        }
    }
}
