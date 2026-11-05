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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager

    private val reminderPreferences by lazy {
        ReminderPreferences(this)
    }

    private var intervalMinutes = DEFAULT_INTERVAL_MINUTES
    private var nextReminderAt = 0L
    private var reminderPending = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (nextReminderAt == 0L) return

            val now = System.currentTimeMillis()

            // Keep the reminder pending until the user dismisses it.
            if (!reminderPending && now >= nextReminderAt) {
                reminderPending = true
                saveTimerState()
                showReminderNotification()
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
                if (nextReminderAt != 0L && !reminderPending) {
                    // Add an interval to the existing deadline.
                    nextReminderAt += intervalMinutes * MILLIS_PER_MINUTE
                    saveTimerState()
                    updateOngoingNotification()
                }
                return START_STICKY
            }

            ACTION_DISMISS -> {
                dismissReminderAndStartNextInterval()
                return START_STICKY
            }

            ACTION_START -> {
                intervalMinutes = intent.getIntExtra(
                    EXTRA_INTERVAL_MINUTES,
                    DEFAULT_INTERVAL_MINUTES
                ).coerceIn(1, 240)
                reminderPending = false
                notificationManager.cancel(REMINDER_NOTIFICATION_ID)
                nextReminderAt =
                    System.currentTimeMillis() + intervalMinutes * MILLIS_PER_MINUTE
                saveTimerState()
            }

            else -> {
                // Restore saved timer state if Android recreates the service.
                intervalMinutes = reminderPreferences.intervalMinutes
                    .coerceIn(1, 240)
                nextReminderAt = reminderPreferences.nextReminderAtMillis
                reminderPending = reminderPreferences.isReminderPending

                if (nextReminderAt == 0L) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        startForeground(
            ONGOING_NOTIFICATION_ID,
            buildOngoingNotification()
        )
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        return START_STICKY
    }

    private fun dismissReminderAndStartNextInterval() {
        if (!reminderPending) return

        reminderPending = false
        notificationManager.cancel(REMINDER_NOTIFICATION_ID)
        nextReminderAt =
            System.currentTimeMillis() + intervalMinutes * MILLIS_PER_MINUTE

        saveTimerState()
        updateOngoingNotification()
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
        notificationManager.createNotificationChannel(statusChannel)

        createReminderChannel(
            channelId = reminderChannelId(sound = true, vibration = true),
            channelName = "Break reminders: sound and vibration",
            soundEnabled = true,
            vibrationEnabled = true
        )
        createReminderChannel(
            channelId = reminderChannelId(sound = true, vibration = false),
            channelName = "Break reminders: sound only",
            soundEnabled = true,
            vibrationEnabled = false
        )
        createReminderChannel(
            channelId = reminderChannelId(sound = false, vibration = true),
            channelName = "Break reminders: vibration only",
            soundEnabled = false,
            vibrationEnabled = true
        )
        createReminderChannel(
            channelId = reminderChannelId(sound = false, vibration = false),
            channelName = "Break reminders: silent",
            soundEnabled = false,
            vibrationEnabled = false
        )
    }

    private fun createReminderChannel(
        channelId: String,
        channelName: String,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when it is time to take a break"
            enableVibration(vibrationEnabled)

            if (soundEnabled) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    alarmAttributes
                )
            } else {
                setSound(null, null)
            }
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun reminderChannelId(sound: Boolean, vibration: Boolean): String {
        return when {
            sound && vibration -> REMINDER_CHANNEL_SOUND_VIBRATION
            sound -> REMINDER_CHANNEL_SOUND_ONLY
            vibration -> REMINDER_CHANNEL_VIBRATION_ONLY
            else -> REMINDER_CHANNEL_SILENT
        }
    }

    private fun buildOngoingNotification(): Notification {
        val statusText = if (reminderPending) {
            "Reminder waiting to be dismissed"
        } else {
            countdownText()
        }

        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Break reminders are on")
            .setContentText(statusText)
            .setContentIntent(openMainScreenPendingIntent(REQUEST_OPEN_MAIN))
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
        val channelId = reminderChannelId(
            sound = reminderPreferences.soundEnabled,
            vibration = reminderPreferences.vibrationEnabled
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time for a break")
            .setContentText("Dismiss this reminder when you're ready to continue.")
            .setContentIntent(openMainScreenPendingIntent(REQUEST_OPEN_REMINDER))
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                servicePendingIntent(ACTION_DISMISS, REQUEST_DISMISS)
            )
            .build()

        notificationManager.notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun countdownText(): String {
        val remainingSeconds = (
                (nextReminderAt - System.currentTimeMillis()).coerceAtLeast(0L) + 999L
                ) / 1_000L
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

    private fun openMainScreenPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun saveTimerState() {
        reminderPreferences.intervalMinutes = intervalMinutes
        reminderPreferences.nextReminderAtMillis = nextReminderAt
        reminderPreferences.isReminderPending = reminderPending
        reminderPreferences.isRunning = true
    }

    private fun stopReminderService() {
        handler.removeCallbacks(updateRunnable)
        reminderPreferences.nextReminderAtMillis = 0L
        reminderPreferences.isReminderPending = false
        reminderPreferences.isRunning = false
        notificationManager.cancel(ONGOING_NOTIFICATION_ID)
        notificationManager.cancel(REMINDER_NOTIFICATION_ID)
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
        const val ACTION_DISMISS = "com.example.simplebreakreminder.DISMISS"
        const val EXTRA_INTERVAL_MINUTES = "interval_minutes"

        private const val DEFAULT_INTERVAL_MINUTES = 20
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val UPDATE_INTERVAL_MILLIS = 1_000L

        private const val STATUS_CHANNEL_ID = "reminder_timer"
        private const val REMINDER_CHANNEL_SOUND_VIBRATION =
            "break_reminders_sound_vibration"
        private const val REMINDER_CHANNEL_SOUND_ONLY =
            "break_reminders_sound_only"
        private const val REMINDER_CHANNEL_VIBRATION_ONLY =
            "break_reminders_vibration_only"
        private const val REMINDER_CHANNEL_SILENT =
            "break_reminders_silent"

        private const val ONGOING_NOTIFICATION_ID = 1001
        private const val REMINDER_NOTIFICATION_ID = 1002
        private const val REQUEST_SKIP = 2001
        private const val REQUEST_STOP = 2002
        private const val REQUEST_DISMISS = 2003
        private const val REQUEST_OPEN_MAIN = 2004
        private const val REQUEST_OPEN_REMINDER = 2005
    }
}