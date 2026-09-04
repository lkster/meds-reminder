package com.example.medsreminder

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Proves AlarmActivity renders only Room-authoritative current-ringing presentation. */
@RunWith(AndroidJUnit4::class)
class AlarmActivityLoadingTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var targetContext: android.content.Context
    private var scenario: ActivityScenario<AlarmActivity>? = null
    private val medicationName = "M14 alarm fixture"

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppDatabase.get(targetContext)
        database.clearAllTables()
        AlarmActivityLoadingTestHook.reset(targetContext)
    }

    @After
    fun tearDown() {
        AlarmActivityLoadingTestHook.releaseCollection(targetContext)
        scenario?.close()
        AlarmActivityLoadingTestHook.reset(targetContext)
        database.clearAllTables()
    }

    @Test
    fun heldRealRoomCollectionTransitionsFromLoadingToRingingAlarm() {
        seedFixture(ringing = true)
        AlarmActivityLoadingTestHook.configure(targetContext, holdBeforeCollection = true)
        scenario = ActivityScenario.launch(AlarmActivity::class.java)

        compose.waitUntil(timeoutMillis = 5_000) {
            AlarmActivityLoadingTestHook.beforeCollectionReached(targetContext)
        }
        compose.onNodeWithText("Loading alarm…").assertExists()
        compose.onNodeWithText(medicationName).assertDoesNotExist()
        compose.onNodeWithText("Taken").assertDoesNotExist()
        compose.onNodeWithText("Snooze").assertDoesNotExist()
        compose.onNodeWithText("Skip").assertDoesNotExist()

        AlarmActivityLoadingTestHook.releaseCollection(targetContext)
        compose.waitUntil(timeoutMillis = 5_000) {
            AlarmActivityLoadingTestHook.firstRoomEmissionReceived(targetContext)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText(medicationName).assertExists() }.isSuccess
        }
        compose.onNodeWithText("Loading alarm…").assertDoesNotExist()
        compose.onNodeWithText(medicationName).assertExists()
        compose.onNodeWithText("Taken").assertHasClickAction()
        compose.onNodeWithText("Snooze").assertHasClickAction()
        compose.onNodeWithText("Skip").assertHasClickAction()
    }

    @Test
    fun authoritativeEmptyCurrentRingingResultClosesActivity() {
        seedFixture(ringing = false)
        AlarmActivityLoadingTestHook.configure(targetContext, holdBeforeCollection = true)
        scenario = ActivityScenario.launch(AlarmActivity::class.java)

        compose.waitUntil(timeoutMillis = 5_000) {
            AlarmActivityLoadingTestHook.beforeCollectionReached(targetContext)
        }
        compose.onNodeWithText("Loading alarm…").assertExists()

        AlarmActivityLoadingTestHook.releaseCollection(targetContext)
        compose.waitUntil(timeoutMillis = 5_000) {
            AlarmActivityLoadingTestHook.firstRoomEmissionReceived(targetContext)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            scenario?.state == Lifecycle.State.DESTROYED
        }
    }

    private fun seedFixture(ringing: Boolean) = runBlocking {
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = medicationName, instructions = "Take with water", enabled = true),
        )
        val reminderId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
        if (ringing) {
            database.occurrenceDao().insert(
                AlarmOccurrenceEntity(
                    id = "m14-ringing-occurrence",
                    reminderTimeId = reminderId,
                    kind = OccurrenceKind.BASE,
                    scheduledAtEpochMillis = 1_785_960_000_000L,
                    status = OccurrenceStatus.RINGING,
                ),
            )
        }
    }
}
