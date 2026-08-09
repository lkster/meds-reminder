package com.example.medsreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) {
            Log.w(TAG, "Ignoring unexpected action: ${intent.action}")
            return
        }

        try {
            context.startForegroundService(AlarmRingingService.startIntent(context))
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to start systemExempted ringing service", error)
            AlarmRingingService.postFallbackNotification(context, error)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.example.medsreminder.action.FIRE_ALARM"
        private const val TAG = "AlarmReceiver"
    }
}

