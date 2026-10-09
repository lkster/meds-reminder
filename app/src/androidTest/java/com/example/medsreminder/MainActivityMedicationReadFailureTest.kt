package com.example.medsreminder

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.ReminderTimeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** M41 contracts for Activity-local medication collection failure and replacement collection. */
@RunWith(AndroidJUnit4::class)
class MainActivityMedicationReadFailureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var database: AppDatabase
    private lateinit var targetContext: android.content.Context
    private val medicationName = "M41 medication fixture"

    @Before
    fun setUp() {
        targetContext = compose.activity.applicationContext
        database = AppDatabase.get(targetContext)
        database.clearAllTables()
        MedicationListLoadingTestHook.reset(targetContext)
        runBlocking {
            val medicationId = database.medicationDao().insertMedication(
                MedicationEntity(name = medicationName, instructions = "M41 instructions", enabled = true),
            )
            database.medicationDao().insertTime(
                ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText(medicationName).assertExists() }.isSuccess
        }
    }

    @After
    fun tearDown() {
        MedicationListLoadingTestHook.releaseCollection(targetContext)
        MedicationListLoadingTestHook.clearCollectionFailure(targetContext)
        MedicationListLoadingTestHook.reset(targetContext)
        database.clearAllTables()
    }

    @Test
    fun recreatedLibraryRetriesAfterInitialMedicationCollectionFailure() {
        MedicationListLoadingTestHook.configure(targetContext, failBeforeCollection = true)
        compose.activityRule.scenario.recreate()

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Could not load medications").assertExists() }.isSuccess
        }
        compose.onNodeWithTag("medication-search").assertIsNotEnabled()
        compose.onNodeWithText("Loading medications…").assertDoesNotExist()
        compose.onNodeWithText("No medications yet").assertDoesNotExist()
        compose.onNodeWithText(medicationName).assertDoesNotExist()

        MedicationListLoadingTestHook.clearCollectionFailure(targetContext)
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            MedicationListLoadingTestHook.firstRoomEmissionReceived(targetContext)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText(medicationName).assertExists() }.isSuccess
        }
        compose.onNodeWithText("Could not load medications").assertDoesNotExist()
    }

    @Test
    fun recreatedDetailsRetriesAfterInitialMedicationCollectionFailure() {
        compose.onNodeWithContentDescription("Medication details 1, $medicationName").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("M41 instructions").assertExists() }.isSuccess
        }
        MedicationListLoadingTestHook.configure(targetContext, failBeforeCollection = true)
        compose.activityRule.scenario.recreate()

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Could not load medication details").assertExists() }.isSuccess
        }
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithText(medicationName).assertDoesNotExist()
        compose.onNodeWithText("M41 instructions").assertDoesNotExist()
        compose.onNodeWithText("Edit medication").assertDoesNotExist()
        compose.onNodeWithText("Delete medication").assertDoesNotExist()

        MedicationListLoadingTestHook.clearCollectionFailure(targetContext)
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("M41 instructions").assertExists() }.isSuccess
        }
        compose.onNodeWithText("Edit medication").assertExists()
    }

    @Test
    fun postEmissionFailurePreservesMedicationLibraryDataUntilRetrySucceeds() {
        MedicationListLoadingTestHook.configure(targetContext, failAfterFirstEmission = true)
        compose.activityRule.scenario.recreate()

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Medications could not be refreshed.").assertExists() }.isSuccess
        }
        compose.onNodeWithText(medicationName).assertExists()
        MedicationListLoadingTestHook.clearCollectionFailure(targetContext)
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Medications could not be refreshed.").assertDoesNotExist() }.isSuccess
        }
        compose.onNodeWithText(medicationName).assertExists()
    }
}
