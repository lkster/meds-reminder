package com.example.medsreminder

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.medsreminder.alarm.AlarmActionReceiver
import com.example.medsreminder.alarm.AlarmStore
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlarmScreen()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AlarmScreen() {
        BackHandler {
            // Intentionally consume Back: the alarm must be resolved with an explicit action.
        }

        LaunchedEffect(Unit) {
            while (true) {
                val scheduledAt = AlarmStore(this@AlarmActivity).scheduledAtMillis()
                if (scheduledAt == null || scheduledAt > System.currentTimeMillis() + 2_000L) {
                    finishAndRemoveTask()
                    break
                }
                delay(500L)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Medication alarm",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                "M0 test alarm",
                modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Button(
                onClick = { resolve(AlarmActionReceiver.ACTION_TAKEN) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Taken")
            }
            Button(
                onClick = { resolve(AlarmActionReceiver.ACTION_SNOOZE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("Snooze 5 minutes")
            }
            OutlinedButton(
                onClick = { resolve(AlarmActionReceiver.ACTION_SKIP) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("Skip")
            }
        }
    }

    private fun resolve(action: String) {
        AlarmActionReceiver.send(this, action)
        finishAndRemoveTask()
    }
}
