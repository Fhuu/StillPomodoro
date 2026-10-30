package com.pomodoro.timer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

data class AmbientTrack(val uri: Uri, val title: String)

object AmbientLibrary {
    fun listTracks(context: Context, treeUri: Uri): List<AmbientTrack> = runCatching {
        val root = DocumentFile.fromTreeUri(context.applicationContext, treeUri) ?: return emptyList()
        collectAudio(root)
            .map { uri -> AmbientTrack(uri, displayName(context, uri)) }
            .sortedBy { it.title.lowercase() }
    }.getOrElse { emptyList() }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Track"
    }

    private fun collectAudio(dir: DocumentFile): List<Uri> {
        val out = mutableListOf<Uri>()
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return out
        for (file in files) {
            when {
                file.isDirectory -> out += collectAudio(file)
                file.isFile && isAudio(file) -> out += file.uri
            }
        }
        return out
    }

    private fun isAudio(file: DocumentFile): Boolean {
        if (file.type?.startsWith("audio/") == true) return true
        val name = file.name?.lowercase() ?: return false
        return name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav")
            || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".aac")
    }
}
