package com.example.medsreminder

import android.Manifest
import android.content.ActivityNotFoundException
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.medsreminder.alarm.AlarmRingingService
import com.example.medsreminder.alarm.AlarmScheduler
import com.example.medsreminder.alarm.AlarmStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val scheduler by lazy { AlarmScheduler(this) }
    private val store by lazy { AlarmStore(this) }

    private var capabilities by mutableStateOf(CapabilityState())
    private var scheduledAtMillis by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmRingingService.ensureNotificationChannel(this)
        refreshState()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    @Composable
    private fun MainScreen() {
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { refreshState() }
        val canSchedule = capabilities.notificationsAllowed &&
            capabilities.channelHighImportance &&
            capabilities.exactAlarmsAllowed

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Medication alarm M0", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A focused spike for exact delivery, lockscreen presentation, actions, and reboot recovery.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Current alarm", style = MaterialTheme.typography.titleMedium)
                    Text(statusText(scheduledAtMillis))
                }
            }

            Button(
                onClick = { scheduleAfter(10_000L) },
                enabled = canSchedule,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Schedule in 10 seconds")
            }
            Button(
                onClick = { scheduleAfter(2 * 60_000L) },
                enabled = canSchedule,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Schedule in 2 minutes")
            }
            OutlinedButton(
                onClick = {
                    scheduler.cancel()
                    refreshState()
                },
                enabled = scheduledAtMillis != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel alarm")
            }

            Spacer(Modifier.height(4.dp))
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Samsung unlocked alarm actions", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "For immediate alarm actions while the phone is unlocked, set:\n" +
                                "Apps → Meds Reminder → Notifications → " +
                                "Pop-up notification style → Detailed.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = ::openAppNotificationSettings) {
                            Text("Open notification settings")
                        }
                    }
                }
            }

            Text("Capabilities", style = MaterialTheme.typography.titleLarge)

            CapabilityCard(
                title = "Notifications",
                available = capabilities.notificationsAllowed,
                detail = "Required for the alarm notification and actions.",
                actionLabel = "Allow",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings()
                    }
                },
            )
            CapabilityCard(
                title = "Alarm channel",
                available = capabilities.channelHighImportance,
                detail = "Must remain high importance for heads-up presentation.",
                actionLabel = "Open channel settings",
                onAction = ::openAlarmChannelSettings,
            )
            CapabilityCard(
                title = "Exact alarms",
                available = capabilities.exactAlarmsAllowed,
                detail = "User-granted Alarms & reminders access for setAlarmClock().",
                actionLabel = "Open alarm access",
                onAction = ::openExactAlarmSettings,
            )
            CapabilityCard(
                title = "Full-screen alarm",
                available = capabilities.fullScreenAllowed,
                detail = if (capabilities.fullScreenAllowed) {
                    "Lockscreen full-screen intent is available."
                } else {
                    "Alarm scheduling still works, but lockscreen delivery may degrade to a notification."
                },
                actionLabel = "Open full-screen access",
                onAction = ::openFullScreenIntentSettings,
            )
        }
    }

    @Composable
    private fun CapabilityCard(
        title: String,
        available: Boolean,
        detail: String,
        actionLabel: String,
        onAction: () -> Unit,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(if (available) "Ready" else "Action needed")
                }
                Text(detail, style = MaterialTheme.typography.bodySmall)
                if (!available) {
                    OutlinedButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }

    private fun scheduleAfter(delayMillis: Long) {
        val triggerAt = System.currentTimeMillis() + delayMillis
        val result = scheduler.schedule(triggerAt)
        refreshState()
        Toast.makeText(
            this,
            if (result.isSuccess) "Alarm scheduled" else "Could not schedule: ${result.exceptionOrNull()?.message}",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun refreshState() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = notificationManager.getNotificationChannel(AlarmRingingService.CHANNEL_ID)
        capabilities = CapabilityState(
            notificationsAllowed = notificationManager.areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
            channelHighImportance = channel?.importance == NotificationManager.IMPORTANCE_HIGH,
            exactAlarmsAllowed = getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            fullScreenAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                notificationManager.canUseFullScreenIntent(),
        )
        scheduledAtMillis = store.scheduledAtMillis()
    }

    private fun statusText(triggerAtMillis: Long?): String {
        if (triggerAtMillis == null) {
            return "No alarm scheduled"
        }
        val formatted = TIME_FORMATTER.format(
            Instant.ofEpochMilli(triggerAtMillis).atZone(ZoneId.systemDefault()),
        )
        return if (triggerAtMillis > System.currentTimeMillis()) {
            "Scheduled for $formatted"
        } else {
            "Triggered at $formatted; awaiting resolution"
        }
    }

    private fun openExactAlarmSettings() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:$packageName")),
        )
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    private fun openAppNotificationSettings() {
        val notificationSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(notificationSettingsIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    private fun openAlarmChannelSettings() {
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmRingingService.CHANNEL_ID),
        )
    }

    private data class CapabilityState(
        val notificationsAllowed: Boolean = false,
        val channelHighImportance: Boolean = false,
        val exactAlarmsAllowed: Boolean = false,
        val fullScreenAllowed: Boolean = false,
    )

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
