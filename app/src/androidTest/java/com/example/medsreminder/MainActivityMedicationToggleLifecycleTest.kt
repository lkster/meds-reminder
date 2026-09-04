package com.example.medsreminder

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.ReminderTimeEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real MainActivity list-toggle operation across its lifecycle boundaries. */
@RunWith(AndroidJUnit4::class)
class MainActivityMedicationToggleLifecycleTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var database: AppDatabase
    private lateinit var targetContext: android.content.Context

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppDatabase.get(targetContext)
        database.clearAllTables()
        MedicationListToggleTestHook.reset(targetContext)
    }

    @After
    fun tearDown() {
        MedicationListToggleTestHook.releaseBeforeRoom(targetContext)
        MedicationListToggleTestHook.releaseAfterRoom(targetContext)
        MedicationListToggleTestHook.reset(targetContext)
        database.clearAllTables()
    }

    @Test
    fun acceptedSwitchSurvivesImmediateRecreationBeforePhaseA() {
        MedicationListToggleTestHook.configure(targetContext, holdBeforeRoom = true)
        seedMedication()
        recreateForControlledFixture()
        try {
            toggleOff()
            compose.activityRule.scenario.recreate()
            eventuallyAtOperationBoundary("Accepted operation did not reach the protected pre-Room boundary") {
                MedicationListToggleTestHook.beforeRoomReached(targetContext)
            }
            MedicationListToggleTestHook.releaseBeforeRoom(targetContext)

            eventually { !enabled() && MedicationListToggleTestHook.phaseBCalls(targetContext) == 1 }
            assertEquals(1, MedicationListToggleTestHook.phaseACalls(targetContext))
            assertEquals(1, MedicationListToggleTestHook.phaseBCalls(targetContext))
            compose.waitForIdle()
            compose.onNodeWithContentDescription(
                "Medication 1, Lifecycle medicine, reminders",
                useUnmergedTree = true,
            ).assertIsOff()
        } finally {
            MedicationListToggleTestHook.releaseBeforeRoom(targetContext)
        }
    }

    @Test
    fun recreationWhilePhaseAIsOutstandingDoesNotDuplicateOperation() {
        MedicationListToggleTestHook.configure(targetContext, holdBeforeRoom = true)
        seedMedication()
        recreateForControlledFixture()
        try {
            toggleOff()
            eventuallyAtOperationBoundary("Accepted operation did not reach the protected pre-Room boundary") {
                MedicationListToggleTestHook.beforeRoomReached(targetContext)
            }
            compose.activityRule.scenario.recreate()
            MedicationListToggleTestHook.releaseBeforeRoom(targetContext)

            eventually { !enabled() && MedicationListToggleTestHook.phaseBCalls(targetContext) == 1 }
            assertEquals(1, MedicationListToggleTestHook.phaseACalls(targetContext))
            assertEquals(1, MedicationListToggleTestHook.phaseBCalls(targetContext))
        } finally {
            MedicationListToggleTestHook.releaseBeforeRoom(targetContext)
        }
    }

    @Test
    fun finishAfterRoomCommitStillCompletesPhaseBExactlyOnce() {
        MedicationListToggleTestHook.configure(targetContext, holdAfterRoom = true)
        seedMedication()
        recreateForControlledFixture()
        try {
            toggleOff()
            eventuallyAtOperationBoundary("Committed operation did not reach the pre-Phase-B boundary") {
                MedicationListToggleTestHook.afterRoomReached(targetContext)
            }
            assertFalse(enabled())

            compose.activityRule.scenario.close()
            MedicationListToggleTestHook.releaseAfterRoom(targetContext)

            eventually { MedicationListToggleTestHook.phaseBCalls(targetContext) == 1 }
            assertEquals(1, MedicationListToggleTestHook.phaseACalls(targetContext))
            assertEquals(1, MedicationListToggleTestHook.phaseBCalls(targetContext))
            assertFalse(enabled())
        } finally {
            MedicationListToggleTestHook.releaseAfterRoom(targetContext)
        }
    }

    @Test
    fun destroyedActivityDeclinesLateFailureFeedback() {
        MedicationListToggleTestHook.configure(
            targetContext,
            holdAfterRoom = true,
            failAfterRoom = true,
        )
        seedMedication()
        recreateForControlledFixture()
        try {
            toggleOff()
            eventuallyAtOperationBoundary { MedicationListToggleTestHook.afterRoomReached(targetContext) }
            compose.activityRule.scenario.close()
            MedicationListToggleTestHook.releaseAfterRoom(targetContext)

            eventually { MedicationListToggleTestHook.feedbackSeen(targetContext) }
            assertEquals(false, MedicationListToggleTestHook.feedbackEligible(targetContext))
            assertFalse(enabled())
        } finally {
            MedicationListToggleTestHook.releaseAfterRoom(targetContext)
        }
    }

    private fun seedMedication() = runBlocking {
        val id = database.medicationDao().insertMedication(
            MedicationEntity(name = "Lifecycle medicine", instructions = null, enabled = true),
        )
        database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = id, minuteOfDay = 8 * 60),
        )
    }

    private fun toggleOff() {
        compose.waitForIdle()
        compose.onNodeWithContentDescription(
            "Medication 1, Lifecycle medicine, reminders",
            useUnmergedTree = true,
        )
            .performScrollTo()
            .assertHasClickAction()
            .assertIsOn()
            .performTouchInput { click() }
    }

    /**
     * The Android Compose rule starts an Activity before @Before. Recreate it only after this
     * test's hook and Room fixture exist, so the real Activity begins the M8 proof from them.
     */
    private fun recreateForControlledFixture() {
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { activity ->
            assertTrue(
                "Target MainActivity cannot see configured M8 test control",
                activity.isMedicationListToggleTestControlActive(),
            )
        }
        compose.onAllNodesWithContentDescription(
            "Medication 1, Lifecycle medicine, reminders",
            useUnmergedTree = true,
        ).assertCountEquals(1)
        compose.onNodeWithContentDescription(
            "Medication 1, Lifecycle medicine, reminders",
            useUnmergedTree = true,
        )
            .assertHasClickAction()
            .assertIsOn()
    }

    private fun enabled(): Boolean = runBlocking {
        database.medicationDao().observeAll().first().single().medication.enabled
    }

    private fun operationProgress(): String =
        "persistedEnabled=${runBlocking { database.medicationDao().observeAll().first().singleOrNull()?.medication?.enabled }}, " +
            "beforeRoom=${MedicationListToggleTestHook.beforeRoomReached(targetContext)}, " +
            "phaseA=${MedicationListToggleTestHook.phaseACalls(targetContext)}, " +
            "afterRoom=${MedicationListToggleTestHook.afterRoomReached(targetContext)}, " +
            "phaseB=${MedicationListToggleTestHook.phaseBCalls(targetContext)}"

    private fun eventuallyAtOperationBoundary(
        message: String = "Timed out waiting for medication-list toggle completion",
        condition: () -> Boolean,
    ) {
        repeat(200) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("$message; ${operationProgress()}", condition())
    }

    private fun eventually(
        message: String = "Timed out waiting for medication-list toggle completion",
        condition: () -> Boolean,
    ) {
        repeat(200) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("$message; ${operationProgress()}", condition())
    }
}
