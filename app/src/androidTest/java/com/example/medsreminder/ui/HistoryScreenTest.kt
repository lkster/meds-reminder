package com.example.medsreminder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.ReminderTimeEntity
import com.example.medsreminder.data.WeekdayMask
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun populatedHistoryRendersOnlyFactualFields() {
        compose.setContent {
            MaterialTheme {
                HistoryScreen(
                    history = listOf(
                        HistoryItem("taken", "Medicine", 1_785_960_000_000L, HistoryOutcome.TAKEN, false),
                        HistoryItem("snooze", "Medicine", 1_785_959_400_000L, HistoryOutcome.NO_RESPONSE, true),
                        HistoryItem("skipped", "Medicine", 1_785_958_800_000L, HistoryOutcome.SKIPPED, false),
                    ),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Taken").assertExists()
        compose.onNodeWithText("Skipped").assertExists()
        compose.onNodeWithText("No response").assertExists()
        compose.onNodeWithText("After snooze").assertExists()
        compose.onNodeWithText("Do not show instructions").assertDoesNotExist()
    }

    @Test
    fun emptyHistoryUsesSubsetAwareCopy() {
        compose.setContent {
            MaterialTheme { HistoryScreen(history = emptyList(), onBack = {}) }
        }

        compose.onNodeWithText("No history yet").assertExists()
        compose.onNodeWithText("Taken, Skipped, and presented alarms with No response appear here.")
            .assertExists()
    }

    @Test
    fun loadingHistoryKeepsBackAvailableWithoutEmptyStateCopy() {
        var backCalls = 0
        compose.setContent {
            MaterialTheme { HistoryScreen(history = null, onBack = { backCalls++ }) }
        }

        compose.onNodeWithText("History").assertExists()
        compose.onNodeWithText("Loading history…").assertExists()
        compose.onNodeWithText("No history yet").assertDoesNotExist()
        compose.onNodeWithText("Taken, Skipped, and presented alarms with No response appear here.")
            .assertDoesNotExist()
        compose.onNodeWithText("Back").performClick()
        assertEquals(1, backCalls)
    }

    @Test
    fun historyEntryAndBackReturnToMedicationList() {
        var historyVisible by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                if (historyVisible) {
                    HistoryScreen(history = emptyList(), onBack = { historyVisible = false })
                } else {
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
                        onHistory = { historyVisible = true },
                        onAdd = {},
                        onEdit = {},
                        onToggle = { _, _ -> },
                        onDelete = {},
                    )
                }
            }
        }

        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("No history yet").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Meds Reminder").assertExists()
    }

    @Test
    fun existingMedicationEditorAlwaysDisclosesReminderHistoryLoss() {
        val draft = EditorDraft(
            id = 1L,
            name = "Medicine",
            instructions = "Do not show instructions",
            enabled = true,
            times = listOf(EditorTime(null, 20 * 60, WeekdayMask.ALL)),
        )
        compose.setContent {
            MaterialTheme {
                MedicationEditorScreen(draft, {}, {}, {})
            }
        }

        compose.onNodeWithText("Removing a reminder and saving also deletes its history.").assertExists()
    }

    @Test
    fun medicationDeletionConfirmationDisclosesHistoryLoss() {
        compose.setContent {
            MaterialTheme {
                MedicationListScreen(
                    medications = listOf(medication()),
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
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Its reminder times, pending alarms, and history will be removed.")
            .assertExists()
    }

    private fun medication() = MedicationWithTimes(
        medication = MedicationEntity(1L, "Medicine", "Do not show instructions", true),
        reminderTimes = listOf(
            ReminderTimeEntity(11L, 1L, 8 * 60, WeekdayMask.ALL),
        ),
    )
}
