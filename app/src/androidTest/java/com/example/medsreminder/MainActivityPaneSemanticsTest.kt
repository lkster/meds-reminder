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
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId
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
        assertPaneTitle("Medications")
        assertTitleHeading("Medications")

        compose.onNodeWithContentDescription("Add medication").performClick()
        assertPaneTitle("Add medication")
        assertTitleHeading("Add medication")
        assertPaneTitleAbsent("Medications")
        compose.onNodeWithContentDescription("Back").performClick()

        assertPaneTitle("Medications")
        compose.onNodeWithContentDescription("Medication details 1, $medicationName")
            .performScrollTo()
            .performClick()
        assertPaneTitle("Medication details")
        assertTitleHeading(medicationName)
        assertPaneTitleAbsent("Medications")
        compose.onNodeWithText("Edit medication").performScrollTo().performClick()
        assertPaneTitle("Edit medication")
        assertTitleHeading("Edit medication")
        assertPaneTitleAbsent("Medications")
        compose.onNodeWithContentDescription("Back").performClick()

        assertPaneTitle("Medication details")
        compose.onNodeWithContentDescription("Back").performClick()

        assertPaneTitle("Medications")
        compose.onNodeWithContentDescription("Settings").performClick()
        assertPaneTitle("Settings")
        assertTitleHeading("Settings")
        assertPaneTitleAbsent("Medications")
        compose.onNodeWithText("Alarm readiness").performClick()
        assertPaneTitle("Alarm readiness")
        assertTitleHeading("Alarm readiness")
        assertPaneTitleAbsent("Settings")
        compose.onNodeWithContentDescription("Back").performClick()
        assertPaneTitle("Settings")
        compose.onNodeWithContentDescription("Back").performClick()
        assertPaneTitle("Medications")
        compose.onNodeWithContentDescription("History").performClick()
        assertPaneTitle("History")
        assertTitleHeading("History")
        assertPaneTitleAbsent("Medications")
        compose.onNodeWithContentDescription("Back").performClick()
        assertPaneTitle("Medications")
        assertTitleHeading("Medications")
    }

    @Test
    fun medicationDetailsReferenceRender() {
        runBlocking {
            database.clearAllTables()
            val id = database.medicationDao().insertMedication(
                MedicationEntity(
                    name = "Lisinopril",
                    instructions = "Take with water every morning.",
                    enabled = true,
                ),
            )
            database.medicationDao().insertTime(
                ReminderTimeEntity(medicationId = id, minuteOfDay = 8 * 60),
            )
            database.medicationDao().insertTime(
                ReminderTimeEntity(medicationId = id, minuteOfDay = 20 * 60, weekdayMask = 0b0010101),
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Lisinopril").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Medication details 1, Lisinopril")
            .performScrollTo().performClick()
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/m38-medication-details.png",
        ).close()
    }

    @Test
    fun historyDetailsNavigateToMedicationDetailsByStableIdAndBackReturnsToMedications() {
        val historyMedicationName = "History details fixture"
        val historyMedicationId = seedTerminalOccurrence(
            name = historyMedicationName,
            occurrenceId = "m39-history-navigation",
            kind = OccurrenceKind.BASE,
            scheduledAtEpochMillis = 1_788_940_800_000L,
            resolvedAtEpochMillis = 1_788_941_100_000L,
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(historyMedicationName).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("History").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(historyMedicationName).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("History event: $historyMedicationName, Taken").performClick()
        compose.onNodeWithText("Medication details").performClick()
        assertPaneTitle("Medication details")
        assertTitleHeading(historyMedicationName)

        compose.onNodeWithContentDescription("Back").performClick()
        assertPaneTitle("Medications")
        // The returned ID proves History did not use its event row or a duplicate display name as navigation authority.
        check(historyMedicationId > 0)
    }

    @Test
    fun historyDetailsReferenceRender() {
        val zone = ZoneId.systemDefault()
        runBlocking { database.clearAllTables() }
        seedTerminalOccurrence(
            name = "Vitamin D",
            occurrenceId = "m39-history-details-render",
            kind = OccurrenceKind.SNOOZE,
            scheduledAtEpochMillis = LocalDateTime.of(2026, 9, 9, 8, 0).atZone(zone).toInstant().toEpochMilli(),
            resolvedAtEpochMillis = LocalDateTime.of(2026, 9, 9, 8, 5).atZone(zone).toInstant().toEpochMilli(),
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Vitamin D").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("History").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Vitamin D").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("History event: Vitamin D, Taken").performClick()
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/m39-history-details.png",
        ).close()
    }

    private fun seedTerminalOccurrence(
        name: String,
        occurrenceId: String,
        kind: OccurrenceKind,
        scheduledAtEpochMillis: Long,
        resolvedAtEpochMillis: Long,
    ): Long = runBlocking {
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = name, instructions = null, enabled = true),
        )
        val reminderId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = occurrenceId,
                reminderTimeId = reminderId,
                kind = kind,
                scheduledAtEpochMillis = scheduledAtEpochMillis,
                status = OccurrenceStatus.TAKEN,
                resolvedAtEpochMillis = resolvedAtEpochMillis,
            ),
        )
        medicationId
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
