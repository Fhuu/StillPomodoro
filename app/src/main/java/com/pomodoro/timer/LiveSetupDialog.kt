package com.pomodoro.timer

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun LiveSetupDialog(open: Boolean, onDismiss: () -> Unit, onStartSession: () -> Unit) {
    if (!open || Build.VERSION.SDK_INT < 36) return
    val context = LocalContext.current
    val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    val liveOk = SessionNotificationBuilder.canUseLiveUpdate(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show timer on lock screen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (liveOk) {
                        "Live notifications are on. Lock your phone during a session to see the timer " +
                            "in the notification area and Now bar."
                    } else {
                        "Allow live notifications for just pomodoro once — then the timer can appear on " +
                            "the lock screen and Samsung Now bar without extra steps each time."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (samsung) {
                    Text(
                        "On Samsung: Settings → Lock screen → Now bar → enable just pomodoro → " +
                            "Show always when locked.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (!liveOk) {
                Button(
                    onClick = { SessionNotificationBuilder.openPromotionSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow live notifications") }
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (samsung) {
                    OutlinedButton(
                        onClick = { SessionNotificationBuilder.openNowBarSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open Now bar settings") }
                }
                TextButton(
                    onClick = {
                        TimerStore.setLiveNotificationSetupDone(context)
                        onStartSession()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (liveOk) "Got it — start" else "Skip for now") }
            }
        },
    )
}
