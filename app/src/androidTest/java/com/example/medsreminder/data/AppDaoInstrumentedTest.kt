package com.example.medsreminder.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.medsreminder.alarm.AlarmReconciler
import com.example.medsreminder.alarm.AlarmScheduler
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDaoInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingMedicationCascadesTimesAndOccurrences() = runBlocking {
        val medication = MedicationEntity(name = "Medicine", instructions = null, enabled = true)
        val medicationId = database.medicationDao().insertMedication(medication)
        val timeId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 480),
        )
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = "018f5f7f-4af8-7c3d-9f67-0a8db8929999",
                reminderTimeId = timeId,
                kind = OccurrenceKind.BASE,
                scheduledAtEpochMillis = 2_000L,
                status = OccurrenceStatus.SCHEDULED,
            ),
        )

        database.medicationDao().deleteMedication(medication.copy(id = medicationId))

        assertEquals(emptyList<ReminderTimeEntity>(), database.medicationDao().getTimes(medicationId))
        assertEquals(null, database.occurrenceDao().get("018f5f7f-4af8-7c3d-9f67-0a8db8929999"))
    }

    @Test
    fun reminderTimeMinuteIsUniqueWithinMedicationOnly() = runBlocking {
        val firstMedicationId = insertMedication("First")
        val secondMedicationId = insertMedication("Second")

        database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = firstMedicationId, minuteOfDay = 480),
        )
        val duplicateFailure = runCatching {
            database.medicationDao().insertTime(
                ReminderTimeEntity(medicationId = firstMedicationId, minuteOfDay = 480),
            )
        }.exceptionOrNull()
        database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = secondMedicationId, minuteOfDay = 480),
        )

        assertTrue(duplicateFailure is SQLiteConstraintException)
        assertEquals(1, database.medicationDao().getTimes(firstMedicationId).size)
        assertEquals(1, database.medicationDao().getTimes(secondMedicationId).size)
    }

    @Test
    fun claimingBaseOccurrencePersistsExactlyOneNextBase() = runBlocking {
        val reminderTimeId = insertReminderTime("Medicine", 480)
        val occurrenceId = "018f5f7f-4af8-7c3d-9f67-0a8db8920001"
        val dueAt = Instant.parse("2026-08-12T08:00:00Z").toEpochMilli()
        val now = dueAt + 1
        database.occurrenceDao().insert(
            occurrence(
                id = occurrenceId,
                reminderTimeId = reminderTimeId,
                status = OccurrenceStatus.SCHEDULED,
                scheduledAt = dueAt,
            ),
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reconciler = AlarmReconciler(context, database, AlarmScheduler(context))

        suspend fun claimAndAdvance(): Int {
            val claimed = database.occurrenceDao().transition(
                occurrenceId,
                OccurrenceStatus.SCHEDULED,
                OccurrenceStatus.RINGING,
                null,
            )
            if (claimed == 1) {
                reconciler.ensureFutureBase(reminderTimeId, 480, WeekdayMask.ALL, now, ZoneId.of("UTC"))
            }
            return claimed
        }

        assertEquals(1, claimAndAdvance())
        assertEquals(0, claimAndAdvance())

        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get(occurrenceId)?.status)
        val futureBases = database.occurrenceDao().getFutureScheduled(now).filter {
            it.reminderTimeId == reminderTimeId && it.kind == OccurrenceKind.BASE
        }
        assertEquals(1, futureBases.size)
        assertEquals(
            Instant.parse("2026-08-13T08:00:00Z").toEpochMilli(),
            futureBases.single().scheduledAtEpochMillis,
        )
    }

    @Test
    fun snoozeCommitTransitionsExactOriginalAndCannotRepeat() = runBlocking {
        val reminderTimeId = insertReminderTime("Medicine", 480)
        val original = occurrence(
            id = "018f5f7f-4af8-7c3d-9f67-0a8db8920002",
            reminderTimeId = reminderTimeId,
            status = OccurrenceStatus.RINGING,
            scheduledAt = 1_000L,
        )
        val snooze = occurrence(
            id = "018f5f7f-4af8-7c3d-9f67-0a8db8920003",
            reminderTimeId = reminderTimeId,
            kind = OccurrenceKind.SNOOZE,
            status = OccurrenceStatus.SCHEDULED,
            scheduledAt = 301_000L,
        )
        database.occurrenceDao().insert(original)

        assertTrue(database.occurrenceDao().commitSnooze(original.id, snooze, 2_000L))
        assertEquals(OccurrenceStatus.SNOOZED, database.occurrenceDao().get(original.id)?.status)
        assertEquals(OccurrenceStatus.SCHEDULED, database.occurrenceDao().get(snooze.id)?.status)

        val losingSnooze = occurrence(
            id = "018f5f7f-4af8-7c3d-9f67-0a8db8920004",
            reminderTimeId = reminderTimeId,
            kind = OccurrenceKind.SNOOZE,
            status = OccurrenceStatus.SCHEDULED,
            scheduledAt = 302_000L,
        )
        assertFalse(database.occurrenceDao().commitSnooze(original.id, losingSnooze, 3_000L))
        assertNull(database.occurrenceDao().get(losingSnooze.id))
        val scheduledSnoozes = database.occurrenceDao().getFutureScheduled(0L).filter {
            it.reminderTimeId == reminderTimeId && it.kind == OccurrenceKind.SNOOZE
        }
        assertEquals(listOf(snooze.id), scheduledSnoozes.map { it.id })
    }

    @Test
    fun resolvingPresentedOccurrenceAdvancesToNextRingingOccurrence() = runBlocking {
        val reminderTimeId = insertReminderTime("Medicine", 480)
        val first = occurrence(
            id = "018f5f7f-4af8-7c3d-9f67-0a8db8920005",
            reminderTimeId = reminderTimeId,
            status = OccurrenceStatus.RINGING,
            scheduledAt = 1_000L,
        )
        val second = occurrence(
            id = "018f5f7f-4af8-7c3d-9f67-0a8db8920006",
            reminderTimeId = reminderTimeId,
            status = OccurrenceStatus.RINGING,
            scheduledAt = 2_000L,
        )
        database.occurrenceDao().insert(first)
        database.occurrenceDao().insert(second)

        assertEquals(2, database.occurrenceDao().ringingCount())
        assertEquals(first.id, database.occurrenceDao().getCurrentRinging()?.occurrenceId)
        assertEquals(1, database.occurrenceDao().markPresented(first.id, 10_000L))
        database.occurrenceDao().clearOtherPresented(first.id)
        assertEquals(1, database.occurrenceDao().presentedRingingCount())
        assertEquals(first.id, database.occurrenceDao().getCurrentRinging()?.occurrenceId)

        assertEquals(
            1,
            database.occurrenceDao().transition(
                first.id,
                OccurrenceStatus.RINGING,
                OccurrenceStatus.TAKEN,
                11_000L,
            ),
        )
        assertEquals(second.id, database.occurrenceDao().getCurrentRinging()?.occurrenceId)
        assertEquals(1, database.occurrenceDao().markPresented(second.id, 12_000L))
        database.occurrenceDao().clearOtherPresented(second.id)

        assertEquals(1, database.occurrenceDao().ringingCount())
        assertEquals(1, database.occurrenceDao().presentedRingingCount())
        assertEquals(second.id, database.occurrenceDao().getCurrentRinging()?.occurrenceId)
    }

    private suspend fun insertMedication(name: String): Long =
        database.medicationDao().insertMedication(
            MedicationEntity(name = name, instructions = null, enabled = true),
        )

    private suspend fun insertReminderTime(name: String, minuteOfDay: Int): Long {
        val medicationId = insertMedication(name)
        return database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = minuteOfDay),
        )
    }

    private fun occurrence(
        id: String,
        reminderTimeId: Long,
        status: OccurrenceStatus,
        scheduledAt: Long,
        kind: OccurrenceKind = OccurrenceKind.BASE,
    ) = AlarmOccurrenceEntity(
        id = id,
        reminderTimeId = reminderTimeId,
        kind = kind,
        scheduledAtEpochMillis = scheduledAt,
        status = status,
    )
}
