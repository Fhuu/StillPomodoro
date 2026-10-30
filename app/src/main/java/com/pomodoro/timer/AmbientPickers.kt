package com.pomodoro.timer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AmbientPickers(onChanged: () -> Unit) {
    val context = LocalContext.current
    var libraryRevision by remember { mutableStateOf(0) }
    var sourceTab by remember {
        mutableStateOf(
            when (TimerStore.ambientMode(context)) {
                AmbientMode.YOUTUBE -> SourceTab.YOUTUBE
                AmbientMode.BUILT_IN -> SourceTab.BUILT_IN
                else -> SourceTab.DEVICE
            },
        )
    }
    var youtubeInput by remember { mutableStateOf("") }
    var youtubeError by remember { mutableStateOf<String?>(null) }
    var builtInSelection by remember { mutableStateOf(TimerStore.builtInAmbientSounds(context)) }

    val folderUri = TimerStore.libraryUri(context)?.let { Uri.parse(it) }
    val singleUri = TimerStore.singleTrackUri(context)?.let { Uri.parse(it) }
    var trackCount by remember { mutableStateOf(0) }
    var scanningLibrary by remember { mutableStateOf(false) }
    LaunchedEffect(libraryRevision, folderUri, singleUri) {
        if (singleUri != null) {
            trackCount = 1
            scanningLibrary = false
            return@LaunchedEffect
        }
        if (folderUri == null) {
            trackCount = 0
            scanningLibrary = false
            return@LaunchedEffect
        }
        scanningLibrary = true
        trackCount = withContext(Dispatchers.IO) {
            AmbientLibrary.listTracks(context, folderUri).size
        }
        scanningLibrary = false
    }

    var youtubeSelectionRev by remember { mutableStateOf(0) }

    fun refreshAmbientUi() {
        PomodoroService.refreshAmbient(context)
        AmbientArt.reloadSelection(context) { onChanged() }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val name = uri.lastPathSegment?.substringAfterLast(':') ?: "Music"
        TimerStore.setMusicLibrary(context, uri.toString(), name)
        sourceTab = SourceTab.DEVICE
        libraryRevision++
        refreshAmbientUi()
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val title = AmbientLibrary.displayName(context, uri)
        TimerStore.setSingleTrackFile(context, uri.toString(), title)
        sourceTab = SourceTab.DEVICE
        libraryRevision++
        refreshAmbientUi()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = sourceTab == SourceTab.DEVICE,
                onClick = {
                    sourceTab = SourceTab.DEVICE
                    if (TimerStore.hasDeviceMusic(context)) {
                        TimerStore.applyDeviceSource(context)
                        refreshAmbientUi()
                    } else {
                        onChanged()
                    }
                },
                label = { Text("On device") },
            )
            FilterChip(
                selected = sourceTab == SourceTab.YOUTUBE,
                onClick = {
                    sourceTab = SourceTab.YOUTUBE
                    val id = TimerStore.youtubeVideoId(context)
                        ?: YoutubePlaylist.entries(context).firstOrNull()?.videoId
                    if (id != null) {
                        TimerStore.setYoutubeSource(
                            context,
                            id,
                            YoutubePlaylist.titleFor(context, id),
                        )
                        youtubeSelectionRev++
                    }
                    refreshAmbientUi()
                },
                label = { Text("YouTube") },
            )
            FilterChip(
                selected = sourceTab == SourceTab.BUILT_IN,
                onClick = {
                    sourceTab = SourceTab.BUILT_IN
                    TimerStore.applyBuiltInSource(context)
                    builtInSelection = TimerStore.builtInAmbientSounds(context)
                    refreshAmbientUi()
                },
                label = { Text("Built-in") },
            )
        }

        when (sourceTab) {
            SourceTab.BUILT_IN -> BuiltInAmbientSection(
                selected = builtInSelection,
                onSelectionChange = { sounds ->
                    builtInSelection = sounds
                    TimerStore.setBuiltInAmbientSounds(context, sounds)
                    refreshAmbientUi()
                },
            )
            SourceTab.DEVICE -> DeviceLibrarySection(
                folderUri = folderUri,
                singleUri = singleUri,
                trackCount = trackCount,
                scanning = scanningLibrary,
                activeMode = TimerStore.ambientMode(context) == AmbientMode.LIBRARY,
                onPickFolder = { folderPicker.launch(null) },
                onPickFile = { filePicker.launch(arrayOf("audio/*")) },
            )

            SourceTab.YOUTUBE -> YoutubePlaylistSection(
                context = context,
                playlistRevision = libraryRevision,
                selectionRevision = youtubeSelectionRev,
                onRevision = { libraryRevision++ },
                onSelectionChange = { youtubeSelectionRev++ },
                onPick = { refreshAmbientUi() },
                youtubeInput = youtubeInput,
                onInputChange = {
                    youtubeInput = it
                    youtubeError = null
                },
                youtubeError = youtubeError,
                onError = { youtubeError = it },
                onClearInput = { youtubeInput = "" },
            )
        }

        OutlinedButton(
            onClick = {
                TimerStore.clearAmbient(context)
                builtInSelection = emptySet()
                PomodoroService.refreshAmbient(context)
                AmbientArt.clear()
                libraryRevision++
                youtubeInput = ""
                onChanged()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Turn off focus audio") }
    }
}

@Composable
private fun YoutubePlaylistSection(
    context: android.content.Context,
    playlistRevision: Int,
    selectionRevision: Int,
    onRevision: () -> Unit,
    onSelectionChange: () -> Unit,
    onPick: () -> Unit,
    youtubeInput: String,
    onInputChange: (String) -> Unit,
    youtubeError: String?,
    onError: (String?) -> Unit,
    onClearInput: () -> Unit,
) {
    val playlist = remember(playlistRevision) { YoutubePlaylist.entries(context) }
    val selectedId = remember(playlistRevision, selectionRevision) { TimerStore.youtubeVideoId(context) }
    val activeMode = TimerStore.ambientMode(context) == AmbientMode.YOUTUBE
    val playbackError = TimerStore.youtubePlaybackError(context)

    Text(
        when {
            selectedId == null -> "Add a link, then tap a row to choose focus audio"
            activeMode -> "Focus audio · ${YoutubePlaylist.titleFor(context, selectedId)}"
            else -> "Tap YouTube above to use: ${YoutubePlaylist.titleFor(context, selectedId)}"
        },
        style = MaterialTheme.typography.labelMedium,
    )
    if (playbackError != null) {
        Text(
            playbackError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    OutlinedTextField(
        value = youtubeInput,
        onValueChange = onInputChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Paste YouTube link") },
        placeholder = { Text("https://youtu.be/…") },
        isError = youtubeError != null,
        supportingText = youtubeError?.let { { Text(it) } },
        singleLine = true,
    )
    OutlinedButton(
        onClick = {
            YoutubePlaylist.add(context, youtubeInput) { entry ->
                if (entry == null) {
                    onError("Could not read a video ID")
                    return@add
                }
                TimerStore.setYoutubeSource(context, entry.videoId, entry.title)
                onSelectionChange()
                onRevision()
                onClearInput()
                onError(null)
                onPick()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add to YouTube playlist") }

    if (playlist.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            playlist.forEach { entry ->
                YoutubePlaylistRow(
                    entry = entry,
                    picked = activeMode && entry.videoId == selectedId,
                    onSelect = {
                        TimerStore.setYoutubeSource(context, entry.videoId, entry.title)
                        onSelectionChange()
                        onPick()
                    },
                    onRemove = {
                        YoutubePlaylist.remove(context, entry.videoId)
                        onRevision()
                        onPick()
                    },
                )
            }
        }
    }
}

@Composable
private fun YoutubePlaylistRow(
    entry: YoutubePlaylistEntry,
    picked: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val thumb = remember(entry.videoId) { YoutubePlaylist.thumbBitmap(entry) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = if (picked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    entry.link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

private enum class SourceTab { DEVICE, YOUTUBE, BUILT_IN }

@Composable
private fun BuiltInAmbientSection(
    selected: Set<BuiltInAmbientSound>,
    onSelectionChange: (Set<BuiltInAmbientSound>) -> Unit,
) {
    Text(
        "Select sounds to layer them together.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BuiltInAmbientSound.entries.forEach { sound ->
        val isSelected = sound in selected
        OutlinedButton(
            onClick = { onSelectionChange(if (isSelected) selected - sound else selected + sound) },
            modifier = Modifier.fillMaxWidth().semantics { this.selected = isSelected },
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(sound.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    sound.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceLibrarySection(
    folderUri: Uri?,
    singleUri: Uri?,
    trackCount: Int,
    scanning: Boolean,
    activeMode: Boolean,
    onPickFolder: () -> Unit,
    onPickFile: () -> Unit,
) {
    val context = LocalContext.current
    val singleTitle = singleUri?.let { AmbientLibrary.displayName(context, it) }
    OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (folderUri == null) "Choose music folder (shuffle)"
            else "Folder · ${folderUri.lastPathSegment?.substringAfterLast(':') ?: "Library"}",
        )
    }
    OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
        Text(if (singleTitle == null) "Choose one song file" else "Song · $singleTitle")
    }
    Text(
        when {
            !activeMode && (folderUri != null || singleUri != null) ->
                "Tap On device above to use local music (not YouTube)."
            scanning -> "Scanning folder…"
            singleUri != null -> "One song · loops on focus"
            folderUri == null -> "Folder shuffles; or pick a single file."
            trackCount == 0 -> "No audio files in this folder."
            else -> "$trackCount songs · shuffle on focus · Next track while timer runs"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
