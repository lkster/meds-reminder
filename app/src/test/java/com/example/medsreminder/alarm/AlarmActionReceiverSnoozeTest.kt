package com.example.medsreminder.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceDetails
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
import org.junit.Assert.assertThrows
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
class AlarmActionReceiverSnoozeTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val database = AppDatabase.get(context)
    private var reminderTimeId = 0L

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        database.clearAllTables()
        context.getSharedPreferences(AlarmPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSystemService(NotificationManager::class.java)
            .cancel(AlarmRingingService.NOTIFICATION_ID)
        ShadowAlarmManager.reset()
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
        context.getSharedPreferences(AlarmPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        ShadowAlarmManager.reset()
    }

    @Test
    fun constructionUsesExactlyTheCapturedUuidTimeAndDuration() {
        val original = details("original")

        val snooze = createSnoozeOccurrence(original, 1_000L, 15, "snooze-id")

        assertEquals("snooze-id", snooze.id)
        assertEquals(original.reminderTimeId, snooze.reminderTimeId)
        assertEquals(OccurrenceKind.SNOOZE, snooze.kind)
        assertEquals(901_000L, snooze.scheduledAtEpochMillis)
        assertEquals(OccurrenceStatus.SCHEDULED, snooze.status)
        assertThrows(IllegalArgumentException::class.java) {
            createSnoozeOccurrence(original, 1_000L, 7, "invalid")
        }
    }

    @Test
    fun configuredDurationKeepsPreRegistrationAndRoomCommitAligned() = runBlocking(Dispatchers.IO) {
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = "original",
                reminderTimeId = reminderTimeId,
                kind = OccurrenceKind.BASE,
                scheduledAtEpochMillis = System.currentTimeMillis() - 1_000L,
                status = OccurrenceStatus.RINGING,
            ),
        )
        AlarmPreferences.setSnoozeMinutes(context, 10)
        val beforeAction = System.currentTimeMillis()

        val actionIntent = shadowOf(
            AlarmActionReceiver.pendingIntent(
                context,
                AlarmActionReceiver.ACTION_SNOOZE,
                "original",
            ),
        ).savedIntent
        AlarmActionReceiver().onReceive(context, actionIntent)

        val snooze = withTimeout(5_000L) {
            var found: AlarmOccurrenceEntity? = null
            while (found == null) {
                found = database.occurrenceDao().getFutureScheduled(beforeAction)
                    .singleOrNull { it.kind == OccurrenceKind.SNOOZE }
                if (found == null) delay(10L)
            }
            requireNotNull(found)
        }
        val original = database.occurrenceDao().get("original")
        assertEquals(OccurrenceStatus.SNOOZED, original?.status)
        assertNotNull(original?.resolvedAtEpochMillis)
        assertEquals(
            original?.resolvedAtEpochMillis?.plus(10 * 60_000L),
            snooze.scheduledAtEpochMillis,
        )
        val projectedIds = shadowOf(context.getSystemService(AlarmManager::class.java))
            .scheduledAlarms
            .mapNotNull { AlarmScheduler.occurrenceId(shadowOf(it.operation).savedIntent) }
        assertEquals(listOf(snooze.id), projectedIds.filter { it == snooze.id })
    }

    private fun details(id: String) = OccurrenceDetails(
        occurrenceId = id,
        reminderTimeId = reminderTimeId,
        kind = OccurrenceKind.BASE,
        status = OccurrenceStatus.RINGING,
        scheduledAtEpochMillis = 1_000L,
        presentedAtEpochMillis = null,
        medicationId = 1L,
        medicationName = "Medicine",
        instructions = null,
        minuteOfDay = 8 * 60,
        weekdayMask = 127,
    )
}
