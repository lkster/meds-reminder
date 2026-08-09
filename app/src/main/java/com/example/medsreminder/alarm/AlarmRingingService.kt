package com.example.medsreminder.alarm

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.medsreminder.AlarmActivity
import com.example.medsreminder.R

class AlarmRingingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopAfterTimeout = Runnable { finishAfterSafetyTimeout() }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var foregroundStarted = false
    private var cleanedUp = false
    private var preserveFallbackNotification = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            Log.w(TAG, "Ignoring unexpected service action: ${intent?.action}")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (foregroundStarted) {
            return START_NOT_STICKY
        }

        val notification = buildAlarmNotification(this)
        if (!getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            handleSystemExemptedFailure(
                IllegalStateException("Exact alarm access was unavailable when ringing started"),
                notification,
            )
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        } catch (error: Throwable) {
            handleSystemExemptedFailure(error, notification)
            return START_NOT_STICKY
        }

        acquireWakeLock()
        startVibration()
        startAlarmAudio()
        handler.postDelayed(stopAfterTimeout, RINGING_TIMEOUT_MILLIS)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cleanUp(removeNotification = !preserveFallbackNotification)
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:m0-alarm")
                .apply {
                    setReferenceCounted(false)
                    acquire(WAKE_LOCK_TIMEOUT_MILLIS)
                }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to acquire alarm wake lock", error)
        }
    }

    private fun startVibration() {
        try {
            vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
            if (vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator?.vibrate(
                        effect,
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(effect, ALARM_AUDIO_ATTRIBUTES)
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to start alarm vibration", error)
        }
    }

    private fun startAlarmAudio() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (alarmUri == null) {
            Log.e(TAG, "No default system alarm tone is configured; using vibration only")
            return
        }

        val audioManager = getSystemService(AudioManager::class.java)
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(ALARM_AUDIO_ATTRIBUTES)
            .setOnAudioFocusChangeListener { }
            .build()
        if (audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.w(TAG, "Alarm audio focus request was denied; attempting playback anyway")
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(ALARM_AUDIO_ATTRIBUTES)
                setDataSource(this@AlarmRingingService, alarmUri)
                isLooping = true
                setOnErrorListener { player, what, extra ->
                    Log.e(TAG, "Alarm audio playback error what=$what extra=$extra")
                    runCatching { player.release() }
                    if (mediaPlayer === player) {
                        mediaPlayer = null
                    }
                    true
                }
                prepare()
                start()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to load or play the default alarm tone; using vibration only", error)
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
        }
    }

    private fun finishAfterSafetyTimeout() {
        Log.w(TAG, "Stopping M0 alarm after the 10-minute safety timeout")
        runCatching { AlarmStore(this).clear() }
            .onFailure { Log.e(TAG, "Unable to clear alarm state after timeout", it) }
        cleanUp(removeNotification = true)
        stopSelf()
    }

    @Synchronized
    private fun cleanUp(removeNotification: Boolean) {
        if (!cleanedUp) {
            cleanedUp = true
            handler.removeCallbacks(stopAfterTimeout)

            runCatching { mediaPlayer?.stop() }
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null

            runCatching { vibrator?.cancel() }
            vibrator = null

            audioFocusRequest?.let { request ->
                runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request) }
            }
            audioFocusRequest = null

            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    runCatching { lock.release() }
                }
            }
            wakeLock = null

            if (foregroundStarted) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                foregroundStarted = false
            }
        }

        if (removeNotification) {
            runCatching {
                getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            }.onFailure {
                Log.e(TAG, "Unable to remove alarm notification during cleanup", it)
            }
        }
    }

    private fun handleSystemExemptedFailure(error: Throwable, notification: Notification) {
        Log.e(TAG, "systemExempted foreground service was rejected; leaving notification fallback", error)
        preserveFallbackNotification = true
        cleanUp(removeNotification = false)
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } catch (notificationError: Throwable) {
            Log.e(TAG, "Unable to post fallback alarm notification", notificationError)
            preserveFallbackNotification = false
        }
        stopSelf()
    }

    companion object {
        // Notification channels are immutable after creation. Use a fresh ID for final M0
        // validation after restoring service-owned sound and vibration.
        const val CHANNEL_ID = "medication_alarm_m0_2"
        const val NOTIFICATION_ID = 7001

        private const val ACTION_START = "com.example.medsreminder.action.START_RINGING"
        private const val TAG = "AlarmRingingService"
        private const val RINGING_TIMEOUT_MILLIS = 10 * 60 * 1000L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = RINGING_TIMEOUT_MILLIS + 30_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 700, 500)
        private val ALARM_AUDIO_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun startIntent(context: Context): Intent =
            Intent(context, AlarmRingingService::class.java).setAction(ACTION_START)

        fun ensureNotificationChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) {
                return
            }
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.alarm_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.alarm_channel_description)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }

        fun postFallbackNotification(context: Context, cause: Throwable) {
            Log.e(TAG, "Posting non-service fallback notification", cause)
            ensureNotificationChannel(context)
            try {
                context.getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildAlarmNotification(context))
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to post fallback alarm notification", error)
            }
        }

        private fun buildAlarmNotification(context: Context): Notification {
            val fullScreenIntent = fullScreenPendingIntent(context)
            val actionIcon = Icon.createWithResource(context, R.drawable.ic_alarm)
            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(context.getString(R.string.alarm_notification_title))
                .setContentText(context.getString(R.string.alarm_notification_text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(
                    Notification.Action.Builder(
                        actionIcon,
                        context.getString(R.string.action_taken),
                        AlarmActionReceiver.pendingIntent(context, AlarmActionReceiver.ACTION_TAKEN, 301),
                    ).build(),
                )
                .addAction(
                    Notification.Action.Builder(
                        actionIcon,
                        context.getString(R.string.action_snooze),
                        AlarmActionReceiver.pendingIntent(context, AlarmActionReceiver.ACTION_SNOOZE, 302),
                    ).build(),
                )
                .addAction(
                    Notification.Action.Builder(
                        actionIcon,
                        context.getString(R.string.action_skip),
                        AlarmActionReceiver.pendingIntent(context, AlarmActionReceiver.ACTION_SKIP, 303),
                    ).build(),
                )
                .build()
        }

        private fun fullScreenPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            val options = ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        if (Build.VERSION.SDK_INT >= 36) {
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        } else {
                            @Suppress("DEPRECATION")
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        },
                    )
                }
            }
            return PendingIntent.getActivity(
                context,
                201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                options.toBundle(),
            )
        }
    }
}
