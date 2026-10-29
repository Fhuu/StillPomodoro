package com.pomodoro.timer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AmbientPickers(onChanged: () -> Unit) {
    val context = LocalContext.current
    var libraryRevision by remember { mutableStateOf(0) }
    var sourceTab by remember {
        mutableStateOf(
            when (TimerStore.ambientMode(context)) {
                AmbientMode.YOUTUBE -> SourceTab.YOUTUBE
                else -> SourceTab.DEVICE
            },
        )
    }
    var youtubeInput by remember { mutableStateOf("") }
    var youtubeError by remember { mutableStateOf<String?>(null) }

    val tree = TimerStore.libraryUri(context)?.let { Uri.parse(it) }
    val tracks = remember(libraryRevision, tree) {
        tree?.let { AmbientLibrary.listTracks(context, it) }.orEmpty()
    }
    val selected = TimerStore.selectedTrackUri(context)

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        val name = uri.lastPathSegment?.substringAfterLast(':') ?: "Music"
        TimerStore.setMusicLibrary(context, uri.toString(), name)
        sourceTab = SourceTab.DEVICE
        libraryRevision++
        onChanged()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = sourceTab == SourceTab.DEVICE,
                onClick = { sourceTab = SourceTab.DEVICE },
                label = { Text("On device") },
            )
            FilterChip(
                selected = sourceTab == SourceTab.YOUTUBE,
                onClick = { sourceTab = SourceTab.YOUTUBE },
                label = { Text("YouTube") },
            )
        }

        when (sourceTab) {
            SourceTab.DEVICE -> DeviceLibrarySection(
                tree = tree,
                tracks = tracks,
                selected = selected,
                onPickFolder = { folderPicker.launch(null) },
                onPickTrack = { track ->
                    TimerStore.setSelectedTrack(context, track.uri.toString(), track.title)
                    AmbientArt.load(context, track.uri)
                    onChanged()
                },
            )

            SourceTab.YOUTUBE -> YoutubePlaylistSection(
                context = context,
                playlistRevision = libraryRevision,
                onRevision = { libraryRevision++ },
                onChanged = onChanged,
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
                AmbientArt.clear()
                libraryRevision++
                youtubeInput = ""
                onChanged()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Turn off focus music")         }
    }
}

@Composable
private fun YoutubePlaylistSection(
    context: android.content.Context,
    playlistRevision: Int,
    onRevision: () -> Unit,
    onChanged: () -> Unit,
    youtubeInput: String,
    onInputChange: (String) -> Unit,
    youtubeError: String?,
    onError: (String?) -> Unit,
    onClearInput: () -> Unit,
) {
    val playlist = remember(playlistRevision) { YoutubePlaylist.entries(context) }
    val selectedId = TimerStore.youtubeVideoId(context)

    Text(
        "Playlist saved in app · ${playlist.size} videos",
        style = MaterialTheme.typography.labelMedium,
    )
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
                AmbientArt.loadAmbientSelection(context) { onChanged() }
                onRevision()
                onClearInput()
                onError(null)
                onChanged()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add to YouTube playlist") }

    if (playlist.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(playlist, key = { it.videoId }) { entry ->
                YoutubePlaylistRow(
                    entry = entry,
                    picked = entry.videoId == selectedId,
                    onSelect = {
                        TimerStore.setYoutubeSource(context, entry.videoId, entry.title)
                        AmbientArt.loadAmbientSelection(context) { onChanged() }
                        onChanged()
                    },
                    onRemove = {
                        YoutubePlaylist.remove(context, entry.videoId)
                        onRevision()
                        onChanged()
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

private enum class SourceTab { DEVICE, YOUTUBE }

@Composable
private fun DeviceLibrarySection(
    tree: Uri?,
    tracks: List<AmbientTrack>,
    selected: String?,
    onPickFolder: () -> Unit,
    onPickTrack: (AmbientTrack) -> Unit,
) {
    OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (tree == null) "Choose where your music is stored"
            else "Music folder · ${tree.lastPathSegment?.substringAfterLast(':') ?: "Library"}",
        )
    }
    if (tree != null && tracks.isEmpty()) {
        Text(
            "No audio files in this folder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (tracks.isNotEmpty()) {
        Text("Library", style = MaterialTheme.typography.labelMedium)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(tracks, key = { it.uri.toString() }) { track ->
                val picked = track.uri.toString() == selected
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onPickTrack(track) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (picked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ) {
                    Text(
                        track.title,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
