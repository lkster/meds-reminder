package com.example.medsreminder

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.medsreminder.data.AppDatabase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Proves MainActivity keeps the list in its pre-emission state until real Room collection starts. */
@RunWith(AndroidJUnit4::class)
class MainActivityMedicationListLoadingTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var database: AppDatabase
    private lateinit var targetContext: android.content.Context

    @Before
    fun setUp() {
        targetContext = compose.activity.applicationContext
        database = AppDatabase.get(targetContext)
        database.clearAllTables()
        MedicationListLoadingTestHook.reset(targetContext)
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText("No medications yet.").assertExists()
            }.isSuccess
        }
    }

    @After
    fun tearDown() {
        compose.runOnUiThread {
            MedicationListLoadingTestHook.releaseCollection(targetContext)
        }
        MedicationListLoadingTestHook.reset(targetContext)
        database.clearAllTables()
    }

    @Test
    fun realRoomFlowTransitionsFromLoadingToConfirmedEmpty() {
        MedicationListLoadingTestHook.configure(targetContext, holdBeforeCollection = true)
        compose.activityRule.scenario.recreate()

        compose.waitUntil(timeoutMillis = 5_000) {
            MedicationListLoadingTestHook.beforeCollectionReached(targetContext)
        }
        compose.onNodeWithText("Loading medications…").assertExists()
        compose.onNodeWithText("No medications yet.").assertDoesNotExist()

        MedicationListLoadingTestHook.releaseCollection(targetContext)
        compose.waitUntil(timeoutMillis = 5_000) {
            MedicationListLoadingTestHook.firstRoomEmissionReceived(targetContext)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Loading medications…").assertDoesNotExist()
        compose.onNodeWithText("No medications yet.").assertExists()
    }
}
