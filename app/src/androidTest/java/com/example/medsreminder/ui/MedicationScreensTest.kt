package com.example.medsreminder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.MedicationScheduleEditResult
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.ReminderTimeEntity
import com.example.medsreminder.data.WeekdayMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class MedicationScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun medicationListHeaderKeepsHistoryReachableWhenLargeTextForcesWrap() {
        var testFontScale by mutableFloatStateOf(1.0f)
        var historyClicks = 0
        var settingsClicks = 0

        compose.setContent {
            val currentPixelDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentPixelDensity,
                    fontScale = testFontScale,
                ),
            ) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).testTag("m19-medication-list-viewport")) {
                        MedicationListScreen(
                            medications = emptyList(),
                            onHistory = { historyClicks++ },
                            onSettings = { settingsClicks++ },
                            onAdd = {},
                            onEdit = {},
                            onToggle = { _, _ -> },
                            onDelete = {},
                        )
                    }
                }
            }
        }

        val viewportBounds = compose.onNodeWithTag("m19-medication-list-viewport").getUnclippedBoundsInRoot()
        val titleBounds = compose.onNodeWithText("Medications").getUnclippedBoundsInRoot()
        val historyNode = compose.onNodeWithContentDescription("History")
        val historyBounds = historyNode.getUnclippedBoundsInRoot()
        assertEquals(360.dp, viewportBounds.right - viewportBounds.left)
        historyNode.assertHasClickAction()
        assertTrue(historyBounds.left >= viewportBounds.left && historyBounds.right <= viewportBounds.right)
        assertTrue(titleBounds.top < historyBounds.bottom && historyBounds.top < titleBounds.bottom)

        compose.runOnIdle { testFontScale = 2.0f }

        val largeTextViewportBounds = compose.onNodeWithTag("m19-medication-list-viewport").getUnclippedBoundsInRoot()
        val largeTextHistoryNode = compose.onNodeWithContentDescription("History")
        val largeTextHistoryBounds = largeTextHistoryNode.getUnclippedBoundsInRoot()
        val largeTextSettingsNode = compose.onNodeWithContentDescription("Settings")
        val largeTextSettingsBounds = largeTextSettingsNode.getUnclippedBoundsInRoot()
        largeTextHistoryNode.assertHasClickAction()
        largeTextSettingsNode.assertHasClickAction()
        assertTrue(
            largeTextHistoryBounds.left >= largeTextViewportBounds.left &&
                largeTextHistoryBounds.right <= largeTextViewportBounds.right,
        )
        assertTrue(largeTextSettingsBounds.left >= largeTextViewportBounds.left && largeTextSettingsBounds.right <= largeTextViewportBounds.right)
        assertTrue(largeTextSettingsBounds.right - largeTextSettingsBounds.left >= 48.dp)
        largeTextHistoryNode.performClick()
        largeTextSettingsNode.performClick()
        compose.runOnIdle { assertEquals(1, historyClicks); assertEquals(1, settingsClicks) }
    }

    @Test
    fun medicationCardLongNameKeepsOverflowAndDirectEditReachableAtNarrowWidth() {
        val longName = "MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM"
        val item = persistedMedication(mask = WeekdayMask.ALL, id = 42L, name = longName)
        var edited: MedicationWithTimes? = null

        compose.setContent {
            val currentPixelDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentPixelDensity,
                    fontScale = 2.0f,
                ),
            ) {
                MaterialTheme {
                    Box(Modifier.width(180.dp).testTag("m21-medication-card-viewport")) {
                        MedicationListScreen(
                            medications = listOf(item), onHistory = {}, onSettings = {}, onAdd = {},
                            onEdit = { edited = it }, onToggle = { _, _ -> },
                            onDelete = {},
                        )
                    }
                }
            }
        }

        val actionNode = compose.onNodeWithContentDescription("Medication actions 1, $longName").performScrollTo()
        val viewportBounds = compose.onNodeWithTag("m21-medication-card-viewport").getUnclippedBoundsInRoot()
        val nameBounds = compose.onNodeWithText(longName).getUnclippedBoundsInRoot()
        val actionBounds = actionNode.getUnclippedBoundsInRoot()

        actionNode.assertHasClickAction()
        assertTrue(actionBounds.left >= viewportBounds.left && actionBounds.right <= viewportBounds.right)
        assertTrue(actionBounds.right - actionBounds.left >= 48.dp)
        val scheduleNode = compose.onNode(hasText("Every day", substring = true)).performScrollTo()
        val scheduleBounds = scheduleNode.getUnclippedBoundsInRoot()
        assertTrue(scheduleBounds.left >= viewportBounds.left && scheduleBounds.right <= viewportBounds.right)
        val editNode = compose.onNodeWithContentDescription("Edit medication 1, $longName").performScrollTo()
        val editBounds = editNode.getUnclippedBoundsInRoot()
        editNode.assertHasClickAction()
        assertTrue(editBounds.left >= viewportBounds.left && editBounds.right <= viewportBounds.right)
        editNode.performClick()
        compose.runOnIdle { assertEquals(item.medication.id, edited?.medication?.id) }
        actionNode.performClick()
        compose.onNodeWithText("Disable reminders").assertHasClickAction()
        compose.onNodeWithText("Delete").assertHasClickAction()
    }

    @Test
    fun loadingMedicationCollectionShowsLoadingAndKeepsAddAvailable() {
        var addClicks = 0
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = null,
                    onHistory = {},
                    onSettings = {},
                    onAdd = { addClicks++ },
                    onEdit = {},
                    onToggle = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithText("Loading medications…").assertExists()
        compose.onNodeWithText("No medications yet").assertDoesNotExist()
        compose.onNodeWithText("Medicine").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add medication").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, addClicks) }
    }

    @Test
    fun emptyMedicationCollectionShowsConfirmedEmptyState() {
        var addClicks = 0
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = emptyList(),
                    onHistory = {},
                    onSettings = {},
                    onAdd = { addClicks++ },
                    onEdit = {},
                    onToggle = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithText("No medications yet").assertExists()
        compose.onNodeWithText("Loading medications…").assertDoesNotExist()
        compose.onNodeWithText("No matching medications").assertDoesNotExist()
        compose.onNodeWithText("Add first medication").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Add medication").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, addClicks) }
    }

    @Test
    fun medicationSearchFiltersTrimmedCaseInsensitiveNamesWithoutChangingItemIdentity() {
        val first = persistedMedication(mask = WeekdayMask.ALL, id = 41L, name = "Beta capsule")
        val second = persistedMedication(mask = WeekdayMask.ALL, id = 42L, name = "Beta tablet")
        val third = persistedMedication(mask = WeekdayMask.ALL, id = 43L, name = "Alpha capsule")
        var edited: MedicationWithTimes? = null

        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(first, second, third), onHistory = {}, onSettings = {}, onAdd = {},
                    onEdit = { edited = it }, onToggle = { _, _ -> }, onDelete = {},
                )
            }
        }

        compose.onNodeWithTag("medication-search").performTextInput("  BETA ")
        val firstMatchBounds = compose.onNodeWithText("Beta capsule").getUnclippedBoundsInRoot()
        val secondMatchBounds = compose.onNodeWithText("Beta tablet").getUnclippedBoundsInRoot()
        compose.onNodeWithText("Alpha capsule").assertDoesNotExist()
        assertTrue(firstMatchBounds.top < secondMatchBounds.top)
        compose.onNodeWithContentDescription("Edit medication 2, Beta tablet").performClick()
        compose.runOnIdle { assertEquals(second.medication.id, edited?.medication?.id) }
    }

    @Test
    fun clearingMedicationSearchRestoresSourceCollection() {
        val first = persistedMedication(mask = WeekdayMask.ALL, id = 41L, name = "Alpha capsule")
        val second = persistedMedication(mask = WeekdayMask.ALL, id = 42L, name = "Beta tablet")
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(first, second), onHistory = {}, onSettings = {}, onAdd = {},
                    onEdit = {}, onToggle = { _, _ -> }, onDelete = {},
                )
            }
        }

        compose.onNodeWithTag("medication-search").performTextInput("alpha")
        compose.onNodeWithText("Beta tablet").assertDoesNotExist()
        val clearNode = compose.onNodeWithContentDescription("Clear medication search")
        val clearBounds = clearNode.getUnclippedBoundsInRoot()
        clearNode.assertHasClickAction().performClick()
        assertTrue(clearBounds.right - clearBounds.left >= 48.dp)
        assertTrue(clearBounds.bottom - clearBounds.top >= 48.dp)
        compose.onNodeWithText("Alpha capsule").assertExists()
        compose.onNodeWithText("Beta tablet").assertExists()
    }

    @Test
    fun searchEmptyIsDistinctFromAuthoritativeGlobalEmpty() {
        val item = persistedMedication(mask = WeekdayMask.ALL, id = 41L, name = "Alpha capsule")
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(item), onHistory = {}, onSettings = {}, onAdd = {},
                    onEdit = {}, onToggle = { _, _ -> }, onDelete = {},
                )
            }
        }

        compose.onNodeWithTag("medication-search").performTextInput("missing")
        compose.onNodeWithText("No matching medications").assertExists()
        compose.onNodeWithText("No medications yet").assertDoesNotExist()
        compose.onNodeWithText("Add first medication").assertDoesNotExist()
        compose.onNodeWithTag("medication-search").assertExists()
        compose.onNodeWithContentDescription("Add medication").assertExists()
    }

    @Test
    fun medicationBrowseExposesPaneHeadingAndSinglePurposefulSearchAndAddSemantics() {
        val item = persistedMedication(mask = WeekdayMask.ALL, id = 41L, name = "Alpha capsule")
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(item), onHistory = {}, onSettings = {}, onAdd = {},
                    onEdit = {}, onToggle = { _, _ -> }, onDelete = {},
                )
            }
        }

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Medications")).assertExists()
        compose.onNode(
            hasText("Medications").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertExists()
        compose.onNodeWithText("Search medications").assertExists()
        compose.onAllNodesWithContentDescription("Search").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Add medication").assertCountEquals(1)

        compose.onNodeWithTag("medication-search").performTextInput("alpha")
        compose.onAllNodesWithContentDescription("Clear medication search").assertCountEquals(1)
    }

    @Test
    fun landscapeMedicationLibraryKeepsSearchLastCardActionsAndFabReachable() {
        val first = persistedMedication(mask = WeekdayMask.ALL, id = 41L, name = "First medication")
        val second = persistedMedication(mask = WeekdayMask.ALL, id = 42L, name = "Second medication")
        val last = persistedMedication(mask = WeekdayMask.ALL, id = 43L, name = "Last medication")
        compose.setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .width(640.dp)
                        .height(240.dp)
                        .testTag("m29-landscape-viewport"),
                ) {
                    MedicationListScreen(
                        medications = listOf(first, second, last), onHistory = {}, onSettings = {}, onAdd = {},
                        onEdit = {}, onToggle = { _, _ -> }, onDelete = {},
                    )
                }
            }
        }

        val viewportBounds = compose.onNodeWithTag("m29-landscape-viewport").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("medication-search").assertExists()
        compose.onNodeWithText("First medication").assertExists()
        val lastEdit = compose.onNodeWithContentDescription("Edit medication 3, Last medication").performScrollTo()
        val lastActions = compose.onNodeWithContentDescription("Medication actions 3, Last medication").performScrollTo()
        val fab = compose.onNodeWithContentDescription("Add medication")
        val lastEditBounds = lastEdit.getUnclippedBoundsInRoot()
        val lastActionsBounds = lastActions.getUnclippedBoundsInRoot()
        val fabBounds = fab.getUnclippedBoundsInRoot()
        lastEdit.assertHasClickAction()
        lastActions.assertHasClickAction()
        fab.assertHasClickAction()
        listOf(lastEditBounds, lastActionsBounds, fabBounds).forEach { bounds ->
            assertTrue(bounds.left >= viewportBounds.left && bounds.right <= viewportBounds.right)
            assertTrue(bounds.top >= viewportBounds.top && bounds.bottom <= viewportBounds.bottom)
        }
    }









    @Test
    fun existingEveryDayScheduleRendersAsEveryDay() {
        val draft = draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60, weekdayMask = WeekdayMask.ALL))

        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(draft, {}, {}, {})
            }
        }

        compose.onNodeWithText("Every day").assertIsSelected()
    }

    @Test
    fun redesignedEditorUsesTruthfulFieldsPaneSemanticsAndToolbarBack() {
        var draft by mutableStateOf(
            draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60)).copy(instructions = "Instructions"),
        )
        var cancels = 0
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(draft, { draft = it }, {}, { cancels++ })
            }
        }

        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Edit medication")).assertExists()
        compose.onNode(
            hasText("Edit medication").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertExists()
        compose.onNodeWithText("Instructions / notes (optional)").assertExists()
        compose.onAllNodesWithText("Dose or instructions (optional)").assertCountEquals(0)
        compose.onNodeWithTag("medication-editor-instructions").performTextInput(" after food")
        compose.runOnIdle { assertEquals(" after foodInstructions", draft.instructions) }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, cancels) }
        compose.onAllNodesWithText("Cancel").assertCountEquals(0)
    }

    @Test
    fun redesignedEditorLocksBackAndEnabledSwitchDuringSave() {
        var enabledChanges = 0
        val draft = draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60))
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(
                    draft = draft,
                    onDraftChange = { enabledChanges++ },
                    onSave = {},
                    onCancel = {},
                    saveState = EditorSaveState.CompletingAlarms(
                        MedicationScheduleEditResult(1, emptyList(), false),
                    ),
                )
            }
        }

        compose.onNodeWithContentDescription("Enable Medicine reminders").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithTag("medication-editor-save").assertIsNotEnabled()
        compose.onNodeWithText("Saving…").assertExists()
        compose.onNodeWithText("Finishing alarm setup…").assertExists()
        compose.onAllNodesWithText("Saved. Updating alarms…").assertCountEquals(0)
        compose.runOnIdle { assertEquals(0, enabledChanges) }
    }

    @Test
    fun redesignedEditorReflowsActionsAndKeepsSaveReachableAtLargeText() {
        val draft = draftWith(
            EditorTime(id = 11L, minuteOfDay = 8 * 60),
            EditorTime(id = 12L, minuteOfDay = 20 * 60),
        )
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                MaterialTheme {
                    Box(Modifier.width(240.dp).height(220.dp).testTag("m30-editor-viewport")) {
                        MedicationEditorScreen(draft, {}, {}, {})
                    }
                }
            }
        }

        val viewport = compose.onNodeWithTag("m30-editor-viewport").getUnclippedBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back")
        val backBounds = back.getUnclippedBoundsInRoot()
        assertTrue(backBounds.right - backBounds.left >= 48.dp)
        assertTrue(backBounds.bottom - backBounds.top >= 48.dp)

        val time = compose.onNodeWithContentDescription("Change reminder 1 time, currently 08:00", true)
        val remove = compose.onNodeWithContentDescription("Remove reminder 1 at 08:00", true)
        time.performScrollTo()
        remove.performScrollTo()
        val timeBounds = time.getUnclippedBoundsInRoot()
        val removeBounds = remove.getUnclippedBoundsInRoot()
        assertTrue(timeBounds.right <= viewport.right && removeBounds.right <= viewport.right)
        assertTrue(timeBounds.bottom - timeBounds.top >= 48.dp)
        assertTrue(removeBounds.bottom - removeBounds.top >= 48.dp)
        compose.onNodeWithContentDescription("Reminder 1 at 08:00, Monday", true).performScrollTo().assertExists()
        val save = compose.onNodeWithTag("medication-editor-save")
        save.performScrollTo().assertIsEnabled()
        val saveBounds = save.getUnclippedBoundsInRoot()
        assertTrue(saveBounds.bottom <= viewport.bottom)
        assertTrue(saveBounds.bottom - saveBounds.top >= 48.dp)
    }

    @Test
    fun listRendersPersistedSelectedWeekdays() {
        val item = persistedMedication(MONDAY_WEDNESDAY_FRIDAY)

        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(item),
                    onHistory = {},
                    onSettings = {},
                    onAdd = {},
                    onEdit = {},
                    onToggle = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNode(hasText("Mon Wed Fri", substring = true)).assertExists()
        compose.onNodeWithText("Loading medications…").assertDoesNotExist()
        compose.onNodeWithText("No medications yet").assertDoesNotExist()
    }



    @Test
    fun togglingWeekdayUpdatesOnlyItsReminderRow() {
        var draft by mutableStateOf(
            draftWith(
                EditorTime(id = 11L, minuteOfDay = 8 * 60, weekdayMask = WeekdayMask.ALL),
                EditorTime(id = 12L, minuteOfDay = 20 * 60, weekdayMask = WeekdayMask.ALL),
            ),
        )
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(draft, { draft = it }, {}, {})
            }
        }

        compose.onAllNodesWithText("Mon")[0].performClick()

        compose.runOnIdle {
            assertEquals(WeekdayMask.ALL xor MONDAY, draft.times[0].weekdayMask)
            assertEquals(WeekdayMask.ALL, draft.times[1].weekdayMask)
        }
    }

    @Test
    fun noSelectedWeekdayDisablesSaveAndShowsValidation() {
        var draft by mutableStateOf(
            draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60, weekdayMask = MONDAY)),
        )
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(draft, { draft = it }, {}, {})
            }
        }
        compose.onNodeWithText("Save").assertIsEnabled()

        compose.onNodeWithText("Mon").performClick()

        compose.onNodeWithText("Select at least one day").assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun whitespaceNameAndDuplicateTimesAreVisibleAndBlockSave() {
        val draft = draftWith(
            EditorTime(id = 11L, minuteOfDay = 8 * 60),
            EditorTime(id = 12L, minuteOfDay = 8 * 60),
        ).copy(name = "   ")

        compose.setContent {
            MaterialTheme { MedicationEditorScreen(draft, {}, {}, {}) }
        }

        compose.onNodeWithText("Enter a medication name").assertExists()
        compose.onAllNodesWithText("Each reminder needs a different time").assertCountEquals(2)
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun distinctMultiReminderScheduleIsSaveable() {
        val draft = draftWith(
            EditorTime(id = 11L, minuteOfDay = 8 * 60),
            EditorTime(id = 12L, minuteOfDay = 20 * 60),
        )

        compose.setContent {
            MaterialTheme { MedicationEditorScreen(draft, {}, {}, {}) }
        }

        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun busyEditorLocksMutationAndCannotResubmit() {
        var changes = 0
        var saves = 0
        val draft = draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60))
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(
                    draft = draft,
                    onDraftChange = { changes++ },
                    onSave = { saves++ },
                    onCancel = {},
                    saveState = EditorSaveState.SavingRoom,
                )
            }
        }

        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.onAllNodesWithText("Mon")[0].assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(0, changes)
            assertEquals(0, saves)
        }
    }

    @Test
    fun roomFailureIsPoliteLiveRegionAndSaveRemainsRetryable() {
        var saves = 0
        val message = "Could not save medication. Try again."
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(
                    draft = draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60)),
                    onDraftChange = {},
                    onSave = { saves++ },
                    onCancel = {},
                    saveState = EditorSaveState.RoomFailure("Room edit failed"),
                )
            }
        }

        compose.onAllNodesWithText(message).assertCountEquals(1)
        compose.onNodeWithText(message)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertEquals(1, liveRegionNodeCount())
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test
    fun postCommitFailureIsPoliteLiveRegionAndRetainsRetryAction() {
        var retries = 0
        val message = "Medication was saved, but alarms could not be updated."
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(
                    draft = draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60)),
                    onDraftChange = {},
                    onSave = {},
                    onCancel = {},
                    saveState = EditorSaveState.PostCommitFailure(
                        MedicationScheduleEditResult(1, emptyList(), false),
                        "Alarm completion failed",
                    ),
                    onRetryPostCommit = { retries++ },
                )
            }
        }

        compose.onAllNodesWithText(message).assertCountEquals(1)
        compose.onNodeWithText(message)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertEquals(1, liveRegionNodeCount())
        compose.onNodeWithText("Retry alarm update").performScrollTo().assertHasClickAction().performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun editorProgressStatesDoNotExposeLiveRegions() {
        val draft = draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60))
        var saveState by mutableStateOf<EditorSaveState>(EditorSaveState.SavingRoom)
        compose.setContent {
            MaterialTheme { MedicationEditorScreen(draft, {}, {}, {}, saveState = saveState) }
        }

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertDoesNotExist()
        compose.runOnIdle {
            saveState = EditorSaveState.CompletingAlarms(
                MedicationScheduleEditResult(1, emptyList(), false),
            )
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertDoesNotExist()
    }

    @Test
    fun medicationActionsExposeContextualLabels() {
        val item = persistedMedication(WeekdayMask.ALL)
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(item),
                    onHistory = {},
                    onSettings = {},
                    onAdd = {},
                    onEdit = {},
                    onToggle = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Medication actions 1, Medicine").assertHasClickAction()
    }

    @Test
    fun medicationActionMenuInvokesTheListToggleCallback() {
        val item = persistedMedication(WeekdayMask.ALL)
        var toggled: Pair<MedicationWithTimes, Boolean>? = null
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(item),
                    onHistory = {},
                    onSettings = {},
                    onAdd = {},
                    onEdit = {},
                    onToggle = { medication, enabled -> toggled = medication to enabled },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Medication actions 1, Medicine").performClick()
        compose.onNodeWithText("Disable reminders").performClick()

        compose.runOnIdle {
            assertEquals(item.medication.id, toggled?.first?.medication?.id)
            assertEquals(false, toggled?.second)
        }
    }

    @Test
    fun editorEnabledSwitchHasMedicationContextAndToggleState() {
        var draft by mutableStateOf(draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60)))
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(
                    draft,
                    { draft = it },
                    {},
                    {},
                )
            }
        }

        val enabledSwitch = compose.onNodeWithContentDescription("Enable Medicine reminders")
        enabledSwitch.assertIsOn().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(false, draft.enabled) }
        compose.onNodeWithContentDescription("Enable Medicine reminders").assertIsOff().assertIsEnabled()
    }

    @Test
    fun duplicateMedicationControlsHaveDistinctContextAndReachCorrectCallbacks() {
        val first = persistedMedication(id = 1L, name = "Medicine", mask = WeekdayMask.ALL)
        val second = persistedMedication(id = 2L, name = "Medicine", mask = WeekdayMask.ALL)
        var editedId: Long? = null
        var deletedId: Long? = null
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(first, second), onHistory = {}, onSettings = {}, onAdd = {},
                    onEdit = { editedId = it.medication.id }, onToggle = { _, _ -> },
                    onDelete = { deletedId = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Medication actions 1, Medicine")
            .assertHasClickAction()
        compose.onNodeWithContentDescription("Medication actions 2, Medicine")
            .assertHasClickAction()
        compose.onNodeWithContentDescription("Edit medication 1, Medicine").assertHasClickAction()
        compose.onNodeWithContentDescription("Edit medication 2, Medicine").assertHasClickAction()
            .performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(2L, editedId) }

        compose.onNodeWithContentDescription("Medication actions 2, Medicine").performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("Delete").performTouchInput { click() }
        compose.onNodeWithText("Delete Medicine?").assertExists()
        compose.onNode(
            hasText("Delete") and hasClickAction() and hasAnyAncestor(isDialog()),
        ).performClick()
        compose.runOnIdle { assertEquals(2L, deletedId) }
    }

    @Test
    fun duplicateReminderTimesHaveDistinctAccessibleControlsAndWeekdayContext() {
        var draft by mutableStateOf(
            draftWith(
                EditorTime(id = 11L, minuteOfDay = 8 * 60),
                EditorTime(id = 12L, minuteOfDay = 8 * 60),
            ),
        )
        compose.setContent {
            MaterialTheme { MedicationEditorScreen(draft, { draft = it }, {}, {}) }
        }

        compose.onNodeWithContentDescription("Change reminder 1 time, currently 08:00", true).assertHasClickAction()
        compose.onNodeWithContentDescription("Change reminder 2 time, currently 08:00", true).assertHasClickAction()
        compose.onNodeWithContentDescription("Remove reminder 1 at 08:00", true).assertHasClickAction()
        compose.onNodeWithContentDescription("Remove reminder 2 at 08:00", true).assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(11L, draft.times.single().id) }
    }

    @Test
    fun weekdayChipsExposeReminderContextFullNamesAndPreserveSelection() {
        var draft by mutableStateOf(
            draftWith(
                EditorTime(id = 11L, minuteOfDay = 8 * 60),
                EditorTime(id = 12L, minuteOfDay = 20 * 60),
            ),
        )
        compose.setContent {
            MaterialTheme { MedicationEditorScreen(draft, { draft = it }, {}, {}) }
        }

        compose.onNodeWithContentDescription("Reminder 1 at 08:00, Every day", true).assertIsSelected()
        compose.onNodeWithContentDescription("Reminder 1 at 08:00, Monday", true).assertIsSelected().performClick()
        compose.onNodeWithContentDescription("Reminder 2 at 20:00, Every day", true).assertIsSelected()
        compose.onNodeWithContentDescription("Reminder 2 at 20:00, Monday", true).assertIsSelected()
        compose.runOnIdle {
            assertEquals(WeekdayMask.ALL xor MONDAY, draft.times[0].weekdayMask)
            assertEquals(WeekdayMask.ALL, draft.times[1].weekdayMask)
        }
    }

    @Test
    fun idleBackInvokesEditorExitButBusyBackIsDisabled() {
        var cancels = 0
        var busy by mutableStateOf(false)
        val draft = draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60))
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(
                    draft = draft,
                    onDraftChange = {},
                    onSave = {},
                    onCancel = { cancels++ },
                    saveState = if (busy) EditorSaveState.SavingRoom else EditorSaveState.Idle,
                )
            }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, cancels) }
        compose.runOnIdle { busy = true }
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
    }

    @Test
    fun reopeningPersistedMedicationRestoresSelectedWeekdays() {
        val reopened = EditorDraft.from(persistedMedication(TUESDAY_THURSDAY))

        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(reopened, {}, {}, {})
            }
        }

        compose.onNodeWithText("Every day").assertIsNotSelected()
        compose.onNodeWithText("Mon").assertIsNotSelected()
        compose.onNodeWithText("Tue").assertIsSelected()
        compose.onNodeWithText("Thu").assertIsSelected()
    }

    @Test
    fun weekdayChoicesReflowWithoutCompressionAndRemainReachableWithLargeText() {
        var draft by mutableStateOf(
            draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60, weekdayMask = WeekdayMask.ALL)),
        )
        var editorWidth by mutableStateOf(2000.dp)

        compose.setContent {
            val currentPixelDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density = currentPixelDensity, fontScale = 2.0f),
            ) {
                MaterialTheme {
                    Box(
                        Modifier
                            .requiredWidth(editorWidth)
                            .testTag("m23-weekday-viewport"),
                    ) {
                        MedicationEditorScreen(draft, { draft = it }, {}, {})
                    }
                }
            }
        }

        fun weekday(day: String) = compose.onNodeWithContentDescription(
            "Reminder 1 at 08:00, $day",
            useUnmergedTree = true,
        )

        val naturalMonBounds = weekday("Monday").getUnclippedBoundsInRoot()
        val naturalTueBounds = weekday("Tuesday").getUnclippedBoundsInRoot()
        val naturalWedBounds = weekday("Wednesday").getUnclippedBoundsInRoot()
        val naturalThuBounds = weekday("Thursday").getUnclippedBoundsInRoot()
        val naturalBounds = listOf(naturalMonBounds, naturalTueBounds, naturalWedBounds, naturalThuBounds)
        assertTrue(naturalBounds.all { it.right - it.left > 0.dp })
        assertTrue(naturalBounds.all { abs((it.top - naturalMonBounds.top).value) <= 1f })

        val naturalMonWidth = naturalMonBounds.right - naturalMonBounds.left
        val naturalTueWidth = naturalTueBounds.right - naturalTueBounds.left
        val naturalWedWidth = naturalWedBounds.right - naturalWedBounds.left
        val naturalThuWidth = naturalThuBounds.right - naturalThuBounds.left
        val naturalFirstGroupWidth =
            naturalMonWidth + naturalTueWidth + naturalWedWidth + naturalThuWidth + 18.dp
        val constrainedFirstGroupWidth =
            naturalFirstGroupWidth - naturalThuWidth / 2f
        compose.runOnIdle { editorWidth = constrainedFirstGroupWidth + 64.dp }

        val constrainedMonBounds = weekday("Monday").getUnclippedBoundsInRoot()
        val constrainedTueBounds = weekday("Tuesday").getUnclippedBoundsInRoot()
        val constrainedWedBounds = weekday("Wednesday").getUnclippedBoundsInRoot()
        val constrainedThuBounds = weekday("Thursday").getUnclippedBoundsInRoot()
        val constrainedBounds = listOf(
            constrainedMonBounds,
            constrainedTueBounds,
            constrainedWedBounds,
            constrainedThuBounds,
        )
        constrainedBounds.zip(naturalBounds).forEach { (constrained, natural) ->
            assertTrue(
                abs(((constrained.right - constrained.left) - (natural.right - natural.left)).value) <= 1f,
            )
        }
        assertTrue(constrainedThuBounds.top > constrainedMonBounds.top)

        val viewportBounds = compose.onNodeWithTag("m23-weekday-viewport").getUnclippedBoundsInRoot()
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").forEach { day ->
            val bounds = weekday(day).getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= viewportBounds.left && bounds.right <= viewportBounds.right)
            weekday(day).assertIsSelected().assertHasClickAction()
        }

        weekday("Thursday").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(WeekdayMask.ALL xor THURSDAY, draft.times[0].weekdayMask) }
        weekday("Thursday").assertIsNotSelected()
        weekday("Monday").assertIsSelected()
    }



    private fun draftWith(vararg times: EditorTime) = EditorDraft(
        id = 1L,
        name = "Medicine",
        instructions = "",
        enabled = true,
        times = times.toList(),
    )

    private fun persistedMedication(
        mask: Int,
        id: Long = 1L,
        name: String = "Medicine",
    ) = MedicationWithTimes(
        medication = MedicationEntity(
            id = id,
            name = name,
            instructions = null,
            enabled = true,
        ),
        reminderTimes = listOf(
            ReminderTimeEntity(
                id = id * 10 + 1,
                medicationId = id,
                minuteOfDay = 8 * 60,
                weekdayMask = mask,
            ),
        ),
    )

    private fun liveRegionNodeCount(): Int =
        compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().countLiveRegionNodes()

    private fun SemanticsNode.countLiveRegionNodes(): Int =
        (if (config.contains(SemanticsProperties.LiveRegion)) 1 else 0) +
            children.sumOf { it.countLiveRegionNodes() }

    companion object {
        private const val MONDAY = 0b0000001
        private const val MONDAY_WEDNESDAY_FRIDAY = 0b0010101
        private const val TUESDAY_THURSDAY = 0b0001010
        private const val THURSDAY = 0b0001000
    }
}
