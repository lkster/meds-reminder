package com.example.medsreminder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
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
            .padding(horizontal = 24.dp)
            .semantics { paneTitle = "History" },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 28.dp),
    ) {
        item { HistoryTopBar(onBack) }
        if (history == null || history.isNotEmpty()) {
            item { Spacer(Modifier.height(20.dp)) }
            item {
                HistoryFilters(
                    selectedFilter = selectedFilter,
                    enabled = history != null,
                    onSelected = { selectedFilter = it },
                )
            }
        }
        when {
            history == null -> {
                item { Spacer(Modifier.height(24.dp)) }
                item { HistoryLoading() }
            }
            history.isEmpty() -> {
                item { Spacer(Modifier.height(64.dp)) }
                item { HistoryGlobalEmpty() }
            }
            dayGroups.isEmpty() -> {
                item { Spacer(Modifier.height(28.dp)) }
                item { HistoryFilterEmpty { selectedFilter = HistoryFilter.ALL } }
            }
            else -> dayGroups.forEachIndexed { groupIndex, group ->
                item { Spacer(Modifier.height(if (groupIndex == 0) 24.dp else 26.dp)) }
                item(key = "day-${group.date}") {
                    Text(
                        formatHistoryDay(group.date),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                item { Spacer(Modifier.height(9.dp)) }
                group.items.forEachIndexed { itemIndex, item ->
                    item(key = item.id) {
                        Column {
                            HistoryCard(item, zoneId)
                            if (itemIndex != group.items.lastIndex) Spacer(Modifier.height(11.dp))
                        }
                    }
                }
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
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
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
            HistoryFilterPill(
                filter = filter,
                selected = selectedFilter == filter,
                enabled = enabled,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun HistoryFilterPill(
    filter: HistoryFilter,
    selected: Boolean,
    enabled: Boolean,
    onSelected: (HistoryFilter) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .semantics(mergeDescendants = true) {}
            .clip(MaterialTheme.shapes.extraLarge)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = { onSelected(filter) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = if (selected) colors.primaryContainer else colors.surface,
            contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            shape = MaterialTheme.shapes.extraLarge,
            border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Text(
                filter.label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
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
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp),
        colors = CardDefaults.cardColors(containerColor = statusColors.surfaceElevated),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    item.medicationName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.padding(start = 8.dp))
                HistoryOutcomePill(item.outcome.label, outcomeColor)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    formatHistoryScheduledTime(item.scheduledAtEpochMillis, zoneId),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                formatHistoryResult(item, zoneId)?.let { result ->
                    Text(
                        result,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (item.afterSnooze) {
                    Text(
                        "After snooze",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryOutcomePill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun HistoryLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Loading history\u2026", style = MaterialTheme.typography.bodyMedium)
        repeat(3) {
            Box(
                Modifier.fillMaxWidth().height(80.dp).clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun HistoryGlobalEmpty() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("No history yet", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
        Text("Resolved medication reminders will appear here.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HistoryFilterEmpty(onShowAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No entries for this filter", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onShowAll, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Show all") }
    }
}

private val HISTORY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val HISTORY_DAY_FORMATTER = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)
