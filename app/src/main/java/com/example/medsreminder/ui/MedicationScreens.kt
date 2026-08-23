package com.example.medsreminder.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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

data class CapabilityItem(
    val title: String,
    val ready: Boolean,
    val detail: String,
    val actionLabel: String,
    val onAction: () -> Unit,
)

@Composable
fun MedicationListScreen(
    medications: List<MedicationWithTimes>,
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
    onDelete: (MedicationWithTimes) -> Unit,
) {
    var deleteCandidate by remember { mutableStateOf<MedicationWithTimes?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Meds Reminder", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = onHistory) { Text("History") }
        }
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
                        modifier = Modifier.testTag("alarm-vibration-toggle"),
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
        if (medications.isEmpty()) Text("No medications yet.")
        medications.forEach { item ->
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
                        )
                    }
                    item.medication.instructions?.let { Text(it) }
                    item.reminderTimes.sortedBy { it.minuteOfDay }.forEach { reminder ->
                        Text("${formatMinute(reminder.minuteOfDay)} — ${formatWeekdays(reminder.weekdayMask)}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEdit(item) }) { Text("Edit") }
                        TextButton(onClick = { deleteCandidate = item }) { Text("Delete") }
                    }
                }
            }
        }

        Text("Alarm capabilities", style = MaterialTheme.typography.titleLarge)
        capabilityItems.forEach { capability ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(capability.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (capability.ready) "Ready" else "Action needed")
                    }
                    Text(capability.detail, style = MaterialTheme.typography.bodySmall)
                    if (!capability.ready) {
                        OutlinedButton(onClick = capability.onAction) { Text(capability.actionLabel) }
                    }
                }
            }
        }
        if (showSamsungGuidance) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Samsung unlocked alarm actions", style = MaterialTheme.typography.titleMedium)
                    Text("For immediate actions, set this app's pop-up notification style to Detailed.")
                    OutlinedButton(onClick = onSamsungSettings) { Text("Open notification settings") }
                }
            }
        }
    }

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${item.medication.name}?") },
            text = { Text("Its reminder times, pending alarms, and history will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteCandidate = null
                    onDelete(item)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun MedicationEditorScreen(
    draft: EditorDraft,
    onDraftChange: (EditorDraft) -> Unit,
    onSave: (EditorDraft) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (draft.id == null) "Add medication" else "Edit medication",
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = { Text("Medication name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.instructions,
            onValueChange = { onDraftChange(draft.copy(instructions = it)) },
            label = { Text("Dose or instructions (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Enabled", style = MaterialTheme.typography.titleMedium)
            Switch(draft.enabled, { onDraftChange(draft.copy(enabled = it)) })
        }
        Text("Reminder schedules", style = MaterialTheme.typography.titleMedium)
        if (draft.id != null) {
            Text(
                "Removing a reminder and saving also deletes its history.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        draft.times.forEachIndexed { index, time ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = {
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
                        }) { Text(formatMinute(time.minuteOfDay)) }
                        if (draft.times.size > 1) {
                            TextButton(onClick = {
                                onDraftChange(draft.copy(times = draft.times.filterIndexed { i, _ -> i != index }))
                            }) { Text("Remove") }
                        }
                    }
                    WeekdaySelector(time.weekdayMask) { weekdayMask ->
                        val changed = draft.times.toMutableList()
                        changed[index] = time.copy(weekdayMask = weekdayMask)
                        onDraftChange(draft.copy(times = changed))
                    }
                    if (time.weekdayMask == 0) {
                        Text(
                            "Select at least one day",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = {
            val nextMinute = ((draft.times.maxOfOrNull { it.minuteOfDay } ?: 7 * 60) + 60) % (24 * 60)
            onDraftChange(
                draft.copy(times = draft.times + EditorTime(null, nextMinute, WeekdayMask.ALL)),
            )
        }) { Text("Add another time") }
        Button(
            onClick = { onSave(draft) },
            enabled = draft.times.all { WeekdayMask.isValid(it.weekdayMask) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

private fun formatMinute(minuteOfDay: Int): String =
    TIME_FORMATTER.format(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))

@Composable
private fun WeekdaySelector(weekdayMask: Int, onChange: (Int) -> Unit) {
    FilterChip(
        selected = weekdayMask == WeekdayMask.ALL,
        onClick = { onChange(WeekdayMask.ALL) },
        label = { Text("Every day") },
    )
    DAY_ROWS.forEach { days ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                val bit = 1 shl (day.value - 1)
                FilterChip(
                    selected = weekdayMask and bit != 0,
                    onClick = { onChange(weekdayMask xor bit) },
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

private val DAY_ROWS = listOf(
    listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
    listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
)
