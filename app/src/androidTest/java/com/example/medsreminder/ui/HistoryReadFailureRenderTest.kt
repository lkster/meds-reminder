package com.example.medsreminder.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.medsreminder.ui.theme.MedsReminderTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic device-side fixture for the M40 initial History read-failure render. */
@RunWith(AndroidJUnit4::class)
class HistoryReadFailureRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun capturesInitialHistoryReadFailure() {
        compose.setContent {
            MedsReminderTheme {
                Box(
                    Modifier
                        .width(360.dp)
                        .fillMaxSize()
                        .testTag("m40-history-read-error-viewport"),
                ) {
                    HistoryScreen(
                        history = null,
                        onBack = {},
                        onMedicationDetails = {},
                        readFailed = true,
                    )
                }
            }
        }

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(targetContext.getExternalFilesDir(null), "m40-history-read-error.png")
        if (output.exists()) check(output.delete()) { "Could not replace $output" }
        FileOutputStream(output).use { stream ->
            check(
                compose.onNodeWithTag("m40-history-read-error-viewport")
                    .captureToImage()
                    .asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, stream),
            ) { "Could not encode $output" }
        }
        assertTrue(output.exists() && output.length() > 0L)
    }
}

