package com.example.medsreminder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.medsreminder.data.HistoryOccurrence
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.ui.theme.LocalMedsReminderColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class HistoryOutcome(val label: String) {
    TAKEN("Taken"),
    SKIPPED("Skipped"),
    NO_RESPONSE("No response"),
}

enum class HistoryFilter(val label: String) {
    ALL("All"),
    TAKEN("Taken"),
    SKIPPED("Skipped"),
    NO_RESPONSE("No response"),
}

data class HistoryItem(
    val id: String,
    val medicationName: String,
    val scheduledAtEpochMillis: Long,
    val resolvedAtEpochMillis: Long?,
    val outcome: HistoryOutcome,
    val afterSnooze: Boolean,
)

fun HistoryOccurrence.toHistoryItem(): HistoryItem = HistoryItem(
    id = occurrenceId,
    medicationName = medicationName,
    scheduledAtEpochMillis = scheduledAtEpochMillis,
    resolvedAtEpochMillis = resolvedAtEpochMillis,
    outcome = when (status) {
        OccurrenceStatus.TAKEN -> HistoryOutcome.TAKEN
        OccurrenceStatus.SKIPPED -> HistoryOutcome.SKIPPED
        OccurrenceStatus.TIMED_OUT -> HistoryOutcome.NO_RESPONSE
        else -> error("History query returned non-qualifying status: $status")
    },
    afterSnooze = kind == OccurrenceKind.SNOOZE,
)

internal fun historyLocalDateTime(epochMillis: Long, zoneId: ZoneId): LocalDateTime =
    Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()

internal fun historyLocalDate(epochMillis: Long, zoneId: ZoneId): LocalDate =
    historyLocalDateTime(epochMillis, zoneId).toLocalDate()

internal data class HistoryDayGroup(val date: LocalDate, val items: List<HistoryItem>)

/** Keeps Room's newest-first order while using the scheduled occurrence as the day identity. */
internal fun groupHistoryByScheduledDate(items: List<HistoryItem>, zoneId: ZoneId): List<HistoryDayGroup> =
    items.groupBy { historyLocalDate(it.scheduledAtEpochMillis, zoneId) }
        .map { (date, entries) -> HistoryDayGroup(date, entries) }

internal fun HistoryFilter.matches(item: HistoryItem): Boolean = when (this) {
    HistoryFilter.ALL -> true
    HistoryFilter.TAKEN -> item.outcome == HistoryOutcome.TAKEN
    HistoryFilter.SKIPPED -> item.outcome == HistoryOutcome.SKIPPED
    HistoryFilter.NO_RESPONSE -> item.outcome == HistoryOutcome.NO_RESPONSE
}

internal fun formatHistoryScheduledTime(epochMillis: Long, zoneId: ZoneId): String =
    "Scheduled ${HISTORY_TIME_FORMATTER.format(historyLocalDateTime(epochMillis, zoneId))}"

internal fun formatHistoryDay(date: LocalDate): String = HISTORY_DAY_FORMATTER.format(date)

internal fun formatHistoryResult(item: HistoryItem, zoneId: ZoneId): String? {
    val resolvedAt = item.resolvedAtEpochMillis ?: return null
    val prefix = when (item.outcome) {
        HistoryOutcome.TAKEN -> "Taken at"
        HistoryOutcome.SKIPPED -> "Skipped at"
        HistoryOutcome.NO_RESPONSE -> "Timed out at"
    }
    val resolved = historyLocalDateTime(resolvedAt, zoneId)
    val scheduledDate = historyLocalDate(item.scheduledAtEpochMillis, zoneId)
    val resultTime = HISTORY_TIME_FORMATTER.format(resolved)
    return if (resolved.toLocalDate() == scheduledDate) {
        "$prefix $resultTime"
    } else {
        "$prefix ${HISTORY_DAY_FORMATTER.format(resolved.toLocalDate())} \u2022 $resultTime"
    }
}

@Composable
fun HistoryScreen(
    history: List<HistoryItem>?,
    onBack: () -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val zoneId = ZoneId.systemDefault()
    val filteredHistory = history?.filter(selectedFilter::matches).orEmpty()
    val dayGroups = groupHistoryByScheduledDate(filteredHistory, zoneId)

    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
            .semantics { paneTitle = "History" },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HistoryTopBar(onBack) }
        if (history == null || history.isNotEmpty()) {
            item {
                HistoryFilters(
                    selectedFilter = selectedFilter,
                    enabled = history != null,
                    onSelected = { selectedFilter = it },
                )
            }
        }
        when {
            history == null -> item { HistoryLoading() }
            history.isEmpty() -> item { HistoryGlobalEmpty() }
            dayGroups.isEmpty() -> item { HistoryFilterEmpty { selectedFilter = HistoryFilter.ALL } }
            else -> dayGroups.forEach { group ->
                item(key = "day-${group.date}") {
                    Text(
                        formatHistoryDay(group.date),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(group.items, key = HistoryItem::id) { item -> HistoryCard(item, zoneId) }
            }
        }
    }
}

@Composable
private fun HistoryTopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Back" },
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
        Text(
            "History",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun HistoryFilters(
    selectedFilter: HistoryFilter,
    enabled: Boolean,
    onSelected: (HistoryFilter) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelected(filter) },
                enabled = enabled,
                modifier = Modifier.sizeIn(minHeight = 48.dp),
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, zoneId: ZoneId) {
    val statusColors = LocalMedsReminderColors.current
    val outcomeColor = when (item.outcome) {
        HistoryOutcome.TAKEN -> statusColors.success
        HistoryOutcome.SKIPPED -> statusColors.warning
        HistoryOutcome.NO_RESPONSE -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.medicationName, style = MaterialTheme.typography.titleMedium)
            Text(formatHistoryScheduledTime(item.scheduledAtEpochMillis, zoneId), style = MaterialTheme.typography.bodyMedium)
            Text(item.outcome.label, color = outcomeColor, style = MaterialTheme.typography.labelLarge)
            formatHistoryResult(item, zoneId)?.let { result ->
                Text(result, style = MaterialTheme.typography.bodyMedium)
            }
            if (item.afterSnooze) Text("After snooze", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Loading history\u2026", style = MaterialTheme.typography.bodyMedium)
        repeat(3) {
            Box(
                Modifier.fillMaxWidth().height(108.dp).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun HistoryGlobalEmpty() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("No history yet", style = MaterialTheme.typography.titleLarge)
        Text("Resolved medication reminders will appear here.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HistoryFilterEmpty(onShowAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No entries for this filter", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onShowAll, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Show all") }
    }
}

private val HISTORY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val HISTORY_DAY_FORMATTER = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)
