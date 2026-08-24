package com.example.medsreminder.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import java.time.ZonedDateTime
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
class AlarmRescheduleReceiverTest {
    private val context = RuntimeEnvironment.getApplication()
    private val database = AppDatabase.get(context)
    private var medication: MedicationEntity? = null

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        shadowOf(context).clearStartedServices()
    }

    @After
    fun tearDown() = runBlocking {
        medication?.let { database.medicationDao().deleteMedication(it) }
        ShadowAlarmManager.reset()
    }

    @Test
    fun bootCompletedReconcilesRoomAndProjectsFutureBaseAndSnooze() = runBlocking {
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = "Boot medicine", instructions = null, enabled = true),
        )
        medication = MedicationEntity(
            id = medicationId,
            name = "Boot medicine",
            instructions = null,
            enabled = true,
        )
        val now = System.currentTimeMillis()
        val localNow = ZonedDateTime.now()
        val tomorrowBit = 1 shl (localNow.dayOfWeek.plus(1).value - 1)
        val reminderTimeId = database.medicationDao().insertTime(
            ReminderTimeEntity(
                medicationId = medicationId,
                minuteOfDay = localNow.hour * 60 + localNow.minute,
                weekdayMask = tomorrowBit,
            ),
        )
        val snooze = AlarmOccurrenceEntity(
            id = "boot-future-snooze",
            reminderTimeId = reminderTimeId,
            kind = OccurrenceKind.SNOOZE,
            scheduledAtEpochMillis = now + 10 * 60 * 1000L,
            status = OccurrenceStatus.SCHEDULED,
        )
        database.occurrenceDao().insert(snooze)

        AlarmRescheduleReceiver().onReceive(
            context,
            Intent(Intent.ACTION_BOOT_COMPLETED),
        )

        var futureBaseId: String? = null
        withTimeout(5_000L) {
            while (true) {
                val baseId = database.occurrenceDao().getFutureBase(reminderTimeId, now)?.id
                val projectedIds = projectedOccurrenceIds()
                if (baseId != null && projectedIds.containsAll(setOf(baseId, snooze.id))) {
                    futureBaseId = baseId
                    break
                }
                delay(10L)
            }
        }

        assertNotNull(futureBaseId)
        val persistedBaseId = requireNotNull(futureBaseId)
        assertEquals(snooze, database.occurrenceDao().get(snooze.id))
        assertEquals(
            setOf(persistedBaseId, snooze.id),
            projectedOccurrenceIds().filter { it == persistedBaseId || it == snooze.id }.toSet(),
        )
    }

    @Test
    fun lockedBootCompletedDoesNotAccessCredentialProtectedSchedule() = runBlocking {
        val reminderTimeId = insertReminder("Locked boot medicine")

        AlarmRescheduleReceiver().onReceive(
            context,
            Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED),
        )

        delay(100L)
        assertNull(database.occurrenceDao().getFutureBase(reminderTimeId, System.currentTimeMillis()))
        assertNull(shadowOf(context).peekNextStartedService())
    }

    @Test
    fun exactGrantBroadcastRechecksCapabilityAndDoesNothingWhenAlreadyRevoked() = runBlocking {
        val reminderTimeId = insertReminder("Revoked medicine")
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        AlarmRescheduleReceiver().onReceive(
            context,
            Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED),
        )

        delay(100L)
        assertNull(database.occurrenceDao().getFutureBase(reminderTimeId, System.currentTimeMillis()))
        assertNull(shadowOf(context).peekNextStartedService())
    }

    @Test
    fun exactGrantRestorationPreservesAndSynchronizesPresentedRinging() = runBlocking {
        val ringingId = insertPresentedRinging("Exact restore medicine")

        AlarmRescheduleReceiver().onReceive(
            context,
            Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED),
        )

        val started = awaitStartedService()
        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get(ringingId)?.status)
        assertEquals(ringingId, started.getStringExtra("occurrence_id"))
    }

    @Test
    fun packageReplacementPreservesAndSynchronizesPresentedRinging() = runBlocking {
        val ringingId = insertPresentedRinging("Updated medicine")

        AlarmRescheduleReceiver().onReceive(
            context,
            Intent(Intent.ACTION_MY_PACKAGE_REPLACED),
        )

        val started = awaitStartedService()
        assertEquals(OccurrenceStatus.RINGING, database.occurrenceDao().get(ringingId)?.status)
        assertEquals(ringingId, started.getStringExtra("occurrence_id"))
    }

    @Test
    fun rebootExpiresPresentedRingingWithoutRestartingService() = runBlocking {
        val ringingId = insertPresentedRinging("Reboot ringing medicine")

        AlarmRescheduleReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        withTimeout(5_000L) {
            while (database.occurrenceDao().get(ringingId)?.status != OccurrenceStatus.EXPIRED) {
                delay(10L)
            }
        }
        assertNull(shadowOf(context).peekNextStartedService())
    }

    private suspend fun insertReminder(name: String): Long {
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = name, instructions = null, enabled = true),
        )
        medication = MedicationEntity(medicationId, name, null, true)
        return database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
    }

    private suspend fun insertPresentedRinging(name: String): String {
        val reminderTimeId = insertReminder(name)
        val id = "ringing-${name.replace(' ', '-')}"
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

    private suspend fun awaitStartedService(): Intent = withTimeout(5_000L) {
        while (true) {
            shadowOf(context).peekNextStartedService()?.let { return@withTimeout it }
            delay(10L)
        }
        error("unreachable")
    }

    @Suppress("DEPRECATION")
    private fun projectedOccurrenceIds(): Set<String> {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return shadowOf(alarmManager).scheduledAlarms.mapNotNull { alarm ->
            AlarmScheduler.occurrenceId(shadowOf(alarm.operation).savedIntent)
        }.toSet()
    }
}
