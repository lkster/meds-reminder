package com.example.medsreminder.alarm

import android.content.Context

class AlarmStore(context: Context) {
    private val preferences = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun scheduledAtMillis(): Long? =
        preferences.getLong(KEY_TRIGGER_AT_MILLIS, NO_ALARM)
            .takeIf { it != NO_ALARM }

    fun save(triggerAtMillis: Long) {
        check(preferences.edit().putLong(KEY_TRIGGER_AT_MILLIS, triggerAtMillis).commit()) {
            "Unable to persist alarm timestamp"
        }
    }

    fun clear() {
        check(preferences.edit().remove(KEY_TRIGGER_AT_MILLIS).commit()) {
            "Unable to clear alarm timestamp"
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "m0_alarm"
        private const val KEY_TRIGGER_AT_MILLIS = "trigger_at_millis"
        private const val NO_ALARM = -1L
    }
}
