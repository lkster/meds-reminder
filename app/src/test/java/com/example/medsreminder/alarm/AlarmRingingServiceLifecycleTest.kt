package com.example.medsreminder.alarm

import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceDetails
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmRingingServiceLifecycleTest {
    private val context = RuntimeEnvironment.getApplication()
    private val database = AppDatabase.get(context)
    private var reminderTimeId = 0L

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        database.clearAllTables()
        shadowOf(context).clearStartedServices()
        context.getSystemService(NotificationManager::class.java)
            .cancel(AlarmRingingService.NOTIFICATION_ID)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = "Medicine", instructions = null, enabled = true),
        )
        reminderTimeId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
    }

    @After
    fun tearDown() = runBlocking(Dispatchers.IO) {
        AlarmRingingService.stopAndRemoveNotification(context)
        database.clearAllTables()
    }

    @Test
    fun staleStartIntentStopsWithoutStartingRingingResources() {
        val stale = details("stale", OccurrenceStatus.RINGING, 1_000L)
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, stale),
        ).create().startCommand(0, 1)
        val service = controller.get()

        awaitServiceWork {
            shadowOf(service).isStoppedBySelf
        }

        assertFalse(service.ringingResourcesStarted)
        assertNull(shadowOf(service).lastForegroundNotification)
        assertEquals(
            0,
            context.getSystemService(NotificationManager::class.java).activeNotifications.size,
        )
        controller.destroy()
    }

    @Test
    fun validFreshStartFirstForegroundNotificationIsActionableAndFullScreen() {
        val current = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 1)
        val service = controller.get()
        val notification = shadowOf(service).lastForegroundNotification

        assertNotNull(notification)
        assertEquals("Medicine", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(3, notification.actions.size)
        assertNotNull(notification.fullScreenIntent)
        assertEquals(notification.contentIntent, notification.fullScreenIntent)
        assertEquals(
            "medsreminder://ringing/session",
            shadowOf(notification.fullScreenIntent).savedIntent.data.toString(),
        )
        assertEquals(
            listOf("Taken", "Snooze 5 min", "Skip"),
            notification.actions.map { it.title.toString() },
        )
        notification.actions.forEach { action ->
            assertEquals(
                "a",
                shadowOf(action.actionIntent).savedIntent.data?.lastPathSegment,
            )
        }

        controller.destroy()
    }

    @Test
    fun resolvingFinalOccurrenceCleansFallbackWithoutStartingEmptySession() =
        runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            val current = database.occurrenceDao().getDetails("a")!!
            AlarmRingingService.postFallbackNotification(context, current, Throwable("test"))
            database.occurrenceDao().transition(
                "a",
                OccurrenceStatus.RINGING,
                OccurrenceStatus.TAKEN,
                2_000L,
            )
            shadowOf(context).clearStartedServices()

            AlarmRingingService.synchronizeWithPersistedQueue(context, database)

            assertNull(shadowOf(context).nextStartedService)
            assertNotNull(shadowOf(context).nextStoppedService)
            assertEquals(
                0,
                context.getSystemService(NotificationManager::class.java).activeNotifications.size,
            )
        }

    @Test
    fun resolvingAStartsOrRefreshesServiceWithQueuedB() = runBlocking(Dispatchers.IO) {
        insert("a", OccurrenceStatus.RINGING, 1_000L)
        insert("b", OccurrenceStatus.RINGING, 2_000L)
        database.occurrenceDao().markPresented("a", 1_500L)
        database.occurrenceDao().transition(
            "a",
            OccurrenceStatus.RINGING,
            OccurrenceStatus.SKIPPED,
            3_000L,
        )
        shadowOf(context).clearStartedServices()

        AlarmRingingService.synchronizeWithPersistedQueue(context, database)

        val started = shadowOf(context).nextStartedService
        assertNotNull(started)
        assertEquals(
            AlarmRingingService::class.java.name,
            started.component?.className,
        )
        assertEquals("b", started.getStringExtra("occurrence_id"))
        assertEquals("b", database.occurrenceDao().getCurrentRinging()?.occurrenceId)
    }

    @Test
    fun capabilityLossExpiryStopsAndCleansPersistedSession() {
        val current = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 1)
        val service = controller.get()
        awaitServiceWork { service.ringingResourcesStarted }

        runBlocking(Dispatchers.IO) {
            database.occurrenceDao().expireAllRinging(2_000L)
            AlarmRingingService.synchronizeWithPersistedQueue(context, database)
            assertNull(database.occurrenceDao().getCurrentRinging())
        }
        assertTrue(shadowOf(context).nextStoppedService.component != null)
        controller.destroy()

        assertFalse(service.ringingResourcesStarted)
        assertEquals(
            0,
            context.getSystemService(NotificationManager::class.java).activeNotifications.size,
        )
    }

    private suspend fun insert(id: String, status: OccurrenceStatus, scheduledAt: Long) {
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = id,
                reminderTimeId = reminderTimeId,
                kind = OccurrenceKind.BASE,
                scheduledAtEpochMillis = scheduledAt,
                status = status,
            ),
        )
    }

    private fun details(id: String, status: OccurrenceStatus, scheduledAt: Long) = OccurrenceDetails(
        occurrenceId = id,
        reminderTimeId = reminderTimeId,
        kind = OccurrenceKind.BASE,
        status = status,
        scheduledAtEpochMillis = scheduledAt,
        presentedAtEpochMillis = null,
        medicationId = 1L,
        medicationName = "Medicine",
        instructions = null,
        minuteOfDay = 8 * 60,
    )

    private fun awaitServiceWork(condition: () -> Boolean) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Service work did not finish", condition())
    }
}
