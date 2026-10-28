package com.pomodoro.timer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pomodoro.timer.ui.AppColors
import com.pomodoro.timer.ui.SoftCardShape
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(open: Boolean, onDismiss: () -> Unit, onChanged: () -> Unit) {
    if (!open) return
    val context = LocalContext.current
    val items = AppPermissions.checklist(context)
    val allOk = AppPermissions.allRequiredGranted(context)
    var islandX by remember(open) { mutableFloatStateOf(TimerStore.cutoutOffsetXDp(context).toFloat()) }
    var islandY by remember(open) { mutableFloatStateOf(TimerStore.cutoutOffsetYDp(context).toFloat()) }
    var islandScaleW by remember(open) { mutableFloatStateOf(TimerStore.islandScaleWidthPercent(context).toFloat()) }
    var islandScaleH by remember(open) { mutableFloatStateOf(TimerStore.islandScaleHeightPercent(context).toFloat()) }
    var islandStyle by remember(open) {
        mutableStateOf(TimerStore.islandCompactStyle(context))
    }
    var ringColor by remember(open) { mutableIntStateOf(TimerStore.islandRingColor(context)) }

    fun applyPosition() {
        TimerStore.setCutoutOffsetXDp(context, islandX.roundToInt())
        TimerStore.setCutoutOffsetYDp(context, islandY.roundToInt())
        IslandOverlay.applyPosition(context)
    }

    fun applyAppearance() {
        TimerStore.setIslandScaleWidthPercent(context, islandScaleW.roundToInt())
        TimerStore.setIslandScaleHeightPercent(context, islandScaleH.roundToInt())
        IslandOverlay.applyAppearance(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = SoftCardShape, color = AppColors.Cream) {
            Column(
                Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                Text("Permissions", style = MaterialTheme.typography.labelLarge)
                items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (item.granted) "OK" else "Needed",
                            color = if (item.granted) AppColors.Sage else AppColors.Terracotta,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Text(
                    if (allOk) "Permissions OK — island while unlocked; timer notification for lock screen / Now Bar."
                    else "Grant missing permissions so the island and timer can run.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Now Bar & lock screen", style = MaterialTheme.typography.labelLarge)
                Text(
                    "1) Start a session once and accept the live-notification prompt if shown.\n" +
                        "2) Settings → Lock screen → Now bar → enable just pomodoro → Show always when locked.\n" +
                        "3) Lock the phone — timer controls stay in the notification; Now bar is the strip at the bottom.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Sideloaded or debug APK (not from Play Store): Samsung often hides third-party apps from " +
                        "Now bar until you also turn on Developer options → Live notifications for all apps. " +
                        "The app cannot enable that for you. A Play Store install usually only needs steps 1–2.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { SessionNotificationBuilder.openNowBarSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open lock screen / Now bar settings") }
                if (Build.VERSION.SDK_INT >= 36 && !SessionNotificationBuilder.canUseLiveUpdate(context)) {
                    OutlinedButton(
                        onClick = { SessionNotificationBuilder.openPromotionSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Allow live / Now Bar notifications") }
                }

                Text("Island position", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Nudge the pill while a session runs. Default sits ~36dp from the top.",
                    style = MaterialTheme.typography.bodySmall
                )
                IslandIntSlider("Horizontal", islandX, -80f..80f, "dp") {
                    islandX = it
                    applyPosition()
                }
                IslandIntSlider("Vertical", islandY, -80f..80f, "dp") {
                    islandY = it
                    applyPosition()
                }

                Text("Island size", style = MaterialTheme.typography.labelLarge)
                IslandIntSlider("Width", islandScaleW, 60f..160f, "%") {
                    islandScaleW = it
                    applyAppearance()
                }
                IslandIntSlider("Height", islandScaleH, 60f..160f, "%") {
                    islandScaleH = it
                    applyAppearance()
                }

                Text("Compact progress style", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            islandStyle = TimerStore.IslandCompactStyle.BAR
                            TimerStore.setIslandCompactStyle(context, islandStyle)
                            IslandOverlay.applyAppearance(context)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = islandStyle != TimerStore.IslandCompactStyle.BAR
                    ) { Text("Bar") }
                    OutlinedButton(
                        onClick = {
                            islandStyle = TimerStore.IslandCompactStyle.RING
                            TimerStore.setIslandCompactStyle(context, islandStyle)
                            IslandOverlay.applyAppearance(context)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = islandStyle != TimerStore.IslandCompactStyle.RING
                    ) { Text("Ring") }
                }
                OutlinedButton(
                    onClick = {
                        islandStyle = TimerStore.IslandCompactStyle.RING_ONLY
                        TimerStore.setIslandCompactStyle(context, islandStyle)
                        IslandOverlay.applyAppearance(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = islandStyle != TimerStore.IslandCompactStyle.RING_ONLY
                ) { Text("Ring only (no pill)") }
                if (islandStyle != TimerStore.IslandCompactStyle.BAR) {
                    Text("Ring color", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            0xFFC56B4A.toInt() to "Terracotta",
                            0xFF8A9A7B.toInt() to "Sage",
                            0xFFF4EFE6.toInt() to "Cream",
                            0xFFFFFFFF.toInt() to "White",
                            0xFF5B8DEF.toInt() to "Blue",
                            0xFF2A2826.toInt() to "Ink",
                        ).forEach { (argb, _) ->
                            val selected = ringColor == argb
                            Box(
                                Modifier
                                    .size(if (selected) 34.dp else 28.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .clickable {
                                        ringColor = argb
                                        TimerStore.setIslandRingColor(context, argb)
                                        IslandOverlay.applyAppearance(context)
                                    }
                            )
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= 33 && !AppPermissions.notificationsGranted(context)) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                            onChanged()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open notification settings") }
                }
                if (!AppPermissions.overlayGranted(context)) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                            onChanged()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Allow display over other apps") }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
private fun IslandIntSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    val steps = (range.endInclusive - range.start).roundToInt().coerceAtLeast(1) - 1
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text("${value.roundToInt()} $unit", style = MaterialTheme.typography.labelLarge)
    }
    Slider(
        value = value.coerceIn(range),
        onValueChange = { onValueChange(it.coerceIn(range)) },
        valueRange = range,
        steps = steps.coerceAtMost(200),
        modifier = Modifier.fillMaxWidth()
    )
}
