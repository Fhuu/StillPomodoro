package com.pomodoro.timer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

fun promptOverlayIfNeeded(context: Context) {
    if (IslandOverlay.canDraw(context)) return
    Toast.makeText(context, "Allow \"Display over other apps\" for the dynamic island", Toast.LENGTH_LONG).show()
    context.startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
