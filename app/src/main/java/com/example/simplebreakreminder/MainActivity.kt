package com.example.simplebreakreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.simplebreakreminder.ui.theme.SimpleBreakReminderTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {

    private var intervalToStartAfterPermission = 20

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startReminderService(intervalToStartAfterPermission)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reminderPreferences = ReminderPreferences(this)

        setContent {
            SimpleBreakReminderTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var intervalText by rememberSaveable {
                    mutableStateOf(reminderPreferences.intervalMinutes.toString())
                }
                var isRunning by remember {
                    mutableStateOf(reminderPreferences.isRunning)
                }
                var isReminderPending by remember {
                    mutableStateOf(reminderPreferences.isReminderPending)
                }
                var nextReminderAt by remember {
                    mutableLongStateOf(reminderPreferences.nextReminderAtMillis)
                }
                var currentTime by remember {
                    mutableLongStateOf(System.currentTimeMillis())
                }
                var soundEnabled by remember {
                    mutableStateOf(reminderPreferences.soundEnabled)
                }
                var vibrationEnabled by remember {
                    mutableStateOf(reminderPreferences.vibrationEnabled)
                }

                BackHandler(enabled = showSettings) {
                    showSettings = false
                }

                LaunchedEffect(Unit) {
                    while (true) {
                        isRunning = reminderPreferences.isRunning
                        isReminderPending = reminderPreferences.isReminderPending
                        nextReminderAt = reminderPreferences.nextReminderAtMillis
                        currentTime = System.currentTimeMillis()
                        soundEnabled = reminderPreferences.soundEnabled
                        vibrationEnabled = reminderPreferences.vibrationEnabled
                        delay(1.seconds)
                    }
                }

                val interval = intervalText.toIntOrNull()
                val isIntervalValid = interval != null && interval in 1..240
                val remainingSeconds =
                    if (isRunning && !isReminderPending && nextReminderAt > 0L) {
                        ((nextReminderAt - currentTime).coerceAtLeast(0L) + 999L) / 1_000L
                    } else {
                        0L
                    }
                val remainingMinutes = remainingSeconds / 60
                val remainingSecondsPart = remainingSeconds % 60

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (showSettings) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            IconButton(
                                onClick = { showSettings = false },
                                modifier = Modifier.semantics {
                                    contentDescription = "Back"
                                }
                            ) {
                                BackChevron()
                            }

                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.headlineMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Sound")
                                    Text(
                                        "Use the default alarm sound",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Switch(
                                    checked = soundEnabled,
                                    onCheckedChange = { enabled ->
                                        soundEnabled = enabled
                                        reminderPreferences.soundEnabled = enabled
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Vibration")

                                Switch(
                                    checked = vibrationEnabled,
                                    onCheckedChange = { enabled ->
                                        vibrationEnabled = enabled
                                        reminderPreferences.vibrationEnabled = enabled
                                    }
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showSettings = true },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Settings"
                                    }
                                ) {
                                    GearIcon()
                                }

                                Text(
                                    text = "Break Reminder",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }

                            OutlinedTextField(
                                value = intervalText,
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() }) {
                                        intervalText = newValue
                                        newValue.toIntOrNull()
                                            ?.takeIf { it in 1..240 }
                                            ?.let {
                                                reminderPreferences.intervalMinutes = it
                                            }
                                    }
                                },
                                label = { Text("Interval in minutes") },
                                supportingText = {
                                    Text(
                                        if (isIntervalValid) {
                                            "Choose from 1 to 240 minutes"
                                        } else {
                                            "Enter a number from 1 to 240"
                                        }
                                    )
                                },
                                isError = !isIntervalValid,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                singleLine = true,
                                enabled = !isRunning
                            )

                            if (isRunning && isReminderPending) {
                                Text(
                                    text = "Time for a break",
                                    style = MaterialTheme.typography.headlineSmall
                                )

                                Button(
                                    onClick = {
                                        sendServiceAction(ReminderService.ACTION_DISMISS)
                                    }
                                ) {
                                    Text("Dismiss reminder")
                                }
                            } else if (isRunning) {
                                Text(
                                    text = "Next reminder in %02d:%02d".format(
                                        remainingMinutes,
                                        remainingSecondsPart
                                    ),
                                    style = MaterialTheme.typography.headlineSmall
                                )

                                Button(
                                    onClick = {
                                        sendServiceAction(ReminderService.ACTION_SKIP)
                                    }
                                ) {
                                    Text("Skip next reminder")
                                }
                            }

                            Button(
                                onClick = {
                                    if (isRunning) {
                                        sendServiceAction(ReminderService.ACTION_STOP)
                                    } else if (interval != null && interval in 1..240) {
                                        if (
                                            Build.VERSION.SDK_INT >= 33 &&
                                            ContextCompat.checkSelfPermission(
                                                this@MainActivity,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            intervalToStartAfterPermission = interval
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        } else {
                                            startReminderService(interval)
                                        }
                                    }
                                },
                                enabled = isRunning || isIntervalValid
                            ) {
                                Text(
                                    if (isRunning) "Stop reminders"
                                    else "Start reminders"
                                )
                            }

                            Text(
                                text = if (isRunning) {
                                    "Reminder mode is on"
                                } else {
                                    "Reminder mode is off"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, ReminderService::class.java).setAction(action)
        startService(intent)
    }

    private fun startReminderService(intervalMinutes: Int) {
        val intent = Intent(this, ReminderService::class.java)
            .setAction(ReminderService.ACTION_START)
            .putExtra(ReminderService.EXTRA_INTERVAL_MINUTES, intervalMinutes)

        ContextCompat.startForegroundService(this, intent)
    }
}

@androidx.compose.runtime.Composable
private fun BackChevron() {
    val iconColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = "Back" }
    ) {
        val strokeWidth = 2.2.dp.toPx()
        val top = Offset(size.width * 0.65f, size.height * 0.18f)
        val middle = Offset(size.width * 0.30f, size.height * 0.50f)
        val bottom = Offset(size.width * 0.65f, size.height * 0.82f)

        drawLine(
            color = iconColor,
            start = top,
            end = middle,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = iconColor,
            start = middle,
            end = bottom,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@androidx.compose.runtime.Composable
private fun GearIcon() {
    val iconColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = "Settings" }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val strokeWidth = 2.dp.toPx()
        val innerRadius = size.minDimension * 0.18f
        val ringRadius = size.minDimension * 0.31f
        val spokeStart = size.minDimension * 0.38f
        val spokeEnd = size.minDimension * 0.48f

        drawCircle(
            color = iconColor,
            radius = ringRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = iconColor,
            radius = innerRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        for (index in 0 until 8) {
            val angle = index * Math.PI / 4.0
            val start = Offset(
                x = center.x + cos(angle).toFloat() * spokeStart,
                y = center.y + sin(angle).toFloat() * spokeStart
            )
            val end = Offset(
                x = center.x + cos(angle).toFloat() * spokeEnd,
                y = center.y + sin(angle).toFloat() * spokeEnd
            )
            drawLine(
                color = iconColor,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}