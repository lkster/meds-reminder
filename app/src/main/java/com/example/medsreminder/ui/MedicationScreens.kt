package com.example.medsreminder.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.unit.dp
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.WeekdayMask
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class EditorTime(
    val id: Long?,
    val minuteOfDay: Int,
    val weekdayMask: Int = WeekdayMask.ALL,
)

data class EditorDraft(
    val id: Long?,
    val name: String,
    val instructions: String,
    val enabled: Boolean,
    val times: List<EditorTime>,
) {
    companion object {
        fun new() = EditorDraft(null, "", "", true, listOf(EditorTime(null, 8 * 60)))

        fun from(item: MedicationWithTimes) = EditorDraft(
            id = item.medication.id,
            name = item.medication.name,
            instructions = item.medication.instructions.orEmpty(),
            enabled = item.medication.enabled,
            times = item.reminderTimes.sortedBy { it.minuteOfDay }
                .map { EditorTime(it.id, it.minuteOfDay, it.weekdayMask) },
        )
    }
}

/** Mirrors the existing persistence preconditions without adding medication-domain rules. */
data class EditorValidation(
    val blankName: Boolean,
    val noReminders: Boolean,
    val duplicateMinutes: Set<Int>,
    val invalidWeekdayRows: Set<Int>,
) {
    val isValid: Boolean
        get() = !blankName && !noReminders && duplicateMinutes.isEmpty() && invalidWeekdayRows.isEmpty()
}

val EditorDraft.editorValidation: EditorValidation
    get() = EditorValidation(
        blankName = name.trim().isBlank(),
        noReminders = times.isEmpty(),
        duplicateMinutes = times.groupingBy { it.minuteOfDay }
            .eachCount()
            .filterValues { it > 1 }
            .keys,
        invalidWeekdayRows = times.mapIndexedNotNull { index, time ->
            index.takeIf { !WeekdayMask.isValid(time.weekdayMask) }
        }.toSet(),
    )

data class CapabilityItem(
    val title: String,
    val ready: Boolean,
    val detail: String,
    val actionLabel: String,
    val onAction: () -> Unit,
    val requiredForReliableDelivery: Boolean = true,
)

@Composable
fun MedicationListScreen(
    medications: List<MedicationWithTimes>?,
    capabilityItems: List<CapabilityItem>,
    alarmSoundLabel: String,
    vibrationEnabled: Boolean,
    snoozeMinutes: Int,
    showSamsungGuidance: Boolean,
    onSamsungSettings: () -> Unit,
    onChooseAlarmSound: () -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
    onHistory: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (MedicationWithTimes) -> Unit,
    onToggle: (MedicationWithTimes, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    // The dialog is UI state, but its target must remain a stable identity rather than a
    // retained list snapshot. Keeping the display name makes restoration independent of the
    // first post-recreation Room Flow emission.
    var deleteCandidateId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteCandidateName by rememberSaveable { mutableStateOf<String?>(null) }
    fun clearDeleteCandidate() {
        deleteCandidateId = null
        deleteCandidateName = null
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).semantics {
            paneTitle = "Meds Reminder"
        },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Meds Reminder",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedButton(onClick = onHistory) { Text("History") }
        }
        AlarmReadinessSection(capabilityItems)
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add medication") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Alarm behavior", style = MaterialTheme.typography.titleLarge)
                Text("Sound", style = MaterialTheme.typography.titleMedium)
                Text(alarmSoundLabel, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onChooseAlarmSound) { Text("Choose alarm sound") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Vibration", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = onVibrationEnabledChange,
                        modifier = Modifier
                            .testTag("alarm-vibration-toggle")
                            .semantics { contentDescription = "Vibration" },
                    )
                }
                Text("Snooze duration", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = snoozeMinutes == minutes,
                            onClick = { onSnoozeMinutesChange(minutes) },
                            label = { Text("$minutes min") },
                        )
                    }
                }
            }
        }
        when {
            medications == null -> Text("Loading medications…")
            medications.isEmpty() -> Text("No medications yet.")
        }
        medications?.forEachIndexed { index, item ->
            val medicationNumber = index + 1
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.medication.name, style = MaterialTheme.typography.titleLarge)
                        Switch(
                            checked = item.medication.enabled,
                            onCheckedChange = { onToggle(item, it) },
                            modifier = Modifier.semantics {
                                contentDescription = "Medication $medicationNumber, ${item.medication.name}, reminders"
                            },
                        )
                    }
                    item.medication.instructions?.let { Text(it) }
                    item.reminderTimes.sortedBy { it.minuteOfDay }.forEach { reminder ->
                        Text("${formatMinute(reminder.minuteOfDay)} — ${formatWeekdays(reminder.weekdayMask)}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onEdit(item) },
                            modifier = Modifier.semantics {
                                contentDescription = "Edit medication $medicationNumber, ${item.medication.name}"
                            },
                        ) { Text("Edit") }
                        TextButton(
                            onClick = {
                                deleteCandidateId = item.medication.id
                                deleteCandidateName = item.medication.name
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Delete medication $medicationNumber, ${item.medication.name}"
                            },
                        ) { Text("Delete") }
                    }
                }
            }
        }

        if (showSamsungGuidance) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Samsung unlocked alarm actions", style = MaterialTheme.typography.titleMedium)
                    Text("Samsung Brief pop-ups may hide immediate Taken, Snooze, and Skip actions. Detailed is a user-controlled Samsung setting that Meds Reminder cannot change.")
                    OutlinedButton(onClick = onSamsungSettings) { Text("Open notification settings") }
                }
            }
        }
    }

    deleteCandidateId?.let { medicationId ->
        AlertDialog(
            onDismissRequest = ::clearDeleteCandidate,
            title = { Text("Delete ${deleteCandidateName ?: "this medication"}?") },
            text = { Text("Its reminder times, pending alarms, and history will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    clearDeleteCandidate()
                    onDelete(medicationId)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = ::clearDeleteCandidate) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AlarmReadinessSection(capabilityItems: List<CapabilityItem>) {
    val unresolvedRequired = capabilityItems.filter { !it.ready && it.requiredForReliableDelivery }
    val unresolvedDegraded = capabilityItems.filter { !it.ready && !it.requiredForReliableDelivery }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                unresolvedRequired.isNotEmpty() -> {
                    Text("Alarm setup needs attention", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Text("Required Android setup is incomplete, so medication alarms should not yet be relied upon.")
                    (unresolvedRequired + unresolvedDegraded).forEach { CapabilityCard(it) }
                }
                unresolvedDegraded.isNotEmpty() -> {
                    Text("Alarm setup is limited", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Text("Actionable alarm notifications remain available, but full-screen presentation is limited.")
                    unresolvedDegraded.forEach { CapabilityCard(it) }
                }
                else -> {
                    Text("Alarm setup — Ready", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Text("Android permissions and core alarm capabilities are ready.")
                }
            }
        }
    }
}

@Composable
private fun CapabilityCard(capability: CapabilityItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(capability.title, style = MaterialTheme.typography.titleMedium)
                Text(if (capability.requiredForReliableDelivery) "Required" else "Limited")
            }
            Text(capability.detail, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = capability.onAction) { Text(capability.actionLabel) }
        }
    }
}

@Composable
fun MedicationEditorScreen(
    draft: EditorDraft,
    onDraftChange: (EditorDraft) -> Unit,
    onSave: (EditorDraft) -> Unit,
    onCancel: () -> Unit,
    saveState: EditorSaveState = EditorSaveState.Idle,
    onRetryPostCommit: () -> Unit = {},
    handleSystemBack: Boolean = true,
) {
    val context = LocalContext.current
    val validation = draft.editorValidation
    val mutationLocked = saveState.locksDraft
    val screenTitle = if (draft.id == null) "Add medication" else "Edit medication"
    // Consume Back while work is owned by the retained ViewModel so it cannot be abandoned.
    if (handleSystemBack) BackHandler { if (!mutationLocked) onCancel() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).semantics {
            paneTitle = screenTitle
        },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            screenTitle,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = { Text("Medication name") },
            singleLine = true,
            isError = validation.blankName,
            supportingText = if (validation.blankName) {
                { Text("Enter a medication name", color = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
            enabled = !mutationLocked,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.instructions,
            onValueChange = { onDraftChange(draft.copy(instructions = it)) },
            label = { Text("Dose or instructions (optional)") },
            enabled = !mutationLocked,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Enabled", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = draft.enabled,
                onCheckedChange = { onDraftChange(draft.copy(enabled = it)) },
                enabled = !mutationLocked,
                modifier = Modifier.semantics {
                    contentDescription = "Enable ${draft.name.ifBlank { "this medication" }} reminders"
                },
            )
        }
        Text("Reminder schedules", style = MaterialTheme.typography.titleMedium)
        if (draft.id != null) {
            Text(
                "Removing a reminder and saving also deletes its history.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        draft.times.forEachIndexed { index, time ->
            val reminderNumber = index + 1
            val reminderTime = formatMinute(time.minuteOfDay)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            enabled = !mutationLocked,
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val changed = draft.times.toMutableList()
                                        changed[index] = time.copy(minuteOfDay = hour * 60 + minute)
                                        onDraftChange(draft.copy(times = changed))
                                    },
                                    time.minuteOfDay / 60,
                                    time.minuteOfDay % 60,
                                    true,
                                ).show()
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Change reminder $reminderNumber time, currently $reminderTime"
                            },
                        ) { Text(reminderTime) }
                        if (draft.times.size > 1) {
                            TextButton(
                                enabled = !mutationLocked,
                                onClick = {
                                    onDraftChange(draft.copy(times = draft.times.filterIndexed { i, _ -> i != index }))
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = "Remove reminder $reminderNumber at $reminderTime"
                                },
                            ) { Text("Remove") }
                        }
                    }
                    WeekdaySelector(
                        weekdayMask = time.weekdayMask,
                        reminderNumber = reminderNumber,
                        reminderTime = reminderTime,
                        enabled = !mutationLocked,
                    ) { weekdayMask ->
                        val changed = draft.times.toMutableList()
                        changed[index] = time.copy(weekdayMask = weekdayMask)
                        onDraftChange(draft.copy(times = changed))
                    }
                    if (!WeekdayMask.isValid(time.weekdayMask)) {
                        Text(
                            "Select at least one day",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (time.minuteOfDay in validation.duplicateMinutes) {
                        Text(
                            "Each reminder needs a different time",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (validation.noReminders) {
            Text(
                "Add at least one reminder time",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(enabled = !mutationLocked, onClick = {
            val nextMinute = ((draft.times.maxOfOrNull { it.minuteOfDay } ?: 7 * 60) + 60) % (24 * 60)
            onDraftChange(
                draft.copy(times = draft.times + EditorTime(null, nextMinute, WeekdayMask.ALL)),
            )
        }) { Text("Add another time") }
        Button(
            onClick = { onSave(draft) },
            enabled = validation.isValid && saveState.canStartSubmission,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (saveState is EditorSaveState.SavingRoom) "Saving…" else "Save") }
        when (saveState) {
            is EditorSaveState.SavingRoom -> Text("Saving medication…")
            is EditorSaveState.CompletingAlarms -> Text("Saved. Updating alarms…")
            is EditorSaveState.RoomFailure -> Text(
                "Could not save: ${saveState.message}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            is EditorSaveState.PostCommitFailure -> {
                Text(
                    "Medication was saved, but alarm updates need retry: ${saveState.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Button(onClick = onRetryPostCommit, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry alarm update")
                }
            }
            EditorSaveState.Idle -> Unit
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !mutationLocked,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Cancel") }
    }
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

private fun formatMinute(minuteOfDay: Int): String =
    TIME_FORMATTER.format(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))

@Composable
private fun WeekdaySelector(
    weekdayMask: Int,
    reminderNumber: Int,
    reminderTime: String,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    FilterChip(
        selected = weekdayMask == WeekdayMask.ALL,
        onClick = { onChange(WeekdayMask.ALL) },
        enabled = enabled,
        modifier = Modifier.semantics {
            contentDescription = "Reminder $reminderNumber at $reminderTime, Every day"
        },
        label = { Text("Every day") },
    )
    DAY_ROWS.forEach { days ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                val bit = 1 shl (day.value - 1)
                FilterChip(
                    selected = weekdayMask and bit != 0,
                    onClick = { onChange(weekdayMask xor bit) },
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription = "Reminder $reminderNumber at $reminderTime, ${fullDayName(day)}"
                    },
                    label = { Text(dayLabel(day)) },
                )
            }
        }
    }
}

private fun formatWeekdays(weekdayMask: Int): String = if (weekdayMask == WeekdayMask.ALL) {
    "Every day"
} else {
    DayOfWeek.entries.filter { weekdayMask and (1 shl (it.value - 1)) != 0 }
        .joinToString(" ", transform = ::dayLabel)
}

private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

private fun fullDayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Monday"
    DayOfWeek.TUESDAY -> "Tuesday"
    DayOfWeek.WEDNESDAY -> "Wednesday"
    DayOfWeek.THURSDAY -> "Thursday"
    DayOfWeek.FRIDAY -> "Friday"
    DayOfWeek.SATURDAY -> "Saturday"
    DayOfWeek.SUNDAY -> "Sunday"
}

private val DAY_ROWS = listOf(
    listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
    listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
)
