package com.example.medsreminder

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/** Proves a recreated History screen stays loading until its own real Room Flow emits. */
@RunWith(AndroidJUnit4::class)
class MainActivityHistoryLoadingTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var database: AppDatabase
    private lateinit var targetContext: android.content.Context
    private val medicationName = "M12 history fixture"

    @Before
    fun setUp() {
        targetContext = compose.activity.applicationContext
        database = AppDatabase.get(targetContext)
        database.clearAllTables()
        HistoryLoadingTestHook.reset(targetContext)
        runBlocking {
            val medicationId = database.medicationDao().insertMedication(
                MedicationEntity(name = medicationName, instructions = null, enabled = true),
            )
            val reminderId = database.medicationDao().insertTime(
                ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
            )
            database.occurrenceDao().insert(
                AlarmOccurrenceEntity(
                    id = "m12-history-occurrence",
                    reminderTimeId = reminderId,
                    kind = OccurrenceKind.BASE,
                    scheduledAtEpochMillis = 1_785_960_000_000L,
                    status = OccurrenceStatus.TAKEN,
                    resolvedAtEpochMillis = 1_785_960_001_000L,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        HistoryLoadingTestHook.releaseCollection(targetContext)
        HistoryLoadingTestHook.reset(targetContext)
        database.clearAllTables()
    }

    @Test
    fun recreatedHistoryTransitionsFromLoadingToRealRoomHistory() {
        compose.onNodeWithText("History").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText(medicationName).assertExists()
            }.isSuccess
        }

        HistoryLoadingTestHook.configure(targetContext, holdBeforeCollection = true)
        compose.activityRule.scenario.recreate()

        compose.waitUntil(timeoutMillis = 5_000) {
            HistoryLoadingTestHook.beforeCollectionReached(targetContext)
        }
        compose.onNodeWithText("History").assertExists()
        compose.onNodeWithText("Loading history…").assertExists()
        compose.onNodeWithText("No history yet").assertDoesNotExist()
        compose.onNodeWithText(medicationName).assertDoesNotExist()

        HistoryLoadingTestHook.releaseCollection(targetContext)
        compose.waitUntil(timeoutMillis = 5_000) {
            HistoryLoadingTestHook.firstRoomEmissionReceived(targetContext)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText(medicationName).assertExists()
            }.isSuccess
        }
        compose.onNodeWithText("Loading history…").assertDoesNotExist()
        compose.onNodeWithText("No history yet").assertDoesNotExist()
    }
}
