package com.example.medsreminder.alarm

import android.app.AlarmManager
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmReconcilerTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var database: AppDatabase
    private lateinit var reconciler: AlarmReconciler
    private var reminderTimeId = 0L

    @Before
    fun setUp() = runBlocking {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
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
        ShadowAlarmManager.reset()
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

    @Test
    fun rebootExpiresPresentedOwnerAndQueuedFollowers() = runBlocking {
        val now = 1_000_000L
        database.occurrenceDao().insert(
            occurrence(now - 60_000L).copy(
                id = "presented",
                status = OccurrenceStatus.RINGING,
                presentedAtEpochMillis = now - 30_000L,
            ),
        )
        database.occurrenceDao().insert(
            occurrence(now - 30_000L).copy(
                id = "queued",
                status = OccurrenceStatus.RINGING,
            ),
        )

        reconciler.reconcile(ReconciliationMode.REBOOT, now, ZoneId.of("UTC"))

        assertEquals(OccurrenceStatus.EXPIRED, database.occurrenceDao().get("presented")?.status)
        assertEquals(OccurrenceStatus.EXPIRED, database.occurrenceDao().get("queued")?.status)
    }

    @Test
    fun packageReplacementPreservesPresentedOwnerAndQueuedFollowers() = runBlocking {
        assertPresentedQueuePreserved(ReconciliationMode.PACKAGE_REPLACED)
    }

    @Test
    fun exactRestorationPreservesPresentedOwnerAndQueuedFollowers() = runBlocking {
        assertPresentedQueuePreserved(ReconciliationMode.EXACT_PERMISSION_RESTORED)
    }

    @Test
    fun strictRecoveryUsesRoutineGraceOnlyForUnpresentedOrphans() = runBlocking {
        val now = 1_000_000L
        val cutoff = AlarmScheduler.routineExpiryCutoff(now)
        database.occurrenceDao().insert(
            occurrence(cutoff).copy(id = "old-orphan", status = OccurrenceStatus.RINGING),
        )
        database.occurrenceDao().insert(
            occurrence(cutoff + 1L).copy(id = "recent-claim", status = OccurrenceStatus.RINGING),
        )

        reconciler.reconcile(
            ReconciliationMode.EXACT_PERMISSION_RESTORED,
            now,
            ZoneId.of("UTC"),
        )

        assertEquals(OccurrenceStatus.EXPIRED, database.occurrenceDao().get("old-orphan")?.status)
        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get("recent-claim")?.status)
    }

    @Test
    fun exactRestorationIsIdempotentAndPreservesFutureSnoozeAndBaseIdentity() = runBlocking {
        val now = Instant.parse("2035-08-20T06:00:00Z").toEpochMilli()
        val snooze = AlarmOccurrenceEntity(
            id = "future-snooze",
            reminderTimeId = reminderTimeId,
            kind = OccurrenceKind.SNOOZE,
            scheduledAtEpochMillis = now + 5 * 60_000L,
            status = OccurrenceStatus.SCHEDULED,
        )
        database.occurrenceDao().insert(snooze)

        reconciler.reconcile(ReconciliationMode.EXACT_PERMISSION_RESTORED, now, ZoneId.of("UTC"))
        val firstBase = database.occurrenceDao().getFutureBase(reminderTimeId, now)!!
        reconciler.reconcile(ReconciliationMode.EXACT_PERMISSION_RESTORED, now, ZoneId.of("UTC"))

        val scheduled = database.occurrenceDao().getFutureScheduled(now)
        assertEquals(snooze, database.occurrenceDao().get(snooze.id))
        assertEquals(1, scheduled.count { it.kind == OccurrenceKind.BASE })
        assertEquals(firstBase.id, scheduled.single { it.kind == OccurrenceKind.BASE }.id)
        assertTrue(projectedOccurrenceIds().containsAll(setOf(firstBase.id, snooze.id)))
    }

    @Test
    fun routineReconciliationRepairsMissingProjectionWithoutReplacingRoomUuid() = runBlocking {
        val now = Instant.parse("2035-08-20T06:00:00Z").toEpochMilli()
        reconciler.reconcile(ReconciliationMode.ROUTINE, now, ZoneId.of("UTC"))
        val desired = database.occurrenceDao().getFutureBase(reminderTimeId, now)!!
        assertTrue(desired.id in projectedOccurrenceIds())

        AlarmScheduler(context).cancelOccurrence(desired.id)
        assertTrue(desired.id !in projectedOccurrenceIds())

        reconciler.reconcile(ReconciliationMode.ROUTINE, now, ZoneId.of("UTC"))

        assertEquals(desired.id, database.occurrenceDao().getFutureBase(reminderTimeId, now)?.id)
        assertTrue(desired.id in projectedOccurrenceIds())
    }

    private suspend fun assertPresentedQueuePreserved(mode: ReconciliationMode) {
        val now = 1_000_000L
        database.occurrenceDao().insert(
            occurrence(now - 60_000L).copy(
                id = "presented",
                status = OccurrenceStatus.RINGING,
                presentedAtEpochMillis = now - 30_000L,
            ),
        )
        database.occurrenceDao().insert(
            occurrence(now - 30_000L).copy(
                id = "queued",
                status = OccurrenceStatus.RINGING,
            ),
        )

        reconciler.reconcile(mode, now, ZoneId.of("UTC"))

        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get("presented")?.status)
        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get("queued")?.status)
        assertEquals("presented", database.occurrenceDao().getCurrentRinging()?.occurrenceId)
    }

    @Suppress("DEPRECATION")
    private fun projectedOccurrenceIds(): Set<String> =
        shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
            .mapNotNull { AlarmScheduler.occurrenceId(shadowOf(it.operation).savedIntent) }
            .toSet()

    private fun occurrence(due: Long) = AlarmOccurrenceEntity(
        id = "due",
        reminderTimeId = reminderTimeId,
        kind = OccurrenceKind.BASE,
        scheduledAtEpochMillis = due,
        status = OccurrenceStatus.SCHEDULED,
    )
}
