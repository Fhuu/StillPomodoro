package com.pomodoro.sound

import android.annotation.SuppressLint
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.lang.ref.WeakReference

class AmbientTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        listening = WeakReference(this)
        updateState()
    }

    private fun updateState() {
        qsTile?.apply {
            state = if (QuickSoundsService.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Ambient sounds"
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                subtitle = if (QuickSoundsService.isPlaying) "Playing" else "Off"
            }
            contentDescription = "Ambient sounds. Tap to toggle; hold for sound settings."
            updateTile()
        }
    }

    override fun onStopListening() {
        if (listening?.get() === this) listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        if (listening?.get() === this) listening = null
        super.onDestroy()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is required below Android 14.
    override fun onClick() {
        super.onClick()
        if (!QuickSoundsService.isPlaying && QuickSoundsStore.selection(this).isEmpty()) {
            val intent = android.content.Intent(this, SoundPanelActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } else {
            QuickSoundsService.toggle(this)
        }
    }

    companion object {
        private var listening: WeakReference<AmbientTileService>? = null

        fun refresh() {
            listening?.get()?.updateState()
        }
    }
}
