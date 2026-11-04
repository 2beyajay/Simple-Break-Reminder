package com.example.simplebreakreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.simplebreakreminder.ui.theme.SimpleBreakReminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SimpleBreakReminderTheme {
                var intervalText by rememberSaveable { mutableStateOf("20") }
                var isRunning by rememberSaveable { mutableStateOf(false) }

                val interval = intervalText.toIntOrNull()
                val isIntervalValid = interval != null && interval in 1..240

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Break Reminder",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    intervalText = newValue
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

                        Button(
                            onClick = { isRunning = !isRunning },
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