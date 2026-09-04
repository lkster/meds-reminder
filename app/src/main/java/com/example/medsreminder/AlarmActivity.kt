package com.example.medsreminder

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.medsreminder.alarm.AlarmActionReceiver
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.OccurrenceDetails
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmActivity : ComponentActivity() {
    private var currentOccurrence by mutableStateOf<OccurrenceDetails?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        lifecycleScope.launch {
            AlarmActivityLoadingTestHook.beforeInitialRoomCollection(applicationContext)
            AppDatabase.get(this@AlarmActivity).occurrenceDao()
                .observeCurrentRinging()
                .collectLatest { occurrence ->
                    AlarmActivityLoadingTestHook.markInitialRoomEmission(applicationContext)
                    withContext(Dispatchers.Main) {
                        if (occurrence == null) {
                            finishAndRemoveTask()
                        } else {
                            currentOccurrence = occurrence
                        }
                    }
                }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlarmScreen(currentOccurrence)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The stable session intent intentionally leaves the Room-observed occurrence untouched.
    }

    @Composable
    private fun AlarmScreen(occurrence: OccurrenceDetails?) {
        BackHandler {
            // The alarm must be resolved with an explicit action.
        }
        val current = occurrence
        if (current == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Loading alarm…",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
            return
        }
        val scheduledTime = TIME_FORMATTER.format(
            Instant.ofEpochMilli(current.scheduledAtEpochMillis).atZone(ZoneId.systemDefault()),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                current.medicationName,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            current.instructions?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                "Scheduled for $scheduledTime",
                modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { resolve(AlarmActionReceiver.ACTION_TAKEN, current.occurrenceId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Taken")
            }
            Button(
                onClick = { resolve(AlarmActionReceiver.ACTION_SNOOZE, current.occurrenceId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("Snooze")
            }
            OutlinedButton(
                onClick = { resolve(AlarmActionReceiver.ACTION_SKIP, current.occurrenceId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("Skip")
            }
        }
    }

    private fun resolve(action: String, occurrenceId: String) {
        AlarmActionReceiver.send(this, action, occurrenceId)
    }

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
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

    fun reset(context: android.content.Context) { directory(context).deleteRecursively() }

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
