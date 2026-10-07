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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
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
                    onMedicationDetails = {},
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
                    onMedicationDetails = {},
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
                    onMedicationDetails = {},
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
        compose.setContent { MedsReminderTheme { HistoryScreen(history = listOf(taken, skipped, noResponse), onBack = {}, onMedicationDetails = {}) } }

        fun assertOnly(visible: String) {
            listOf("Taken medication", "Skipped medication", "No response medication").forEach { name ->
                if (name == visible) compose.onNodeWithText(name).assertExists()
                else compose.onNodeWithText(name).assertDoesNotExist()
            }
        }
        fun filter(label: String) = compose.onNode(
            hasText(label)
                .and(hasClickAction())
                .and(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)),
        )

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
                        HistoryScreen(history = listOf(item("taken", "Medicine", localEpoch(2026, 9, 18, 8, 0, zone), null, HistoryOutcome.TAKEN)), onBack = {}, onMedicationDetails = {})
                    }
                }
            }
        }

        val viewport = compose.onNodeWithTag("m28-history-filter-viewport").getUnclippedBoundsInRoot()
        val filters = listOf("All", "Taken", "Skipped", "No response").map { label ->
            compose.onNode(
                hasText(label)
                    .and(hasClickAction())
                    .and(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)),
            )
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
                            onMedicationDetails = {},
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
        compose.onNodeWithContentDescription("History event: $longName, No response").performClick()
        compose.onNodeWithContentDescription("Close history details").assertHasClickAction()
        compose.onNodeWithText("Medication details").performScrollTo().assertHasClickAction()
    }

    @Test
    fun scheduledDayLabelHasHeadingSemantics() {
        val zone = ZoneId.systemDefault()
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(history = listOf(item("day", "Medicine", localEpoch(2026, 9, 18, 8, 0, zone), null, HistoryOutcome.TAKEN)), onBack = {}, onMedicationDetails = {})
            }
        }

        compose.onNode(
            hasText("Fri, 18 Sep 2026").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertExists()
    }

    @Test
    fun emptyHistoryUsesTransitionalCopyAndBack() {
        var backCalls = 0
        compose.setContent { MedsReminderTheme { HistoryScreen(history = emptyList(), onBack = { backCalls++ }, onMedicationDetails = {}) } }

        compose.onNodeWithText("No history yet").assertExists()
        compose.onNodeWithText("Resolved medication reminders will appear here.").assertExists()
        compose.onNodeWithText("All").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun loadingHistoryKeepsDisabledFiltersAndBackWithoutEmptyStateCopy() {
        var backCalls = 0
        compose.setContent { MedsReminderTheme { HistoryScreen(history = null, onBack = { backCalls++ }, onMedicationDetails = {}) } }

        compose.onNodeWithText("History").assertExists()
        compose.onNodeWithText("Loading history\u2026").assertExists()
        compose.onNodeWithText("All").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("No history yet").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun initialReadFailureKeepsHistoryShellAndDisabledFiltersWithoutLoadingOrEmptyCopy() {
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(history = null, onBack = {}, onMedicationDetails = {}, readFailed = true)
            }
        }

        compose.onNodeWithText("History").assertExists()
        compose.onNodeWithText("All").assertIsNotEnabled()
        compose.onNodeWithText("Could not load history").assertExists()
        compose.onNodeWithText("History is unavailable right now.").assertExists()
        compose.onNodeWithText("Try again").assertHasClickAction()
        compose.onNodeWithText("Loading history…").assertDoesNotExist()
        compose.onNodeWithTag("history-loading").assertDoesNotExist()
        compose.onNodeWithText("No history yet").assertDoesNotExist()
    }

    @Test
    fun initialReadFailureRetryInvokesCallbackOnce() {
        var retries = 0
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(
                    history = null,
                    onBack = {},
                    onMedicationDetails = {},
                    readFailed = true,
                    onRetryRead = { retries++ },
                )
            }
        }

        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun refreshFailurePreservesAuthoritativeHistoryAndDetailsInteraction() {
        val zone = ZoneId.systemDefault()
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(
                    history = listOf(item("entry", "Retained medicine", localEpoch(2026, 9, 18, 8, 0, zone), null, HistoryOutcome.TAKEN)),
                    onBack = {},
                    onMedicationDetails = {},
                    readFailed = true,
                )
            }
        }

        compose.onNodeWithText("All").assertIsEnabled()
        compose.onNodeWithText("History could not be refreshed.").assertExists()
        compose.onNodeWithText("Try again").assertHasClickAction()
        compose.onNodeWithText("Retained medicine").assertExists()
        compose.onNodeWithContentDescription("History event: Retained medicine, Taken").performClick()
        compose.onNodeWithText("History details").assertExists()
    }

    @Test
    fun initialReadFailureWrapsAndKeepsRetryReachableAtLargeText() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2.0f)) {
                Box(Modifier.width(320.dp).testTag("m40-history-read-failure-viewport")) {
                    MedsReminderTheme {
                        HistoryScreen(history = null, onBack = {}, onMedicationDetails = {}, readFailed = true)
                    }
                }
            }
        }

        val viewport = compose.onNodeWithTag("m40-history-read-failure-viewport").getUnclippedBoundsInRoot()
        compose.onNodeWithText("Could not load history").performScrollTo().assertExists()
        compose.onNodeWithText("History is unavailable right now.").performScrollTo().assertExists()
        val retry = compose.onNodeWithText("Try again").performScrollTo()
        val retryBounds = retry.getUnclippedBoundsInRoot()
        assertTrue(retryBounds.left >= viewport.left && retryBounds.right <= viewport.right)
        assertTrue(retryBounds.top >= viewport.top && retryBounds.bottom <= viewport.bottom)
        assertTrue(retryBounds.bottom - retryBounds.top >= 48.dp)
    }

    @Test
    fun historyCardDetailsUseStableMedicationIdAndOnlySupportedFacts() {
        val zone = ZoneId.systemDefault()
        var detailsMedicationId: Long? = null
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(
                    history = listOf(
                        item(
                            id = "first",
                            name = "Same name",
                            scheduled = localEpoch(2026, 9, 9, 8, 0, zone),
                            resolved = localEpoch(2026, 9, 9, 8, 5, zone),
                            outcome = HistoryOutcome.TAKEN,
                            afterSnooze = true,
                            medicationId = 42L,
                        ),
                        item(
                            id = "second",
                            name = "Same name",
                            scheduled = localEpoch(2026, 9, 8, 8, 0, zone),
                            resolved = null,
                            outcome = HistoryOutcome.SKIPPED,
                            medicationId = 7L,
                        ),
                    ),
                    onBack = {},
                    onMedicationDetails = { detailsMedicationId = it },
                )
            }
        }

        compose.onNodeWithContentDescription("History event: Same name, Taken").performClick()
        compose.onNodeWithText("History details").assertExists()
        compose.onNodeWithText("Wed, 9 Sep 2026 • 08:00").assertExists()
        fun sheetContains(text: String) = compose.onNode(
            hasTestTag("history-details-sheet").and(hasAnyDescendant(hasText(text))),
        ).assertExists()
        sheetContains("Taken at 08:05")
        sheetContains("After snooze")
        compose.onNodeWithText("Edit entry").assertDoesNotExist()
        compose.onNodeWithText("Dose").assertDoesNotExist()
        compose.onNodeWithText("Medication details").performClick()
        assertEquals(42L, detailsMedicationId)
    }

    @Test
    fun closingDetailsReturnsToTheSameHistoryBrowseState() {
        val zone = ZoneId.systemDefault()
        compose.setContent {
            MedsReminderTheme {
                HistoryScreen(
                    history = listOf(item("entry", "Medicine", localEpoch(2026, 9, 9, 8, 0, zone), null, HistoryOutcome.TAKEN)),
                    onBack = {},
                    onMedicationDetails = {},
                )
            }
        }

        compose.onNodeWithContentDescription("History event: Medicine, Taken").performClick()
        compose.onNodeWithContentDescription("Close history details").performClick()
        compose.onNodeWithText("History details").assertDoesNotExist()
        compose.onNodeWithText("Medicine").assertExists()
        compose.onNodeWithText("All").assertIsSelected()
    }

    @Test
    fun historyEntryAndBackReturnToMedicationList() {
        var historyVisible by mutableStateOf(false)
        compose.setContent {
            MedsReminderTheme {
                if (historyVisible) {
                    HistoryScreen(history = emptyList(), onBack = { historyVisible = false }, onMedicationDetails = {})
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
        compose.onNodeWithText("Deleting this medication removes its reminder times, pending alarms, and history.").assertExists()
    }

    private fun item(
        id: String,
        name: String,
        scheduled: Long,
        resolved: Long?,
        outcome: HistoryOutcome,
        afterSnooze: Boolean = false,
        medicationId: Long = 1L,
    ) = HistoryItem(id, medicationId, name, scheduled, resolved, outcome, afterSnooze)

    private fun localEpoch(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun medication() = MedicationWithTimes(
        medication = MedicationEntity(1L, "Medicine", "Do not show instructions", true),
        reminderTimes = listOf(ReminderTimeEntity(11L, 1L, 8 * 60, WeekdayMask.ALL)),
    )
}
