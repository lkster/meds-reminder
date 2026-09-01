package com.example.medsreminder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.ReminderTimeEntity
import com.example.medsreminder.data.WeekdayMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationScreensTest {
    @get:Rule
    val compose = createComposeRule()

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

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        compose.onNodeWithText("Alarm setup needs attention").assertExists()
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

        compose.onNodeWithContentDescription("Enable Medicine reminders").assertIsOn()
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
            "Enable Medicine reminders",
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

    private fun draftWith(vararg times: EditorTime) = EditorDraft(
        id = 1L,
        name = "Medicine",
        instructions = "",
        enabled = true,
        times = times.toList(),
    )

    private fun persistedMedication(mask: Int) = MedicationWithTimes(
        medication = MedicationEntity(
            id = 1L,
            name = "Medicine",
            instructions = null,
            enabled = true,
        ),
        reminderTimes = listOf(
            ReminderTimeEntity(
                id = 11L,
                medicationId = 1L,
                minuteOfDay = 8 * 60,
                weekdayMask = mask,
            ),
        ),
    )

    companion object {
        private const val MONDAY = 0b0000001
        private const val MONDAY_WEDNESDAY_FRIDAY = 0b0010101
        private const val TUESDAY_THURSDAY = 0b0001010
    }
}
