package com.example.medsreminder.data

import androidx.room.Room
import com.example.medsreminder.alarm.AlarmReconciler
import com.example.medsreminder.alarm.AlarmScheduler
import com.example.medsreminder.alarm.ReconciliationMode
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MedicationScheduleEditsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = Instant.parse("2026-08-17T06:00:10Z").toEpochMilli() // Monday 08:00:10
    private lateinit var database: AppDatabase
    private var medicationId = 0L
    private var reminderTimeId = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = "Medicine", instructions = null, enabled = true),
        )
        reminderTimeId = database.medicationDao().insertTime(
            ReminderTimeEntity(
                medicationId = medicationId,
                minuteOfDay = 8 * 60,
                weekdayMask = MONDAY_TO_FRIDAY,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun weekdayEditPreservesEligibleDueUuidAndCorrectCanonicalFuture() = runBlocking {
        insert("due", dueMillis())
        insert("future", Instant.parse("2026-08-18T06:00:00Z").toEpochMilli())

        updateWeekdays(MONDAY_TO_SATURDAY)

        assertEquals(OccurrenceStatus.SCHEDULED, database.occurrenceDao().get("due")?.status)
        assertEquals(OccurrenceStatus.SCHEDULED, database.occurrenceDao().get("future")?.status)
        assertEquals(listOf("due", "future"), database.occurrenceDao().getScheduledBases(reminderTimeId).map { it.id })
    }

    @Test
    fun weekdayEditInvalidatesDueUuidWhenConcreteWeekdayWasRemoved() = runBlocking {
        insert("due", dueMillis())
        insert("future", Instant.parse("2026-08-18T06:00:00Z").toEpochMilli())

        val result = updateWeekdays(TUESDAY_TO_SATURDAY)

        assertNull(database.occurrenceDao().get("due"))
        assertEquals(listOf("due"), result.obsoleteOccurrenceIds)
        assertEquals("future", database.occurrenceDao().getFutureBase(reminderTimeId, now)?.id)
    }

    @Test
    fun weekdayEditReplacesCanonicalFutureWhenExpectedInstantChanges() = runBlocking {
        insert("old-future", Instant.parse("2026-08-18T06:00:00Z").toEpochMilli())

        val result = updateWeekdays(MONDAY_ONLY)

        assertNull(database.occurrenceDao().get("old-future"))
        assertEquals(listOf("old-future"), result.obsoleteOccurrenceIds)
        val replacement = database.occurrenceDao().getFutureBase(reminderTimeId, now)
        assertEquals(Instant.parse("2026-08-24T06:00:00Z").toEpochMilli(), replacement?.scheduledAtEpochMillis)
    }

    @Test
    fun weekdayEditDoesNotRetainDueBaseOutsideGrace() = runBlocking {
        val outsideGrace = now - AlarmScheduler.DELIVERY_GRACE_MILLIS - 1L
        insert("old-due", outsideGrace)

        val result = updateWeekdays(MONDAY_TO_SATURDAY)

        assertNull(database.occurrenceDao().get("old-due"))
        assertEquals(listOf("old-due"), result.obsoleteOccurrenceIds)
        assertNotNull(database.occurrenceDao().getFutureBase(reminderTimeId, now))
    }

    @Test
    fun routineReconciliationPreservesDuePlusCanonicalFutureInvariant() = runBlocking {
        insert("due", dueMillis())
        val reconciler = AlarmReconciler(context, database, AlarmScheduler(context))

        reconciler.reconcile(ReconciliationMode.ROUTINE, now, zone)

        val scheduled = database.occurrenceDao().getScheduledBases(reminderTimeId)
        assertEquals(2, scheduled.size)
        assertEquals("due", scheduled.first().id)
        assertEquals(1, scheduled.count { it.scheduledAtEpochMillis > now })
    }

    @Test
    fun editBeforeReceiverClaimPreservesEligibleDueAndDoesNotDuplicateFuture() = runBlocking {
        insert("due", dueMillis())
        updateWeekdays(MONDAY_TO_SATURDAY)

        assertEquals(
            1,
            database.occurrenceDao().transition(
                "due",
                OccurrenceStatus.SCHEDULED,
                OccurrenceStatus.RINGING,
                null,
            ),
        )
        assertEquals(1, database.occurrenceDao().getScheduledBases(reminderTimeId).count {
            it.scheduledAtEpochMillis > now
        })
    }

    @Test
    fun editBeforeReceiverClaimRejectsInvalidatedDue() = runBlocking {
        insert("due", dueMillis())
        updateWeekdays(TUESDAY_TO_SATURDAY)

        assertEquals(
            0,
            database.occurrenceDao().transition(
                "due",
                OccurrenceStatus.SCHEDULED,
                OccurrenceStatus.RINGING,
                null,
            ),
        )
    }

    @Test
    fun receiverClaimBeforeWeekdayEditPreservesRingingOccurrence() = runBlocking {
        insert("due", dueMillis())
        database.occurrenceDao().transition(
            "due",
            OccurrenceStatus.SCHEDULED,
            OccurrenceStatus.RINGING,
            null,
        )

        updateWeekdays(TUESDAY_TO_SATURDAY)

        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get("due")?.status)
        assertEquals(1, database.occurrenceDao().getScheduledBases(reminderTimeId).count {
            it.scheduledAtEpochMillis > now
        })
    }

    @Test
    fun weekdayEditPreservesPendingSnooze() = runBlocking {
        insert(
            id = "snooze",
            scheduledAt = now + 5 * 60 * 1000L,
            kind = OccurrenceKind.SNOOZE,
        )

        updateWeekdays(MONDAY_TO_SATURDAY)

        assertEquals(OccurrenceStatus.SCHEDULED, database.occurrenceDao().get("snooze")?.status)
    }

    @Test
    fun weekdayEditPreservesRingingSnooze() = runBlocking {
        val ringingSnooze = AlarmOccurrenceEntity(
            id = "ringing-snooze",
            reminderTimeId = reminderTimeId,
            kind = OccurrenceKind.SNOOZE,
            scheduledAtEpochMillis = now - 30_000L,
            status = OccurrenceStatus.RINGING,
            presentedAtEpochMillis = now - 20_000L,
        )
        database.occurrenceDao().insert(ringingSnooze)

        updateWeekdays(MONDAY_TO_SATURDAY)

        assertEquals(ringingSnooze, database.occurrenceDao().get(ringingSnooze.id))
    }

    @Test
    fun weekdayEditPreservesTerminalOccurrence() = runBlocking {
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = "taken",
                reminderTimeId = reminderTimeId,
                kind = OccurrenceKind.BASE,
                scheduledAtEpochMillis = now - 60_000L,
                status = OccurrenceStatus.TAKEN,
                resolvedAtEpochMillis = now - 30_000L,
            ),
        )

        updateWeekdays(MONDAY_ONLY)

        assertEquals(OccurrenceStatus.TAKEN, database.occurrenceDao().get("taken")?.status)
    }

    @Test
    fun sourceTimeEditKeepsM1NonterminalCancellationSemantics() = runBlocking {
        insert("base", now + 60_000L)
        insert("snooze", now + 5 * 60_000L, OccurrenceKind.SNOOZE)

        val result = apply(
            ReminderScheduleEdit(reminderTimeId, 9 * 60, MONDAY_TO_FRIDAY),
        )

        assertNull(database.occurrenceDao().get("base"))
        assertNull(database.occurrenceDao().get("snooze"))
        assertEquals(setOf("base", "snooze"), result.obsoleteOccurrenceIds.toSet())
        val replacement = database.occurrenceDao().getFutureBase(reminderTimeId, now)
        assertNotNull(replacement)
        assertNotEquals("base", replacement?.id)
    }

    @Test
    fun repeatedUnchangedEditDoesNotDuplicateCanonicalFuture() = runBlocking {
        apply(ReminderScheduleEdit(reminderTimeId, 8 * 60, MONDAY_TO_FRIDAY))
        val first = database.occurrenceDao().getFutureBase(reminderTimeId, now)

        apply(ReminderScheduleEdit(reminderTimeId, 8 * 60, MONDAY_TO_FRIDAY))

        assertEquals(first?.id, database.occurrenceDao().getFutureBase(reminderTimeId, now)?.id)
        assertEquals(1, database.occurrenceDao().getScheduledBases(reminderTimeId).count {
            it.scheduledAtEpochMillis > now
        })
    }

    @Test
    fun createAndEditPersistSelectedWeekdaysPerReminder() = runBlocking {
        val created = database.applyMedicationScheduleEdit(
            MedicationScheduleEdit(
                medicationId = null,
                name = "Second medicine",
                instructions = null,
                enabled = true,
                reminders = listOf(
                    ReminderScheduleEdit(null, 9 * 60, MONDAY_WEDNESDAY_FRIDAY),
                    ReminderScheduleEdit(null, 20 * 60, WEEKEND),
                ),
            ),
            now,
            zone,
        )
        val createdTimes = database.medicationDao().getTimes(created.medicationId)
            .sortedBy { it.minuteOfDay }
        assertEquals(
            listOf(MONDAY_WEDNESDAY_FRIDAY, WEEKEND),
            createdTimes.map { it.weekdayMask },
        )

        database.applyMedicationScheduleEdit(
            MedicationScheduleEdit(
                medicationId = created.medicationId,
                name = "Second medicine",
                instructions = null,
                enabled = true,
                reminders = listOf(
                    ReminderScheduleEdit(createdTimes[0].id, 9 * 60, TUESDAY_THURSDAY),
                    ReminderScheduleEdit(createdTimes[1].id, 20 * 60, WEEKEND),
                ),
            ),
            now,
            zone,
        )

        assertEquals(
            listOf(TUESDAY_THURSDAY, WEEKEND),
            database.medicationDao().getTimes(created.medicationId)
                .sortedBy { it.minuteOfDay }
                .map { it.weekdayMask },
        )
    }

    @Test
    fun disableCancelsNonterminalStateAndReenableCreatesOneCanonicalFuture() = runBlocking {
        insert("base", now + 60_000L)
        insert("snooze", now + 5 * 60_000L, OccurrenceKind.SNOOZE)

        apply(
            reminders = listOf(ReminderScheduleEdit(reminderTimeId, 8 * 60, MONDAY_TO_FRIDAY)),
            enabled = false,
        )

        assertNull(database.occurrenceDao().get("base"))
        assertNull(database.occurrenceDao().get("snooze"))
        assertNull(database.occurrenceDao().getFutureBase(reminderTimeId, now))

        apply(
            reminders = listOf(ReminderScheduleEdit(reminderTimeId, 8 * 60, MONDAY_TO_FRIDAY)),
            enabled = true,
        )

        assertEquals(1, database.occurrenceDao().getScheduledBases(reminderTimeId).count {
            it.scheduledAtEpochMillis > now
        })
    }

    @Test
    fun listDisableChangesOnlyEnabledAndCancelsMedicationNonterminalOccurrences() = runBlocking {
        insert("base", now + 60_000L)
        insert("snooze", now + 5 * 60_000L, OccurrenceKind.SNOOZE)

        val result = requireNotNull(
            database.applyMedicationListToggle(medicationId, false, now, zone),
        )

        assertFalse(database.medicationDao().get(medicationId)!!.enabled)
        assertEquals(setOf("base", "snooze"), result.obsoleteOccurrenceIds.toSet())
        assertNull(database.occurrenceDao().get("base"))
        assertNull(database.occurrenceDao().get("snooze"))
        assertNull(database.occurrenceDao().getFutureBase(reminderTimeId, now))
    }

    @Test
    fun listEnableReadsCurrentRoomRemindersAndKeepsOneCanonicalFuture() = runBlocking {
        database.medicationDao().updateMedication(
            database.medicationDao().get(medicationId)!!.copy(enabled = false),
        )
        database.medicationDao().updateTime(
            database.medicationDao().getTimes(medicationId).single().copy(
                minuteOfDay = 9 * 60,
                weekdayMask = MONDAY_ONLY,
            ),
        )

        database.applyMedicationListToggle(medicationId, true, now, zone)
        database.applyMedicationListToggle(medicationId, true, now, zone)

        assertTrue(database.medicationDao().get(medicationId)!!.enabled)
        val currentReminder = database.medicationDao().getTimes(medicationId).single()
        assertEquals(9 * 60, currentReminder.minuteOfDay)
        assertEquals(MONDAY_ONLY, currentReminder.weekdayMask)
        assertEquals(1, database.occurrenceDao().getScheduledBases(currentReminder.id).count {
            it.scheduledAtEpochMillis > now
        })
    }

    @Test
    fun listToggleCannotRewriteNewerMedicationOrReminderFields() = runBlocking {
        val currentMedication = database.medicationDao().get(medicationId)!!
        database.medicationDao().updateMedication(
            currentMedication.copy(name = "New name", instructions = "New instructions"),
        )
        database.medicationDao().updateTime(
            database.medicationDao().getTimes(medicationId).single().copy(
                minuteOfDay = 21 * 60,
                weekdayMask = WEEKEND,
            ),
        )

        database.applyMedicationListToggle(medicationId, false, now, zone)

        assertEquals(
            MedicationEntity(medicationId, "New name", "New instructions", false),
            database.medicationDao().get(medicationId),
        )
        val reminder = database.medicationDao().getTimes(medicationId).single()
        assertEquals(reminderTimeId, reminder.id)
        assertEquals(21 * 60, reminder.minuteOfDay)
        assertEquals(WEEKEND, reminder.weekdayMask)
    }

    @Test
    fun listToggleForMissingMedicationIsHarmlessNoOp() = runBlocking {
        database.medicationDao().deleteMedication(database.medicationDao().get(medicationId)!!)

        val result = database.applyMedicationListToggle(medicationId, true, now, zone)

        assertNull(result)
        assertNull(database.medicationDao().get(medicationId))
        assertTrue(database.medicationDao().getTimes(medicationId).isEmpty())
        assertTrue(database.occurrenceDao().getScheduledBases(reminderTimeId).isEmpty())
    }

    @Test
    fun addingAndRemovingReminderUpdatesCanonicalBasesAndUsesExistingCascade() = runBlocking {
        insert("source-snooze", now + 5 * 60_000L, OccurrenceKind.SNOOZE)
        apply(
            reminders = listOf(
                ReminderScheduleEdit(reminderTimeId, 8 * 60, MONDAY_TO_FRIDAY),
                ReminderScheduleEdit(null, 20 * 60, WeekdayMask.ALL),
            ),
            enabled = true,
        )
        val added = database.medicationDao().getTimes(medicationId).single { it.id != reminderTimeId }
        assertNotNull(database.occurrenceDao().getFutureBase(added.id, now))

        apply(
            reminders = listOf(ReminderScheduleEdit(added.id, added.minuteOfDay, added.weekdayMask)),
            enabled = true,
        )

        assertNull(database.occurrenceDao().get("source-snooze"))
        assertEquals(listOf(added.id), database.medicationDao().getTimes(medicationId).map { it.id })
        assertEquals(1, database.occurrenceDao().getScheduledBases(added.id).count {
            it.scheduledAtEpochMillis > now
        })
    }

    private suspend fun updateWeekdays(mask: Int): MedicationScheduleEditResult =
        apply(ReminderScheduleEdit(reminderTimeId, 8 * 60, mask))

    private suspend fun apply(reminder: ReminderScheduleEdit): MedicationScheduleEditResult =
        apply(listOf(reminder), enabled = true)

    private suspend fun apply(
        reminders: List<ReminderScheduleEdit>,
        enabled: Boolean,
    ): MedicationScheduleEditResult =
        database.applyMedicationScheduleEdit(
            MedicationScheduleEdit(
                medicationId = medicationId,
                name = "Medicine",
                instructions = null,
                enabled = enabled,
                reminders = reminders,
            ),
            now,
            zone,
        )

    private suspend fun insert(
        id: String,
        scheduledAt: Long,
        kind: OccurrenceKind = OccurrenceKind.BASE,
    ) {
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = id,
                reminderTimeId = reminderTimeId,
                kind = kind,
                scheduledAtEpochMillis = scheduledAt,
                status = OccurrenceStatus.SCHEDULED,
            ),
        )
    }

    private fun dueMillis() = Instant.parse("2026-08-17T06:00:00Z").toEpochMilli()

    companion object {
        private const val MONDAY_TO_FRIDAY = 0b0011111
        private const val MONDAY_TO_SATURDAY = 0b0111111
        private const val TUESDAY_TO_SATURDAY = 0b0111110
        private const val MONDAY_ONLY = 0b0000001
        private const val MONDAY_WEDNESDAY_FRIDAY = 0b0010101
        private const val TUESDAY_THURSDAY = 0b0001010
        private const val WEEKEND = 0b1100000
    }
}
