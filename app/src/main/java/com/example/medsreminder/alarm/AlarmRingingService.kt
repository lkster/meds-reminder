package com.example.medsreminder.alarm

import android.app.ActivityOptions
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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.room.withTransaction
import com.example.medsreminder.AlarmActivity
import com.example.medsreminder.R
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.OccurrenceDetails
import com.example.medsreminder.data.OccurrenceStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AlarmRingingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val stopCurrentAfterTimeout = Runnable { timeoutCurrentOccurrence() }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusOccurrenceId: String? = null
    private var audioFocusGeneration: Long? = null
    private var foregroundStarted = false
    private var cleanedUp = false
    private var preserveFallbackNotification = false
    internal var ringingResourcesStarted = false
        private set
    internal var activeOutputOccurrenceId: String? = null
        private set
    internal var activePreferenceSnapshot: AlarmPreferenceSnapshot? = null
        private set
    internal val activeMediaPlayer: MediaPlayer?
        get() = mediaPlayer
    internal val activeAudioFocusRequest: AudioFocusRequest?
        get() = audioFocusRequest
    internal val activeWakeLock: PowerManager.WakeLock?
        get() = wakeLock
    @Volatile private var currentOccurrenceId: String? = null
    @Volatile private var refreshGeneration = 0L
    private var audioPresentationGeneration = 0L

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isStartOrRecovery = intent == null || intent.action == ACTION_START
        if (!isStartOrRecovery) {
            Log.w(TAG, "Ignoring unexpected service action: ${intent?.action}")
            if (foregroundStarted) return START_STICKY
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!foregroundStarted) {
            cleanedUp = false
            preserveFallbackNotification = false
            val scheduler = AlarmScheduler(this)
            if (!scheduler.requiredPresentationReady()) {
                Log.e(TAG, "Actionable alarm notification presentation is unavailable")
                currentOccurrenceId = null
                cleanUp(removeNotification = true)
                serviceScope.launch {
                    runCatching {
                        AppDatabase.get(this@AlarmRingingService)
                            .occurrenceDao()
                            .expireAllRinging(System.currentTimeMillis())
                    }.onFailure { Log.e(TAG, "Unable to fail closed ringing queue", it) }
                    handler.post { stopSelf(startId) }
                }
                return START_NOT_STICKY
            }
            if (!scheduler.canScheduleExactAlarms()) {
                handleSystemExemptedFailure(
                    IllegalStateException("Exact alarm access was unavailable when ringing started"),
                )
                return START_NOT_STICKY
            }

            val selection = selectForFreshForegroundStart()
            val selected = selection?.current
            if (selected == null) {
                if (selection == null) {
                    Log.e(TAG, "Timed out or failed while validating the ringing queue")
                }
                currentOccurrenceId = null
                cleanUp(removeNotification = true)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            val firstAlarmNotification = buildAlarmNotification(
                context = this,
                occurrenceId = selected.occurrenceId,
                medicationName = selected.medicationName,
                instructions = selected.instructions,
                scheduledAtMillis = selected.scheduledAtEpochMillis,
                waitingCount = (selection.count - 1).coerceAtLeast(0),
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        firstAlarmNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, firstAlarmNotification)
                }
                foregroundStarted = true
            } catch (error: Throwable) {
                handleSystemExemptedFailure(error)
                return START_NOT_STICKY
            }

            presentSelection(selection, ++refreshGeneration, notificationAlreadyPosted = true)
            return START_STICKY
        }

        serviceScope.launch { refreshQueue() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        cleanUp(removeNotification = !preserveFallbackNotification)
        super.onDestroy()
    }

    private suspend fun refreshQueue() = refreshMutex.withLock {
        val generation = ++refreshGeneration
        presentSelection(selectCurrentRinging(), generation, notificationAlreadyPosted = false)
    }

    private fun selectForFreshForegroundStart(): QueueSelection? = try {
        runBlocking {
            withTimeoutOrNull(FOREGROUND_VALIDATION_TIMEOUT_MILLIS) {
                withContext(Dispatchers.IO) {
                    refreshMutex.withLock { selectCurrentRinging() }
                }
            }
        }
    } catch (error: Throwable) {
        Log.e(TAG, "Unable to validate the ringing queue before foreground promotion", error)
        null
    }

    private suspend fun selectCurrentRinging(): QueueSelection {
        val database = AppDatabase.get(this)
        var current: OccurrenceDetails? = null
        var count = 0
        database.withTransaction {
            val dao = database.occurrenceDao()
            current = dao.getCurrentRinging()
            current?.let { candidate ->
                dao.markPresented(candidate.occurrenceId, System.currentTimeMillis())
                dao.clearOtherPresented(candidate.occurrenceId)
                current = dao.getDetails(candidate.occurrenceId)
            }
            count = dao.ringingCount()
        }
        return QueueSelection(current, count)
    }

    private fun presentSelection(
        selection: QueueSelection,
        generation: Long,
        notificationAlreadyPosted: Boolean,
    ) {
        val selected = selection.current
        if (selected == null) {
            handler.post {
                if (generation != refreshGeneration) return@post
                currentOccurrenceId = null
                cleanUp(removeNotification = true)
                stopSelf()
            }
            return
        }

        val presentedAt = selected.presentedAtEpochMillis ?: System.currentTimeMillis()
        val remaining = (presentedAt + RINGING_TIMEOUT_MILLIS - System.currentTimeMillis())
            .coerceAtLeast(0L)
        val notification = buildAlarmNotification(
            context = this,
            occurrenceId = selected.occurrenceId,
            medicationName = selected.medicationName,
            instructions = selected.instructions,
            scheduledAtMillis = selected.scheduledAtEpochMillis,
            waitingCount = (selection.count - 1).coerceAtLeast(0),
        )
        handler.post {
            if (generation != refreshGeneration) return@post
            if (!cleanedUp) {
                val previousOccurrenceId = currentOccurrenceId
                val occurrenceChanged = previousOccurrenceId != selected.occurrenceId
                if (occurrenceChanged && previousOccurrenceId != null) {
                    stopConfigurableRingingOutput()
                }
                currentOccurrenceId = selected.occurrenceId
                if (!notificationAlreadyPosted) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
                }
                handler.removeCallbacks(stopCurrentAfterTimeout)
                handler.postDelayed(stopCurrentAfterTimeout, remaining)
                val startedNow = ensureSessionResourcesStarted()
                if (occurrenceChanged && !startedNow) refreshWakeLockTimeout()
                if (occurrenceChanged || activeOutputOccurrenceId == null) {
                    val preferences = AlarmPreferences.read(this@AlarmRingingService)
                    startConfigurableRingingOutput(selected.occurrenceId, preferences)
                }
            }
        }
    }

    private data class QueueSelection(
        val current: OccurrenceDetails?,
        val count: Int,
    )

    private fun ensureSessionResourcesStarted(): Boolean {
        if (ringingResourcesStarted) return false
        ringingResourcesStarted = true
        acquireWakeLock()
        return true
    }

    private fun startConfigurableRingingOutput(
        occurrenceId: String,
        preferences: AlarmPreferenceSnapshot,
    ) {
        activeOutputOccurrenceId = occurrenceId
        activePreferenceSnapshot = preferences
        val generation = ++audioPresentationGeneration
        if (preferences.vibrationEnabled) startVibration()
        startAlarmAudio(occurrenceId, generation, preferences.selectedSoundUri)
    }

    private fun timeoutCurrentOccurrence() {
        val occurrenceId = currentOccurrenceId ?: return
        serviceScope.launch {
            val dao = AppDatabase.get(this@AlarmRingingService).occurrenceDao()
            val current = dao.get(occurrenceId)
            val deadline = current?.presentedAtEpochMillis?.plus(RINGING_TIMEOUT_MILLIS)
            if (current?.status == OccurrenceStatus.RINGING &&
                deadline != null && deadline <= System.currentTimeMillis()
            ) {
                dao.transition(
                    occurrenceId,
                    OccurrenceStatus.RINGING,
                    OccurrenceStatus.TIMED_OUT,
                    System.currentTimeMillis(),
                )
            }
            refreshQueue()
        }
    }

    private fun acquireWakeLock() {
        try {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:m1-alarm")
                .apply {
                    setReferenceCounted(false)
                    acquire(WAKE_LOCK_TIMEOUT_MILLIS)
                }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to acquire alarm wake lock", error)
        }
    }

    private fun refreshWakeLockTimeout() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
                lock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to refresh alarm wake lock", error)
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

    private fun startAlarmAudio(
        occurrenceId: String,
        generation: Long,
        selectedSoundUri: Uri?,
    ) {
        val candidates = AlarmPreferences.soundCandidates(
            selectedSoundUri,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        )
        if (candidates.isEmpty()) {
            Log.e(TAG, "No default system alarm tone is configured; using vibration only")
            return
        }
        val audioManager = getSystemService(AudioManager::class.java)
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(ALARM_AUDIO_ATTRIBUTES)
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = focusRequest
        audioFocusOccurrenceId = occurrenceId
        audioFocusGeneration = generation
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.w(TAG, "Alarm audio focus request was denied; attempting playback anyway")
        }
        startSoundCandidate(candidates, 0, occurrenceId, generation)
    }

    private fun startSoundCandidate(
        candidates: List<Uri>,
        candidateIndex: Int,
        occurrenceId: String,
        generation: Long,
    ) {
        if (!isCurrentAudioPresentation(occurrenceId, generation)) return
        val alarmUri = candidates.getOrNull(candidateIndex)
        if (alarmUri == null) {
            Log.e(TAG, "Unable to play an alarm tone; using vibration only")
            abandonAudioFocusIfOwned(occurrenceId, generation)
            return
        }
        val player = try {
            MediaPlayer()
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to create alarm player for $alarmUri", error)
            if (isCurrentAudioPresentation(occurrenceId, generation)) {
                startSoundCandidate(candidates, candidateIndex + 1, occurrenceId, generation)
            }
            return
        }
        try {
            player.setAudioAttributes(ALARM_AUDIO_ATTRIBUTES)
            player.setDataSource(this, alarmUri)
            player.isLooping = true
            player.setOnErrorListener { callbackPlayer, what, extra ->
                handler.post {
                    handlePlayerError(
                        player = callbackPlayer,
                        what = what,
                        extra = extra,
                        candidates = candidates,
                        candidateIndex = candidateIndex,
                        occurrenceId = occurrenceId,
                        generation = generation,
                    )
                }
                true
            }
            mediaPlayer = player
            player.prepare()
            player.start()
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to initialize alarm tone $alarmUri", error)
            if (mediaPlayer === player) mediaPlayer = null
            releasePlayer(player)
            if (isCurrentAudioPresentation(occurrenceId, generation)) {
                startSoundCandidate(candidates, candidateIndex + 1, occurrenceId, generation)
            }
        }
    }

    private fun handlePlayerError(
        player: MediaPlayer,
        what: Int,
        extra: Int,
        candidates: List<Uri>,
        candidateIndex: Int,
        occurrenceId: String,
        generation: Long,
    ) {
        if (mediaPlayer !== player || !isCurrentAudioPresentation(occurrenceId, generation)) {
            return
        }
        Log.e(TAG, "Alarm audio playback error what=$what extra=$extra")
        mediaPlayer = null
        releasePlayer(player)
        startSoundCandidate(candidates, candidateIndex + 1, occurrenceId, generation)
    }

    private fun isCurrentAudioPresentation(occurrenceId: String, generation: Long): Boolean =
        currentOccurrenceId == occurrenceId &&
            activeOutputOccurrenceId == occurrenceId &&
            audioPresentationGeneration == generation

    private fun releasePlayer(player: MediaPlayer) {
        runCatching { player.setOnErrorListener(null) }
        runCatching { player.stop() }
        runCatching { player.release() }
    }

    private fun abandonAudioFocusIfOwned(occurrenceId: String, generation: Long) {
        val request = audioFocusRequest ?: return
        if (audioFocusOccurrenceId != occurrenceId || audioFocusGeneration != generation) return
        runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request) }
        if (audioFocusRequest === request &&
            audioFocusOccurrenceId == occurrenceId && audioFocusGeneration == generation
        ) {
            audioFocusRequest = null
            audioFocusOccurrenceId = null
            audioFocusGeneration = null
        }
    }

    private fun stopConfigurableRingingOutput() {
        ++audioPresentationGeneration
        mediaPlayer?.let(::releasePlayer)
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        audioFocusRequest?.let { request ->
            runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request) }
        }
        audioFocusRequest = null
        audioFocusOccurrenceId = null
        audioFocusGeneration = null
        activeOutputOccurrenceId = null
        activePreferenceSnapshot = null
    }

    @Synchronized
    private fun cleanUp(removeNotification: Boolean) {
        if (!cleanedUp) {
            cleanedUp = true
            handler.removeCallbacks(stopCurrentAfterTimeout)
            stopConfigurableRingingOutput()
            wakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
            wakeLock = null
            ringingResourcesStarted = false
            if (foregroundStarted) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                foregroundStarted = false
            }
        }
        if (removeNotification) {
            runCatching { getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
                .onFailure { Log.e(TAG, "Unable to remove alarm notification", it) }
        }
    }

    private fun handleSystemExemptedFailure(error: Throwable) {
        Log.e(TAG, "systemExempted foreground service was rejected", error)
        cleanUp(removeNotification = false)
        serviceScope.launch {
            val current = AppDatabase.get(this@AlarmRingingService)
                .occurrenceDao()
                .getCurrentRinging()
            handler.post {
                if (current != null) {
                    preserveFallbackNotification = true
                    postFallbackNotification(this@AlarmRingingService, current, error)
                } else {
                    preserveFallbackNotification = false
                    getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                }
                stopSelf()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "medication_alarm_m0_2"
        const val NOTIFICATION_ID = 7001

        private const val ACTION_START = "com.example.medsreminder.action.START_RINGING"
        private const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        private const val EXTRA_MEDICATION_NAME = "medication_name"
        private const val EXTRA_INSTRUCTIONS = "instructions"
        private const val EXTRA_SCHEDULED_AT = "scheduled_at"
        private const val TAG = "AlarmRingingService"
        private const val FOREGROUND_VALIDATION_TIMEOUT_MILLIS = 2_000L
        private const val RINGING_TIMEOUT_MILLIS = 10 * 60 * 1000L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = RINGING_TIMEOUT_MILLIS + 30_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 700, 500)
        private val ALARM_AUDIO_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        fun startIntent(context: Context, occurrence: OccurrenceDetails): Intent =
            Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OCCURRENCE_ID, occurrence.occurrenceId)
                putExtra(EXTRA_MEDICATION_NAME, occurrence.medicationName)
                putExtra(EXTRA_INSTRUCTIONS, occurrence.instructions)
                putExtra(EXTRA_SCHEDULED_AT, occurrence.scheduledAtEpochMillis)
            }

        suspend fun synchronizeWithPersistedQueue(
            context: Context,
            database: AppDatabase = AppDatabase.get(context),
        ) {
            val current = database.occurrenceDao().getCurrentRinging()
            if (current == null) {
                stopAndRemoveNotification(context)
                return
            }
            try {
                context.startForegroundService(startIntent(context, current))
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to start or refresh ringing service", error)
                postFallbackNotification(context, current, error)
            }
        }

        fun stopAndRemoveNotification(context: Context) {
            runCatching { context.stopService(Intent(context, AlarmRingingService::class.java)) }
                .onFailure { Log.e(TAG, "Unable to stop ringing service", it) }
            runCatching {
                context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            }.onFailure { Log.e(TAG, "Unable to remove ringing notification", it) }
        }

        fun ensureNotificationChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            // Recreating the same channel is idempotent and lets an M0 install pick up the M1
            // user-visible name/description without changing its immutable sound behavior.
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

        fun postFallbackNotification(context: Context, occurrence: OccurrenceDetails, cause: Throwable) {
            Log.e(TAG, "Posting non-service fallback notification", cause)
            ensureNotificationChannel(context)
            runCatching {
                context.getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID,
                    buildAlarmNotification(
                        context,
                        occurrence.occurrenceId,
                        occurrence.medicationName,
                        occurrence.instructions,
                        occurrence.scheduledAtEpochMillis,
                        0,
                    ),
                )
            }.onFailure { Log.e(TAG, "Unable to post fallback alarm notification", it) }
        }

        private fun buildAlarmNotification(
            context: Context,
            occurrenceId: String?,
            medicationName: String,
            instructions: String?,
            scheduledAtMillis: Long,
            waitingCount: Int,
        ): Notification {
            val sessionIntent = sessionPendingIntent(context)
            val time = TIME_FORMATTER.format(
                Instant.ofEpochMilli(scheduledAtMillis).atZone(ZoneId.systemDefault()),
            )
            val text = buildString {
                append(instructions?.takeIf { it.isNotBlank() } ?: "Scheduled for $time")
                if (waitingCount > 0) append(" · $waitingCount more waiting")
            }
            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(medicationName)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

            if (occurrenceId != null) {
                builder.setContentIntent(sessionIntent)
                builder.setFullScreenIntent(sessionIntent, true)
                val icon = Icon.createWithResource(context, R.drawable.ic_alarm)
                builder.addAction(
                    Notification.Action.Builder(
                        icon,
                        context.getString(R.string.action_taken),
                        AlarmActionReceiver.pendingIntent(context, AlarmActionReceiver.ACTION_TAKEN, occurrenceId),
                    ).build(),
                ).addAction(
                    Notification.Action.Builder(
                        icon,
                        context.getString(R.string.action_snooze),
                        AlarmActionReceiver.pendingIntent(context, AlarmActionReceiver.ACTION_SNOOZE, occurrenceId),
                    ).build(),
                ).addAction(
                    Notification.Action.Builder(
                        icon,
                        context.getString(R.string.action_skip),
                        AlarmActionReceiver.pendingIntent(context, AlarmActionReceiver.ACTION_SKIP, occurrenceId),
                    ).build(),
                )
            }
            return builder.build()
        }

        private fun sessionPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AlarmActivity::class.java).apply {
                action = "com.example.medsreminder.action.OPEN_RINGING_SESSION"
                data = Uri.parse("medsreminder://ringing/session")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
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
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                options.toBundle(),
            )
        }
    }
}
