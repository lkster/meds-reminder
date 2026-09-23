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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.view.KeyEvent
import com.example.medsreminder.ui.theme.MedsReminderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun settingsHasOnlySupportedAlarmControlsAndReadinessEntry() {
        compose.setContent {
            MedsReminderTheme {
                SettingsScreen(items(), "System default", true, 5, {}, {}, {}, {}, {})
            }
        }
        listOf("Settings", "Alarm readiness", "Alarm", "Alarm sound", "Vibration", "Default Snooze").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun settingsReadinessSummaryRemainsAnActionableEntry() {
        var opened = 0
        compose.setContent {
            MedsReminderTheme {
                SettingsScreen(items(notifications = false), "System default", true, 10, {}, { opened++ }, {}, {}, {})
            }
        }

        compose.onNodeWithText("Alarm readiness").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun settingsRowsKeepLargeTargetsAndLongSoundLabelReachable() {
        val longSoundName = "System default alarm tone with a long device-provided label"
        compose.setContent {
            MedsReminderTheme {
                Box(Modifier.width(360.dp)) {
                    SettingsScreen(items(), longSoundName, true, 10, {}, {}, {}, {}, {})
                }
            }
        }

        compose.onNodeWithText(longSoundName).assertIsDisplayed()
        val soundBounds = compose.onNodeWithText("Alarm sound").assertHasClickAction().getUnclippedBoundsInRoot()
        val snoozeBounds = compose.onNodeWithText("Default Snooze").assertHasClickAction().getUnclippedBoundsInRoot()
        assertTrue(soundBounds.bottom - soundBounds.top >= 48.dp)
        assertTrue(snoozeBounds.bottom - snoozeBounds.top >= 48.dp)
    }

    @Test
    fun readinessScreenAlwaysShowsFourCapabilities() {
        compose.setContent { MedsReminderTheme { AlarmReadinessScreen(items(), false, {}, {}) } }
        listOf("Notifications", "Alarm notifications", "Exact alarms", "Full-screen alarm").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun snoozeSelectionCommitsOnlyAfterDone() {
        var committed: Int? = null
        compose.setContent { MedsReminderTheme { SnoozeSelectionSheet(5, {}, { committed = it }) } }
        compose.onNodeWithText("15 min").performClick()
        assertNull(committed)
        compose.onNodeWithText("Done").performClick()
        assertEquals(15, committed)
    }

    @Test
    fun readinessProjectionCoversRequiredAndFullScreenCombinations() {
        assertEquals(AlarmReadinessState.READY, calculateAlarmReadiness(items(true, true, true, true)))
        assertEquals(AlarmReadinessState.LIMITED, calculateAlarmReadiness(items(true, true, true, false)))
        assertEquals(AlarmReadinessState.NEEDS_ATTENTION, calculateAlarmReadiness(items(false, true, true, true)))
        assertEquals(AlarmReadinessState.NEEDS_ATTENTION, calculateAlarmReadiness(items(true, false, true, true)))
        assertEquals(AlarmReadinessState.NEEDS_ATTENTION, calculateAlarmReadiness(items(true, true, false, true)))
        assertEquals(AlarmReadinessState.NEEDS_ATTENTION, calculateAlarmReadiness(items(false, true, true, false)))
    }

    @Test
    fun soundAndVibrationCallbacksUseExistingOwners() {
        var sounds = 0
        var vibration: Boolean? = null
        compose.setContent { MedsReminderTheme { SettingsScreen(items(), "Morning Bell", true, 5, {}, {}, { sounds++ }, { vibration = it }, {}) } }
        compose.onNodeWithText("Morning Bell").assertIsDisplayed()
        compose.onNodeWithText("Alarm sound").performClick()
        compose.onNodeWithContentDescription("Vibration").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(1, sounds); assertEquals(false, vibration) }
    }

    @Test
    fun readyDetailKeepsAllCapabilityRowsAndTextualReadyState() {
        compose.setContent { MedsReminderTheme { AlarmReadinessScreen(items(), false, {}, {}) } }
        compose.onNodeWithText("Everything is ready").assertIsDisplayed()
        compose.onAllNodesWithText("Ready").assertCountEquals(4)
        listOf("Notifications", "Alarm notifications", "Exact alarms", "Full-screen alarm").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun requiredAndFullScreenRemediationInvokeTheirOwnCallbacks() {
        var required = 0
        var fullScreen = 0
        val values = listOf(
            CapabilityItem("Notifications", false, "Permission required", "Allow notifications", { required++ }),
            CapabilityItem("Alarm notifications", true, "OK", "", {}),
            CapabilityItem("Exact alarms", true, "OK", "", {}),
            CapabilityItem("Full-screen alarm", false, "Full-screen access unavailable", "Open full-screen access", { fullScreen++ }, false),
        )
        compose.setContent { MedsReminderTheme { AlarmReadinessScreen(values, false, {}, {}) } }
        compose.onAllNodesWithText("Needs attention").assertCountEquals(1)
        compose.onNodeWithText("Allow notifications").performClick()
        compose.onNodeWithText("Open full-screen access").performClick()
        compose.runOnIdle { assertEquals(1, required); assertEquals(1, fullScreen) }
    }

    @Test
    fun fullScreenOnlyFailureUsesLimitedAndCurrentExplanation() {
        val values = listOf(
            CapabilityItem("Notifications", true, "Notifications are allowed.", "", {}),
            CapabilityItem("Alarm notifications", true, "Actionable alarm notifications are allowed.", "", {}),
            CapabilityItem("Exact alarms", true, "Exact alarms are allowed.", "", {}),
            CapabilityItem("Full-screen alarm", false, "Full-screen presentation is unavailable.", "Open full-screen access", {}, false),
        )
        compose.setContent { MedsReminderTheme { AlarmReadinessScreen(values, false, {}, {}) } }
        compose.onNodeWithText("Alarm setup is limited").assertIsDisplayed()
        compose.onAllNodesWithText("Limited").assertCountEquals(1)
        compose.onNodeWithText("Full-screen alarm").assertIsDisplayed()
        compose.onNodeWithText("Actionable alarm notifications remain available, but full-screen presentation is unavailable.").assertIsDisplayed()
        compose.onNodeWithText("Open full-screen access").assertHasClickAction()
    }

    @Test
    fun snoozeUsesSelectedTemporaryChoiceAndCommitsOnlyDone() {
        var commits = 0
        var value: Int? = null
        compose.setContent { MedsReminderTheme { SnoozeSelectionSheet(5, {}, { commits++; value = it }) } }
        assertSheetChoiceSelected("5 min")
        listOf("5 min", "10 min", "15 min", "30 min").forEach { sheetChoice(it).assertIsDisplayed() }
        sheetChoice("15 min").performClick()
        assertSheetChoiceSelected("15 min")
        compose.runOnIdle { assertEquals(0, commits) }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(1, commits); assertEquals(15, value) }
    }

    @Test
    fun backAffordancesAreMeaningful() {
        compose.setContent { MedsReminderTheme { SettingsScreen(items(), "System default", true, 5, {}, {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Back").assertHasClickAction()
    }

    @Test
    fun requiredReadinessUsesDestinationHeadingTextAndRemediationSemantics() {
        var remediation = 0
        val values = listOf(
            CapabilityItem("Notifications", false, "Permission required", "Allow notifications", { remediation++ }),
            CapabilityItem("Alarm notifications", true, "Ready", "", {}),
            CapabilityItem("Exact alarms", true, "Ready", "", {}),
            CapabilityItem("Full-screen alarm", true, "Ready", "", {}, false),
        )
        compose.setContent { MedsReminderTheme { AlarmReadinessScreen(values, false, {}, {}) } }

        compose.onNodeWithText("Alarm readiness").assertIsDisplayed()
        compose.onAllNodesWithText("Needs attention").assertCountEquals(1)
        compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onNodeWithText("Allow notifications").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Back").assertHasClickAction()
        compose.runOnIdle { assertEquals(1, remediation) }
    }

    @Test
    fun samsungGuidanceDoesNotChangeTheReadinessProjection() {
        val values = items(fullScreen = false)
        assertEquals(AlarmReadinessState.LIMITED, calculateAlarmReadiness(values))
        var showSamsungGuidance by mutableStateOf(false)
        compose.setContent {
            MedsReminderTheme {
                AlarmReadinessScreen(values, showSamsungGuidance, {}, {})
            }
        }

        compose.onAllNodesWithText("Limited").assertCountEquals(1)
        compose.runOnIdle { showSamsungGuidance = true }
        compose.onAllNodesWithText("Limited").assertCountEquals(1)
        compose.onNodeWithText("Samsung unlocked alarm actions").assertIsDisplayed()
        assertEquals(AlarmReadinessState.LIMITED, calculateAlarmReadiness(values))
    }

    @Test
    fun settingsAlarmControlsUseTheM27SnoozeSheetFlow() {
        var sounds = 0
        var vibration: Boolean? = null
        var snoozeCommits = 0
        var committedMinutes: Int? = null
        compose.setContent {
            MedsReminderTheme {
                SettingsScreen(
                    items(), "Morning Bell", true, 5,
                    {}, {}, { sounds++ }, { vibration = it }, { minutes ->
                        snoozeCommits++
                        committedMinutes = minutes
                    },
                )
            }
        }

        compose.onNodeWithText("Morning Bell").assertIsDisplayed()
        compose.onNodeWithText("Alarm sound").performClick()
        compose.onNodeWithContentDescription("Vibration").performClick()
        compose.onNodeWithText("Default Snooze").assertIsDisplayed().performClick()
        assertSheetChoiceSelected("5 min")
        listOf("5 min", "10 min", "15 min", "30 min").forEach { sheetChoice(it).assertIsDisplayed() }
        compose.onAllNodesWithText("20 min").assertCountEquals(0)
        sheetChoice("15 min").performClick()
        assertSheetChoiceSelected("15 min")
        compose.runOnIdle {
            assertEquals(1, sounds)
            assertEquals(false, vibration)
            assertEquals(0, snoozeCommits)
        }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(1, snoozeCommits); assertEquals(15, committedMinutes) }
    }

    @Test
    fun snoozeTemporaryChoiceIsDiscardedWhenBackDismissesTheSheet() {
        var commits = 0
        compose.setContent {
            MedsReminderTheme {
                SettingsScreen(items(), "System default", true, 5, {}, {}, {}, {}, { commits++ })
            }
        }

        compose.onNodeWithText("Default Snooze").performClick()
        sheetChoice("30 min").performClick()
        assertSheetChoiceSelected("30 min")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("Default Snooze").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, commits) }
    }

    @Test
    fun largeTextSettingsAndReadinessControlsRemainReachable() {
        var readinessRemediations = 0
        var snoozeCommits = 0
        var committedSnooze: Int? = null
        var showReadiness by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                if (showReadiness) {
                    Box(Modifier.width(360.dp).testTag("readiness-viewport")) {
                        AlarmReadinessScreen(
                            listOf(
                                CapabilityItem("Notifications", false, "Notification permission is required before medication alarms can be delivered.", "Allow notifications in system settings", { readinessRemediations++ }),
                                CapabilityItem("Alarm notifications", true, "Ready", "", {}),
                                CapabilityItem("Exact alarms", true, "Ready", "", {}),
                                CapabilityItem("Full-screen alarm", true, "Ready", "", {}, false),
                            ),
                            false, {}, {},
                        )
                    }
                } else {
                    Box(Modifier.width(360.dp).testTag("settings-viewport")) {
                        SettingsScreen(
                            items(notifications = false), "Morning Bell", true, 5,
                            {}, {}, {}, {}, { minutes ->
                                snoozeCommits++
                                committedSnooze = minutes
                            },
                        )
                    }
                }
            }
        }

        val viewport = compose.onNodeWithTag("settings-viewport").getUnclippedBoundsInRoot()
        val snooze = compose.onNodeWithText("Default Snooze")
        snooze.assertHasClickAction()
        val snoozeBounds = snooze.getUnclippedBoundsInRoot()
        assertTrue(snoozeBounds.left >= viewport.left && snoozeBounds.right <= viewport.right)
        compose.onNodeWithText("Default Snooze").performClick()
        listOf("5 min", "10 min", "15 min", "30 min").forEach { sheetChoice(it).assertIsDisplayed() }
        sheetChoice("30 min").performClick()
        assertSheetChoiceSelected("30 min")
        compose.runOnIdle { assertEquals(0, snoozeCommits) }
        compose.onNodeWithText("Done").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, snoozeCommits); assertEquals(30, committedSnooze) }

        compose.runOnIdle { showReadiness = true }
        compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onAllNodesWithText("Needs attention").assertCountEquals(1)
        compose.onNodeWithText("Notification permission is required before medication alarms can be delivered.").assertIsDisplayed()
        compose.onNodeWithText("Allow notifications in system settings").assertHasClickAction().performClick()
        val back = compose.onNodeWithContentDescription("Back")
        back.assertHasClickAction()
        val backBounds = back.getUnclippedBoundsInRoot()
        assertTrue(backBounds.right - backBounds.left >= 48.dp && backBounds.bottom - backBounds.top >= 48.dp)
        compose.runOnIdle { assertEquals(1, readinessRemediations) }
    }

    private fun items(notifications: Boolean = true, channel: Boolean = true, exact: Boolean = true, fullScreen: Boolean = true) = listOf(
        CapabilityItem("Notifications", notifications, "Ready", "Open", {}),
        CapabilityItem("Alarm notifications", channel, "Ready", "Open", {}),
        CapabilityItem("Exact alarms", exact, "Ready", "Open", {}),
        CapabilityItem("Full-screen alarm", fullScreen, "Ready", "Open", {}, false),
    )

    private fun sheetChoice(label: String) = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))

    private fun assertSheetChoiceSelected(label: String) {
        val selected = compose.onAllNodes(isSelected(), useUnmergedTree = true)
        selected.assertCountEquals(1)
        val selectedBounds = selected[0].getUnclippedBoundsInRoot()
        val choiceBounds = sheetChoice(label).getUnclippedBoundsInRoot()
        assertTrue(selectedBounds.top < choiceBounds.bottom && choiceBounds.top < selectedBounds.bottom)
    }
}
