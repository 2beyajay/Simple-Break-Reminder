package com.ajaydubey.simplebreakreminder

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

    var isRunning: Boolean
        get() = preferences.getBoolean(KEY_RUNNING, false)
        set(value) {
            preferences.edit().putBoolean(KEY_RUNNING, value).apply()
        }

    var nextReminderAtMillis: Long
        get() = preferences.getLong(KEY_NEXT_REMINDER_AT, 0L)
        set(value) {
            preferences.edit().putLong(KEY_NEXT_REMINDER_AT, value).apply()
        }

    var isReminderPending: Boolean
        get() = preferences.getBoolean(KEY_REMINDER_PENDING, false)
        set(value) {
            preferences.edit().putBoolean(KEY_REMINDER_PENDING, value).apply()
        }

    var soundEnabled: Boolean
        get() = preferences.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
        }

    var vibrationEnabled: Boolean
        get() = preferences.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()
        }

    var reminderAutoDismissAtMillis: Long
        get() = preferences.getLong(KEY_AUTO_DISMISS_AT, 0L)
        set(value) {
            preferences.edit().putLong(KEY_AUTO_DISMISS_AT, value).apply()
        }

    var consecutiveMissedReminders: Int
        get() = preferences.getInt(KEY_CONSECUTIVE_MISSES, 0)
        set(value) {
            preferences.edit()
                .putInt(KEY_CONSECUTIVE_MISSES, value.coerceAtLeast(0))
                .apply()
        }

    private companion object {
        const val PREFERENCES_FILE = "break_reminder_preferences"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val KEY_RUNNING = "reminders_running"
        const val KEY_NEXT_REMINDER_AT = "next_reminder_at"
        const val KEY_REMINDER_PENDING = "reminder_pending"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_AUTO_DISMISS_AT = "reminder_auto_dismiss_at"
        const val KEY_CONSECUTIVE_MISSES = "consecutive_missed_reminders"
        const val DEFAULT_INTERVAL_MINUTES = 20
    }
}