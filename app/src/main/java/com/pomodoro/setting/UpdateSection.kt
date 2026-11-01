package com.pomodoro.setting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pomodoro.update.ApkUpdateInstaller
import com.pomodoro.update.AppUpdater
import java.util.Locale

@Composable
internal fun UpdateSection() {
    val context = LocalContext.current
    val updater = remember(context.applicationContext) { AppUpdater.get(context) }
    val state = updater.state
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var canInstall by remember { mutableStateOf(ApkUpdateInstaller.canInstall(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canInstall = ApkUpdateInstaller.canInstall(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Text("App updates", style = MaterialTheme.typography.labelLarge)
    Text("Version ${updater.installedVersion}", style = MaterialTheme.typography.bodySmall)
    when (state) {
        AppUpdater.State.Idle -> Unit
        AppUpdater.State.Checking -> Text("Checking GitHub Releases…", style = MaterialTheme.typography.bodySmall)
        AppUpdater.State.NoUpdate -> Text("No newer APK release found.", style = MaterialTheme.typography.bodySmall)
        is AppUpdater.State.Available -> {
            val size = String.format(Locale.getDefault(), "%.1f", state.release.size / (1024.0 * 1024.0))
            Text("${state.release.tag} is available ($size MB).", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { updater.download(state.release) }, modifier = Modifier.fillMaxWidth()) { Text("Download update") }
        }
        is AppUpdater.State.Downloading -> Text("Downloading update… ${state.percent}%", style = MaterialTheme.typography.bodySmall)
        is AppUpdater.State.Ready -> {
            Text("${state.release.tag} is ready. Android will ask you to confirm installation.", style = MaterialTheme.typography.bodySmall)
            if (!canInstall) Text("Allow app updates, then return here to install.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { updater.install() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (canInstall) "Install update" else "Allow app updates")
            }
        }
        AppUpdater.State.OpeningInstaller -> Text("Opening Android's installer…", style = MaterialTheme.typography.bodySmall)
        is AppUpdater.State.Error -> Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    OutlinedButton(
        onClick = { updater.check() },
        enabled = state !is AppUpdater.State.Checking && state !is AppUpdater.State.Downloading && state !is AppUpdater.State.OpeningInstaller,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Check for updates") }
}
