package com.example.medsreminder.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class MedsReminderThemeTest {
    @Test
    fun semanticPalettesAreFrozenAndDistinct() {
        assertEquals(Color(0xFFF7FAF9), MedsReminderPalette.light.background)
        assertEquals(Color(0xFF006B5A), MedsReminderPalette.light.accent)
        assertEquals(Color(0xFF101512), MedsReminderPalette.dark.background)
        assertEquals(Color(0xFF6FDCC2), MedsReminderPalette.dark.accent)
        assertNotEquals(Color.Black, MedsReminderPalette.dark.background)
        assertNotEquals(MedsReminderPalette.dark.surface, MedsReminderPalette.dark.surfaceContainer)
        assertNotEquals(MedsReminderPalette.dark.surfaceContainer, MedsReminderPalette.dark.surfaceElevated)
    }

    @Test
    fun alarmPaletteIsFixedDarkAndReadable() {
        val alarm = MedsReminderPalette.alarm
        assertNotEquals(Color.Black, alarm.background)
        assertNotEquals(alarm.surface, alarm.surfaceElevated)
        assertNotEquals(MedsReminderPalette.light.background, alarm.background)
        assertTrue(contrast(alarm.textPrimary, alarm.background) >= 4.5)
        assertTrue(contrast(alarm.onAccent, alarm.accent) >= 4.5)
        assertTrue(contrast(alarm.borderSubtle, alarm.surface) >= 3.0)
    }

    @Test
    fun importantTextPairsMeetContrastRequirement() {
        assertTrue(contrast(MedsReminderPalette.light.textPrimary, MedsReminderPalette.light.background) >= 4.5)
        assertTrue(contrast(MedsReminderPalette.light.textSecondary, MedsReminderPalette.light.surface) >= 4.5)
        assertTrue(contrast(MedsReminderPalette.light.onAccent, MedsReminderPalette.light.accent) >= 4.5)
        assertTrue(contrast(MedsReminderPalette.dark.textPrimary, MedsReminderPalette.dark.background) >= 4.5)
        assertTrue(contrast(MedsReminderPalette.dark.textSecondary, MedsReminderPalette.dark.surface) >= 4.5)
        assertTrue(contrast(MedsReminderPalette.dark.onAccent, MedsReminderPalette.dark.accent) >= 4.5)
    }

    @Test
    fun onSurfaceStatusTextContrastsWithEachEffectiveStatusContainer() {
        listOf(MedsReminderPalette.light, MedsReminderPalette.dark).forEach { palette ->
            listOf(palette.success, palette.warning, palette.error).forEach { status ->
                assertTrue(contrast(palette.textPrimary, composite(status, palette.surface, 0.10f)) >= 4.5)
            }
        }
    }

    private fun composite(foreground: Color, background: Color, alpha: Float): Color = Color(
        foreground.red * alpha + background.red * (1 - alpha),
        foreground.green * alpha + background.green * (1 - alpha),
        foreground.blue * alpha + background.blue * (1 - alpha),
    )

    private fun contrast(foreground: Color, background: Color): Double {
        fun linear(channel: Float): Double = if (channel <= 0.04045f) channel / 12.92 else ((channel + 0.055) / 1.055).toDouble().pow(2.4)
        fun luminance(color: Color): Double = 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
