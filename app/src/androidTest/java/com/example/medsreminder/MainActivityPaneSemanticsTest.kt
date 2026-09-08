package com.example.medsreminder

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.ReminderTimeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the real same-Activity boundary that swaps each top-level pane. */
@RunWith(AndroidJUnit4::class)
class MainActivityPaneSemanticsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var database: AppDatabase
    private val medicationName = "M17 pane fixture"

    @Before
    fun setUp() {
        database = AppDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext)
        database.clearAllTables()
        runBlocking {
            val medicationId = database.medicationDao().insertMedication(
                MedicationEntity(name = medicationName, instructions = null, enabled = true),
            )
            database.medicationDao().insertTime(
                ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
            )
        }
    }

    @After
    fun tearDown() {
        database.clearAllTables()
    }

    @Test
    fun mainActivityTransitionsExposeTheCurrentPaneTitle() {
        waitForMedication()
        assertPaneTitle("Meds Reminder")
        assertTitleHeading("Meds Reminder")

        compose.onNodeWithText("Add medication").performClick()
        assertPaneTitle("Add medication")
        assertTitleHeading("Add medication")
        assertPaneTitleAbsent("Meds Reminder")
        compose.onNodeWithText("Cancel").performClick()

        assertPaneTitle("Meds Reminder")
        compose.onNodeWithContentDescription("Edit medication 1, $medicationName")
            .performScrollTo()
            .performClick()
        assertPaneTitle("Edit medication")
        assertTitleHeading("Edit medication")
        assertPaneTitleAbsent("Meds Reminder")
        compose.onNodeWithText("Cancel").performClick()

        assertPaneTitle("Meds Reminder")
        compose.onNodeWithText("History").performClick()
        assertPaneTitle("History")
        assertTitleHeading("History")
        assertPaneTitleAbsent("Meds Reminder")
        compose.onNodeWithText("Back").performClick()
        assertPaneTitle("Meds Reminder")
        assertTitleHeading("Meds Reminder")
    }

    private fun waitForMedication() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(medicationName).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertPaneTitle(title: String) {
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)).assertExists()
    }

    private fun assertPaneTitleAbsent(title: String) {
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)).assertDoesNotExist()
    }

    private fun assertTitleHeading(title: String) {
        compose.onNode(
            hasText(title).and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertExists()
    }
}
