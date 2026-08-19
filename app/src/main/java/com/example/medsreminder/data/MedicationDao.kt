package com.example.medsreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Transaction
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<MedicationWithTimes>>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getWithTimes(id: Long): MedicationWithTimes?

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun get(id: Long): MedicationEntity?

    @Insert
    suspend fun insertMedication(medication: MedicationEntity): Long

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Delete
    suspend fun deleteMedication(medication: MedicationEntity)

    @Query("SELECT * FROM reminder_times WHERE medication_id = :medicationId ORDER BY minute_of_day, id")
    suspend fun getTimes(medicationId: Long): List<ReminderTimeEntity>

    @Query("SELECT medication_id FROM reminder_times WHERE id = :reminderTimeId")
    suspend fun getMedicationIdForReminder(reminderTimeId: Long): Long?

    @Insert
    suspend fun insertTime(reminderTime: ReminderTimeEntity): Long

    @Update
    suspend fun updateTime(reminderTime: ReminderTimeEntity)

    @Delete
    suspend fun deleteTime(reminderTime: ReminderTimeEntity)

    @Query(
        """
        SELECT rt.id AS reminder_time_id, rt.medication_id, rt.minute_of_day, rt.weekday_mask
        FROM reminder_times rt
        INNER JOIN medications m ON m.id = rt.medication_id
        WHERE m.enabled = 1
        ORDER BY rt.id
        """,
    )
    suspend fun getEnabledReminderTimes(): List<EnabledReminderTime>
}
