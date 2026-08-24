package com.example.medsreminder.alarm

import android.app.NotificationManager
import android.content.Intent
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class AlarmReceiverStaleDeliveryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val database = AppDatabase.get(context)
    private var medicationId = 0L
    private var reminderTimeId = 0L

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        database.clearAllTables()
        shadowOf(context).clearStartedServices()
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        context.getSystemService(NotificationManager::class.java).also {
            shadowOf(it).setNotificationsEnabled(true)
        }
        AlarmRingingService.ensureNotificationChannel(context)
        medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = "Medicine", instructions = null, enabled = true),
        )
        reminderTimeId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
    }

    @After
    fun tearDown() = runBlocking(Dispatchers.IO) {
        database.clearAllTables()
        shadowOf(context).clearStartedServices()
        ShadowAlarmManager.reset()
    }

    @Test
    fun obsoleteOccurrenceUuidDoesNotStartRingingService() = runBlocking {
        val staleOccurrenceId = "obsolete-m2-occurrence"
        database.occurrenceDao().insert(scheduled(staleOccurrenceId, System.currentTimeMillis()))
        database.medicationDao().deleteMedication(
            MedicationEntity(
                id = medicationId,
                name = "Medicine",
                instructions = null,
                enabled = true,
            ),
        )
        assertNull(database.occurrenceDao().get(staleOccurrenceId))

        deliver(staleOccurrenceId)
        delay(100)

        assertNull(shadowOf(context).peekNextStartedService())
    }

    @Test
    fun validColdDeliveryClaimsOnceAndAdvancesBaseAtClaimBoundary() = runBlocking {
        val occurrenceId = "valid-cold-base"
        database.occurrenceDao().insert(scheduled(occurrenceId, System.currentTimeMillis()))

        deliver(occurrenceId)
        awaitStatus(occurrenceId, OccurrenceStatus.RINGING)
        assertNotNull(awaitStartedService())
        val futureBases = database.occurrenceDao().getScheduledBases(reminderTimeId)
            .filter { it.scheduledAtEpochMillis > System.currentTimeMillis() }
        assertEquals(1, futureBases.size)

        shadowOf(context).clearStartedServices()
        deliver(occurrenceId)
        delay(100)

        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get(occurrenceId)?.status)
        assertNull(shadowOf(context).peekNextStartedService())
        assertEquals(
            futureBases.map { it.id },
            database.occurrenceDao().getScheduledBases(reminderTimeId)
                .filter { it.scheduledAtEpochMillis > System.currentTimeMillis() }
                .map { it.id },
        )
    }

    @Test
    fun materiallyLateDeliveryExpiresAndStillAdvancesFutureBase() = runBlocking {
        val occurrenceId = "materially-late-base"
        val scheduledAt = System.currentTimeMillis() - AlarmScheduler.DELIVERY_GRACE_MILLIS - 5_000L
        database.occurrenceDao().insert(scheduled(occurrenceId, scheduledAt))

        deliver(occurrenceId)
        awaitStatus(occurrenceId, OccurrenceStatus.EXPIRED)

        assertNull(shadowOf(context).peekNextStartedService())
        assertEquals(
            1,
            database.occurrenceDao().getScheduledBases(reminderTimeId)
                .count { it.scheduledAtEpochMillis > System.currentTimeMillis() },
        )
    }

    @Test
    fun exactAccessUnavailableRejectsNewDeliveryButKeepsDesiredFutureBaseInRoom() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val occurrenceId = "no-exact-access"
        database.occurrenceDao().insert(scheduled(occurrenceId, System.currentTimeMillis()))

        deliver(occurrenceId)
        awaitStatus(occurrenceId, OccurrenceStatus.EXPIRED)

        assertNull(shadowOf(context).peekNextStartedService())
        assertEquals(
            1,
            database.occurrenceDao().getScheduledBases(reminderTimeId)
                .count { it.scheduledAtEpochMillis > System.currentTimeMillis() },
        )
    }

    @Test
    fun unavailableNotificationPresentationRejectsNewDelivery() = runBlocking {
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(false)
        val occurrenceId = "no-notification-presentation"
        database.occurrenceDao().insert(scheduled(occurrenceId, System.currentTimeMillis()))

        deliver(occurrenceId)
        awaitStatus(occurrenceId, OccurrenceStatus.EXPIRED)

        assertNull(shadowOf(context).peekNextStartedService())
    }

    private fun deliver(occurrenceId: String) {
        AlarmReceiver().onReceive(
            context,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                data = AlarmScheduler.occurrenceUri(occurrenceId)
            },
        )
    }

    private suspend fun awaitStatus(id: String, expected: OccurrenceStatus) {
        withTimeout(5_000L) {
            while (database.occurrenceDao().get(id)?.status != expected) delay(10)
        }
    }

    private suspend fun awaitStartedService(): Intent = withTimeout(5_000L) {
        while (true) {
            shadowOf(context).peekNextStartedService()?.let { return@withTimeout it }
            delay(10)
        }
        error("unreachable")
    }

    private fun scheduled(id: String, scheduledAt: Long) = AlarmOccurrenceEntity(
        id = id,
        reminderTimeId = reminderTimeId,
        kind = OccurrenceKind.BASE,
        scheduledAtEpochMillis = scheduledAt,
        status = OccurrenceStatus.SCHEDULED,
    )
}
