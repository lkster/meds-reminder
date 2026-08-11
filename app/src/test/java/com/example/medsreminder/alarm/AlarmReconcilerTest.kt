package com.example.medsreminder.alarm

import androidx.room.Room
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private fun occurrence(due: Long) = AlarmOccurrenceEntity(
        id = "due",
        reminderTimeId = reminderTimeId,
        kind = OccurrenceKind.BASE,
        scheduledAtEpochMillis = due,
        status = OccurrenceStatus.SCHEDULED,
    )
}
