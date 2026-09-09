package com.example.medsreminder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
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
                            capabilityItems = emptyList(),
                            alarmSoundLabel = "System default",
                            vibrationEnabled = true,
                            snoozeMinutes = 5,
                            showSamsungGuidance = false,
                            onSamsungSettings = {},
                            onChooseAlarmSound = {},
                            onVibrationEnabledChange = {},
                            onSnoozeMinutesChange = {},
                            onHistory = { historyClicks++ },
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
        val titleBounds = compose.onNodeWithText("Meds Reminder").getUnclippedBoundsInRoot()
        val historyNode = compose.onNodeWithText("History")
        val historyBounds = historyNode.getUnclippedBoundsInRoot()
        assertEquals(360.dp, viewportBounds.right - viewportBounds.left)
        historyNode.assertHasClickAction()
        assertTrue(historyBounds.left >= viewportBounds.left && historyBounds.right <= viewportBounds.right)
        assertTrue(titleBounds.top < historyBounds.bottom && historyBounds.top < titleBounds.bottom)

        compose.runOnIdle { testFontScale = 2.0f }

        val largeTextViewportBounds = compose.onNodeWithTag("m19-medication-list-viewport").getUnclippedBoundsInRoot()
        val largeTextTitleBounds = compose.onNodeWithText("Meds Reminder").getUnclippedBoundsInRoot()
        val largeTextHistoryNode = compose.onNodeWithText("History")
        val largeTextHistoryBounds = largeTextHistoryNode.getUnclippedBoundsInRoot()
        largeTextHistoryNode.assertHasClickAction()
        assertTrue(largeTextHistoryBounds.top >= largeTextTitleBounds.bottom)
        assertTrue(
            largeTextHistoryBounds.left >= largeTextViewportBounds.left &&
                largeTextHistoryBounds.right <= largeTextViewportBounds.right,
        )
        largeTextHistoryNode.performClick()
        compose.runOnIdle { assertEquals(1, historyClicks) }
    }

    @Test
    fun snoozeDurationChoicesReflowAndRemainReachableWithLargeText() {
        var testFontScale by mutableFloatStateOf(1.0f)
        var snoozeMinutes by mutableStateOf(5)
        var requestedSnoozeMinutes: Int? = null

        compose.setContent {
            val currentPixelDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentPixelDensity,
                    fontScale = testFontScale,
                ),
            ) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).testTag("m22-snooze-duration-viewport")) {
                        MedicationListScreen(
                            medications = emptyList(),
                            capabilityItems = emptyList(),
                            alarmSoundLabel = "System default",
                            vibrationEnabled = true,
                            snoozeMinutes = snoozeMinutes,
                            showSamsungGuidance = false,
                            onSamsungSettings = {},
                            onChooseAlarmSound = {},
                            onVibrationEnabledChange = {},
                            onSnoozeMinutesChange = {
                                requestedSnoozeMinutes = it
                                snoozeMinutes = it
                            },
                            onHistory = {},
                            onAdd = {},
                            onEdit = {},
                            onToggle = { _, _ -> },
                            onDelete = {},
                        )
                    }
                }
            }
        }

        val viewportBounds = compose.onNodeWithTag("m22-snooze-duration-viewport").getUnclippedBoundsInRoot()
        val fiveMinuteNode = compose.onNodeWithText("5 min")
        fiveMinuteNode.assertIsSelected().assertHasClickAction()
        listOf("5 min", "10 min", "15 min", "30 min").forEach { label ->
            val bounds = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
            compose.onNodeWithText(label).assertHasClickAction()
            assertTrue(bounds.left >= viewportBounds.left && bounds.right <= viewportBounds.right)
        }

        compose.runOnIdle { testFontScale = 2.0f }

        val largeTextViewportBounds = compose.onNodeWithTag("m22-snooze-duration-viewport").getUnclippedBoundsInRoot()
        val largeTextFiveMinuteBounds = compose.onNodeWithText("5 min").getUnclippedBoundsInRoot()
        val largeTextThirtyMinuteBounds = compose.onNodeWithText("30 min").getUnclippedBoundsInRoot()
        assertTrue(largeTextThirtyMinuteBounds.top > largeTextFiveMinuteBounds.top)
        listOf("5 min", "10 min", "15 min", "30 min").forEach { label ->
            val bounds = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= largeTextViewportBounds.left && bounds.right <= largeTextViewportBounds.right)
        }

        compose.onNodeWithText("30 min").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(30, requestedSnoozeMinutes)
        }
        compose.onNodeWithText("30 min").assertIsSelected().assertHasClickAction()
    }

    @Test
    fun medicationCardLongNameKeepsEnabledSwitchReachableAtNarrowWidth() {
        val longName = "MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM"
        val item = persistedMedication(mask = WeekdayMask.ALL, id = 42L, name = longName)
        var toggled: Pair<MedicationWithTimes, Boolean>? = null

        compose.setContent {
            val currentPixelDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentPixelDensity,
                    fontScale = 1.0f,
                ),
            ) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).testTag("m21-medication-card-viewport")) {
                        MedicationListScreen(
                            medications = listOf(item), capabilityItems = emptyList(),
                            alarmSoundLabel = "System default", vibrationEnabled = true, snoozeMinutes = 5,
                            showSamsungGuidance = false, onSamsungSettings = {}, onChooseAlarmSound = {},
                            onVibrationEnabledChange = {}, onSnoozeMinutesChange = {}, onHistory = {}, onAdd = {},
                            onEdit = {}, onToggle = { medication, enabled -> toggled = medication to enabled },
                            onDelete = {},
                        )
                    }
                }
            }
        }

        val switchNode = compose.onNodeWithContentDescription(
            "Medication 1, $longName, reminders",
            useUnmergedTree = true,
        ).performScrollTo()
        val viewportBounds = compose.onNodeWithTag("m21-medication-card-viewport").getUnclippedBoundsInRoot()
        val nameBounds = compose.onNodeWithText(longName).getUnclippedBoundsInRoot()
        val switchBounds = switchNode.getUnclippedBoundsInRoot()

        switchNode.assertHasClickAction().assertIsOn()
        assertTrue(switchBounds.left >= viewportBounds.left && switchBounds.right <= viewportBounds.right)
        assertTrue(switchBounds.right - switchBounds.left >= 48.dp)
        assertTrue(nameBounds.right <= switchBounds.left)

        switchNode.performClick()
        compose.runOnIdle {
            assertEquals(item.medication.id, toggled?.first?.medication?.id)
            assertEquals(false, toggled?.second)
        }
    }

    @Test
    fun loadingMedicationCollectionShowsLoadingAndKeepsAddAvailable() {
        var addClicks = 0
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = null,
                    capabilityItems = emptyList(),
                    alarmSoundLabel = "System default",
                    vibrationEnabled = true,
                    snoozeMinutes = 5,
                    showSamsungGuidance = false,
                    onSamsungSettings = {},
                    onChooseAlarmSound = {},
                    onVibrationEnabledChange = {},
                    onSnoozeMinutesChange = {},
                    onHistory = {},
                    onAdd = { addClicks++ },
                    onEdit = {},
                    onToggle = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithText("Loading medications…").assertExists()
        compose.onNodeWithText("No medications yet.").assertDoesNotExist()
        compose.onNodeWithText("Medicine").assertDoesNotExist()
        compose.onNodeWithText("Add medication").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, addClicks) }
    }

    @Test
    fun emptyMedicationCollectionShowsConfirmedEmptyState() {
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = emptyList(),
                    capabilityItems = emptyList(),
                    alarmSoundLabel = "System default",
                    vibrationEnabled = true,
                    snoozeMinutes = 5,
                    showSamsungGuidance = false,
                    onSamsungSettings = {},
                    onChooseAlarmSound = {},
                    onVibrationEnabledChange = {},
                    onSnoozeMinutesChange = {},
                    onHistory = {},
                    onAdd = {},
                    onEdit = {},
                    onToggle = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithText("No medications yet.").assertExists()
        compose.onNodeWithText("Loading medications…").assertDoesNotExist()
    }

    @Test
    fun alarmReadinessShowsRequiredIssuesBeforeCrudAndKeepsActionsAvailable() {
        var addClicks = 0
        var historyClicks = 0
        var notificationClicks = 0
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = emptyList(),
                    capabilityItems = listOf(CapabilityItem("Notifications", false, "Notification permission is required so medication alarms can show their actionable notification.", "Allow notifications", { notificationClicks++ })),
                    alarmSoundLabel = "System default", vibrationEnabled = true, snoozeMinutes = 5,
                    showSamsungGuidance = false, onSamsungSettings = {}, onChooseAlarmSound = {},
                    onVibrationEnabledChange = {}, onSnoozeMinutesChange = {}, onHistory = { historyClicks++ },
                    onAdd = { addClicks++ }, onEdit = {}, onToggle = { _, _ -> }, onDelete = {},
                )
            }
        }

        compose.onNodeWithText("Alarm setup needs attention").assertExists()
        val readinessTop = compose.onNodeWithText("Alarm setup needs attention")
            .getUnclippedBoundsInRoot().top
        val addMedicationTop = compose.onNodeWithText("Add medication")
            .getUnclippedBoundsInRoot().top
        assertTrue(readinessTop < addMedicationTop)
        compose.onNodeWithText("Allow notifications").performClick()
        compose.onNodeWithText("Add medication").assertIsEnabled().performClick()
        compose.onNodeWithText("History").assertIsEnabled().performClick()
        assertEquals(1, notificationClicks)
        assertEquals(1, addClicks)
        assertEquals(1, historyClicks)
    }

    @Test
    fun allReadyReadinessCollapsesDetailedCards() {
        val ready = listOf(
            CapabilityItem("Notifications", true, "", "", {}),
            CapabilityItem("Alarm channel", true, "", "", {}),
            CapabilityItem("Exact alarms", true, "", "", {}),
            CapabilityItem("Full-screen alarm", true, "", "", {}, requiredForReliableDelivery = false),
        )
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = emptyList(), capabilityItems = ready, alarmSoundLabel = "System default",
                    vibrationEnabled = true, snoozeMinutes = 5, showSamsungGuidance = false,
                    onSamsungSettings = {}, onChooseAlarmSound = {}, onVibrationEnabledChange = {},
                    onSnoozeMinutesChange = {}, onHistory = {}, onAdd = {}, onEdit = {},
                    onToggle = { _, _ -> }, onDelete = {},
                )
            }
        }
        compose.onNodeWithText("Alarm setup — Ready").assertExists()
        compose.onNodeWithText("Notifications").assertDoesNotExist()
    }

    @Test
    fun fsiOnlyReadinessIsLimitedAndKeepsNotificationFallbackVisible() {
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = emptyList(),
                    capabilityItems = listOf(
                        CapabilityItem("Notifications", true, "", "", {}),
                        CapabilityItem("Alarm channel", true, "", "", {}),
                        CapabilityItem("Exact alarms", true, "", "", {}),
                        CapabilityItem(
                            "Full-screen alarm", false,
                            "Reminders can still use the actionable alarm notification, but the full-screen alarm may not appear over the lock screen.",
                            "Open full-screen access", {}, requiredForReliableDelivery = false,
                        ),
                    ),
                    alarmSoundLabel = "System default", vibrationEnabled = true, snoozeMinutes = 5,
                    showSamsungGuidance = false, onSamsungSettings = {}, onChooseAlarmSound = {},
                    onVibrationEnabledChange = {}, onSnoozeMinutesChange = {}, onHistory = {}, onAdd = {},
                    onEdit = {}, onToggle = { _, _ -> }, onDelete = {},
                )
            }
        }

        compose.onNodeWithText("Alarm setup is limited").assertExists()
        compose.onNodeWithText("Alarm setup needs attention").assertDoesNotExist()
        compose.onNodeWithText("Actionable alarm notifications remain available, but full-screen presentation is limited.").assertExists()
        compose.onNodeWithText("Full-screen alarm").assertExists()
        compose.onNodeWithText("Open full-screen access").assertExists()
    }

    @Test
    fun readinessHeadingStatusAndActionExposeNormalSemantics() {
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = emptyList(),
                    capabilityItems = listOf(CapabilityItem("Notifications", false, "Notification permission is required so medication alarms can show their actionable notification.", "Allow notifications", {})),
                    alarmSoundLabel = "System default", vibrationEnabled = true, snoozeMinutes = 5,
                    showSamsungGuidance = false, onSamsungSettings = {}, onChooseAlarmSound = {},
                    onVibrationEnabledChange = {}, onSnoozeMinutesChange = {}, onHistory = {}, onAdd = {},
                    onEdit = {}, onToggle = { _, _ -> }, onDelete = {},
                )
            }
        }

        compose.onNodeWithText("Alarm setup needs attention").assertExists()
        compose.onNode(
            hasText("Alarm setup needs attention")
                .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
        ).assertExists()
        compose.onNodeWithText("Required").assertExists()
        compose.onNodeWithText("Allow notifications").assertHasClickAction()
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
    fun listRendersPersistedSelectedWeekdays() {
        val item = persistedMedication(MONDAY_WEDNESDAY_FRIDAY)

        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(item),
                    capabilityItems = emptyList(),
                    alarmSoundLabel = "System default",
                    vibrationEnabled = true,
                    snoozeMinutes = 5,
                    showSamsungGuidance = false,
                    onSamsungSettings = {},
                    onChooseAlarmSound = {},
                    onVibrationEnabledChange = {},
                    onSnoozeMinutesChange = {},
                    onHistory = {},
                    onAdd = {},
                    onEdit = {},
                    onToggle = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNode(hasText("Mon Wed Fri", substring = true)).assertExists()
        compose.onNodeWithText("Loading medications…").assertDoesNotExist()
        compose.onNodeWithText("No medications yet.").assertDoesNotExist()
    }

    @Test
    fun alarmBehaviorControlsExposeGlobalSoundVibrationAndSnoozeChoices() {
        var vibrationEnabled by mutableStateOf(true)
        var snoozeMinutes by mutableStateOf(5)
        var chooseSoundClicks = 0
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = emptyList(),
                    capabilityItems = emptyList(),
                    alarmSoundLabel = "Morning Bell",
                    vibrationEnabled = vibrationEnabled,
                    snoozeMinutes = snoozeMinutes,
                    showSamsungGuidance = false,
                    onSamsungSettings = {},
                    onChooseAlarmSound = { chooseSoundClicks++ },
                    onVibrationEnabledChange = { vibrationEnabled = it },
                    onSnoozeMinutesChange = { snoozeMinutes = it },
                    onHistory = {},
                    onAdd = {},
                    onEdit = {},
                    onToggle = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithText("Morning Bell").assertExists()
        compose.onNodeWithText("5 min").assertIsSelected()
        compose.onNodeWithText("15 min").performClick()
        compose.onNodeWithText("15 min").assertIsSelected()
        compose.onNodeWithText("Choose alarm sound").performClick()
        compose.onNodeWithTag("alarm-vibration-toggle").performClick()

        compose.runOnIdle {
            assertEquals(15, snoozeMinutes)
            assertEquals(1, chooseSoundClicks)
            assertEquals(false, vibrationEnabled)
        }
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
        val message = "Could not save: Room edit failed"
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
        val message = "Medication was saved, but alarm updates need retry: Alarm completion failed"
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
        compose.onNodeWithText("Retry alarm update").assertHasClickAction().performClick()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
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
    fun switchesKeepToggleStateAndExposeContextualLabels() {
        val item = persistedMedication(WeekdayMask.ALL)
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(item),
                    capabilityItems = emptyList(),
                    alarmSoundLabel = "System default",
                    vibrationEnabled = false,
                    snoozeMinutes = 5,
                    showSamsungGuidance = false,
                    onSamsungSettings = {},
                    onChooseAlarmSound = {},
                    onVibrationEnabledChange = {},
                    onSnoozeMinutesChange = {},
                    onHistory = {},
                    onAdd = {},
                    onEdit = {},
                    onToggle = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Medication 1, Medicine, reminders").assertIsOn()
        compose.onNodeWithContentDescription("Vibration").assertIsOff()
    }

    @Test
    fun medicationEnabledSwitchInvokesTheListToggleCallback() {
        val item = persistedMedication(WeekdayMask.ALL)
        var toggled: Pair<MedicationWithTimes, Boolean>? = null
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(item),
                    capabilityItems = emptyList(),
                    alarmSoundLabel = "System default",
                    vibrationEnabled = true,
                    snoozeMinutes = 5,
                    showSamsungGuidance = false,
                    onSamsungSettings = {},
                    onChooseAlarmSound = {},
                    onVibrationEnabledChange = {},
                    onSnoozeMinutesChange = {},
                    onHistory = {},
                    onAdd = {},
                    onEdit = {},
                    onToggle = { medication, enabled -> toggled = medication to enabled },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithContentDescription(
            "Medication 1, Medicine, reminders",
            useUnmergedTree = true,
        ).assertHasClickAction().assertIsOn().performClick()

        compose.runOnIdle {
            assertEquals(item.medication.id, toggled?.first?.medication?.id)
            assertEquals(false, toggled?.second)
        }
    }

    @Test
    fun editorEnabledSwitchHasMedicationContextAndToggleState() {
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(
                    draftWith(EditorTime(id = 11L, minuteOfDay = 8 * 60)),
                    {},
                    {},
                    {},
                )
            }
        }

        compose.onNodeWithContentDescription("Enable Medicine reminders").assertIsOn()
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
                    medications = listOf(first, second), capabilityItems = emptyList(),
                    alarmSoundLabel = "System default", vibrationEnabled = true, snoozeMinutes = 5,
                    showSamsungGuidance = false, onSamsungSettings = {}, onChooseAlarmSound = {},
                    onVibrationEnabledChange = {}, onSnoozeMinutesChange = {}, onHistory = {}, onAdd = {},
                    onEdit = { editedId = it.medication.id }, onToggle = { _, _ -> },
                    onDelete = { deletedId = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Medication 1, Medicine, reminders", true)
            .assertHasClickAction().assertIsOn()
        compose.onNodeWithContentDescription("Medication 2, Medicine, reminders", true)
            .assertHasClickAction().assertIsOn()
        compose.onNodeWithContentDescription("Edit medication 1, Medicine").assertHasClickAction()
        compose.onNodeWithContentDescription("Edit medication 2, Medicine").assertHasClickAction()
            .performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(2L, editedId) }

        compose.onNodeWithContentDescription("Delete medication 1, Medicine").assertHasClickAction()
        compose.onNodeWithContentDescription("Delete medication 2, Medicine").performScrollTo().performTouchInput { click() }
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
    fun idleCancelInvokesEditorExitButBusyCancelIsDisabled() {
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
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancels) }
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
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
