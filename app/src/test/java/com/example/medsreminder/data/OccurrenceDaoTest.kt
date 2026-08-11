package com.example.medsreminder.data

import androidx.room.Room
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class OccurrenceDaoTest {
    private lateinit var database: AppDatabase
    private var reminderTimeId: Long = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = "Medicine", instructions = null, enabled = true),
        )
        reminderTimeId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun staleActionCannotResolveDifferentState() = runBlocking {
        val occurrence = occurrence(OccurrenceStatus.RINGING)
        database.occurrenceDao().insert(occurrence)

        assertEquals(
            1,
            database.occurrenceDao().transition(
                occurrence.id,
                OccurrenceStatus.RINGING,
                OccurrenceStatus.TAKEN,
                2_000L,
            ),
        )
        assertEquals(
            0,
            database.occurrenceDao().transition(
                occurrence.id,
                OccurrenceStatus.RINGING,
                OccurrenceStatus.SKIPPED,
                3_000L,
            ),
        )
        assertEquals(OccurrenceStatus.TAKEN, database.occurrenceDao().get(occurrence.id)?.status)
    }

    @Test
    fun snoozeCommitIsConditionalAndAtomic() = runBlocking {
        val original = occurrence(OccurrenceStatus.RINGING)
        val snooze = occurrence(
            status = OccurrenceStatus.SCHEDULED,
            kind = OccurrenceKind.SNOOZE,
            scheduledAt = 301_000L,
        )
        database.occurrenceDao().insert(original)

        assertTrue(database.occurrenceDao().commitSnooze(original.id, snooze, 2_000L))
        assertEquals(OccurrenceStatus.SNOOZED, database.occurrenceDao().get(original.id)?.status)
        assertEquals(OccurrenceStatus.SCHEDULED, database.occurrenceDao().get(snooze.id)?.status)

        val losingSnooze = occurrence(
            status = OccurrenceStatus.SCHEDULED,
            kind = OccurrenceKind.SNOOZE,
            scheduledAt = 302_000L,
        )
        assertFalse(database.occurrenceDao().commitSnooze(original.id, losingSnooze, 3_000L))
        assertNull(database.occurrenceDao().get(losingSnooze.id))
    }

    @Test
    fun presentationBelongsToOnlyOneQueuedOccurrence() = runBlocking {
        val first = occurrence(OccurrenceStatus.RINGING, scheduledAt = 1_000L)
        val second = occurrence(OccurrenceStatus.RINGING, scheduledAt = 2_000L)
        database.occurrenceDao().insert(first)
        database.occurrenceDao().insert(second)

        database.occurrenceDao().markPresented(first.id, 10_000L)
        database.occurrenceDao().markPresented(second.id, 11_000L)
        database.occurrenceDao().clearOtherPresented(first.id)

        assertEquals(10_000L, database.occurrenceDao().get(first.id)?.presentedAtEpochMillis)
        assertNull(database.occurrenceDao().get(second.id)?.presentedAtEpochMillis)
        assertEquals(first.id, database.occurrenceDao().getCurrentRinging()?.occurrenceId)
    }

    @Test
    fun orphanedUnpresentedRingingCanExpireWithoutTouchingPresentedSession() = runBlocking {
        val presented = occurrence(OccurrenceStatus.RINGING, scheduledAt = 1_000L)
        val queued = occurrence(OccurrenceStatus.RINGING, scheduledAt = 2_000L)
        database.occurrenceDao().insert(presented)
        database.occurrenceDao().insert(queued)
        database.occurrenceDao().markPresented(presented.id, 3_000L)

        assertEquals(1, database.occurrenceDao().presentedRingingCount())

        database.occurrenceDao().expireUnpresentedRingingThrough(5_000L, 5_000L)

        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get(presented.id)?.status)
        assertEquals(OccurrenceStatus.EXPIRED, database.occurrenceDao().get(queued.id)?.status)
    }

    @Test
    fun removingNonterminalStatePreservesTerminalOutcome() = runBlocking {
        val scheduled = occurrence(OccurrenceStatus.SCHEDULED, scheduledAt = 1_000L)
        val ringing = occurrence(OccurrenceStatus.RINGING, scheduledAt = 2_000L)
        val taken = occurrence(OccurrenceStatus.TAKEN, scheduledAt = 3_000L)
        database.occurrenceDao().insert(scheduled)
        database.occurrenceDao().insert(ringing)
        database.occurrenceDao().insert(taken)

        database.occurrenceDao().deleteNonterminal(reminderTimeId)

        assertNull(database.occurrenceDao().get(scheduled.id))
        assertNull(database.occurrenceDao().get(ringing.id))
        assertEquals(OccurrenceStatus.TAKEN, database.occurrenceDao().get(taken.id)?.status)
    }

    private fun occurrence(
        status: OccurrenceStatus,
        kind: OccurrenceKind = OccurrenceKind.BASE,
        scheduledAt: Long = 1_000L,
    ) = AlarmOccurrenceEntity(
        id = UUID.randomUUID().toString(),
        reminderTimeId = reminderTimeId,
        kind = kind,
        scheduledAtEpochMillis = scheduledAt,
        status = status,
    )
}
