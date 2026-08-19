package com.example.medsreminder.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val instructions: String?,
    val enabled: Boolean,
)

@Entity(
    tableName = "reminder_times",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("medication_id"),
        Index(value = ["medication_id", "minute_of_day"], unique = true),
    ],
)
data class ReminderTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medication_id") val medicationId: Long,
    @ColumnInfo(name = "minute_of_day") val minuteOfDay: Int,
    @ColumnInfo(name = "weekday_mask", defaultValue = "127") val weekdayMask: Int = WeekdayMask.ALL,
) {
    init {
        WeekdayMask.requireValid(weekdayMask)
    }
}

enum class OccurrenceKind {
    BASE,
    SNOOZE,
}

enum class OccurrenceStatus {
    SCHEDULED,
    RINGING,
    TAKEN,
    SKIPPED,
    SNOOZED,
    TIMED_OUT,
    EXPIRED,
}

@Entity(
    tableName = "alarm_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = ReminderTimeEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminder_time_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("reminder_time_id"),
        Index(
            value = ["reminder_time_id", "kind", "scheduled_at_epoch_millis"],
            unique = true,
        ),
        Index(value = ["status", "scheduled_at_epoch_millis"]),
    ],
)
data class AlarmOccurrenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "reminder_time_id") val reminderTimeId: Long,
    val kind: OccurrenceKind,
    @ColumnInfo(name = "scheduled_at_epoch_millis") val scheduledAtEpochMillis: Long,
    val status: OccurrenceStatus,
    @ColumnInfo(name = "presented_at_epoch_millis") val presentedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "resolved_at_epoch_millis") val resolvedAtEpochMillis: Long? = null,
)

data class MedicationWithTimes(
    @Embedded val medication: MedicationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "medication_id",
    )
    val reminderTimes: List<ReminderTimeEntity>,
)

data class EnabledReminderTime(
    @ColumnInfo(name = "reminder_time_id") val reminderTimeId: Long,
    @ColumnInfo(name = "medication_id") val medicationId: Long,
    @ColumnInfo(name = "minute_of_day") val minuteOfDay: Int,
    @ColumnInfo(name = "weekday_mask") val weekdayMask: Int,
)

data class OccurrenceDetails(
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "reminder_time_id") val reminderTimeId: Long,
    val kind: OccurrenceKind,
    val status: OccurrenceStatus,
    @ColumnInfo(name = "scheduled_at_epoch_millis") val scheduledAtEpochMillis: Long,
    @ColumnInfo(name = "presented_at_epoch_millis") val presentedAtEpochMillis: Long?,
    @ColumnInfo(name = "medication_id") val medicationId: Long,
    @ColumnInfo(name = "medication_name") val medicationName: String,
    val instructions: String?,
    @ColumnInfo(name = "minute_of_day") val minuteOfDay: Int,
    @ColumnInfo(name = "weekday_mask") val weekdayMask: Int,
)
