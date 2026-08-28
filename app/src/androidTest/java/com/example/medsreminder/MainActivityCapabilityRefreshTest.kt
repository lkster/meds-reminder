package com.example.medsreminder

import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class MainActivityCapabilityRefreshTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun fullScreenAccessRefreshesWhenTheSameActivityResumes() {
        assumeTrue("M7 lifecycle proof requires API 34+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val packageName = context.packageName
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val originalRawMode = rawFsiMode(packageName)
        val originalEffectiveState = notificationManager.canUseFullScreenIntent()
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            setFsiMode(packageName, "deny")
            assertFalse(notificationManager.canUseFullScreenIntent())

            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.waitForIdle()
            compose.onNodeWithText("Full-screen alarm").assertExists()
            compose.onNodeWithText(
                "Reminders can still use the actionable alarm notification, but the full-screen alarm may not appear over the lock screen.",
            ).assertExists()
            compose.onNodeWithText("Open full-screen access").assertExists()

            var originalActivity: MainActivity? = null
            scenario.onActivity { originalActivity = it }
            scenario.moveToState(Lifecycle.State.STARTED)

            setFsiMode(packageName, "allow")
            assertTrue(notificationManager.canUseFullScreenIntent())

            scenario.moveToState(Lifecycle.State.RESUMED)
            var resumedActivity: MainActivity? = null
            scenario.onActivity { resumedActivity = it }
            assertSame(originalActivity, resumedActivity)

            compose.waitForIdle()
            compose.onNodeWithText("Open full-screen access").assertDoesNotExist()
            compose.onNodeWithText(
                "Reminders can still use the actionable alarm notification, but the full-screen alarm may not appear over the lock screen.",
            ).assertDoesNotExist()
        } finally {
            scenario?.close()
            setFsiMode(packageName, originalRawMode)
            assertTrue(
                "FSI AppOp mode was not restored: ${rawFsiMode(packageName)}",
                rawFsiMode(packageName) == originalRawMode,
            )
            assertTrue(
                "Effective FSI state was not restored",
                notificationManager.canUseFullScreenIntent() == originalEffectiveState,
            )
        }
    }

    private fun setFsiMode(packageName: String, mode: String) {
        shell("appops set --uid $packageName USE_FULL_SCREEN_INTENT $mode")
    }

    private fun rawFsiMode(packageName: String): String {
        val output = shell("appops get --uid $packageName USE_FULL_SCREEN_INTENT")
        val match = Regex("USE_FULL_SCREEN_INTENT\\s*:\\s*(allow|ignore|deny|default)")
            .find(output.lowercase())
            ?.groupValues
            ?.get(1)
        return match ?: if (output.contains("No operations", ignoreCase = true)) "default" else {
            error("Could not parse USE_FULL_SCREEN_INTENT AppOp mode: $output")
        }
    }

    private fun shell(command: String): String {
        return InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }
}
