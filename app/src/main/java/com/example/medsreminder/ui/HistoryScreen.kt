package com.example.medsreminder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.medsreminder.data.HistoryOccurrence
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class HistoryOutcome(val label: String) {
    TAKEN("Taken"),
    SKIPPED("Skipped"),
    NO_RESPONSE("No response"),
}

data class HistoryItem(
    val id: String,
    val medicationName: String,
    val scheduledAtEpochMillis: Long,
    val outcome: HistoryOutcome,
    val afterSnooze: Boolean,
)

fun HistoryOccurrence.toHistoryItem(): HistoryItem = HistoryItem(
    id = occurrenceId,
    medicationName = medicationName,
    scheduledAtEpochMillis = scheduledAtEpochMillis,
    outcome = when (status) {
        OccurrenceStatus.TAKEN -> HistoryOutcome.TAKEN
        OccurrenceStatus.SKIPPED -> HistoryOutcome.SKIPPED
        OccurrenceStatus.TIMED_OUT -> HistoryOutcome.NO_RESPONSE
        else -> error("History query returned non-qualifying status: $status")
    },
    afterSnooze = kind == OccurrenceKind.SNOOZE,
)

internal fun historyLocalDateTime(
    scheduledAtEpochMillis: Long,
    zoneId: ZoneId,
): LocalDateTime = Instant.ofEpochMilli(scheduledAtEpochMillis).atZone(zoneId).toLocalDateTime()

@Composable
fun HistoryScreen(
    history: List<HistoryItem>?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp).semantics { paneTitle = "History" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("History", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        when {
            history == null -> item { Text("Loading history…") }
            history.isEmpty() -> item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No history yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Taken, Skipped, and presented alarms with No response appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> items(history, key = HistoryItem::id) { item ->
                HistoryCard(item)
            }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.medicationName, style = MaterialTheme.typography.titleLarge)
            Text(formatHistoryTime(item.scheduledAtEpochMillis), style = MaterialTheme.typography.bodyMedium)
            Text(item.outcome.label, style = MaterialTheme.typography.titleMedium)
            if (item.afterSnooze) Text("After snooze", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val HISTORY_TIME_FORMATTER = DateTimeFormatter.ofPattern("EEE, d MMM yyyy • HH:mm")

private fun formatHistoryTime(scheduledAtEpochMillis: Long): String = HISTORY_TIME_FORMATTER.format(
    historyLocalDateTime(scheduledAtEpochMillis, ZoneId.systemDefault()),
)
