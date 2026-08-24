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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.medsreminder.data.WeekdayMask
import com.example.medsreminder.data.applyMedicationScheduleEdit
import com.example.medsreminder.ui.CapabilityItem
import com.example.medsreminder.ui.EditorDraft
import com.example.medsreminder.ui.HistoryItem
import com.example.medsreminder.ui.HistoryScreen
import com.example.medsreminder.ui.MedicationEditorScreen
import com.example.medsreminder.ui.MedicationListScreen
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
    private var editor by mutableStateOf<EditorDraft?>(null)
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
        val currentEditor = editor
        if (currentEditor != null) {
            MedicationEditorScreen(
                draft = currentEditor,
                onDraftChange = { editor = it },
                onSave = { saveMedication(it) },
                onCancel = { editor = null },
            )
            return
        }
        if (historyVisible) {
            HistoryScreen(history = history, onBack = { historyVisible = false })
            return
        }

        val capabilityItems = listOf(
            CapabilityItem(
                "Notifications",
                capabilities.notificationsAllowed,
                "Required for the actionable alarm notification.",
                "Allow",
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openAppNotificationSettings()
            },
            CapabilityItem(
                "Alarm channel",
                capabilities.channelHighImportance,
                "Must remain high importance for heads-up presentation.",
                "Open channel settings",
                ::openAlarmChannelSettings,
            ),
            CapabilityItem(
                "Exact alarms",
                capabilities.exactAlarmsAllowed,
                "Required for precise daily delivery.",
                "Open alarm access",
                ::openExactAlarmSettings,
            ),
            CapabilityItem(
                "Full-screen alarm",
                capabilities.fullScreenAllowed,
                "Without access, delivery degrades to the alarm notification.",
                "Open full-screen access",
                ::openFullScreenIntentSettings,
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
            onAdd = { editor = EditorDraft.new() },
            onEdit = { editor = EditorDraft.from(it) },
            onToggle = { item, enabled -> saveMedication(EditorDraft.from(item).copy(enabled = enabled)) },
            onDelete = ::deleteMedication,
        )
    }

    private fun saveMedication(draft: EditorDraft) {
        val name = draft.name.trim()
        val minutes = draft.times.map { it.minuteOfDay }
        if (name.isBlank() || minutes.isEmpty() || minutes.distinct().size != minutes.size ||
            draft.times.any { !WeekdayMask.isValid(it.weekdayMask) }
        ) {
            Toast.makeText(
                this,
                "Enter a name, at least one unique time, and weekdays for every reminder",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val nowMillis = System.currentTimeMillis()
                val result = database.applyMedicationScheduleEdit(
                    edit = MedicationScheduleEdit(
                        medicationId = draft.id,
                        name = name,
                        instructions = draft.instructions.trim().ifBlank { null },
                        enabled = draft.enabled,
                        reminders = draft.times.map {
                            ReminderScheduleEdit(it.id, it.minuteOfDay, it.weekdayMask)
                        },
                    ),
                    nowMillis = nowMillis,
                    zoneId = ZoneId.systemDefault(),
                )
                result.obsoleteOccurrenceIds.forEach(scheduler::cancelOccurrence)
                reconciler.reconcile(ReconciliationMode.ROUTINE)
                if (result.refreshRinging) {
                    AlarmRingingService.synchronizeWithPersistedQueue(this@MainActivity, database)
                }
            }.onSuccess {
                withContext(Dispatchers.Main) { editor = null }
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
            notificationsAllowed = notificationManager.areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
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
        val notificationsAllowed: Boolean = false,
        val channelHighImportance: Boolean = false,
        val exactAlarmsAllowed: Boolean = false,
        val fullScreenAllowed: Boolean = false,
    )
}

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
