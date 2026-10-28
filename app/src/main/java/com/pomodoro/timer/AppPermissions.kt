package com.pomodoro.timer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object AppPermissions {
    data class Item(val title: String, val granted: Boolean, val required: Boolean = true)

    fun notificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun overlayGranted(context: Context): Boolean = IslandOverlay.canDraw(context)

    fun allRequiredGranted(context: Context): Boolean =
        notificationsGranted(context) && overlayGranted(context)

    fun checklist(context: Context): List<Item> = listOf(
        Item("Notifications (timer in status bar)", notificationsGranted(context)),
        Item("Display over other apps (dynamic island)", overlayGranted(context)),
    )
}
