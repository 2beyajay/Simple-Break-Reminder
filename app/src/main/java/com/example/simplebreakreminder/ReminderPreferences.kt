package com.example.simplebreakreminder

import android.content.Context

class ReminderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_FILE,
        Context.MODE_PRIVATE
    )

    var intervalMinutes: Int
        get() = preferences.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        set(value) {
            preferences.edit()
                .putInt(KEY_INTERVAL_MINUTES, value.coerceIn(1, 240))
                .apply()
        }

    private companion object {
        const val PREFERENCES_FILE = "break_reminder_preferences"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val DEFAULT_INTERVAL_MINUTES = 20
    }
}