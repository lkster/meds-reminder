package com.example.medsreminder

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real M9 delete confirmation and accepted-operation lifecycle boundaries. */
@RunWith(AndroidJUnit4::class)
class MainActivityMedicationDeleteLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var database: AppDatabase
    private lateinit var targetContext: android.content.Context
    private var medicationId = 0L

    @Before fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppDatabase.get(targetContext)
        database.clearAllTables()
        MedicationDeleteTestHook.reset(targetContext)
    }

    @After fun tearDown() {
        MedicationDeleteTestHook.releaseBeforeRoom(targetContext)
        MedicationDeleteTestHook.releaseAfterRoom(targetContext)
        MedicationDeleteTestHook.reset(targetContext)
        database.clearAllTables()
    }

    @Test fun deleteConfirmationSurvivesRecreationAndCancelLeavesRoomUntouched() {
        seedMedication(); recreateForFixture()
        openDeleteConfirmation()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Delete Lifecycle medicine?").assertExists()
        compose.onNodeWithText("Cancel").performTouchInput { click() }
        assertTrue(medicationExists())
    }

    @Test fun acceptedDeletionSurvivesRecreationBeforeRoom() {
        MedicationDeleteTestHook.configure(targetContext, holdBeforeRoom = true)
        seedMedication(); recreateForFixture()
        try {
            confirmDelete()
            compose.activityRule.scenario.recreate()
            eventually { MedicationDeleteTestHook.beforeRoomReached(targetContext) }
            MedicationDeleteTestHook.releaseBeforeRoom(targetContext)
            eventually { !medicationExists() && MedicationDeleteTestHook.phaseBCompletions(targetContext) == 1 }
            assertEquals(1, MedicationDeleteTestHook.phaseACalls(targetContext))
            assertEquals(1, MedicationDeleteTestHook.phaseBCalls(targetContext))
            assertEquals(1, MedicationDeleteTestHook.phaseBCompletions(targetContext))
        } finally { MedicationDeleteTestHook.releaseBeforeRoom(targetContext) }
    }

    @Test fun finishAfterRoomCommitStillCompletesPhaseBExactlyOnce() {
        MedicationDeleteTestHook.configure(targetContext, holdAfterRoom = true)
        // MainActivity synchronizes ringing during onResume. Seed after the controlled Activity
        // exists so that pre-delete resume recovery cannot consume this test's queue fixture.
        recreateForFixture(); seedMedication(withRinging = true)
        try {
            confirmDelete()
            eventually { MedicationDeleteTestHook.afterRoomReached(targetContext) }
            assertFalse(medicationExists())
            compose.activityRule.scenario.close()
            MedicationDeleteTestHook.releaseAfterRoom(targetContext)
            eventually(attempts = 600) {
                MedicationDeleteTestHook.phaseBCompletions(targetContext) == 1 &&
                    MedicationDeleteTestHook.ringingSynchronizationCompletions(targetContext) == 1
            }
            assertEquals(1, MedicationDeleteTestHook.phaseACalls(targetContext))
            assertEquals(1, MedicationDeleteTestHook.phaseBCalls(targetContext))
            assertEquals(1, MedicationDeleteTestHook.phaseBCompletions(targetContext))
            assertEquals(1, MedicationDeleteTestHook.ringingSynchronizationCompletions(targetContext))
            assertFalse(medicationExists())
        } finally { MedicationDeleteTestHook.releaseAfterRoom(targetContext) }
    }

    @Test fun destroyedActivityDeclinesLatePostCommitFailureFeedback() {
        MedicationDeleteTestHook.configure(targetContext, holdAfterRoom = true, failAfterRoom = true)
        seedMedication(); recreateForFixture()
        try {
            confirmDelete()
            eventually { MedicationDeleteTestHook.afterRoomReached(targetContext) }
            compose.activityRule.scenario.close()
            MedicationDeleteTestHook.releaseAfterRoom(targetContext)
            eventually { MedicationDeleteTestHook.feedbackSeen(targetContext) }
            assertFalse(MedicationDeleteTestHook.feedbackEligible(targetContext))
            assertFalse(medicationExists())
        } finally { MedicationDeleteTestHook.releaseAfterRoom(targetContext) }
    }

    private fun seedMedication(withRinging: Boolean = false) = runBlocking {
        medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = "Lifecycle medicine", instructions = null, enabled = true),
        )
        val reminderId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
        if (withRinging) {
            database.occurrenceDao().insert(
                AlarmOccurrenceEntity(
                    id = "lifecycle-ringing",
                    reminderTimeId = reminderId,
                    kind = OccurrenceKind.BASE,
                    scheduledAtEpochMillis = System.currentTimeMillis(),
                    status = OccurrenceStatus.RINGING,
                    presentedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun recreateForFixture() {
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.activityRule.scenario.onActivity {
            assertTrue(it.isMedicationDeleteTestControlActive() || !MedicationDeleteTestHook.isActive(targetContext))
        }
    }

    private fun openDeleteConfirmation() {
        // The fixture reaches this screen through the production Room Flow. Wait for its unique
        // row and its sole card action rather than racing the initial Flow emission.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Lifecycle medicine").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("Delete").fetchSemanticsNodes().size == 1
        }
        compose.waitForIdle()
        compose.onNodeWithText("Delete")
            .performScrollTo().assertHasClickAction().performTouchInput { click() }
        compose.onNodeWithText("Delete Lifecycle medicine?").assertExists()
    }

    private fun confirmDelete() {
        openDeleteConfirmation()
        // The card's Delete action remains in the tree behind the dialog; the dialog confirmation
        // is the second real button and is the acceptance boundary under test.
        compose.onAllNodesWithText("Delete")[1].performTouchInput { click() }
    }

    private fun medicationExists(): Boolean = runBlocking { database.medicationDao().get(medicationId) != null }

    private fun eventually(attempts: Int = 200, condition: () -> Boolean) {
        repeat(attempts) { if (condition()) return; Thread.sleep(25) }
        assertTrue("M9 delete operation did not reach expected completion", condition())
    }
}
