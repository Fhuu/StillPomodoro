package com.pomodoro.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import com.pomodoro.timer.ui.AppColors
import com.pomodoro.timer.ui.PomodoroTheme
import com.pomodoro.timer.ui.SoftCardShape
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
        setContent { PomodoroTheme { PomodoroApp() } }
    }
}

@Composable
fun PomodoroApp() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var permTick by remember { mutableIntStateOf(0) }
    var ambientLabel by remember { mutableStateOf(TimerStore.ambientLabel(context)) }
    var sessionMin by remember { mutableIntStateOf(TimerStore.focusMinutes(context)) }
    var ambientArt by remember { mutableStateOf<Bitmap?>(AmbientArt.currentBitmap()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
            sessionMin = TimerStore.focusMinutes(context)
        }
    }
    @Suppress("UNUSED_VARIABLE") val refresh = tick
    val sessionActive = TimerStore.isSessionActive(context)

    LaunchedEffect(ambientLabel) {
        AmbientArt.loadAmbientSelection(context) { ambientArt = it }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Cream).statusBarsPadding()) {
        TextButton(
            onClick = { settingsOpen = true },
            modifier = Modifier
                .zIndex(1f)
                .align(Alignment.TopStart)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) { Text("☰", style = MaterialTheme.typography.headlineSmall) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 56.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
            Text("just", style = MaterialTheme.typography.titleLarge)
            Text("pomodoro", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SoftCardShape,
                color = AppColors.CreamDeep.copy(alpha = 0.45f),
                tonalElevation = 0.dp
            ) {
                Column(
                    Modifier.padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(TimerStore.phaseLabel(context).uppercase(), style = MaterialTheme.typography.labelLarge)
                    Text(TimerStore.formatRemaining(context), style = MaterialTheme.typography.displayMedium)
                }
            }

            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SoftCardShape,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.65f)
            ) {
                Column(
                    Modifier.padding(vertical = 20.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Session length", style = MaterialTheme.typography.labelLarge)
                    SessionKnob(
                        minutes = sessionMin,
                        onMinutesChange = {
                            sessionMin = it
                            TimerStore.setFocusMinutes(context, it)
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (sessionActive) {
                        PomodoroService.stop(context)
                    } else {
                        TimerStore.setFocusMinutes(context, sessionMin)
                        if (!AppPermissions.overlayGranted(context)) {
                            settingsOpen = true
                        }
                        PomodoroService.startFocus(context)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = SoftCardShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sessionActive) AppColors.Ink else AppColors.Terracotta,
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                Text(
                    if (sessionActive) "Stop session" else "Begin focus · $sessionMin min"
                )
            }

            if (!sessionActive) {
                OutlinedButton(
                    onClick = {
                        promptOverlayIfNeeded(context)
                        PomodoroService.startBreak(context)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = SoftCardShape
                ) { Text("Soft break · 5 min") }
            } else {
                OutlinedButton(
                    onClick = { PomodoroService.holdPause(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = SoftCardShape
                ) {
                    Text(if (TimerStore.isSessionPaused(context)) "Resume" else "Pause")
                }
            }

            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SoftCardShape,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.65f)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ambient", style = MaterialTheme.typography.labelLarge)
                    val art = ambientArt
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Text(
                        ambientLabel ?: "None selected",
                        style = MaterialTheme.typography.bodySmall
                    )
                    AmbientPickers(onChanged = { ambientLabel = TimerStore.ambientLabel(context) })
                }
            }
        }

        SettingsDialog(
            open = settingsOpen,
            onDismiss = { settingsOpen = false },
            onChanged = { permTick++ }
        )
    }
}