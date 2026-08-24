package com.example.medsreminder

import android.app.NotificationManager
import androidx.room.Room
import com.example.medsreminder.alarm.AlarmReconciler
import com.example.medsreminder.alarm.AlarmRingingService
import com.example.medsreminder.alarm.AlarmScheduler
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MainActivityRingingRecoveryTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        shadowOf(context).clearStartedServices()
        ShadowAlarmManager.reset()
        context.getSystemService(NotificationManager::class.java).also {
            shadowOf(it).setNotificationsEnabled(true)
        }
        AlarmRingingService.ensureNotificationChannel(context)
    }

    @After
    fun tearDown() {
        AlarmRingingService.stopAndRemoveNotification(context)
        database.close()
        shadowOf(context).clearStartedServices()
        ShadowAlarmManager.reset()
    }

    @Test
    fun exactAccessUnavailableDoesNotExpireCurrentRingingAndSynchronizationIsAttempted() = runBlocking {
        val ringingId = insertPresentedRinging("exact-unavailable")
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = AlarmScheduler(context)

        synchronizeRingingOnResume(
            context,
            database,
            scheduler,
            AlarmReconciler(context, database, scheduler),
        )

        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get(ringingId)?.status)
        assertEquals(false, scheduler.projectionReady())
        assertEquals(true, scheduler.requiredPresentationReady())
        val started = shadowOf(context).peekNextStartedService()
        assertNotNull(started)
        assertEquals(ringingId, started?.getStringExtra("occurrence_id"))
        // FSI is deliberately absent from synchronizeRingingOnResume's expiry predicate; an
        // actionable notification is sufficient for current-ringing recovery.
    }

    @Test
    fun unavailableActionableNotificationExpiresAndCleansCurrentRinging() = runBlocking {
        val ringingId = insertPresentedRinging("notifications-unavailable")
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(false)
        val scheduler = AlarmScheduler(context)

        synchronizeRingingOnResume(
            context,
            database,
            scheduler,
            AlarmReconciler(context, database, scheduler),
        )

        assertEquals(OccurrenceStatus.EXPIRED, database.occurrenceDao().get(ringingId)?.status)
        assertNull(shadowOf(context).peekNextStartedService())
        assertNotNull(shadowOf(context).nextStoppedService)
        assertEquals(
            0,
            context.getSystemService(NotificationManager::class.java).activeNotifications.size,
        )
    }

    private suspend fun insertPresentedRinging(id: String): String {
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = "Medicine", instructions = null, enabled = true),
        )
        val reminderTimeId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = id,
                reminderTimeId = reminderTimeId,
                kind = OccurrenceKind.BASE,
                scheduledAtEpochMillis = System.currentTimeMillis() - 60_000L,
                status = OccurrenceStatus.RINGING,
                presentedAtEpochMillis = System.currentTimeMillis() - 30_000L,
            ),
        )
        return id
    }
}
