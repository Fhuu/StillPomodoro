package com.pomodoro.timer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pomodoro.timer.ui.AppColors
import com.pomodoro.timer.ui.PomodoroTheme
import com.pomodoro.timer.ui.SoftCardShape

class QuickStartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PomodoroTheme { QuickStartSheet(onDismiss = { finish() }) } }
    }
}

@Composable
private fun QuickStartSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var minutes by remember { mutableIntStateOf(TimerStore.focusMinutes(context)) }
    var ambientLabel by remember { mutableStateOf(TimerStore.ambientLabel(context)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = SoftCardShape,
            color = AppColors.Cream
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Start session", style = MaterialTheme.typography.titleLarge)
                Text("Length", style = MaterialTheme.typography.labelLarge)
                SessionKnob(minutes = minutes, onMinutesChange = { minutes = it })
                Text("Ambient", style = MaterialTheme.typography.labelLarge)
                Text(ambientLabel ?: "None", style = MaterialTheme.typography.bodySmall)
                AmbientPickers(onChanged = { ambientLabel = TimerStore.ambientLabel(context) })
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        TimerStore.setFocusMinutes(context, minutes)
                        promptOverlayIfNeeded(context)
                        PomodoroService.startFocus(context)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = SoftCardShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Terracotta)
                ) { Text("Begin · $minutes min") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = SoftCardShape) {
                    Text("Cancel")
                }
            }
        }
    }
}
