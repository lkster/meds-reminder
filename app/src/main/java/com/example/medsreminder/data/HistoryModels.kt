package com.example.medsreminder.data

import androidx.room.ColumnInfo

data class HistoryOccurrence(
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "medication_id") val medicationId: Long,
    @ColumnInfo(name = "medication_name") val medicationName: String,
    val kind: OccurrenceKind,
    val status: OccurrenceStatus,
    @ColumnInfo(name = "scheduled_at_epoch_millis") val scheduledAtEpochMillis: Long,
    @ColumnInfo(name = "resolved_at_epoch_millis") val resolvedAtEpochMillis: Long?,
)
