package com.example.medsreminder

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.medsreminder.alarm.AlarmActionReceiver
import com.example.medsreminder.alarm.AlarmPreferences
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.OccurrenceDetails
import com.example.medsreminder.ui.theme.AlarmTheme
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmActivity : ComponentActivity() {
    private var currentOccurrence by mutableStateOf<OccurrenceDetails?>(null)
    private var configuredSnoozeMinutes by mutableStateOf(AlarmPreferences.DEFAULT_SNOOZE_MINUTES)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        refreshSnoozeMinutes()

        lifecycleScope.launch {
            AlarmActivityLoadingTestHook.beforeInitialRoomCollection(applicationContext)
            AppDatabase.get(this@AlarmActivity).occurrenceDao().observeCurrentRinging().collectLatest { occurrence ->
                AlarmActivityLoadingTestHook.markInitialRoomEmission(applicationContext)
                withContext(Dispatchers.Main) {
                    if (occurrence == null) finishAndRemoveTask() else currentOccurrence = occurrence
                }
            }
        }

        setContent {
            AlarmTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlarmScreen(
                        occurrence = currentOccurrence,
                        configuredSnoozeMinutes = configuredSnoozeMinutes,
                        onTaken = { resolve(AlarmActionReceiver.ACTION_TAKEN, it) },
                        onSkip = { resolve(AlarmActionReceiver.ACTION_SKIP, it) },
                        onDefaultSnooze = { resolve(AlarmActionReceiver.ACTION_SNOOZE, it) },
                        onAlternateSnooze = { occurrenceId, minutes ->
                            resolve(AlarmActionReceiver.ACTION_SNOOZE, occurrenceId, minutes)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSnoozeMinutes()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The stable session intent intentionally leaves the Room-observed occurrence untouched.
    }

    private fun refreshSnoozeMinutes() {
        configuredSnoozeMinutes = AlarmPreferences.read(this).snoozeMinutes
    }

    private fun resolve(action: String, occurrenceId: String, snoozeMinutes: Int? = null) {
        AlarmActionReceiver.send(this, action, occurrenceId, snoozeMinutes)
    }

    companion object {
        internal val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** Room-authoritative alarm presentation, deliberately keyed only by the current occurrence UUID. */
@Composable
internal fun AlarmScreen(
    occurrence: OccurrenceDetails?,
    configuredSnoozeMinutes: Int,
    onTaken: (String) -> Unit,
    onSkip: (String) -> Unit,
    onDefaultSnooze: (String) -> Unit,
    onAlternateSnooze: (String, Int) -> Unit,
) {
    if (occurrence == null) {
        BackHandler { /* A ringing alarm requires an explicit resolution action. */ }
        AlarmLoadingScreen()
        return
    }
    key(occurrence.occurrenceId) {
        var snoozeOptionsVisible by remember { mutableStateOf(false) }
        BackHandler {
            if (snoozeOptionsVisible) snoozeOptionsVisible = false
            // Otherwise retain the explicit-resolution back behavior.
        }
        AlarmLoadedScreen(
            occurrence = occurrence,
            configuredSnoozeMinutes = configuredSnoozeMinutes,
            onTaken = onTaken,
            onSkip = onSkip,
            onDefaultSnooze = onDefaultSnooze,
            onMoreSnooze = { snoozeOptionsVisible = true },
        )
        if (snoozeOptionsVisible) {
            AlternateSnoozeSheet(
                configuredSnoozeMinutes = configuredSnoozeMinutes,
                onDismiss = { snoozeOptionsVisible = false },
                onSelect = { minutes ->
                    snoozeOptionsVisible = false
                    onAlternateSnooze(occurrence.occurrenceId, minutes)
                },
            )
        }
    }
}

@Composable
private fun AlarmLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.sizeIn(minWidth = 28.dp, minHeight = 28.dp), strokeWidth = 3.dp)
        Text(
            "Loading alarm…",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlarmLoadedScreen(
    occurrence: OccurrenceDetails,
    configuredSnoozeMinutes: Int,
    onTaken: (String) -> Unit,
    onSkip: (String) -> Unit,
    onDefaultSnooze: (String) -> Unit,
    onMoreSnooze: () -> Unit,
) {
    val scheduledTime = AlarmActivity.TIME_FORMATTER.format(Instant.ofEpochMilli(occurrence.scheduledAtEpochMillis).atZone(ZoneId.systemDefault()))
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(occurrence.medicationName, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text("Scheduled for $scheduledTime", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        occurrence.instructions?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onTaken(occurrence.occurrenceId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Mark as taken") }
        SnoozeSplitControl(
            minutes = configuredSnoozeMinutes,
            onDefaultSnooze = { onDefaultSnooze(occurrence.occurrenceId) },
            onMoreSnooze = onMoreSnooze,
        )
        OutlinedButton(
            onClick = { onSkip(occurrence.occurrenceId) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) { Text("Skip this dose") }
    }
}

@Composable
private fun SnoozeSplitControl(minutes: Int, onDefaultSnooze: () -> Unit, onMoreSnooze: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 360.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDefaultSnooze, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Snooze $minutes min") }
                OutlinedButton(
                    onClick = onMoreSnooze,
                    modifier = Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp).semantics { contentDescription = "More snooze options" },
                ) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDefaultSnooze, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Snooze $minutes min") }
                OutlinedButton(
                    onClick = onMoreSnooze,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = "More snooze options" },
                ) { Text("More snooze options") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlternateSnoozeSheet(configuredSnoozeMinutes: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Snooze options", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            AlarmPreferences.ALLOWED_SNOOZE_MINUTES.forEach { minutes ->
                OutlinedButton(onClick = { onSelect(minutes) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("$minutes min", modifier = Modifier.weight(1f))
                    if (minutes == configuredSnoozeMinutes) Text("Default", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Narrow M14 test control for the initial real current-ringing Room Flow collection. */
internal object AlarmActivityLoadingTestHook {
    private const val DIRECTORY = "m14-alarm-activity-loading-test-hook"
    private const val ACTIVE = "active"
    private const val HOLD_BEFORE_COLLECTION = "hold-before-collection"
    private const val BEFORE_COLLECTION_REACHED = "before-collection-reached"
    private const val FIRST_ROOM_EMISSION = "first-room-emission"
    private const val HOLD_TIMEOUT_MILLIS = 15_000L

    fun configure(context: android.content.Context, holdBeforeCollection: Boolean = false) {
        reset(context)
        directory(context).mkdirs()
        touch(context, ACTIVE)
        if (holdBeforeCollection) touch(context, HOLD_BEFORE_COLLECTION)
    }

    fun reset(context: android.content.Context) {
        directory(context).deleteRecursively()
    }
    fun releaseCollection(context: android.content.Context) {
        val holdFile = file(context, HOLD_BEFORE_COLLECTION)
        check(!holdFile.exists() || holdFile.delete()) { "M14 test hook release could not clear its hold" }
    }
    fun beforeCollectionReached(context: android.content.Context): Boolean =
        file(context, BEFORE_COLLECTION_REACHED).exists()

    fun firstRoomEmissionReceived(context: android.content.Context): Boolean =
        file(context, FIRST_ROOM_EMISSION).exists()

    suspend fun beforeInitialRoomCollection(context: android.content.Context) {
        if (!file(context, ACTIVE).exists()) return
        withContext(Dispatchers.IO) {
            touch(context, BEFORE_COLLECTION_REACHED)
            if (!file(context, HOLD_BEFORE_COLLECTION).exists()) return@withContext
            val deadline = SystemClock.elapsedRealtime() + HOLD_TIMEOUT_MILLIS
            while (file(context, HOLD_BEFORE_COLLECTION).exists()) {
                if (!file(context, ACTIVE).exists()) return@withContext
                check(SystemClock.elapsedRealtime() < deadline) { "M14 test hook was not released" }
                delay(10)
            }
        }
    }

    fun markInitialRoomEmission(context: android.content.Context) {
        if (file(context, ACTIVE).exists()) touch(context, FIRST_ROOM_EMISSION)
    }
    private fun touch(context: android.content.Context, key: String) {
        directory(context).mkdirs()
        file(context, key).writeText("")
    }

    private fun directory(context: android.content.Context) =
        File(context.applicationContext.filesDir, DIRECTORY)
    private fun file(context: android.content.Context, key: String) = File(directory(context), key)
}
