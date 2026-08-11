package com.example.medsreminder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

private const val DETAILS_SELECT = """
    SELECT ao.id AS occurrence_id,
           ao.reminder_time_id,
           ao.kind,
           ao.status,
           ao.scheduled_at_epoch_millis,
           ao.presented_at_epoch_millis,
           m.id AS medication_id,
           m.name AS medication_name,
           m.instructions,
           rt.minute_of_day
    FROM alarm_occurrences ao
    INNER JOIN reminder_times rt ON rt.id = ao.reminder_time_id
    INNER JOIN medications m ON m.id = rt.medication_id
"""

@Dao
interface OccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(occurrence: AlarmOccurrenceEntity): Long

    @Query("SELECT * FROM alarm_occurrences WHERE id = :id")
    suspend fun get(id: String): AlarmOccurrenceEntity?

    @Query("$DETAILS_SELECT WHERE ao.id = :id")
    suspend fun getDetails(id: String): OccurrenceDetails?

    @Query(
        "$DETAILS_SELECT WHERE ao.status = 'RINGING' " +
            "ORDER BY CASE WHEN ao.presented_at_epoch_millis IS NULL THEN 1 ELSE 0 END, " +
            "ao.presented_at_epoch_millis, ao.scheduled_at_epoch_millis, ao.id LIMIT 1",
    )
    suspend fun getCurrentRinging(): OccurrenceDetails?

    @Query(
        "$DETAILS_SELECT WHERE ao.status = 'RINGING' " +
            "ORDER BY CASE WHEN ao.presented_at_epoch_millis IS NULL THEN 1 ELSE 0 END, " +
            "ao.presented_at_epoch_millis, ao.scheduled_at_epoch_millis, ao.id LIMIT 1",
    )
    fun observeCurrentRinging(): Flow<OccurrenceDetails?>

    @Query("SELECT COUNT(*) FROM alarm_occurrences WHERE status = 'RINGING'")
    suspend fun ringingCount(): Int

    @Query(
        "SELECT COUNT(*) FROM alarm_occurrences WHERE status = 'RINGING' " +
            "AND presented_at_epoch_millis IS NOT NULL",
    )
    suspend fun presentedRingingCount(): Int

    @Query(
        "UPDATE alarm_occurrences SET status = :newStatus, resolved_at_epoch_millis = :resolvedAt " +
            "WHERE id = :id AND status = :expectedStatus",
    )
    suspend fun transition(
        id: String,
        expectedStatus: OccurrenceStatus,
        newStatus: OccurrenceStatus,
        resolvedAt: Long?,
    ): Int

    @Transaction
    suspend fun commitSnooze(
        originalOccurrenceId: String,
        snooze: AlarmOccurrenceEntity,
        resolvedAt: Long,
    ): Boolean {
        if (transition(
                originalOccurrenceId,
                OccurrenceStatus.RINGING,
                OccurrenceStatus.SNOOZED,
                resolvedAt,
            ) != 1
        ) return false
        check(insert(snooze) != -1L) { "Snooze UUID collision" }
        return true
    }

    @Query(
        "UPDATE alarm_occurrences SET presented_at_epoch_millis = :presentedAt " +
            "WHERE id = :id AND status = 'RINGING' AND presented_at_epoch_millis IS NULL",
    )
    suspend fun markPresented(id: String, presentedAt: Long): Int

    @Query(
        "UPDATE alarm_occurrences SET presented_at_epoch_millis = NULL " +
            "WHERE status = 'RINGING' AND id != :currentId AND presented_at_epoch_millis IS NOT NULL",
    )
    suspend fun clearOtherPresented(currentId: String)

    @Query(
        "SELECT * FROM alarm_occurrences WHERE status = 'SCHEDULED' " +
            "AND scheduled_at_epoch_millis > :nowMillis ORDER BY scheduled_at_epoch_millis, id",
    )
    suspend fun getFutureScheduled(nowMillis: Long): List<AlarmOccurrenceEntity>

    @Query(
        "SELECT * FROM alarm_occurrences WHERE reminder_time_id = :reminderTimeId " +
            "AND kind = 'BASE' AND status = 'SCHEDULED' AND scheduled_at_epoch_millis > :nowMillis " +
            "ORDER BY scheduled_at_epoch_millis LIMIT 1",
    )
    suspend fun getFutureBase(reminderTimeId: Long, nowMillis: Long): AlarmOccurrenceEntity?

    @Query(
        "SELECT id FROM alarm_occurrences WHERE reminder_time_id = :reminderTimeId " +
            "AND status IN ('SCHEDULED', 'RINGING')",
    )
    suspend fun getNonterminalIds(reminderTimeId: Long): List<String>

    @Query(
        "SELECT ao.id FROM alarm_occurrences ao INNER JOIN reminder_times rt ON rt.id = ao.reminder_time_id " +
            "WHERE rt.medication_id = :medicationId AND ao.status IN ('SCHEDULED', 'RINGING')",
    )
    suspend fun getMedicationNonterminalIds(medicationId: Long): List<String>

    @Query(
        "DELETE FROM alarm_occurrences WHERE reminder_time_id = :reminderTimeId " +
            "AND status IN ('SCHEDULED', 'RINGING')",
    )
    suspend fun deleteNonterminal(reminderTimeId: Long)

    @Query(
        "DELETE FROM alarm_occurrences WHERE reminder_time_id IN " +
            "(SELECT id FROM reminder_times WHERE medication_id = :medicationId) " +
            "AND status IN ('SCHEDULED', 'RINGING')",
    )
    suspend fun deleteMedicationNonterminal(medicationId: Long)

    @Query(
        "SELECT id FROM alarm_occurrences WHERE kind = 'BASE' AND status = 'SCHEDULED' " +
            "AND scheduled_at_epoch_millis > :nowMillis",
    )
    suspend fun getFutureBaseIds(nowMillis: Long): List<String>

    @Query(
        "DELETE FROM alarm_occurrences WHERE kind = 'BASE' AND status = 'SCHEDULED' " +
            "AND scheduled_at_epoch_millis > :nowMillis",
    )
    suspend fun deleteFutureBases(nowMillis: Long)

    @Query(
        "SELECT id FROM alarm_occurrences WHERE status = 'SCHEDULED' " +
            "AND scheduled_at_epoch_millis <= :cutoffMillis",
    )
    suspend fun getPastScheduledIds(cutoffMillis: Long): List<String>

    @Query(
        "UPDATE alarm_occurrences SET status = 'EXPIRED', resolved_at_epoch_millis = :resolvedAt " +
            "WHERE status = 'SCHEDULED' AND scheduled_at_epoch_millis <= :cutoffMillis",
    )
    suspend fun expireScheduledThrough(cutoffMillis: Long, resolvedAt: Long): Int

    @Query(
        "UPDATE alarm_occurrences SET status = 'EXPIRED', resolved_at_epoch_millis = :resolvedAt " +
            "WHERE status = 'RINGING' AND presented_at_epoch_millis IS NULL " +
            "AND scheduled_at_epoch_millis <= :cutoffMillis",
    )
    suspend fun expireUnpresentedRingingThrough(cutoffMillis: Long, resolvedAt: Long): Int

    @Query(
        "UPDATE alarm_occurrences SET status = 'EXPIRED', resolved_at_epoch_millis = :resolvedAt " +
            "WHERE status = 'RINGING'",
    )
    suspend fun expireAllRinging(resolvedAt: Long): Int
}
