package com.example.medsreminder.alarm

import android.content.Intent
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmReceiverStaleDeliveryTest {
    @Test
    fun obsoleteOccurrenceUuidDoesNotStartRingingService() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = AppDatabase.get(context)
        val medication = MedicationEntity(name = "Deleted medicine", instructions = null, enabled = true)
        val medicationId = database.medicationDao().insertMedication(medication)
        val reminderTimeId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
        val staleOccurrenceId = "obsolete-m2-occurrence"
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = staleOccurrenceId,
                reminderTimeId = reminderTimeId,
                kind = OccurrenceKind.BASE,
                scheduledAtEpochMillis = System.currentTimeMillis(),
                status = OccurrenceStatus.SCHEDULED,
            ),
        )
        database.medicationDao().deleteMedication(medication.copy(id = medicationId))
        assertNull(database.occurrenceDao().get(staleOccurrenceId))

        val applicationShadow = shadowOf(context)
        applicationShadow.clearStartedServices()
        AlarmReceiver().onReceive(
            context,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_FIRE
                data = AlarmScheduler.occurrenceUri(staleOccurrenceId)
            },
        )

        for (attempt in 0 until 50) {
            if (applicationShadow.peekNextStartedService() != null) break
            Thread.sleep(10)
        }
        assertNull(applicationShadow.peekNextStartedService())
    }
}
