package com.example.medsreminder

import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.Dispatchers
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
            AppDatabase.get(this@AlarmActivity).occurrenceDao()
                .observeCurrentRinging()
                .collectLatest { occurrence ->
                    withContext(Dispatchers.Main) {
                        currentOccurrence = occurrence
                        if (occurrence == null) finishAndRemoveTask()
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
        val current = occurrence ?: return
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
