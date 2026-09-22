package com.example.medsreminder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.ReminderTimeEntity
import com.example.medsreminder.data.WeekdayMask
import com.example.medsreminder.ui.theme.MedsReminderTheme
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun populatedHistoryRendersOnlyFactualFieldsAndResultTimes() {
        val zone = ZoneId.systemDefault()
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(
                    history = listOf(
                        item("taken", "Medicine", localEpoch(2026, 9, 18, 8, 0, zone), localEpoch(2026, 9, 18, 8, 5, zone), HistoryOutcome.TAKEN),
                        item("snooze", "Medicine", localEpoch(2026, 9, 18, 7, 50, zone), localEpoch(2026, 9, 18, 8, 10, zone), HistoryOutcome.NO_RESPONSE, true),
                        item("skipped", "Medicine", localEpoch(2026, 9, 18, 7, 40, zone), localEpoch(2026, 9, 18, 7, 43, zone), HistoryOutcome.SKIPPED),
                    ),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Scheduled 08:00").assertExists()
        compose.onNodeWithText("Taken at 08:05").assertExists()
        compose.onNodeWithText("Skipped at 07:43").assertExists()
        compose.onNodeWithText("Timed out at 08:10").assertExists()
        compose.onNodeWithText("After snooze").assertExists()
        compose.onNodeWithText("Do not show instructions").assertDoesNotExist()
    }

    @Test
    fun crossMidnightResultStaysWithScheduledDay() {
        val zone = ZoneId.systemDefault()
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(
                    history = listOf(
                        item("cross", "Medicine", localEpoch(2026, 9, 18, 23, 55, zone), localEpoch(2026, 9, 19, 0, 5, zone), HistoryOutcome.TAKEN),
                    ),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Fri, 18 Sep 2026").assertExists()
        compose.onNodeWithText("Sat, 19 Sep 2026").assertDoesNotExist()
        compose.onNodeWithText("Scheduled 23:55").assertExists()
        compose.onNodeWithText("Taken at Sat, 19 Sep 2026 \u2022 00:05").assertExists()
    }

    @Test
    fun filtersAreSelectedAndFilterEmptyCanShowAll() {
        val zone = ZoneId.systemDefault()
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(
                    history = listOf(item("taken", "Medicine", localEpoch(2026, 9, 18, 8, 0, zone), null, HistoryOutcome.TAKEN)),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithText("Skipped").performClick()
        compose.onNodeWithText("No entries for this filter").assertExists()
        compose.onNodeWithText("No history yet").assertDoesNotExist()
        compose.onNodeWithText("Show all").performClick()
        compose.onNodeWithText("Medicine").assertExists()
    }

    @Test
    fun outcomeFiltersShowOnlyTheirUniqueFactualRowsAndRestoreAll() {
        val zone = ZoneId.systemDefault()
        val taken = item("taken", "Taken medication", localEpoch(2026, 9, 18, 8, 0, zone), null, HistoryOutcome.TAKEN)
        val skipped = item("skipped", "Skipped medication", localEpoch(2026, 9, 18, 7, 0, zone), null, HistoryOutcome.SKIPPED)
        val noResponse = item("no-response", "No response medication", localEpoch(2026, 9, 18, 6, 0, zone), null, HistoryOutcome.NO_RESPONSE)
        compose.setContent { MedsReminderTheme { HistoryScreen(history = listOf(taken, skipped, noResponse), onBack = {}) } }

        fun assertOnly(visible: String) {
            listOf("Taken medication", "Skipped medication", "No response medication").forEach { name ->
                if (name == visible) compose.onNodeWithText(name).assertExists()
                else compose.onNodeWithText(name).assertDoesNotExist()
            }
        }
        fun filter(label: String) = compose.onNode(hasText(label).and(hasClickAction()))

        filter("All").assertIsSelected()
        listOf("Taken medication", "Skipped medication", "No response medication").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onAllNodesWithText("Taken", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("Skipped", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("No response", useUnmergedTree = true).assertCountEquals(2)

        filter("Taken").performClick()
        filter("Taken").assertIsSelected()
        assertOnly("Taken medication")
        compose.onAllNodesWithText("Taken", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("Skipped", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("No response", useUnmergedTree = true).assertCountEquals(1)

        filter("Skipped").performClick()
        filter("Skipped").assertIsSelected()
        assertOnly("Skipped medication")

        filter("No response").performClick()
        filter("No response").assertIsSelected()
        assertOnly("No response medication")

        filter("All").performClick()
        filter("All").assertIsSelected()
        listOf("Taken medication", "Skipped medication", "No response medication").forEach {
            compose.onNodeWithText(it).assertExists()
        }
    }

    @Test
    fun filtersWrapAndRemainActionableAtLargeText() {
        val zone = ZoneId.systemDefault()
        compose.setContent {
            val currentPixelDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(currentPixelDensity, 2.0f)) {
                Box(Modifier.width(360.dp).testTag("m28-history-filter-viewport")) {
                    MedsReminderTheme {
                        HistoryScreen(history = listOf(item("taken", "Medicine", localEpoch(2026, 9, 18, 8, 0, zone), null, HistoryOutcome.TAKEN)), onBack = {})
                    }
                }
            }
        }

        val viewport = compose.onNodeWithTag("m28-history-filter-viewport").getUnclippedBoundsInRoot()
        val filters = listOf("All", "Taken", "Skipped", "No response").map { label ->
            compose.onNode(hasText(label).and(hasClickAction()))
        }
        val bounds = filters.map { node ->
            node.assertHasClickAction()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= viewport.left && bounds.right <= viewport.right)
            assertTrue(bounds.right > bounds.left && bounds.bottom - bounds.top >= 48.dp)
            bounds
        }
        assertTrue(bounds.zipWithNext().all { (first, second) -> second.top >= first.top })
        assertTrue(bounds.drop(1).any { it.top > bounds.first().top })
    }

    @Test
    fun longHistoryRowWrapsAndKeepsAllFactsReachableAtLargeText() {
        val zone = ZoneId.systemDefault()
        val longName = "Very long medication identity that must remain completely reachable at large text"
        compose.setContent {
            val currentPixelDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(currentPixelDensity, 2.0f)) {
                Box(Modifier.width(360.dp).testTag("m28-history-row-viewport")) {
                    MedsReminderTheme {
                        HistoryScreen(
                            history = listOf(item("long", longName, localEpoch(2026, 9, 18, 8, 0, zone), localEpoch(2026, 9, 18, 8, 5, zone), HistoryOutcome.NO_RESPONSE, true)),
                            onBack = {},
                        )
                    }
                }
            }
        }

        val viewport = compose.onNodeWithTag("m28-history-row-viewport").getUnclippedBoundsInRoot()
        val name = compose.onNodeWithText(longName).performScrollTo()
        val nameBounds = name.getUnclippedBoundsInRoot()
        assertTrue(nameBounds.left >= viewport.left && nameBounds.right <= viewport.right)
        assertTrue(nameBounds.bottom - nameBounds.top > 48.dp)
        compose.onNodeWithText("Scheduled 08:00").assertExists()
        compose.onNodeWithText("Timed out at 08:05").assertExists()
        compose.onNodeWithText("After snooze").assertExists()
        compose.onAllNodesWithText("No response", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun scheduledDayLabelHasHeadingSemantics() {
        val zone = ZoneId.systemDefault()
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(history = listOf(item("day", "Medicine", localEpoch(2026, 9, 18, 8, 0, zone), null, HistoryOutcome.TAKEN)), onBack = {})
            }
        }

        compose.onNode(
            hasText("Fri, 18 Sep 2026").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertExists()
    }

    @Test
    fun emptyHistoryUsesTransitionalCopyAndBack() {
        var backCalls = 0
        compose.setContent { MedsReminderTheme { HistoryScreen(history = emptyList(), onBack = { backCalls++ }) } }

        compose.onNodeWithText("No history yet").assertExists()
        compose.onNodeWithText("Resolved medication reminders will appear here.").assertExists()
        compose.onNodeWithText("All").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun loadingHistoryKeepsDisabledFiltersAndBackWithoutEmptyStateCopy() {
        var backCalls = 0
        compose.setContent { MedsReminderTheme { HistoryScreen(history = null, onBack = { backCalls++ }) } }

        compose.onNodeWithText("History").assertExists()
        compose.onNodeWithText("Loading history\u2026").assertExists()
        compose.onNodeWithText("All").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("No history yet").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun historyEntryAndBackReturnToMedicationList() {
        var historyVisible by mutableStateOf(false)
        compose.setContent {
            MedsReminderTheme {
                if (historyVisible) {
                    HistoryScreen(history = emptyList(), onBack = { historyVisible = false })
                } else {
                    MedicationListScreen(emptyList(), { historyVisible = true }, {}, {}, {}, { _, _ -> }, {})
                }
            }
        }

        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithText("No history yet").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Medications").assertExists()
    }

    @Test
    fun existingMedicationEditorAlwaysDisclosesReminderHistoryLoss() {
        val draft = EditorDraft(1L, "Medicine", "Do not show instructions", true, listOf(EditorTime(null, 20 * 60, WeekdayMask.ALL)))
        compose.setContent { MedsReminderTheme { MedicationEditorScreen(draft, {}, {}, {}) } }
        compose.onNodeWithText("Removing a reminder and saving also deletes its history.").assertExists()
    }

    @Test
    fun medicationDeletionConfirmationDisclosesHistoryLoss() {
        compose.setContent {
            MedsReminderTheme {
                MedicationListScreen(listOf(medication()), {}, {}, {}, {}, { _, _ -> }, {})
            }
        }
        compose.onNodeWithContentDescription("Medication actions 1, Medicine").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Its reminder times, pending alarms, and history will be removed.").assertExists()
    }

    private fun item(id: String, name: String, scheduled: Long, resolved: Long?, outcome: HistoryOutcome, afterSnooze: Boolean = false) =
        HistoryItem(id, name, scheduled, resolved, outcome, afterSnooze)

    private fun localEpoch(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun medication() = MedicationWithTimes(
        medication = MedicationEntity(1L, "Medicine", "Do not show instructions", true),
        reminderTimes = listOf(ReminderTimeEntity(11L, 1L, 8 * 60, WeekdayMask.ALL)),
    )
}
