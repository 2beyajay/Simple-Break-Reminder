package com.example.simplebreakreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Path
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
                var currentPage by rememberSaveable { mutableStateOf("home") }
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

                BackHandler(enabled = currentPage != "home") {
                    currentPage = "home"
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

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentPage) {
                        "settings" -> {
                            SettingsPage(
                                soundEnabled = soundEnabled,
                                vibrationEnabled = vibrationEnabled,
                                onBack = { currentPage = "home" },
                                onSoundChanged = { enabled ->
                                    soundEnabled = enabled
                                    reminderPreferences.soundEnabled = enabled
                                },
                                onVibrationChanged = { enabled ->
                                    vibrationEnabled = enabled
                                    reminderPreferences.vibrationEnabled = enabled
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        "about" -> {
                            AboutPage(
                                onBack = { currentPage = "home" },
                                onOpenRepository = {
                                    val repoIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        REPOSITORY_URL.toUri()
                                    )
                                    startActivity(repoIntent)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        else -> {
                            MainPage(
                                intervalText = intervalText,
                                isIntervalValid = isIntervalValid,
                                isRunning = isRunning,
                                isReminderPending = isReminderPending,
                                remainingSeconds = remainingSeconds,
                                onIntervalChanged = { newValue ->
                                    if (newValue.all { it.isDigit() }) {
                                        intervalText = newValue
                                        newValue.toIntOrNull()
                                            ?.takeIf { it in 1..240 }
                                            ?.let {
                                                reminderPreferences.intervalMinutes = it
                                            }
                                    }
                                },
                                onOpenSettings = { currentPage = "settings" },
                                onOpenAbout = { currentPage = "about" },
                                onSkip = {
                                    sendServiceAction(ReminderService.ACTION_SKIP)
                                },
                                onDismiss = {
                                    sendServiceAction(ReminderService.ACTION_DISMISS)
                                },
                                onStartStop = {
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
                                modifier = Modifier.padding(innerPadding)
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

    private companion object {
        const val REPOSITORY_URL =
            "https://github.com/2beyajay/Simple-Break-Reminder"
    }
}

@Composable
private fun MainPage(
    intervalText: String,
    isIntervalValid: Boolean,
    isRunning: Boolean,
    isReminderPending: Boolean,
    remainingSeconds: Long,
    onIntervalChanged: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingMinutes = remainingSeconds / 60
    val remainingSecondsPart = remainingSeconds % 60

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.semantics {
                    contentDescription = "Settings"
                }
            ) {
                GearIcon()
            }

            HeaderDivider()

            Text(
                text = "Break Reminder",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )

            HeaderDivider()

            IconButton(
                onClick = onOpenAbout,
                modifier = Modifier.semantics {
                    contentDescription = "About"
                }
            ) {
                InfoIcon()
            }
        }

        OutlinedTextField(
            value = intervalText,
            onValueChange = onIntervalChanged,
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

            Button(onClick = onDismiss) {
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

            Button(onClick = onSkip) {
                Text("Skip next reminder")
            }
        }

        Button(
            onClick = onStartStop,
            enabled = isRunning || isIntervalValid
        ) {
            Text(if (isRunning) "Stop reminders" else "Start reminders")
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

@Composable
private fun SettingsPage(
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onBack: () -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        IconButton(
            onClick = onBack,
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
                onCheckedChange = onSoundChanged
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
                onCheckedChange = onVibrationChanged
            )
        }
    }
}

@Composable
private fun AboutPage(
    onBack: () -> Unit,
    onOpenRepository: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.semantics {
                contentDescription = "Back"
            }
        ) {
            BackChevron()
        }

        Text(
            text = "About",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "A little reminder to pause, stretch, and take care of yourself " +
                    "while you work. Set an interval that suits your day, and let " +
                    "Break Reminder help you build healthier work habits.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Features",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = """
                        • No Bloat
                        • Auto dismiss after 30 seconds
                        • Auto stop after 2 missed reminders
                    """.trimIndent(),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = onOpenRepository) {
            GitHubMark()
            Spacer(modifier = Modifier.width(10.dp))
            Text("View this project on GitHub")
        }

        Text(
            text = "Made to help make breaks a little easier.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeaderDivider() {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .width(1.dp)
            .height(24.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = dividerColor,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = size.width
            )
        }
    }
}

@Composable
private fun BackChevron() {
    val iconColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.size(24.dp)) {
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

@Composable
private fun GearIcon() {
    val iconColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.size(24.dp)) {
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
            drawLine(
                color = iconColor,
                start = Offset(
                    center.x + cos(angle).toFloat() * spokeStart,
                    center.y + sin(angle).toFloat() * spokeStart
                ),
                end = Offset(
                    center.x + cos(angle).toFloat() * spokeEnd,
                    center.y + sin(angle).toFloat() * spokeEnd
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun InfoIcon() {
    val iconColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = "About" }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val strokeWidth = 1.8.dp.toPx()

        drawCircle(
            color = iconColor,
            radius = size.minDimension * 0.43f,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = iconColor,
            radius = strokeWidth * 0.75f,
            center = Offset(center.x, size.height * 0.34f)
        )
        drawLine(
            color = iconColor,
            start = Offset(center.x, size.height * 0.46f),
            end = Offset(center.x, size.height * 0.70f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun GitHubMark() {
    val markColor = MaterialTheme.colorScheme.onSurface
    val backgroundColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = Modifier.size(24.dp)) {
        val width = size.width
        val height = size.height

        drawCircle(
            color = markColor,
            radius = size.minDimension * 0.48f,
            center = Offset(width / 2f, height / 2f)
        )

        val catHead = Path().apply {
            moveTo(width * 0.27f, height * 0.38f)
            lineTo(width * 0.23f, height * 0.16f)
            lineTo(width * 0.43f, height * 0.27f)
            cubicTo(
                width * 0.48f, height * 0.25f,
                width * 0.52f, height * 0.25f,
                width * 0.57f, height * 0.27f
            )
            lineTo(width * 0.77f, height * 0.16f)
            lineTo(width * 0.73f, height * 0.38f)
            cubicTo(
                width * 0.86f, height * 0.54f,
                width * 0.78f, height * 0.76f,
                width * 0.63f, height * 0.78f
            )
            lineTo(width * 0.37f, height * 0.78f)
            cubicTo(
                width * 0.22f, height * 0.76f,
                width * 0.14f, height * 0.54f,
                width * 0.27f, height * 0.38f
            )
            close()
        }

        drawPath(
            path = catHead,
            color = backgroundColor
        )
    }
}