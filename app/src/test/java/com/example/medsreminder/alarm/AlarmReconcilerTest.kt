package com.example.medsreminder.alarm

import androidx.room.Room
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import com.example.medsreminder.data.WeekdayMask
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmReconcilerTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var database: AppDatabase
    private lateinit var reconciler: AlarmReconciler
    private var reminderTimeId = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = "Medicine", instructions = null, enabled = true),
        )
        reminderTimeId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
        reconciler = AlarmReconciler(context, database, AlarmScheduler(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun routineReconciliationPreservesOccurrenceAtDeliveryGraceBoundary() = runBlocking {
        val due = 1_000_000L
        database.occurrenceDao().insert(occurrence(due))

        reconciler.reconcile(
            mode = ReconciliationMode.ROUTINE,
            nowMillis = due + AlarmScheduler.DELIVERY_GRACE_MILLIS,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(OccurrenceStatus.SCHEDULED, database.occurrenceDao().get("due")?.status)
        assertNotNull(
            database.occurrenceDao().getFutureBase(
                reminderTimeId,
                due + AlarmScheduler.DELIVERY_GRACE_MILLIS,
            ),
        )
    }

    @Test
    fun rebootExpiresPastOccurrenceWithoutCatchUpAndCreatesFutureBase() = runBlocking {
        val due = 1_000_000L
        val now = due + 1L
        database.occurrenceDao().insert(occurrence(due))

        reconciler.reconcile(
            mode = ReconciliationMode.REBOOT,
            nowMillis = now,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(OccurrenceStatus.EXPIRED, database.occurrenceDao().get("due")?.status)
        assertNotNull(database.occurrenceDao().getFutureBase(reminderTimeId, now))
    }

    @Test
    fun wallClockChangeRebuildsBaseAndPreservesFutureSnooze() = runBlocking {
        val now = Instant.parse("2035-08-20T06:00:00Z").toEpochMilli()
        val oldBase = AlarmOccurrenceEntity(
            id = "old-base",
            reminderTimeId = reminderTimeId,
            kind = OccurrenceKind.BASE,
            scheduledAtEpochMillis = now + 60 * 60 * 1000L,
            status = OccurrenceStatus.SCHEDULED,
        )
        val snooze = AlarmOccurrenceEntity(
            id = "future-snooze",
            reminderTimeId = reminderTimeId,
            kind = OccurrenceKind.SNOOZE,
            scheduledAtEpochMillis = now + 5 * 60 * 1000L,
            status = OccurrenceStatus.SCHEDULED,
        )
        database.occurrenceDao().insert(oldBase)
        database.occurrenceDao().insert(snooze)
        val newZone = ZoneId.of("Asia/Tokyo")

        reconciler.reconcile(ReconciliationMode.WALL_CLOCK_CHANGED, now, newZone)

        assertNull(database.occurrenceDao().get(oldBase.id))
        assertEquals(snooze, database.occurrenceDao().get(snooze.id))
        val replacement = database.occurrenceDao().getFutureBase(reminderTimeId, now)
        assertNotNull(replacement)
        assertEquals(
            NextOccurrenceCalculator.next(
                8 * 60,
                WeekdayMask.ALL,
                Instant.ofEpochMilli(now),
                newZone,
            ).toEpochMilli(),
            replacement?.scheduledAtEpochMillis,
        )
    }

    private fun occurrence(due: Long) = AlarmOccurrenceEntity(
        id = "due",
        reminderTimeId = reminderTimeId,
        kind = OccurrenceKind.BASE,
        scheduledAtEpochMillis = due,
        status = OccurrenceStatus.SCHEDULED,
    )
}
