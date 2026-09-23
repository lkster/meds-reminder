package com.example.medsreminder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Snooze
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
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).semantics { paneTitle = "Settings" },
    ) {
        SecondaryTopBar("Settings", onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            ReadinessSummaryCard(calculateAlarmReadiness(capabilityItems), onReadiness)
            Spacer(Modifier.height(30.dp))
            Text("Alarm", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = LocalMedsReminderColors.current.surfaceElevated),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                SettingsActionRow("Alarm sound", alarmSoundLabel, Icons.AutoMirrored.Filled.VolumeUp, onChooseAlarmSound)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsSwitchRow("Vibration", vibrationEnabled, onVibrationEnabledChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsActionRow("Default Snooze", "$snoozeMinutes min", Icons.Outlined.Snooze) { snoozeSheetVisible = true }
            }
            Spacer(Modifier.height(24.dp))
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
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).semantics { paneTitle = "Alarm readiness" },
    ) {
        SecondaryTopBar("Alarm readiness", onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            ReadinessSummaryCard(state, onClick = null, detailed = true)
            Spacer(Modifier.height(18.dp))
            capabilityItems.forEachIndexed { index, item ->
                CapabilityRow(item)
                if (index < capabilityItems.lastIndex) Spacer(Modifier.height(11.dp))
            }
            if (showSamsungGuidance) {
                Spacer(Modifier.height(22.dp))
                SamsungGuidanceCard(onSamsungSettings)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SamsungGuidanceCard(onSamsungSettings: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Samsung unlocked alarm actions", style = MaterialTheme.typography.titleMedium)
            Text("Samsung Brief pop-ups may hide immediate Taken, Snooze, and Skip actions. Detailed is a user-controlled Samsung setting that Meds Reminder cannot change.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onSamsungSettings, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text("Open notification settings") }
        }
    }
}

@Composable
private fun SecondaryTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Back" },
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(24.dp)) }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.semantics { heading() },
        )
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
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(state, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (onClick != null) Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 66.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Vibration, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Vibration" })
    }
}

@Composable
private fun CapabilityRow(item: CapabilityItem) {
    val status = LocalMedsReminderColors.current
    val color = when { item.ready -> status.success; item.requiredForReliableDelivery -> MaterialTheme.colorScheme.error; else -> status.warning }
    val stateText = when { item.ready -> "OK"; item.requiredForReliableDelivery -> "Needs attention"; else -> "Limited" }
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (item.ready) status.surfaceElevated else color.copy(alpha = 0.09f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                StatusIcon(if (item.ready) AlarmReadinessState.READY else if (item.requiredForReliableDelivery) AlarmReadinessState.NEEDS_ATTENTION else AlarmReadinessState.LIMITED, color)
                BoxWithConstraints(Modifier.weight(1f)) {
                    if (maxWidth < 260.dp || LocalDensity.current.fontScale >= 1.5f) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(stateText, color = color, style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.widthIn(min = 8.dp))
                            Text(stateText, color = color, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (!item.ready) OutlinedButton(onClick = item.onAction, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium) { Text(item.actionLabel) }
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
    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 16.dp)) {
            Text("Default Snooze", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(16.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = LocalMedsReminderColors.current.surfaceElevated),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column {
                    AlarmPreferences.ALLOWED_SNOOZE_MINUTES.forEachIndexed { index, minutes ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedMinutes = minutes }.padding(horizontal = 12.dp).heightIn(min = 52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$minutes min", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            RadioButton(selected = selectedMinutes == minutes, onClick = { selectedMinutes = minutes })
                        }
                        if (index < AlarmPreferences.ALLOWED_SNOOZE_MINUTES.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Button(onClick = { onDone(selectedMinutes) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text("Done") }
        }
    }
}
