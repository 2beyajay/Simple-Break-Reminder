package com.example.simplebreakreminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager

    private val preferences by lazy {
        getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    }

    private var intervalMinutes = DEFAULT_INTERVAL_MINUTES
    private var nextReminderAt = 0L

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (nextReminderAt == 0L) return

            val now = System.currentTimeMillis()
            if (now >= nextReminderAt) {
                showReminderNotification()
                nextReminderAt = now + intervalMinutes * MILLIS_PER_MINUTE
                saveTimerState()
            }

            updateOngoingNotification()
            handler.postDelayed(this, UPDATE_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopReminderService()
                return START_NOT_STICKY
            }

            ACTION_SKIP -> {
                if (nextReminderAt != 0L) {
                    nextReminderAt =
                        System.currentTimeMillis() + intervalMinutes * MILLIS_PER_MINUTE
                    saveTimerState()
                    updateOngoingNotification()
                }
                return START_STICKY
            }

            ACTION_START -> {
                intervalMinutes = (intent.getIntExtra(
                    EXTRA_INTERVAL_MINUTES,
                    DEFAULT_INTERVAL_MINUTES
                )).coerceIn(1, 240)

                nextReminderAt =
                    System.currentTimeMillis() + intervalMinutes * MILLIS_PER_MINUTE
                saveTimerState()
            }

            else -> {
                // Restore the timer if Android recreates this service.
                intervalMinutes = preferences.getInt(
                    KEY_INTERVAL_MINUTES,
                    DEFAULT_INTERVAL_MINUTES
                ).coerceIn(1, 240)
                nextReminderAt = preferences.getLong(KEY_NEXT_REMINDER_AT, 0L)

                if (nextReminderAt == 0L) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        startAsForegroundService()
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        return START_STICKY
    }

    private fun startAsForegroundService() {
        startForeground(
            ONGOING_NOTIFICATION_ID,
            buildOngoingNotification()
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            "Reminder timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the break reminder countdown"
            setSound(null, null)
            enableVibration(false)
        }

        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Break reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when it is time to take a break"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                alarmAttributes
            )
            enableVibration(true)
        }

        notificationManager.createNotificationChannel(statusChannel)
        notificationManager.createNotificationChannel(reminderChannel)
    }

    private fun buildOngoingNotification(): Notification {
        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Break reminders are on")
            .setContentText(countdownText())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_next,
                "Skip",
                servicePendingIntent(ACTION_SKIP, REQUEST_SKIP)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                servicePendingIntent(ACTION_STOP, REQUEST_STOP)
            )
            .build()
    }

    private fun updateOngoingNotification() {
        notificationManager.notify(
            ONGOING_NOTIFICATION_ID,
            buildOngoingNotification()
        )
    }

    private fun showReminderNotification() {
        val notification = NotificationCompat.Builder(this, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time for a break")
            .setContentText("Take a short break, then get back to it.")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun countdownText(): String {
        val remainingSeconds = (
                (nextReminderAt - System.currentTimeMillis()).coerceAtLeast(0L) + 999L
                ) / 1000L
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        return "Next break in %02d:%02d".format(minutes, seconds)
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ReminderService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun saveTimerState() {
        preferences.edit()
            .putInt(KEY_INTERVAL_MINUTES, intervalMinutes)
            .putLong(KEY_NEXT_REMINDER_AT, nextReminderAt)
            .putBoolean(KEY_RUNNING, true)
            .apply()
    }

    private fun stopReminderService() {
        handler.removeCallbacks(updateRunnable)
        preferences.edit()
            .putLong(KEY_NEXT_REMINDER_AT, 0L)
            .putBoolean(KEY_RUNNING, false)
            .apply()
        notificationManager.cancel(ONGOING_NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.simplebreakreminder.START"
        const val ACTION_SKIP = "com.example.simplebreakreminder.SKIP"
        const val ACTION_STOP = "com.example.simplebreakreminder.STOP"
        const val EXTRA_INTERVAL_MINUTES = "interval_minutes"

        private const val PREFERENCES_FILE = "break_reminder_preferences"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_NEXT_REMINDER_AT = "next_reminder_at"
        private const val KEY_RUNNING = "reminders_running"

        private const val DEFAULT_INTERVAL_MINUTES = 20
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val UPDATE_INTERVAL_MILLIS = 1_000L

        private const val STATUS_CHANNEL_ID = "reminder_timer"
        private const val REMINDER_CHANNEL_ID = "break_reminders"
        private const val ONGOING_NOTIFICATION_ID = 1001
        private const val REMINDER_NOTIFICATION_ID = 1002
        private const val REQUEST_SKIP = 2001
        private const val REQUEST_STOP = 2002
    }
}