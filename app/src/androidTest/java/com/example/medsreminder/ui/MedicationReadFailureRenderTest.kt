package com.example.medsreminder.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.ReminderTimeEntity
import com.example.medsreminder.data.WeekdayMask
import com.example.medsreminder.ui.theme.MedsReminderTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic device-side M41 fixtures; each image is written to app external files. */
@RunWith(AndroidJUnit4::class)
class MedicationReadFailureRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun capturesInitialMedicationLibraryReadFailure() {
        capture("m41-medications-read-error.png", "m41-library-read-error") {
            MedicationListScreen(
                medications = null, onHistory = {}, onSettings = {}, onAdd = {},
                onOpenDetails = {}, onToggle = { _, _ -> }, onDelete = {}, readFailed = true,
            )
        }
    }

    @Test
    fun capturesMedicationLibraryRefreshFailure() {
        capture("m41-medications-refresh-error.png", "m41-library-refresh-error") {
            MedicationListScreen(
                medications = listOf(renderMedication()), onHistory = {}, onSettings = {}, onAdd = {},
                onOpenDetails = {}, onToggle = { _, _ -> }, onDelete = {}, readFailed = true,
            )
        }
    }

    @Test
    fun capturesInitialMedicationDetailsReadFailureAtLargeText() {
        capture("m41-medication-details-read-error-large-text.png", "m41-details-read-error-large-text") {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                MedicationDetailsScreen(null, {}, {}, { _, _ -> }, {}, readFailed = true)
            }
        }
    }

    private fun capture(fileName: String, tag: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            MedsReminderTheme {
                Box(Modifier.width(360.dp).fillMaxSize().testTag(tag)) { content() }
            }
        }
        writeCapture(fileName, tag)
    }

    private fun writeCapture(fileName: String, tag: String) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(targetContext.getExternalFilesDir(null), fileName)
        if (output.exists()) check(output.delete()) { "Could not replace $output" }
        FileOutputStream(output).use { stream ->
            check(
                compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, stream),
            ) { "Could not encode $output" }
        }
        assertTrue(output.exists() && output.length() > 0L)
    }

    private fun renderMedication() = MedicationWithTimes(
        MedicationEntity(941L, "M41 render medication", "Take with water", true),
        listOf(ReminderTimeEntity(942L, 941L, 8 * 60, WeekdayMask.ALL)),
    )
}
