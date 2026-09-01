package com.example.medsreminder

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.lifecycleScope
import com.example.medsreminder.alarm.AlarmRingingService
import com.example.medsreminder.alarm.AlarmPreferenceSnapshot
import com.example.medsreminder.alarm.AlarmPreferences
import com.example.medsreminder.alarm.AlarmReconciler
import com.example.medsreminder.alarm.AlarmScheduler
import com.example.medsreminder.alarm.ReconciliationMode
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.HistoryOccurrence
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.MedicationDeleteResult
import com.example.medsreminder.data.applyMedicationDelete
import com.example.medsreminder.data.applyMedicationListToggle
import com.example.medsreminder.ui.CapabilityItem
import com.example.medsreminder.ui.HistoryItem
import com.example.medsreminder.ui.HistoryScreen
import com.example.medsreminder.ui.MedicationEditorViewModel
import com.example.medsreminder.ui.MedicationEditorScreen
import com.example.medsreminder.ui.MedicationListScreen
import com.example.medsreminder.ui.MedicationEditorViewModelTestHook
import com.example.medsreminder.ui.toHistoryItem
import java.time.ZoneId
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.get(this) }
    private val scheduler by lazy { AlarmScheduler(applicationContext) }
    private val reconciler by lazy { AlarmReconciler(applicationContext, database, scheduler) }

    // Null until this Activity receives its first authoritative Room emission.
    private var medications by mutableStateOf<List<MedicationWithTimes>?>(null)
    private var history by mutableStateOf<List<HistoryItem>>(emptyList())
    private lateinit var editorOwner: MedicationEditorViewModel
    private var capabilities by mutableStateOf(CapabilityState())
    private var alarmPreferences by mutableStateOf(
        AlarmPreferenceSnapshot(
            selectedSoundUri = null,
            vibrationEnabled = true,
            snoozeMinutes = AlarmPreferences.DEFAULT_SNOOZE_MINUTES,
        ),
    )
    private var alarmSoundLabel by mutableStateOf("System default")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editorOwner = MedicationEditorViewModelTestHook.factory?.let { testFactory ->
            ViewModelProvider(this, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    check(modelClass == MedicationEditorViewModel::class.java)
                    return testFactory(application, extras.createSavedStateHandle()) as T
                }
            })[MedicationEditorViewModel::class.java]
        } ?: ViewModelProvider(this)[MedicationEditorViewModel::class.java]
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editorOwner.draft != null) {
                    // cancelIdleEditor intentionally consumes Back while an operation is active.
                    editorOwner.cancelIdleEditor()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        AlarmRingingService.ensureNotificationChannel(this)
        refreshCapabilities()
        refreshAlarmPreferences()
        lifecycleScope.launch {
            MedicationListLoadingTestHook.beforeInitialRoomCollection(applicationContext)
            database.medicationDao().observeAll().collectLatest {
                medications = it
                MedicationListLoadingTestHook.markInitialRoomEmission(applicationContext)
            }
        }
        lifecycleScope.launch {
            database.occurrenceDao().observeHistory().collectLatest { occurrences ->
                history = occurrences.map(HistoryOccurrence::toHistoryItem)
            }
        }
        setContent {
            MaterialTheme {
                Surface { MainContent() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCapabilities()
        refreshAlarmPreferences()
        lifecycleScope.launch(Dispatchers.IO) {
            synchronizeRingingOnResume(this@MainActivity, database, scheduler, reconciler)
        }
    }

    @Composable
    private fun MainContent() {
        var historyVisible by rememberSaveable { mutableStateOf(false) }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { refreshCapabilities() }
        val ringtonePickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val pickedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                        Uri::class.java,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
                if (pickedUri != null) {
                    AlarmPreferences.setSoundUri(this, pickedUri)
                    refreshAlarmPreferences()
                }
            }
        }
        val currentEditor = editorOwner.draft
        if (currentEditor != null) {
            MedicationEditorScreen(
                draft = currentEditor,
                onDraftChange = editorOwner::updateDraft,
                onSave = { editorOwner.submit() },
                onCancel = editorOwner::cancelIdleEditor,
                saveState = editorOwner.saveState,
                onRetryPostCommit = editorOwner::retryPostCommitCompletion,
                handleSystemBack = false,
            )
            return
        }
        if (historyVisible) {
            HistoryScreen(history = history, onBack = { historyVisible = false })
            return
        }

        val capabilityItems = listOf(
            buildNotificationCapabilityItem(
                runtimePermissionReady = capabilities.runtimeNotificationPermissionGranted,
                appNotificationsEnabled = capabilities.appNotificationsEnabled,
                onRequestRuntimePermission = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onOpenNotificationSettings = ::openAppNotificationSettings,
            ),
            CapabilityItem(
                "Alarm channel",
                capabilities.channelHighImportance,
                "The Medication alarms channel must remain at the high importance required for actionable heads-up alarm presentation.",
                "Open channel settings",
                ::openAlarmChannelSettings,
            ),
            CapabilityItem(
                "Exact alarms",
                capabilities.exactAlarmsAllowed,
                "Exact alarm access is required to register reminders for their configured time. Saved medication schedules remain stored and can be reconciled when access returns.",
                "Open alarm access",
                ::openExactAlarmSettings,
            ),
            CapabilityItem(
                "Full-screen alarm",
                capabilities.fullScreenAllowed,
                "Reminders can still use the actionable alarm notification, but the full-screen alarm may not appear over the lock screen.",
                "Open full-screen access",
                ::openFullScreenIntentSettings,
                requiredForReliableDelivery = false,
            ),
        )
        MedicationListScreen(
            medications = medications,
            capabilityItems = capabilityItems,
            alarmSoundLabel = alarmSoundLabel,
            vibrationEnabled = alarmPreferences.vibrationEnabled,
            snoozeMinutes = alarmPreferences.snoozeMinutes,
            showSamsungGuidance = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
            onSamsungSettings = ::openAppNotificationSettings,
            onChooseAlarmSound = {
                ringtonePickerLauncher.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                            AlarmPreferences.defaultAlarmUri(),
                        )
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            AlarmPreferences.pickerExistingUri(this@MainActivity),
                        )
                    },
                )
            },
            onVibrationEnabledChange = { enabled ->
                AlarmPreferences.setVibrationEnabled(this, enabled)
                refreshAlarmPreferences()
            },
            onSnoozeMinutesChange = { minutes ->
                AlarmPreferences.setSnoozeMinutes(this, minutes)
                refreshAlarmPreferences()
            },
            onHistory = { historyVisible = true },
            onAdd = editorOwner::openNew,
            onEdit = editorOwner::openExisting,
            onToggle = ::saveMedicationListToggle,
            onDelete = ::deleteMedication,
        )
    }

    /** List enable/disable deliberately remains an independent Activity operation. */
    private fun saveMedicationListToggle(item: MedicationWithTimes, enabled: Boolean) {
        val medicationId = item.medication.id
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val outcome = withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    completeMedicationListToggle(medicationId, enabled)
                }
            }
            deliverMedicationListToggleFeedback(outcome, isActive)
        }
    }

    private suspend fun completeMedicationListToggle(
        medicationId: Long,
        enabled: Boolean,
    ): MedicationListToggleOutcome {
        val result = try {
            MedicationListToggleTestHook.beforeRoom(applicationContext)
            MedicationListToggleTestHook.incrementPhaseA(applicationContext)
            database.applyMedicationListToggle(
                medicationId = medicationId,
                enabled = enabled,
                nowMillis = System.currentTimeMillis(),
                zoneId = ZoneId.systemDefault(),
            )
        } catch (error: Throwable) {
            Log.e(TAG, "Medication-list toggle failed before Room persistence", error)
            return MedicationListToggleOutcome.PersistenceFailure(error)
        }
        if (result == null) return MedicationListToggleOutcome.Stale

        return try {
            MedicationListToggleTestHook.afterRoomBeforePhaseB(applicationContext)
            MedicationListToggleTestHook.incrementPhaseB(applicationContext)
            result.obsoleteOccurrenceIds.forEach(scheduler::cancelOccurrence)
            reconciler.reconcile(ReconciliationMode.ROUTINE)
            if (result.refreshRinging) {
                AlarmRingingService.synchronizeWithPersistedQueue(applicationContext, database)
            }
            MedicationListToggleOutcome.Success
        } catch (error: Throwable) {
            Log.e(TAG, "Medication-list toggle persisted, but alarm completion failed", error)
            MedicationListToggleOutcome.AlarmCompletionFailure(error)
        }
    }

    private fun deliverMedicationListToggleFeedback(
        outcome: MedicationListToggleOutcome,
        lifecycleCoroutineActive: Boolean,
    ) {
        val canDeliver = lifecycleCoroutineActive && !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        MedicationListToggleTestHook.onFeedbackEligibility(applicationContext, canDeliver)
        if (!canDeliver) return
        when (outcome) {
            is MedicationListToggleOutcome.PersistenceFailure -> Toast.makeText(
                this,
                "Could not update medication: ${outcome.error.message}",
                Toast.LENGTH_LONG,
            ).show()
            is MedicationListToggleOutcome.AlarmCompletionFailure -> Toast.makeText(
                this,
                "Medication updated, but alarms could not be refreshed. Alarm setup will be retried when Meds Reminder resumes.",
                Toast.LENGTH_LONG,
            ).show()
            MedicationListToggleOutcome.Success,
            MedicationListToggleOutcome.Stale,
            -> Unit
        }
    }

    /** An accepted deletion is a finite Room-then-projection operation owned by this Activity. */
    private fun deleteMedication(medicationId: Long) {
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val outcome = withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    completeMedicationDelete(medicationId)
                }
            }
            deliverMedicationDeleteFeedback(outcome, isActive)
        }
    }

    private suspend fun completeMedicationDelete(medicationId: Long): MedicationDeleteOutcome {
        val result = try {
            MedicationDeleteTestHook.beforeRoom(applicationContext)
            MedicationDeleteTestHook.incrementPhaseA(applicationContext)
            database.applyMedicationDelete(medicationId)
        } catch (error: Throwable) {
            Log.e(TAG, "Medication deletion failed before Room persistence", error)
            return MedicationDeleteOutcome.PersistenceFailure(error)
        }
        if (result is MedicationDeleteResult.Stale) return MedicationDeleteOutcome.Stale
        result as MedicationDeleteResult.Deleted

        return try {
            MedicationDeleteTestHook.afterRoomBeforePhaseB(applicationContext)
            MedicationDeleteTestHook.incrementPhaseB(applicationContext)
            result.obsoleteOccurrenceIds.forEach(scheduler::cancelOccurrence)
            reconciler.reconcile(ReconciliationMode.ROUTINE)
            if (result.refreshRinging) {
                AlarmRingingService.synchronizeWithPersistedQueue(applicationContext, database)
                MedicationDeleteTestHook.markRingingSynchronizationCompleted(applicationContext)
            }
            MedicationDeleteTestHook.markPhaseBCompleted(applicationContext)
            MedicationDeleteOutcome.Success
        } catch (error: Throwable) {
            Log.e(TAG, "Medication deleted, but alarm completion failed", error)
            MedicationDeleteOutcome.AlarmCompletionFailure(error)
        }
    }

    private fun deliverMedicationDeleteFeedback(
        outcome: MedicationDeleteOutcome,
        lifecycleCoroutineActive: Boolean,
    ) {
        val canDeliver = lifecycleCoroutineActive && !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        MedicationDeleteTestHook.onFeedbackEligibility(applicationContext, canDeliver)
        if (!canDeliver) return
        when (outcome) {
            is MedicationDeleteOutcome.PersistenceFailure -> Toast.makeText(
                this,
                "Could not delete medication: ${outcome.error.message}",
                Toast.LENGTH_LONG,
            ).show()
            is MedicationDeleteOutcome.AlarmCompletionFailure -> Toast.makeText(
                this,
                "Medication deleted, but alarms could not be fully cleaned up. Alarm setup will be retried when Meds Reminder resumes.",
                Toast.LENGTH_LONG,
            ).show()
            MedicationDeleteOutcome.Success,
            MedicationDeleteOutcome.Stale,
            -> Unit
        }
    }

    /** Test-only target-side visibility check for the narrow M8 filesystem control. */
    internal fun isMedicationListToggleTestControlActive(): Boolean =
        MedicationListToggleTestHook.isActive(applicationContext)

    /** Test-only target-side visibility check for the narrow M9 filesystem control. */
    internal fun isMedicationDeleteTestControlActive(): Boolean =
        MedicationDeleteTestHook.isActive(applicationContext)

    private fun refreshCapabilities() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = notificationManager.getNotificationChannel(AlarmRingingService.CHANNEL_ID)
        capabilities = CapabilityState(
            runtimeNotificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            appNotificationsEnabled = notificationManager.areNotificationsEnabled(),
            channelHighImportance = channel?.importance?.let { it >= NotificationManager.IMPORTANCE_HIGH } == true,
            exactAlarmsAllowed = getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            fullScreenAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                notificationManager.canUseFullScreenIntent(),
        )
    }

    private fun refreshAlarmPreferences() {
        alarmPreferences = AlarmPreferences.read(this)
        val selectedUri = alarmPreferences.selectedSoundUri
        alarmSoundLabel = if (selectedUri == null) {
            "System default"
        } else {
            runCatching {
                val ringtone = RingtoneManager.getRingtone(this, selectedUri)
                try {
                    ringtone?.getTitle(this)
                } finally {
                    runCatching { ringtone?.stop() }
                }
            }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "Selected alarm sound"
        }
    }

    private fun openExactAlarmSettings() = startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:$packageName")),
    )

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    private fun openAppNotificationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    private fun openAlarmChannelSettings() = startActivity(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmRingingService.CHANNEL_ID),
    )

    private data class CapabilityState(
        val runtimeNotificationPermissionGranted: Boolean = false,
        val appNotificationsEnabled: Boolean = false,
        val channelHighImportance: Boolean = false,
        val exactAlarmsAllowed: Boolean = false,
        val fullScreenAllowed: Boolean = false,
    )
}

internal fun buildNotificationCapabilityItem(
    runtimePermissionReady: Boolean,
    appNotificationsEnabled: Boolean,
    onRequestRuntimePermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
): CapabilityItem = when {
    !runtimePermissionReady -> CapabilityItem(
        title = "Notifications",
        ready = false,
        detail = "Notification permission is required so medication alarms can show their actionable notification.",
        actionLabel = "Allow notifications",
        onAction = onRequestRuntimePermission,
    )
    !appNotificationsEnabled -> CapabilityItem(
        title = "Notifications",
        ready = false,
        detail = "Notifications are disabled for Meds Reminder in Android settings, so medication alarms cannot show their actionable notification.",
        actionLabel = "Open notification settings",
        onAction = onOpenNotificationSettings,
    )
    else -> CapabilityItem(
        title = "Notifications",
        ready = true,
        detail = "Notifications are ready for the actionable alarm notification.",
        actionLabel = "",
        onAction = {},
    )
}

private sealed interface MedicationListToggleOutcome {
    data object Success : MedicationListToggleOutcome
    data object Stale : MedicationListToggleOutcome
    data class PersistenceFailure(val error: Throwable) : MedicationListToggleOutcome
    data class AlarmCompletionFailure(val error: Throwable) : MedicationListToggleOutcome
}

private sealed interface MedicationDeleteOutcome {
    data object Success : MedicationDeleteOutcome
    data object Stale : MedicationDeleteOutcome
    data class PersistenceFailure(val error: Throwable) : MedicationDeleteOutcome
    data class AlarmCompletionFailure(val error: Throwable) : MedicationDeleteOutcome
}

/** Narrow M11 test control for the initial real medication Room Flow collection. */
internal object MedicationListLoadingTestHook {
    private const val DIRECTORY = "m11-medication-list-loading-test-hook"
    private const val ACTIVE = "active"
    private const val HOLD_BEFORE_COLLECTION = "hold-before-collection"
    private const val BEFORE_COLLECTION_REACHED = "before-collection-reached"
    private const val FIRST_ROOM_EMISSION = "first-room-emission"
    private const val HOLD_TIMEOUT_MILLIS = 15_000L

    fun configure(context: Context, holdBeforeCollection: Boolean = false) {
        reset(context)
        directory(context).mkdirs()
        touch(context, ACTIVE)
        if (holdBeforeCollection) touch(context, HOLD_BEFORE_COLLECTION)
    }

    fun reset(context: Context) { directory(context).deleteRecursively() }
    fun releaseCollection(context: Context) {
        val holdFile = file(context, HOLD_BEFORE_COLLECTION)
        check(!holdFile.exists() || holdFile.delete()) { "M11 test hook release could not clear its hold" }
    }
    fun beforeCollectionReached(context: Context): Boolean =
        file(context, BEFORE_COLLECTION_REACHED).exists()
    fun firstRoomEmissionReceived(context: Context): Boolean = file(context, FIRST_ROOM_EMISSION).exists()

    suspend fun beforeInitialRoomCollection(context: Context) {
        if (!file(context, ACTIVE).exists()) return
        withContext(Dispatchers.IO) {
            touch(context, BEFORE_COLLECTION_REACHED)
            if (!file(context, HOLD_BEFORE_COLLECTION).exists()) return@withContext
            val deadline = SystemClock.elapsedRealtime() + HOLD_TIMEOUT_MILLIS
            while (file(context, HOLD_BEFORE_COLLECTION).exists()) {
                if (!file(context, ACTIVE).exists()) return@withContext
                check(SystemClock.elapsedRealtime() < deadline) { "M11 test hook was not released" }
                delay(10)
            }
        }
    }

    fun markInitialRoomEmission(context: Context) {
        if (file(context, ACTIVE).exists()) touch(context, FIRST_ROOM_EMISSION)
    }

    private fun touch(context: Context, key: String) {
        directory(context).mkdirs()
        file(context, key).writeText("")
    }

    private fun directory(context: Context) = File(context.applicationContext.filesDir, DIRECTORY)
    private fun file(context: Context, key: String) = File(directory(context), key)
}

/**
 * Narrow test-only observation/hold points for the real list-toggle operation.
 *
 * Instrumentation test and target APK classes are not guaranteed to share an object instance or
 * preference cache. Controls are therefore filesystem sentinels in target-app private storage.
 * Production has no control directory and each observation is then a no-op.
 */
internal object MedicationListToggleTestHook {
    private const val DIRECTORY = "m8-list-toggle-test-hook"
    private const val ACTIVE = "active"
    private const val HOLD_BEFORE_ROOM = "hold-before-room"
    private const val HOLD_AFTER_ROOM = "hold-after-room"
    private const val RELEASE_BEFORE_ROOM = "release-before-room"
    private const val RELEASE_AFTER_ROOM = "release-after-room"
    private const val FAIL_AFTER_ROOM = "fail-after-room"
    private const val BEFORE_ROOM_REACHED = "before-room-reached"
    private const val AFTER_ROOM_REACHED = "after-room-reached"
    private const val PHASE_A_CALLS = "phase-a-calls"
    private const val PHASE_B_CALLS = "phase-b-calls"
    private const val FEEDBACK_SEEN = "feedback-seen"
    private const val FEEDBACK_ELIGIBLE = "feedback-eligible"
    private const val HOLD_TIMEOUT_MILLIS = 15_000L

    fun configure(
        context: Context,
        holdBeforeRoom: Boolean = false,
        holdAfterRoom: Boolean = false,
        failAfterRoom: Boolean = false,
    ) {
        reset(context)
        directory(context).mkdirs()
        touch(context, ACTIVE)
        if (holdBeforeRoom) touch(context, HOLD_BEFORE_ROOM)
        if (holdAfterRoom) touch(context, HOLD_AFTER_ROOM)
        if (failAfterRoom) touch(context, FAIL_AFTER_ROOM)
    }

    fun reset(context: Context) {
        directory(context).deleteRecursively()
    }

    fun releaseBeforeRoom(context: Context) {
        touch(context, RELEASE_BEFORE_ROOM)
    }

    fun releaseAfterRoom(context: Context) {
        touch(context, RELEASE_AFTER_ROOM)
    }

    fun beforeRoomReached(context: Context): Boolean = file(context, BEFORE_ROOM_REACHED).exists()
    fun afterRoomReached(context: Context): Boolean = file(context, AFTER_ROOM_REACHED).exists()
    fun phaseACalls(context: Context): Int = value(context, PHASE_A_CALLS)
    fun phaseBCalls(context: Context): Int = value(context, PHASE_B_CALLS)
    fun feedbackSeen(context: Context): Boolean = file(context, FEEDBACK_SEEN).exists()
    fun feedbackEligible(context: Context): Boolean = file(context, FEEDBACK_ELIGIBLE).readTextOrNull() == "true"
    fun isActive(context: Context): Boolean = file(context, ACTIVE).exists()

    suspend fun beforeRoom(context: Context) {
        mark(context, BEFORE_ROOM_REACHED)
        awaitRelease(context, HOLD_BEFORE_ROOM, RELEASE_BEFORE_ROOM)
    }

    suspend fun afterRoomBeforePhaseB(context: Context) {
        mark(context, AFTER_ROOM_REACHED)
        awaitRelease(context, HOLD_AFTER_ROOM, RELEASE_AFTER_ROOM)
        if (file(context, FAIL_AFTER_ROOM).exists()) {
            throw IllegalStateException("Test alarm completion failure")
        }
    }

    fun incrementPhaseA(context: Context) = increment(context, PHASE_A_CALLS)
    fun incrementPhaseB(context: Context) = increment(context, PHASE_B_CALLS)

    fun onFeedbackEligibility(context: Context, eligible: Boolean) {
        if (!isActive(context)) return
        touch(context, FEEDBACK_SEEN)
        file(context, FEEDBACK_ELIGIBLE).writeText(eligible.toString())
    }

    private suspend fun awaitRelease(context: Context, holdKey: String, releaseKey: String) {
        if (!isActive(context) || !file(context, holdKey).exists()) return
        val deadline = SystemClock.elapsedRealtime() + HOLD_TIMEOUT_MILLIS
        while (!file(context, releaseKey).exists()) {
            if (!isActive(context)) return
            check(SystemClock.elapsedRealtime() < deadline) { "M8 test hook was not released" }
            delay(10)
        }
    }

    private fun mark(context: Context, key: String) {
        if (isActive(context)) touch(context, key)
    }

    private fun increment(context: Context, key: String) {
        if (!isActive(context)) return
        file(context, key).writeText((value(context, key) + 1).toString())
    }

    private fun value(context: Context, key: String): Int =
        file(context, key).readTextOrNull()?.toIntOrNull() ?: 0

    private fun touch(context: Context, key: String) {
        directory(context).mkdirs()
        file(context, key).writeText("")
    }

    private fun directory(context: Context) = File(context.applicationContext.filesDir, DIRECTORY)
    private fun file(context: Context, key: String) = File(directory(context), key)
    private fun File.readTextOrNull(): String? = if (exists()) readText() else null
}

/** Narrow M9 delete-operation test control; production has no control directory. */
internal object MedicationDeleteTestHook {
    private const val DIRECTORY = "m9-medication-delete-test-hook"
    private const val ACTIVE = "active"
    private const val HOLD_BEFORE_ROOM = "hold-before-room"
    private const val HOLD_AFTER_ROOM = "hold-after-room"
    private const val RELEASE_BEFORE_ROOM = "release-before-room"
    private const val RELEASE_AFTER_ROOM = "release-after-room"
    private const val FAIL_AFTER_ROOM = "fail-after-room"
    private const val BEFORE_ROOM_REACHED = "before-room-reached"
    private const val AFTER_ROOM_REACHED = "after-room-reached"
    private const val PHASE_A_CALLS = "phase-a-calls"
    private const val PHASE_B_CALLS = "phase-b-calls"
    private const val PHASE_B_COMPLETIONS = "phase-b-completions"
    private const val RINGING_SYNCHRONIZATION_COMPLETIONS = "ringing-synchronization-completions"
    private const val FEEDBACK_SEEN = "feedback-seen"
    private const val FEEDBACK_ELIGIBLE = "feedback-eligible"
    private const val HOLD_TIMEOUT_MILLIS = 15_000L

    fun configure(
        context: Context,
        holdBeforeRoom: Boolean = false,
        holdAfterRoom: Boolean = false,
        failAfterRoom: Boolean = false,
    ) {
        reset(context)
        directory(context).mkdirs()
        touch(context, ACTIVE)
        if (holdBeforeRoom) touch(context, HOLD_BEFORE_ROOM)
        if (holdAfterRoom) touch(context, HOLD_AFTER_ROOM)
        if (failAfterRoom) touch(context, FAIL_AFTER_ROOM)
    }

    fun reset(context: Context) { directory(context).deleteRecursively() }
    fun releaseBeforeRoom(context: Context) = touch(context, RELEASE_BEFORE_ROOM)
    fun releaseAfterRoom(context: Context) = touch(context, RELEASE_AFTER_ROOM)
    fun beforeRoomReached(context: Context): Boolean = file(context, BEFORE_ROOM_REACHED).exists()
    fun afterRoomReached(context: Context): Boolean = file(context, AFTER_ROOM_REACHED).exists()
    fun phaseACalls(context: Context): Int = value(context, PHASE_A_CALLS)
    fun phaseBCalls(context: Context): Int = value(context, PHASE_B_CALLS)
    fun phaseBCompletions(context: Context): Int = value(context, PHASE_B_COMPLETIONS)
    fun ringingSynchronizationCompletions(context: Context): Int =
        value(context, RINGING_SYNCHRONIZATION_COMPLETIONS)
    fun feedbackSeen(context: Context): Boolean = file(context, FEEDBACK_SEEN).exists()
    fun feedbackEligible(context: Context): Boolean = file(context, FEEDBACK_ELIGIBLE).readTextOrNull() == "true"
    fun isActive(context: Context): Boolean = file(context, ACTIVE).exists()

    suspend fun beforeRoom(context: Context) {
        mark(context, BEFORE_ROOM_REACHED)
        awaitRelease(context, HOLD_BEFORE_ROOM, RELEASE_BEFORE_ROOM)
    }

    suspend fun afterRoomBeforePhaseB(context: Context) {
        mark(context, AFTER_ROOM_REACHED)
        awaitRelease(context, HOLD_AFTER_ROOM, RELEASE_AFTER_ROOM)
        if (file(context, FAIL_AFTER_ROOM).exists()) {
            throw IllegalStateException("Test alarm cleanup failure")
        }
    }

    fun incrementPhaseA(context: Context) = increment(context, PHASE_A_CALLS)
    fun incrementPhaseB(context: Context) = increment(context, PHASE_B_CALLS)
    fun markPhaseBCompleted(context: Context) = increment(context, PHASE_B_COMPLETIONS)
    fun markRingingSynchronizationCompleted(context: Context) =
        increment(context, RINGING_SYNCHRONIZATION_COMPLETIONS)

    fun onFeedbackEligibility(context: Context, eligible: Boolean) {
        if (!isActive(context)) return
        touch(context, FEEDBACK_SEEN)
        file(context, FEEDBACK_ELIGIBLE).writeText(eligible.toString())
    }

    private suspend fun awaitRelease(context: Context, holdKey: String, releaseKey: String) {
        if (!isActive(context) || !file(context, holdKey).exists()) return
        val deadline = SystemClock.elapsedRealtime() + HOLD_TIMEOUT_MILLIS
        while (!file(context, releaseKey).exists()) {
            if (!isActive(context)) return
            check(SystemClock.elapsedRealtime() < deadline) { "M9 test hook was not released" }
            delay(10)
        }
    }

    private fun mark(context: Context, key: String) { if (isActive(context)) touch(context, key) }
    private fun increment(context: Context, key: String) {
        if (isActive(context)) file(context, key).writeText((value(context, key) + 1).toString())
    }
    private fun value(context: Context, key: String): Int =
        file(context, key).readTextOrNull()?.toIntOrNull() ?: 0
    private fun touch(context: Context, key: String) {
        directory(context).mkdirs()
        file(context, key).writeText("")
    }
    private fun directory(context: Context) = File(context.applicationContext.filesDir, DIRECTORY)
    private fun file(context: Context, key: String) = File(directory(context), key)
    private fun File.readTextOrNull(): String? = if (exists()) readText() else null
}

private const val TAG = "MainActivity"

internal suspend fun synchronizeRingingOnResume(
    context: Context,
    database: AppDatabase,
    scheduler: AlarmScheduler,
    reconciler: AlarmReconciler,
) {
    runCatching { reconciler.reconcile(ReconciliationMode.ROUTINE) }
    if (!scheduler.requiredPresentationReady()) {
        // Do not revive audio/vibration when its actionable presentation is no longer available.
        // Exact projection and FSI capability are deliberately not expiry inputs here.
        database.occurrenceDao().expireAllRinging(System.currentTimeMillis())
    }
    AlarmRingingService.synchronizeWithPersistedQueue(context, database)
}
