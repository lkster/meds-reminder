package com.example.medsreminder.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings

data class AlarmPreferenceSnapshot(
    val selectedSoundUri: Uri?,
    val vibrationEnabled: Boolean,
    val snoozeMinutes: Int,
)

object AlarmPreferences {
    const val DEFAULT_SNOOZE_MINUTES = 5
    val ALLOWED_SNOOZE_MINUTES = listOf(5, 10, 15, 30)

    internal const val PREFERENCES_NAME = "alarm_behavior"
    internal const val KEY_SOUND_URI = "sound_uri"
    internal const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    internal const val KEY_SNOOZE_MINUTES = "snooze_minutes"

    fun read(context: Context): AlarmPreferenceSnapshot {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val soundUri = runCatching { preferences.getString(KEY_SOUND_URI, null) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?.takeIf { it.scheme != null }
            ?.takeUnless(::isSystemDefaultUri)
        val vibrationEnabled = runCatching {
            preferences.getBoolean(KEY_VIBRATION_ENABLED, true)
        }.getOrDefault(true)
        val snoozeMinutes = runCatching {
            preferences.getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
        }.getOrDefault(DEFAULT_SNOOZE_MINUTES)
            .takeIf(ALLOWED_SNOOZE_MINUTES::contains)
            ?: DEFAULT_SNOOZE_MINUTES
        return AlarmPreferenceSnapshot(soundUri, vibrationEnabled, snoozeMinutes)
    }

    fun setSoundUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (uri == null || isSystemDefaultUri(uri)) remove(KEY_SOUND_URI)
                else putString(KEY_SOUND_URI, uri.toString())
            }
            .apply()
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VIBRATION_ENABLED, enabled)
            .apply()
    }

    fun setSnoozeMinutes(context: Context, minutes: Int) {
        require(minutes in ALLOWED_SNOOZE_MINUTES)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SNOOZE_MINUTES, minutes)
            .apply()
    }

    fun defaultAlarmUri(): Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    fun soundCandidates(selectedSoundUri: Uri?, defaultSoundUri: Uri?): List<Uri> =
        listOfNotNull(selectedSoundUri, defaultSoundUri).distinct()

    fun pickerExistingUri(context: Context): Uri? =
        read(context).selectedSoundUri ?: defaultAlarmUri()

    private fun isSystemDefaultUri(uri: Uri): Boolean =
        uri == Settings.System.DEFAULT_ALARM_ALERT_URI || uri == defaultAlarmUri()
}
