package com.example.medsreminder

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
    private val oversizedInstructions = (1..80).joinToString("\n") { "Instruction line $it" }

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

    @Test
    fun oversizedRingingContentKeepsResolutionActionsReachableByScrolling() {
        val oversizedMedicationName = "M16 oversized alarm fixture"
        seedRingingFixture(
            occurrenceId = "m16-oversized-occurrence",
            medicationName = oversizedMedicationName,
            instructions = oversizedInstructions,
        )
        scenario = ActivityScenario.launch(AlarmActivity::class.java)

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText(oversizedMedicationName).assertExists() }.isSuccess
        }
        compose.onNodeWithText("Loading alarm…").assertDoesNotExist()
        compose.onNodeWithText(oversizedMedicationName).assertIsDisplayed()
        listOf("Taken", "Snooze", "Skip").forEach { action ->
            compose.onNodeWithText(action).performScrollTo().assertIsDisplayed().assertHasClickAction()
        }
    }

    @Test
    fun queueOwnerChangeDoesNotReusePreviousAlarmScrollPosition() {
        val firstMedicationName = "M16 first queued alarm"
        val secondMedicationName = "M16 second queued alarm"
        seedRingingFixture(
            occurrenceId = "m16-first-queued-occurrence",
            medicationName = firstMedicationName,
            instructions = oversizedInstructions,
            scheduledAtEpochMillis = 1_785_960_000_000L,
        )
        seedRingingFixture(
            occurrenceId = "m16-second-queued-occurrence",
            medicationName = secondMedicationName,
            instructions = oversizedInstructions,
            scheduledAtEpochMillis = 1_785_960_001_000L,
        )
        scenario = ActivityScenario.launch(AlarmActivity::class.java)

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText(firstMedicationName).assertExists() }.isSuccess
        }
        compose.onNodeWithText("Skip").performScrollTo().assertIsDisplayed()

        runBlocking {
            database.occurrenceDao().transition(
                id = "m16-first-queued-occurrence",
                expectedStatus = OccurrenceStatus.RINGING,
                newStatus = OccurrenceStatus.TAKEN,
                resolvedAt = 1_785_960_002_000L,
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText(secondMedicationName).assertExists() }.isSuccess
        }
        compose.onNodeWithText("Loading alarm…").assertDoesNotExist()
        compose.onNodeWithText(secondMedicationName).assertIsDisplayed()
    }

    private fun seedFixture(ringing: Boolean) {
        if (ringing) {
            seedRingingFixture(
                occurrenceId = "m14-ringing-occurrence",
                medicationName = medicationName,
                instructions = "Take with water",
            )
        } else {
            runBlocking {
                val medicationId = database.medicationDao().insertMedication(
                    MedicationEntity(name = medicationName, instructions = "Take with water", enabled = true),
                )
                database.medicationDao().insertTime(
                    ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
                )
            }
        }
    }

    private fun seedRingingFixture(
        occurrenceId: String,
        medicationName: String,
        instructions: String,
        scheduledAtEpochMillis: Long = 1_785_960_000_000L,
    ) = runBlocking {
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = medicationName, instructions = instructions, enabled = true),
        )
        val reminderId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = occurrenceId,
                reminderTimeId = reminderId,
                kind = OccurrenceKind.BASE,
                scheduledAtEpochMillis = scheduledAtEpochMillis,
                status = OccurrenceStatus.RINGING,
            ),
        )
    }
}
