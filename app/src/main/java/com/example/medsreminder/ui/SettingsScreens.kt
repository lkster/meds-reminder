package com.example.medsreminder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import com.example.medsreminder.alarm.AlarmPreferences
import com.example.medsreminder.ui.theme.LocalMedsReminderColors

enum class AlarmReadinessState { READY, LIMITED, NEEDS_ATTENTION }

fun calculateAlarmReadiness(capabilityItems: List<CapabilityItem>): AlarmReadinessState = when {
    capabilityItems.any { it.requiredForReliableDelivery && !it.ready } -> AlarmReadinessState.NEEDS_ATTENTION
    capabilityItems.any { !it.requiredForReliableDelivery && !it.ready } -> AlarmReadinessState.LIMITED
    else -> AlarmReadinessState.READY
}

@Composable
fun SettingsScreen(
    capabilityItems: List<CapabilityItem>,
    alarmSoundLabel: String,
    vibrationEnabled: Boolean,
    snoozeMinutes: Int,
    onBack: () -> Unit,
    onReadiness: () -> Unit,
    onChooseAlarmSound: () -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onSnoozeMinutesChange: (Int) -> Unit,
) {
    var snoozeSheetVisible by remember { mutableStateOf(false) }
    BackHandler(enabled = snoozeSheetVisible) { snoozeSheetVisible = false }
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp).semantics { paneTitle = "Settings" },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SecondaryTopBar("Settings", onBack)
        ReadinessSummaryCard(calculateAlarmReadiness(capabilityItems), onReadiness)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Alarm", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                SettingsActionRow("Alarm sound", alarmSoundLabel, onChooseAlarmSound)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow("Vibration", vibrationEnabled, onVibrationEnabledChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow("Default Snooze", "$snoozeMinutes min") { snoozeSheetVisible = true }
            }
        }
    }
    if (snoozeSheetVisible) {
        SnoozeSelectionSheet(
            currentMinutes = snoozeMinutes,
            onDismiss = { snoozeSheetVisible = false },
            onDone = {
                onSnoozeMinutesChange(it)
                snoozeSheetVisible = false
            },
        )
    }
}

@Composable
fun AlarmReadinessScreen(
    capabilityItems: List<CapabilityItem>,
    showSamsungGuidance: Boolean,
    onBack: () -> Unit,
    onSamsungSettings: () -> Unit,
) {
    val state = calculateAlarmReadiness(capabilityItems)
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp).semantics { paneTitle = "Alarm readiness" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SecondaryTopBar("Alarm readiness", onBack)
        ReadinessSummaryCard(state, onClick = null, detailed = true)
        capabilityItems.forEach { CapabilityRow(it) }
        if (showSamsungGuidance) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Samsung unlocked alarm actions", style = MaterialTheme.typography.titleMedium)
                    Text("Samsung Brief pop-ups may hide immediate Taken, Snooze, and Skip actions. Detailed is a user-controlled Samsung setting that Meds Reminder cannot change.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onSamsungSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open notification settings") }
                }
            }
        }
    }
}

@Composable
private fun SecondaryTopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Back" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun ReadinessSummaryCard(state: AlarmReadinessState, onClick: (() -> Unit)?, detailed: Boolean = false) {
    val statusColors = LocalMedsReminderColors.current
    val (color, title, detail) = when (state) {
        AlarmReadinessState.READY -> Triple(statusColors.success, if (detailed) "Everything is ready" else "Alarm readiness", if (detailed) "Required alarm capabilities and full-screen presentation are available." else "Everything is configured")
        AlarmReadinessState.LIMITED -> Triple(statusColors.warning, if (detailed) "Alarm setup is limited" else "Alarm readiness", if (detailed) "Actionable alarm notifications remain available, but full-screen presentation is unavailable." else "Full-screen presentation is limited")
        AlarmReadinessState.NEEDS_ATTENTION -> Triple(MaterialTheme.colorScheme.error, if (detailed) "Alarm setup needs attention" else "Alarm readiness", if (detailed) "Configure the required items below before relying on medication alarms." else "Alarm setup needs attention")
    }
    val modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            StatusIcon(state, color)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
                Text(state.accessibleLabel(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun AlarmReadinessState.accessibleLabel(): String = when (this) {
    AlarmReadinessState.READY -> "Ready"
    AlarmReadinessState.LIMITED -> "Limited"
    AlarmReadinessState.NEEDS_ATTENTION -> "Needs attention"
}

@Composable
private fun SettingsActionRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp).heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (title == "Alarm sound") Icons.AutoMirrored.Filled.VolumeUp else Icons.Outlined.Settings, null)
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(value, style = MaterialTheme.typography.bodyMedium) }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Vibration, null)
        Text(title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = "Vibration" })
    }
}

@Composable
private fun CapabilityRow(item: CapabilityItem) {
    val status = LocalMedsReminderColors.current
    val color = when { item.ready -> status.success; item.requiredForReliableDelivery -> MaterialTheme.colorScheme.error; else -> status.warning }
    val stateText = when { item.ready -> "OK"; item.requiredForReliableDelivery -> "Needs attention"; else -> "Limited" }
    Card(
        Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (item.ready) MaterialTheme.colorScheme.surface else color.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(if (item.ready) AlarmReadinessState.READY else if (item.requiredForReliableDelivery) AlarmReadinessState.NEEDS_ATTENTION else AlarmReadinessState.LIMITED, color)
                Column { Text(item.title, style = MaterialTheme.typography.titleMedium); Text(stateText, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium) }
            }
            Text(item.detail, style = MaterialTheme.typography.bodyMedium)
            if (!item.ready) OutlinedButton(onClick = item.onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(item.actionLabel) }
        }
    }
}

@Composable
private fun StatusIcon(state: AlarmReadinessState, color: Color) = Icon(
    when (state) {
        AlarmReadinessState.READY -> Icons.Outlined.CheckCircle
        AlarmReadinessState.LIMITED -> Icons.Outlined.WarningAmber
        AlarmReadinessState.NEEDS_ATTENTION -> Icons.Outlined.ErrorOutline
    },
    contentDescription = null,
    tint = color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeSelectionSheet(currentMinutes: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    var selectedMinutes by remember(currentMinutes) { mutableStateOf(currentMinutes) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Default Snooze", style = MaterialTheme.typography.titleLarge)
            AlarmPreferences.ALLOWED_SNOOZE_MINUTES.forEach { minutes ->
                Row(
                    Modifier.fillMaxWidth().clickable { selectedMinutes = minutes }.heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$minutes min", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    RadioButton(selected = selectedMinutes == minutes, onClick = { selectedMinutes = minutes })
                }
            }
            Button(onClick = { onDone(selectedMinutes) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
        }
    }
}
