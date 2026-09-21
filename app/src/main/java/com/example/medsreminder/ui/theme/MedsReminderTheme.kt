package com.example.medsreminder.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MedsReminderColors(
    val success: Color,
    val warning: Color,
    val textDisabled: Color,
    val surfaceElevated: Color,
)

val LocalMedsReminderColors = staticCompositionLocalOf {
    MedsReminderColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

object MedsReminderPalette {
    val light = SemanticPalette(
        background = Color(0xFFF7FAF9), surface = Color(0xFFFBFDFC), surfaceElevated = Color.White,
        surfaceContainer = Color(0xFFF0F5F2), surfaceContainerHigh = Color(0xFFE7EFEB),
        textPrimary = Color(0xFF17201D), textSecondary = Color(0xFF52615B), textDisabled = Color(0xFF8A9691),
        borderSubtle = Color(0xFFD5DFDA), accent = Color(0xFF006B5A), accentContainer = Color(0xFFC5F2E5),
        onAccent = Color.White, success = Color(0xFF1B7F4C), warning = Color(0xFFA35F00),
        error = Color(0xFFBA1A1A), onError = Color.White,
    )
    val dark = SemanticPalette(isDark = true,
        background = Color(0xFF101512), surface = Color(0xFF151B18), surfaceElevated = Color(0xFF29332E),
        surfaceContainer = Color(0xFF1B221F), surfaceContainerHigh = Color(0xFF222B27),
        textPrimary = Color(0xFFE9F1ED), textSecondary = Color(0xFFB6C3BC), textDisabled = Color(0xFF7F8C86),
        borderSubtle = Color(0xFF3A4640), accent = Color(0xFF6FDCC2), accentContainer = Color(0xFF005143),
        onAccent = Color(0xFF00382F), success = Color(0xFF6DD58F), warning = Color(0xFFFFB95C),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    )
    /** Fixed alarm environment; this deliberately does not follow the system appearance. */
    val alarm = SemanticPalette(isDark = true,
        background = Color(0xFF071C22), surface = Color(0xFF0D2930), surfaceElevated = Color(0xFF163941),
        surfaceContainer = Color(0xFF102E35), surfaceContainerHigh = Color(0xFF1A3D45),
        textPrimary = Color(0xFFF0F8F8), textSecondary = Color(0xFFC1D1D3), textDisabled = Color(0xFF82989D),
        borderSubtle = Color(0xFF5A858E), accent = Color(0xFF65E2CC), accentContainer = Color(0xFF174E4A),
        onAccent = Color(0xFF003731), success = Color(0xFF79DDA2), warning = Color(0xFFFFC36B),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    )
}

data class SemanticPalette(
    val isDark: Boolean = false,
    val background: Color, val surface: Color, val surfaceElevated: Color,
    val surfaceContainer: Color, val surfaceContainerHigh: Color,
    val textPrimary: Color, val textSecondary: Color, val textDisabled: Color,
    val borderSubtle: Color, val accent: Color, val accentContainer: Color, val onAccent: Color,
    val success: Color, val warning: Color, val error: Color, val onError: Color,
) {
    private fun schemeArguments() = androidx.compose.material3.lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentContainer,
        onPrimaryContainer = textPrimary, background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary, surfaceVariant = surfaceContainer,
        onSurfaceVariant = textSecondary, surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh, outline = borderSubtle,
        outlineVariant = borderSubtle, error = error, onError = onError,
    )

    fun colorScheme(): ColorScheme {
        val values = schemeArguments()
        return if (isDark) androidx.compose.material3.darkColorScheme(
            primary = values.primary, onPrimary = values.onPrimary, primaryContainer = values.primaryContainer,
            onPrimaryContainer = values.onPrimaryContainer, background = values.background, onBackground = values.onBackground,
            surface = values.surface, onSurface = values.onSurface, surfaceVariant = values.surfaceVariant,
            onSurfaceVariant = values.onSurfaceVariant, surfaceContainer = values.surfaceContainer,
            surfaceContainerHigh = values.surfaceContainerHigh, outline = values.outline,
            outlineVariant = values.outlineVariant, error = values.error, onError = values.onError,
        ) else values
    }
}

val MedsReminderTypography = Typography(
    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

val MedsReminderShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun MedsReminderTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val palette = if (darkTheme) MedsReminderPalette.dark else MedsReminderPalette.light
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = palette.background.toArgb()
                window.navigationBarColor = palette.surface.toArgb()
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMedsReminderColors provides MedsReminderColors(
            palette.success, palette.warning, palette.textDisabled, palette.surfaceElevated,
        ),
    ) {
        MaterialTheme(colorScheme = palette.colorScheme(), typography = MedsReminderTypography, shapes = MedsReminderShapes, content = content)
    }
}

/** A dedicated alarm palette so lock-screen alarm presentation is always visually predictable. */
@Composable
fun AlarmTheme(content: @Composable () -> Unit) {
    val palette = MedsReminderPalette.alarm
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = palette.background.toArgb()
                window.navigationBarColor = palette.background.toArgb()
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMedsReminderColors provides MedsReminderColors(
            palette.success, palette.warning, palette.textDisabled, palette.surfaceElevated,
        ),
    ) {
        MaterialTheme(colorScheme = palette.colorScheme(), typography = MedsReminderTypography, shapes = MedsReminderShapes, content = content)
    }
}
