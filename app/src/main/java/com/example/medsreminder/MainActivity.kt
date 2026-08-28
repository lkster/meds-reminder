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
import android.provider.Settings
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
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.medsreminder.alarm.AlarmRingingService
import com.example.medsreminder.alarm.AlarmPreferenceSnapshot
import com.example.medsreminder.alarm.AlarmPreferences
import com.example.medsreminder.alarm.AlarmReconciler
import com.example.medsreminder.alarm.AlarmScheduler
import com.example.medsreminder.alarm.ReconciliationMode
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.HistoryOccurrence
import com.example.medsreminder.data.MedicationScheduleEdit
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.ReminderScheduleEdit
import com.example.medsreminder.data.applyMedicationScheduleEdit
import com.example.medsreminder.ui.CapabilityItem
import com.example.medsreminder.ui.HistoryItem
import com.example.medsreminder.ui.HistoryScreen
import com.example.medsreminder.ui.MedicationEditorViewModel
import com.example.medsreminder.ui.MedicationEditorScreen
import com.example.medsreminder.ui.MedicationListScreen
import com.example.medsreminder.ui.MedicationEditorViewModelTestHook
import com.example.medsreminder.ui.toHistoryItem
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.get(this) }
    private val scheduler by lazy { AlarmScheduler(this) }
    private val reconciler by lazy { AlarmReconciler(this, database, scheduler) }

    private var medications by mutableStateOf<List<MedicationWithTimes>>(emptyList())
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
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    check(modelClass == MedicationEditorViewModel::class.java)
                    return testFactory(application) as T
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
            database.medicationDao().observeAll().collectLatest { medications = it }
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
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val result = applyMedicationListToggle(database, item, enabled, System.currentTimeMillis())
                result.obsoleteOccurrenceIds.forEach(scheduler::cancelOccurrence)
                reconciler.reconcile(ReconciliationMode.ROUTINE)
                if (result.refreshRinging) {
                    AlarmRingingService.synchronizeWithPersistedQueue(this@MainActivity, database)
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Could not save: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun deleteMedication(item: MedicationWithTimes) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                var obsoleteIds = emptyList<String>()
                var refreshRinging = false
                database.withTransaction {
                    val ringingId = database.occurrenceDao().getCurrentRinging()?.occurrenceId
                    obsoleteIds = database.occurrenceDao()
                        .getMedicationNonterminalIds(item.medication.id)
                    refreshRinging = ringingId != null
                    database.medicationDao().deleteMedication(item.medication)
                }
                obsoleteIds.forEach(scheduler::cancelOccurrence)
                if (refreshRinging) {
                    AlarmRingingService.synchronizeWithPersistedQueue(this@MainActivity, database)
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Could not delete: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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

/** The narrow Room edit used exclusively by the medication-list Enabled switch. */
internal suspend fun applyMedicationListToggle(
    database: AppDatabase,
    item: MedicationWithTimes,
    enabled: Boolean,
    nowMillis: Long,
): com.example.medsreminder.data.MedicationScheduleEditResult = database.applyMedicationScheduleEdit(
    edit = MedicationScheduleEdit(
        medicationId = item.medication.id,
        name = item.medication.name.trim(),
        instructions = item.medication.instructions?.trim()?.ifBlank { null },
        enabled = enabled,
        reminders = item.reminderTimes.map {
            ReminderScheduleEdit(it.id, it.minuteOfDay, it.weekdayMask)
        },
    ),
    nowMillis = nowMillis,
    zoneId = ZoneId.systemDefault(),
)

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
