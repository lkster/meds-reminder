package com.example.medsreminder.alarm

import android.content.Context
import android.net.Uri
import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmPreferencesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val preferences
        get() = context.getSharedPreferences(AlarmPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun defaultsPreserveM2AlarmBehavior() {
        val snapshot = AlarmPreferences.read(context)

        assertNull(snapshot.selectedSoundUri)
        assertTrue(snapshot.vibrationEnabled)
        assertEquals(5, snapshot.snoozeMinutes)
    }

    @Test
    fun writesAndReadsGlobalPreferences() {
        val selected = Uri.parse("content://media/internal/audio/media/42")

        AlarmPreferences.setSoundUri(context, selected)
        AlarmPreferences.setVibrationEnabled(context, false)
        AlarmPreferences.setSnoozeMinutes(context, 15)

        val snapshot = AlarmPreferences.read(context)
        assertEquals(selected, snapshot.selectedSoundUri)
        assertFalse(snapshot.vibrationEnabled)
        assertEquals(15, snapshot.snoozeMinutes)
    }

    @Test
    fun invalidStoredValuesFallBackIndependently() {
        preferences.edit()
            .putString(AlarmPreferences.KEY_SOUND_URI, "not-a-uri")
            .putString(AlarmPreferences.KEY_VIBRATION_ENABLED, "false")
            .putString(AlarmPreferences.KEY_SNOOZE_MINUTES, "30")
            .commit()

        val snapshot = AlarmPreferences.read(context)
        assertNull(snapshot.selectedSoundUri)
        assertTrue(snapshot.vibrationEnabled)
        assertEquals(5, snapshot.snoozeMinutes)

        preferences.edit().putInt(AlarmPreferences.KEY_SNOOZE_MINUTES, 7).commit()
        assertEquals(5, AlarmPreferences.read(context).snoozeMinutes)
    }

    @Test
    fun systemDefaultIsCanonicalizedAndCandidatesAreBoundedAndDistinct() {
        val explicit = Uri.parse("content://media/internal/audio/media/42")
        val default = Settings.System.DEFAULT_ALARM_ALERT_URI

        AlarmPreferences.setSoundUri(context, explicit)
        AlarmPreferences.setSoundUri(context, default)

        assertNull(AlarmPreferences.read(context).selectedSoundUri)
        assertEquals(listOf(explicit, default), AlarmPreferences.soundCandidates(explicit, default))
        assertEquals(listOf(default), AlarmPreferences.soundCandidates(default, default))
        assertEquals(emptyList<Uri>(), AlarmPreferences.soundCandidates(null, null))
    }
}
