package com.example.medsreminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.medsreminder.alarm.AlarmRingingService
import com.example.medsreminder.alarm.AlarmReconciler
import com.example.medsreminder.alarm.AlarmScheduler
import com.example.medsreminder.alarm.ReconciliationMode
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.ReminderTimeEntity
import com.example.medsreminder.ui.CapabilityItem
import com.example.medsreminder.ui.EditorDraft
import com.example.medsreminder.ui.MedicationEditorScreen
import com.example.medsreminder.ui.MedicationListScreen
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
    private var editor by mutableStateOf<EditorDraft?>(null)
    private var capabilities by mutableStateOf(CapabilityState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmRingingService.ensureNotificationChannel(this)
        refreshCapabilities()
        lifecycleScope.launch {
            database.medicationDao().observeAll().collectLatest { medications = it }
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
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { reconciler.reconcile(ReconciliationMode.ROUTINE) }
            if (!scheduler.projectionReady()) {
                // Do not revive audio/vibration when its actionable presentation is no longer
                // available. Future BASE state remains persisted for a later reconciliation.
                database.occurrenceDao().expireAllRinging(System.currentTimeMillis())
            }
            AlarmRingingService.synchronizeWithPersistedQueue(this@MainActivity, database)
        }
    }

    @Composable
    private fun MainContent() {
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { refreshCapabilities() }
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
            showSamsungGuidance = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
            onSamsungSettings = ::openAppNotificationSettings,
            onAdd = { editor = EditorDraft.new() },
            onEdit = { editor = EditorDraft.from(it) },
            onToggle = { item, enabled -> saveMedication(EditorDraft.from(item).copy(enabled = enabled)) },
            onDelete = ::deleteMedication,
        )
    }

    private fun saveMedication(draft: EditorDraft) {
        val name = draft.name.trim()
        val minutes = draft.times.map { it.minuteOfDay }
        if (name.isBlank() || minutes.isEmpty() || minutes.distinct().size != minutes.size) {
            Toast.makeText(this, "Enter a name and at least one unique time", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val obsoleteIds = mutableListOf<String>()
            var refreshRinging = false
            runCatching {
                database.withTransaction {
                    val medicationDao = database.medicationDao()
                    val occurrenceDao = database.occurrenceDao()
                    val ringingId = occurrenceDao.getCurrentRinging()?.occurrenceId
                    val previous = draft.id?.let { medicationDao.get(it) }
                    val medicationId = if (previous == null) {
                        medicationDao.insertMedication(
                            MedicationEntity(
                                name = name,
                                instructions = draft.instructions.trim().ifBlank { null },
                                enabled = draft.enabled,
                            ),
                        )
                    } else {
                        medicationDao.updateMedication(
                            previous.copy(
                                name = name,
                                instructions = draft.instructions.trim().ifBlank { null },
                                enabled = draft.enabled,
                            ),
                        )
                        previous.id
                    }

                    val existing = medicationDao.getTimes(medicationId).associateBy { it.id }
                    val retainedIds = draft.times.mapNotNull { it.id }.toSet()
                    existing.values.filter { it.id !in retainedIds }.forEach { removed ->
                        obsoleteIds += occurrenceDao.getNonterminalIds(removed.id)
                        medicationDao.deleteTime(removed)
                    }

                    draft.times.forEach { editorTime ->
                        val old = editorTime.id?.let(existing::get)
                        if (old == null) {
                            medicationDao.insertTime(
                                ReminderTimeEntity(
                                    medicationId = medicationId,
                                    minuteOfDay = editorTime.minuteOfDay,
                                ),
                            )
                        } else if (old.minuteOfDay != editorTime.minuteOfDay) {
                            obsoleteIds += occurrenceDao.getNonterminalIds(old.id)
                            occurrenceDao.deleteNonterminal(old.id)
                            medicationDao.updateTime(old.copy(minuteOfDay = editorTime.minuteOfDay))
                        }
                    }

                    if (!draft.enabled) {
                        obsoleteIds += occurrenceDao.getMedicationNonterminalIds(medicationId)
                        occurrenceDao.deleteMedicationNonterminal(medicationId)
                    } else {
                        medicationDao.getTimes(medicationId).forEach { time ->
                            reconciler.ensureFutureBase(
                                time.id,
                                time.minuteOfDay,
                                System.currentTimeMillis(),
                                ZoneId.systemDefault(),
                            )
                        }
                    }
                    refreshRinging = ringingId != null
                }
                obsoleteIds.distinct().forEach(scheduler::cancelOccurrence)
                reconciler.reconcile(ReconciliationMode.ROUTINE)
                if (refreshRinging) {
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
