package com.example.medsreminder.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.WeekdayMask
import com.example.medsreminder.ui.theme.LocalMedsReminderColors
import androidx.compose.ui.graphics.SolidColor
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
    onHistory: () -> Unit,
    onSettings: () -> Unit,
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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var actionMenuMedicationId by rememberSaveable { mutableStateOf<Long?>(null) }
    val trimmedSearchQuery = searchQuery.trim()
    val filteredMedications = medications?.let { source ->
        if (trimmedSearchQuery.isBlank()) source else source.filter {
            it.medication.name.contains(trimmedSearchQuery, ignoreCase = true)
        }
    }
    val hasAuthoritativeMedications = medications?.isNotEmpty() == true
    val hasNoSearchMatches = hasAuthoritativeMedications &&
        trimmedSearchQuery.isNotBlank() && filteredMedications.isNullOrEmpty()
    fun clearDeleteCandidate() {
        deleteCandidateId = null
        deleteCandidateName = null
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { paneTitle = "Medications" },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            MedicationListTopBar(onHistory = onHistory, onSettings = onSettings)
            Spacer(Modifier.height(20.dp))
            if (medications == null || hasAuthoritativeMedications) {
                MedicationSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                    enabled = medications != null,
                )
                Spacer(Modifier.height(20.dp))
            }
            when {
                medications == null -> MedicationListLoadingState()
                medications.isEmpty() -> MedicationListEmptyState(onAdd = onAdd)
                hasNoSearchMatches -> MedicationSearchEmptyState()
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    filteredMedications.orEmpty().forEachIndexed { index, item ->
                        MedicationLibraryCard(
                            item = item,
                            medicationNumber = index + 1,
                            onEdit = onEdit,
                            onToggle = onToggle,
                            menuExpanded = actionMenuMedicationId == item.medication.id,
                            onMenuExpandedChange = { expanded ->
                                actionMenuMedicationId = item.medication.id.takeIf { expanded }
                            },
                            onDeleteRequested = {
                                actionMenuMedicationId = null
                                deleteCandidateId = item.medication.id
                                deleteCandidateName = item.medication.name
                            },
                        )
                    }
                }
            }
            if (medications == null || hasAuthoritativeMedications) Spacer(Modifier.padding(bottom = 78.dp))
        }
        if (medications == null || hasAuthoritativeMedications) {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .sizeIn(minWidth = 58.dp, minHeight = 58.dp)
                    .semantics { contentDescription = "Add medication" }
                    .testTag("medication-add-fab"),
            ) { Icon(Icons.Outlined.Add, contentDescription = null) }
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
private fun MedicationListTopBar(onHistory: () -> Unit, onSettings: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Medications",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onHistory,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "History" },
            ) { Icon(Icons.Outlined.History, contentDescription = null) }
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Settings" },
            ) { Icon(Icons.Outlined.Settings, contentDescription = null) }
        }
    }
}

@Composable
private fun MedicationSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().testTag("medication-search"),
        ) { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search medications", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = "Clear medication search" },
                    ) { Icon(Icons.Outlined.Clear, contentDescription = null) }
                }
            }
        }
    }
}

@Composable
private fun MedicationListLoadingState() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Loading medications…", style = MaterialTheme.typography.bodyMedium)
        repeat(3) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {}
        }
    }
}

@Composable
private fun MedicationListEmptyState(onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("No medications yet", style = MaterialTheme.typography.titleLarge)
        Text("Add a medication and its reminder times to begin.", style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onAdd,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) { Text("Add first medication") }
    }
}

@Composable
private fun MedicationSearchEmptyState() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("No matching medications", style = MaterialTheme.typography.titleMedium)
        Text("Try a different medication name.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MedicationLibraryCard(
    item: MedicationWithTimes,
    medicationNumber: Int,
    onEdit: (MedicationWithTimes) -> Unit,
    onToggle: (MedicationWithTimes, Boolean) -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onDeleteRequested: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clickable { onEdit(item) }
            .semantics { contentDescription = "Edit medication $medicationNumber, ${item.medication.name}" },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = LocalMedsReminderColors.current.surfaceElevated),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val scheduleLines = compactScheduleLines(item)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(item.medication.name, style = MaterialTheme.typography.titleMedium)
                    scheduleLines.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!item.medication.enabled) {
                        Text(
                            "Reminders off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box {
                IconButton(
                    onClick = { onMenuExpandedChange(true) },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "Medication actions $medicationNumber, ${item.medication.name}" },
                ) { Icon(Icons.Outlined.MoreVert, contentDescription = null) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpandedChange(false) }) {
                    DropdownMenuItem(
                        text = { Text(if (item.medication.enabled) "Disable reminders" else "Enable reminders") },
                        onClick = {
                            onMenuExpandedChange(false)
                            onToggle(item, !item.medication.enabled)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = onDeleteRequested,
                    )
                }
            }
        }
    }
}

private fun compactScheduleLines(item: MedicationWithTimes): List<String> {
    val reminders = item.reminderTimes.sortedBy { it.minuteOfDay }
    if (reminders.isEmpty()) return listOf("No reminder times")
    val masks = reminders.map { it.weekdayMask }.distinct()
    return if (masks.size == 1) {
        listOf(formatWeekdays(masks.single()), reminders.joinToString(" · ") { formatMinute(it.minuteOfDay) })
    } else {
        reminders.map { "${formatMinute(it.minuteOfDay)} · ${formatWeekdays(it.weekdayMask)}" }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    capability.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { paneTitle = screenTitle },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("medication-editor-scroll"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCancel,
                    enabled = !mutationLocked,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "Back" },
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = null) }
                Text(
                    screenTitle,
                    modifier = Modifier.padding(start = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            EditorSectionCard {
                Text("Medication", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = { Text("Medication name") },
                    singleLine = true,
                    isError = validation.blankName,
                    supportingText = if (validation.blankName) {
                        { Text("Enter a medication name", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    enabled = !mutationLocked,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.instructions,
                    onValueChange = { onDraftChange(draft.copy(instructions = it)) },
                    label = { Text("Instructions / notes (optional)") },
                    enabled = !mutationLocked,
                    modifier = Modifier.fillMaxWidth().testTag("medication-editor-instructions"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reminders enabled", style = MaterialTheme.typography.titleMedium)
                        Text("Turn all medication reminders on or off.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { onDraftChange(draft.copy(enabled = it)) },
                        enabled = !mutationLocked,
                        modifier = Modifier.semantics {
                            contentDescription = "Enable ${draft.name.ifBlank { "this medication" }} reminders"
                        },
                    )
                }
            }

            Text("Reminders", style = MaterialTheme.typography.titleLarge)
            if (draft.id != null) {
                Text(
                    "Removing a reminder and saving also deletes its history.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            draft.times.forEachIndexed { index, time ->
                val reminderNumber = index + 1
                val reminderTime = formatMinute(time.minuteOfDay)
                EditorSectionCard {
                    Text("Reminder $reminderNumber", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
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
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription = "Change reminder $reminderNumber time, currently $reminderTime"
                                },
                        ) { Text(reminderTime) }
                        if (draft.times.size > 1) {
                            TextButton(
                                enabled = !mutationLocked,
                                onClick = {
                                    onDraftChange(draft.copy(times = draft.times.filterIndexed { i, _ -> i != index }))
                                },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics {
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
            if (validation.noReminders) {
                Text(
                    "Add at least one reminder time",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                enabled = !mutationLocked,
                onClick = {
                    val nextMinute = ((draft.times.maxOfOrNull { it.minuteOfDay } ?: 7 * 60) + 60) % (24 * 60)
                    onDraftChange(draft.copy(times = draft.times + EditorTime(null, nextMinute, WeekdayMask.ALL)))
                },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) { Text("Add reminder time") }
            Button(
                onClick = { onSave(draft) },
                enabled = validation.isValid && saveState.canStartSubmission,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).testTag("medication-editor-save"),
            ) {
                Text(
                    if (saveState is EditorSaveState.SavingRoom || saveState is EditorSaveState.CompletingAlarms) {
                        "Saving…"
                    } else {
                        "Save"
                    },
                )
            }
            when (saveState) {
                is EditorSaveState.SavingRoom -> EditorProgress("Saving medication…")
                is EditorSaveState.CompletingAlarms -> EditorProgress("Finishing alarm setup…")
                is EditorSaveState.RoomFailure -> Text(
                    "Could not save medication. Try again.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                is EditorSaveState.PostCommitFailure -> {
                    Text(
                        "Medication was saved, but alarms could not be updated.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Button(
                        onClick = onRetryPostCommit,
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                    ) { Text("Retry alarm update") }
                }
                EditorSaveState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun EditorSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun EditorProgress(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
