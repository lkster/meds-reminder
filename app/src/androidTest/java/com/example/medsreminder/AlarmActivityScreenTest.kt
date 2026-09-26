package com.example.medsreminder

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.medsreminder.data.OccurrenceDetails
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.ui.theme.AlarmTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Presentation coverage for the production AlarmScreen decomposition. */
class AlarmActivityScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun suppliedDefaultsAreTruthfulAndSelectorUsesOnlySupportedDurations() {
        val suppliedDefault = mutableStateOf(5)
        compose.setContent {
            AlarmTheme {
                AlarmScreen(
                    occurrence = occurrence(),
                    configuredSnoozeMinutes = suppliedDefault.value,
                    onTaken = {},
                    onSkip = {},
                    onDefaultSnooze = {},
                    onAlternateSnooze = { _, _ -> },
                )
            }
        }
        listOf(5, 10, 15, 30).forEach { defaultMinutes ->
            compose.runOnIdle { suppliedDefault.value = defaultMinutes }
            compose.onNodeWithText("Snooze $defaultMinutes min").assertIsDisplayed().assertHasClickAction()
        }

        compose.onNodeWithContentDescription("More snooze options").assertHasClickAction().performClick()
        listOf(5, 10, 15, 30).forEach { compose.onNodeWithText("$it min").assertIsDisplayed() }
        compose.onNodeWithText("20 min").assertDoesNotExist()
        compose.onNodeWithText("Default snooze").assertIsDisplayed()
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun alternateSelectionSnoozesImmediatelyAndBackDismissesWithoutSnoozing() {
        var selectedMinutes: Int? = null
        render(15, onAlternateSnooze = { selectedMinutes = it })

        compose.onNodeWithContentDescription("More snooze options").performClick()
        compose.onNodeWithText("5 min").performClick()
        compose.runOnIdle { assertEquals(5, selectedMinutes) }

        selectedMinutes = null
        compose.onNodeWithContentDescription("More snooze options").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Snooze options").assertDoesNotExist()
        assertNull(selectedMinutes)
    }

    @Test
    fun normalWidthSnoozePairUsesSeparateWideAndCompactActionTargets() {
        render(10)

        val defaultBounds = compose.onNodeWithText("Snooze 10 min").assertHasClickAction().getUnclippedBoundsInRoot()
        val moreBounds = compose.onNodeWithContentDescription("More snooze options").assertHasClickAction().getUnclippedBoundsInRoot()
        assertTrue(defaultBounds.right - defaultBounds.left >= (moreBounds.right - moreBounds.left) * 3f)
        assertTrue(defaultBounds.right < moreBounds.left)
        assertTrue(moreBounds.right - moreBounds.left >= 48.dp)
        assertTrue(moreBounds.bottom - moreBounds.top >= 48.dp)
        assertTrue(defaultBounds.top <= moreBounds.bottom && moreBounds.top <= defaultBounds.bottom)
    }

    @Test
    fun sheetRowsAreImmediateTargetsAndExplicitCloseDismissesWithoutSnoozing() {
        var selectedMinutes: Int? = null
        render(10, onAlternateSnooze = { selectedMinutes = it })

        compose.onNodeWithContentDescription("More snooze options").performClick()
        listOf(5, 10, 15, 30).forEach { minutes ->
            compose.onNodeWithText("$minutes min").assertIsDisplayed().assertHasClickAction().getUnclippedBoundsInRoot().also { bounds ->
                assertTrue(bounds.bottom - bounds.top >= 48.dp)
            }
        }
        compose.onNodeWithText("Default snooze").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close snooze options").performClick()
        compose.onNodeWithText("Snooze options").assertDoesNotExist()
        assertNull(selectedMinutes)
    }

    @Test
    fun longContentAndLargeTextKeepResolutionControlsReachable() {
        compose.setContent {
            AlarmTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                    Box(Modifier.width(240.dp)) {
                        AlarmScreen(
                            occurrence = occurrence(),
                            configuredSnoozeMinutes = 10,
                            onTaken = {},
                            onSkip = {},
                            onDefaultSnooze = {},
                            onAlternateSnooze = { _, _ -> },
                        )
                    }
                }
            }
        }

        compose.onNodeWithText(occurrence().medicationName).assertIsDisplayed()
        compose.onNodeWithText(requireNotNull(occurrence().instructions)).assertIsDisplayed()
        listOf("Mark as taken", "Snooze 10 min", "More snooze options", "Skip this dose").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasClickAction()
        }
        compose.onNodeWithContentDescription("More snooze options").assertHasClickAction()
    }

    @Test
    fun resolutionControlsHaveTouchTargetsAndFollowContentReadingOrder() {
        render(5)

        val contentBounds = listOf(
            compose.onNodeWithText(occurrence().medicationName),
            compose.onNodeWithText("Scheduled for", substring = true),
            compose.onNodeWithText(requireNotNull(occurrence().instructions)),
        ).map { it.getUnclippedBoundsInRoot() }
        val actionNodes = listOf(
            compose.onNodeWithText("Mark as taken"),
            compose.onNodeWithText("Snooze 5 min"),
            compose.onNodeWithContentDescription("More snooze options"),
            compose.onNodeWithText("Skip this dose"),
        )
        val actionBounds = actionNodes.map { node ->
            node.assertHasClickAction()
            node.getUnclippedBoundsInRoot().also { bounds ->
                assertTrue(bounds.right - bounds.left >= 48.dp)
                assertTrue(bounds.bottom - bounds.top >= 48.dp)
            }
        }

        assertTrue(contentBounds.zipWithNext().all { (first, second) -> second.top >= first.top })
        assertTrue(contentBounds.last().bottom <= actionBounds.first().top)
        assertTrue(actionBounds[0].top <= actionBounds[1].top)
        assertTrue(actionBounds[1].top <= actionBounds[2].bottom && actionBounds[2].top <= actionBounds[1].bottom)
        assertTrue(actionBounds[1].bottom <= actionBounds[3].top)
    }

    private fun render(defaultMinutes: Int, onAlternateSnooze: (Int) -> Unit = {}) {
        compose.setContent {
            AlarmTheme {
                AlarmScreen(
                    occurrence = occurrence(),
                    configuredSnoozeMinutes = defaultMinutes,
                    onTaken = {},
                    onSkip = {},
                    onDefaultSnooze = {},
                    onAlternateSnooze = { _, minutes -> onAlternateSnooze(minutes) },
                )
            }
        }
    }

    private fun occurrence() = OccurrenceDetails(
        occurrenceId = "screen-occurrence",
        reminderTimeId = 1L,
        kind = OccurrenceKind.BASE,
        status = OccurrenceStatus.RINGING,
        scheduledAtEpochMillis = 1_785_960_000_000L,
        presentedAtEpochMillis = null,
        medicationId = 2L,
        medicationName = "Very long medication name that should wrap without losing controls",
        instructions = "Take this complete instruction after a meal; it must wrap rather than ellipsize.",
        minuteOfDay = 8 * 60,
        weekdayMask = 127,
    )
}
