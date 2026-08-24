package com.example.medsreminder.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.example.medsreminder.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(TAG, "Deferring medication reconciliation until first unlock")
                return
            }
            Intent.ACTION_BOOT_COMPLETED -> ReconciliationMode.REBOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> ReconciliationMode.PACKAGE_REPLACED
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                ReconciliationMode.EXACT_PERMISSION_RESTORED
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                ReconciliationMode.WALL_CLOCK_CHANGED
            else -> {
                Log.w(TAG, "Ignoring unexpected action: ${intent.action}")
                return
            }
        }
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) {
            Log.i(TAG, "Credential storage is locked; reconciliation remains deferred")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = AlarmScheduler(context.applicationContext)
                if (mode == ReconciliationMode.EXACT_PERMISSION_RESTORED &&
                    !scheduler.canScheduleExactAlarms()
                ) return@launch

                val database = AppDatabase.get(context.applicationContext)
                AlarmReconciler(
                    context.applicationContext,
                    database,
                    scheduler,
                ).reconcile(mode)
                if (mode == ReconciliationMode.PACKAGE_REPLACED ||
                    mode == ReconciliationMode.EXACT_PERMISSION_RESTORED
                ) {
                    AlarmRingingService.synchronizeWithPersistedQueue(
                        context.applicationContext,
                        database,
                    )
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to reconcile alarms after ${intent.action}", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReschedule"
    }
}
