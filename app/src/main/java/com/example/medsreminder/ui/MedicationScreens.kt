package com.example.medsreminder.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccessTime
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

data class MedicationDeleteDialogState(
    val medicationId: Long,
    val medicationName: String,
    val submitting: Boolean = false,
    val error: String? = null,
)

@Composable
fun MedicationListScreen(
    medications: List<MedicationWithTimes>?,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onOpenDetails: (MedicationWithTimes) -> Unit,
    onToggle: (MedicationWithTimes, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    deleteDialogState: MedicationDeleteDialogState? = null,
    onDeleteRequested: ((Long, String) -> Unit)? = null,
    onDismissDelete: (() -> Unit)? = null,
    readFailed: Boolean = false,
    onRetryRead: () -> Unit = {},
) {
    // The dialog is UI state, but its target must remain a stable identity rather than a
    // retained list snapshot. Keeping the display name makes restoration independent of the
    // first post-recreation Room Flow emission.
    var deleteCandidateId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteCandidateName by rememberSaveable { mutableStateOf<String?>(null) }
    var localSubmitting by rememberSaveable { mutableStateOf(false) }
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
        localSubmitting = false
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
            if (medications != null && readFailed) {
                MedicationRefreshFailure("Medications could not be refreshed.", onRetryRead)
                Spacer(Modifier.height(16.dp))
            }
            when {
                medications == null && readFailed -> MedicationInitialReadFailure(onRetryRead)
                medications == null -> MedicationListLoadingState()
                medications.isEmpty() -> MedicationListEmptyState(onAdd = onAdd)
                hasNoSearchMatches -> MedicationSearchEmptyState()
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    filteredMedications.orEmpty().forEachIndexed { index, item ->
                        MedicationLibraryCard(
                            item = item,
                            medicationNumber = index + 1,
                            onOpenDetails = onOpenDetails,
                            onToggle = onToggle,
                            menuExpanded = actionMenuMedicationId == item.medication.id,
                            onMenuExpandedChange = { expanded ->
                                actionMenuMedicationId = item.medication.id.takeIf { expanded }
                            },
                            onDeleteRequested = {
                                actionMenuMedicationId = null
                                if (onDeleteRequested != null) {
                                    onDeleteRequested(item.medication.id, item.medication.name)
                                } else {
                                    deleteCandidateId = item.medication.id
                                    deleteCandidateName = item.medication.name
                                }
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

    val dialog = deleteDialogState ?: deleteCandidateId?.let {
        MedicationDeleteDialogState(it, deleteCandidateName ?: "this medication", localSubmitting)
    }
    dialog?.let { state ->
        MedicationDeleteDialog(
            state = state,
            onDismiss = onDismissDelete ?: ::clearDeleteCandidate,
            onConfirm = {
                if (!state.submitting) {
                    if (onDeleteRequested == null) localSubmitting = true
                    onDelete(state.medicationId)
                }
            },
        )
    }
}

/** Medication-local wrapper shared by the Library and Details surfaces. */
@Composable
fun MedicationDeleteDialog(
    state: MedicationDeleteDialogState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MedicationDecisionDialog(
        title = "Delete ${state.medicationName}?",
        body = "Deleting this medication removes its reminder times, pending alarms, and history.",
        error = state.error,
        confirmLabel = if (state.error == null) "Delete" else "Retry delete",
        dismissLabel = "Cancel",
        submitting = state.submitting,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun MedicationDecisionDialog(
    title: String,
    body: String,
    error: String? = null,
    confirmLabel: String,
    dismissLabel: String,
    submitting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Dialog(onDismissRequest = { if (!submitting) onDismiss() }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error != null) Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (submitting) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                if (largeText) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onConfirm, enabled = !submitting,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text(confirmLabel) }
                        TextButton(onClick = onDismiss, enabled = !submitting,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(dismissLabel) }
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss, enabled = !submitting,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(dismissLabel) }
                        Button(
                            onClick = onConfirm, enabled = !submitting,
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text(confirmLabel) }
                    }
                }
            }
        }
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
private fun MedicationInitialReadFailure(onRetryRead: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Could not load medications",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
        Text("Medication list is unavailable right now.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetryRead, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
            Text("Try again")
        }
    }
}

@Composable
private fun MedicationRefreshFailure(message: String, onRetryRead: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetryRead, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text("Try again")
            }
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
    onOpenDetails: (MedicationWithTimes) -> Unit,
    onToggle: (MedicationWithTimes, Boolean) -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onDeleteRequested: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clickable { onOpenDetails(item) }
            .semantics { contentDescription = "Medication details $medicationNumber, ${item.medication.name}" },
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

@Composable
fun MedicationDetailsScreen(
    medication: MedicationWithTimes?,
    onBack: () -> Unit,
    onEdit: (MedicationWithTimes) -> Unit,
    onDeleteRequested: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    deleteDialogState: MedicationDeleteDialogState? = null,
    onDismissDelete: (() -> Unit)? = null,
    readFailed: Boolean = false,
    onRetryRead: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { paneTitle = "Medication details" },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "Back" },
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = null) }
            }
            if (medication == null) {
                if (readFailed) MedicationDetailsInitialReadFailure(onRetryRead, Modifier.weight(1f))
                else MedicationDetailsLoadingState()
            } else {
                MedicationDetailsBody(
                    item = medication,
                    onEdit = onEdit,
                    onDeleteRequested = onDeleteRequested,
                    modifier = Modifier.weight(1f),
                    refreshFailed = readFailed,
                    onRetryRead = onRetryRead,
                )
            }
        }
    }
    if (deleteDialogState != null) {
        MedicationDeleteDialog(
            state = deleteDialogState,
            onDismiss = onDismissDelete ?: {},
            onConfirm = { if (!deleteDialogState.submitting) onDelete(deleteDialogState.medicationId) },
        )
    }
}

@Composable
private fun MedicationDetailsLoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Loading medication details…", style = MaterialTheme.typography.titleMedium)
        Text(
            "Medication information will appear when it is available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MedicationDetailsInitialReadFailure(onRetryRead: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Could not load medication details",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
        Text("Medication information is unavailable right now.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetryRead, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
            Text("Try again")
        }
    }
}

@Composable
private fun MedicationDetailsBody(
    item: MedicationWithTimes,
    onEdit: (MedicationWithTimes) -> Unit,
    onDeleteRequested: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
    refreshFailed: Boolean = false,
    onRetryRead: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag("medication-details-scroll"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (refreshFailed) MedicationRefreshFailure("Medication details could not be refreshed.", onRetryRead)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                item.medication.name,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            if (!item.medication.enabled) {
                Text(
                    "Reminders off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item.medication.instructions?.takeIf { it.isNotBlank() }?.let { instructions ->
            MedicationDetailsSection("Instructions / notes") { Text(instructions, style = MaterialTheme.typography.bodyLarge) }
        }
        MedicationDetailsSection("Reminders") {
            val reminders = item.reminderTimes.sortedWith(compareBy({ it.minuteOfDay }, { it.id }))
            if (reminders.isEmpty()) {
                Text("No reminder times", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminders.forEach { reminder ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.AccessTime, contentDescription = null)
                                Column {
                                    Text(formatMinute(reminder.minuteOfDay), style = MaterialTheme.typography.titleMedium)
                                    Text(formatWeekdays(reminder.weekdayMask), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { onEdit(item) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text("Edit medication") }
        OutlinedButton(
            onClick = { onDeleteRequested(item.medication.id, item.medication.name) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Delete medication") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MedicationDetailsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = LocalMedsReminderColors.current.surfaceElevated,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
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
    isDirty: Boolean = false,
    discardDialogVisible: Boolean? = null,
    onDiscardDialogVisibilityChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val validation = draft.editorValidation
    val mutationLocked = saveState.locksDraft
    val screenTitle = if (draft.id == null) "Add medication" else "Edit medication"
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    fun setDiscardVisible(visible: Boolean) {
        if (onDiscardDialogVisibilityChange != null) onDiscardDialogVisibilityChange(visible)
        else showDiscardDialog = visible
    }
    fun requestExit() {
        if (!mutationLocked) {
            if (isDirty) setDiscardVisible(true) else onCancel()
        }
    }
    // Consume Back while work is owned by the retained ViewModel so it cannot be abandoned.
    if (handleSystemBack) BackHandler(enabled = !mutationLocked) { requestExit() }
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = ::requestExit,
                    enabled = !mutationLocked,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "Back" },
                ) { Icon(Icons.Outlined.ArrowBack, contentDescription = null) }
                Text(
                    screenTitle,
                    modifier = Modifier.padding(start = 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .testTag("medication-editor-scroll"),
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Medication", style = MaterialTheme.typography.titleLarge)
                EditorFieldLabel("Medication name")
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    singleLine = true,
                    isError = validation.blankName,
                    supportingText = if (validation.blankName) {
                        { Text("Enter a medication name", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    enabled = !mutationLocked,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                EditorFieldLabel("Instructions / notes (optional)")
                OutlinedTextField(
                    value = draft.instructions,
                    onValueChange = { onDraftChange(draft.copy(instructions = it)) },
                    enabled = !mutationLocked,
                    minLines = 4,
                    shape = MaterialTheme.shapes.large,
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

            Spacer(Modifier.height(28.dp))
            Text("Reminders", style = MaterialTheme.typography.titleLarge)
            if (draft.id != null) {
                Text(
                    "Removing a reminder and saving also deletes its history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            draft.times.forEachIndexed { index, time ->
                val reminderNumber = index + 1
                val reminderTime = formatMinute(time.minuteOfDay)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderTimeRow(
                        reminderNumber = reminderNumber,
                        reminderTime = reminderTime,
                        canRemove = draft.times.size > 1,
                        enabled = !mutationLocked,
                        onChangeTime = {
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
                        onRemove = {
                            onDraftChange(draft.copy(times = draft.times.filterIndexed { i, _ -> i != index }))
                        },
                    )
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
                if (index != draft.times.lastIndex) Spacer(Modifier.height(20.dp))
            }
            if (validation.noReminders) {
                Text(
                    "Add at least one reminder time",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                enabled = !mutationLocked,
                onClick = {
                    val nextMinute = ((draft.times.maxOfOrNull { it.minuteOfDay } ?: 7 * 60) + 60) % (24 * 60)
                    onDraftChange(draft.copy(times = draft.times + EditorTime(null, nextMinute, WeekdayMask.ALL)))
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.sizeIn(minWidth = 8.dp))
                Text("Add reminder time")
            }
            Spacer(Modifier.height(20.dp))
        }
        EditorBottomActionRegion(
            validation = validation,
            saveState = saveState,
            onSave = { onSave(draft) },
            onRetryPostCommit = onRetryPostCommit,
        )
        }
    }
    if ((discardDialogVisible ?: showDiscardDialog) && !mutationLocked) {
        MedicationDecisionDialog(
            title = "Discard changes?",
            body = "Your unsaved changes to this medication will be lost.",
            confirmLabel = "Discard changes",
            dismissLabel = "Keep editing",
            onDismiss = { setDiscardVisible(false) },
            onConfirm = { setDiscardVisible(false); onCancel() },
        )
    }
}

@Composable
private fun EditorFieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReminderTimeRow(
    reminderNumber: Int,
    reminderTime: String,
    canRemove: Boolean,
    enabled: Boolean,
    onChangeTime: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(enabled = enabled, onClick = onChangeTime)
                    .padding(horizontal = 16.dp)
                    .semantics {
                        contentDescription = "Change reminder $reminderNumber time, currently $reminderTime"
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AccessTime, contentDescription = null)
                Text(reminderTime, style = MaterialTheme.typography.titleMedium)
            }
            if (canRemove) {
                IconButton(
                    enabled = enabled,
                    onClick = onRemove,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Remove reminder $reminderNumber at $reminderTime"
                        },
                ) { Icon(Icons.Outlined.Clear, contentDescription = null) }
            }
        }
    }
}

@Composable
private fun EditorBottomActionRegion(
    validation: EditorValidation,
    saveState: EditorSaveState,
    onSave: () -> Unit,
    onRetryPostCommit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        Button(
            onClick = onSave,
            enabled = validation.isValid && saveState.canStartSubmission,
            shape = MaterialTheme.shapes.extraLarge,
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
    val chipColors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    FilterChip(
        selected = weekdayMask == WeekdayMask.ALL,
        onClick = { onChange(WeekdayMask.ALL) },
        enabled = enabled,
        colors = chipColors,
        modifier = Modifier.semantics {
            contentDescription = "Reminder $reminderNumber at $reminderTime, Every day"
        },
        label = { Text("Every day") },
    )
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val bit = 1 shl (day.value - 1)
            FilterChip(
                selected = weekdayMask and bit != 0,
                onClick = { onChange(weekdayMask xor bit) },
                enabled = enabled,
                colors = chipColors,
                modifier = Modifier.semantics {
                    contentDescription = "Reminder $reminderNumber at $reminderTime, ${fullDayName(day)}"
                },
                label = { Text(dayLabel(day)) },
            )
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
