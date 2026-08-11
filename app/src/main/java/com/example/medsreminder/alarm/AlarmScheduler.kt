package com.example.medsreminder.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.medsreminder.MainActivity
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.OccurrenceStatus

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    fun requiredPresentationReady(): Boolean {
        AlarmRingingService.ensureNotificationChannel(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val runtimePermissionReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channel = notificationManager.getNotificationChannel(AlarmRingingService.CHANNEL_ID)
        return runtimePermissionReady &&
            notificationManager.areNotificationsEnabled() &&
            channel != null &&
            channel.importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun projectionReady(): Boolean = canScheduleExactAlarms() && requiredPresentationReady()

    fun fullScreenAllowed(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            manager.canUseFullScreenIntent()
    }

    fun registerOccurrence(occurrence: AlarmOccurrenceEntity, medicationId: Long): Result<Unit> = runCatching {
        require(occurrence.status == OccurrenceStatus.SCHEDULED)
        require(occurrence.scheduledAtEpochMillis > System.currentTimeMillis())
        check(canScheduleExactAlarms()) { "Exact alarm access is not granted" }
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(
                occurrence.scheduledAtEpochMillis,
                detailsPendingIntent(medicationId),
            ),
            firePendingIntent(occurrence.id),
        )
    }

    fun cancelOccurrence(occurrenceId: String) {
        alarmManager.cancel(firePendingIntent(occurrenceId))
    }

    fun firePendingIntent(occurrenceId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = occurrenceUri(occurrenceId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun detailsPendingIntent(medicationId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_MEDICATION
            data = Uri.parse("medsreminder://medication/$medicationId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun cleanUpLegacyM0Alarm() {
        val legacyIntent = Intent(context, AlarmReceiver::class.java).setAction(LEGACY_M0_FIRE_ACTION)
        val legacyPendingIntent = PendingIntent.getBroadcast(
            context,
            100,
            legacyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (legacyPendingIntent != null) {
            alarmManager.cancel(legacyPendingIntent)
            legacyPendingIntent.cancel()
        }
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences("m0_alarm", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    companion object {
        const val DELIVERY_GRACE_MILLIS = 2 * 60 * 1000L
        private const val ACTION_VIEW_MEDICATION = "com.example.medsreminder.action.VIEW_MEDICATION"
        private const val LEGACY_M0_FIRE_ACTION = "com.example.medsreminder.action.FIRE_ALARM"

        fun occurrenceUri(occurrenceId: String): Uri =
            Uri.parse("medsreminder://alarm/occurrence/$occurrenceId")

        fun occurrenceId(intent: Intent): String? {
            val uri = intent.data ?: return null
            if (uri.scheme != "medsreminder" || uri.host != "alarm") return null
            val segments = uri.pathSegments
            return segments.takeIf { it.size == 2 && it[0] == "occurrence" }?.get(1)
        }

        fun isWithinDeliveryGrace(scheduledAtMillis: Long, nowMillis: Long): Boolean =
            nowMillis >= scheduledAtMillis - 1_000L &&
                nowMillis - scheduledAtMillis <= DELIVERY_GRACE_MILLIS

        fun routineExpiryCutoff(nowMillis: Long): Long =
            nowMillis - DELIVERY_GRACE_MILLIS - 1L
    }
}
