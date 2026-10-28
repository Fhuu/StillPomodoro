package com.pomodoro.timer

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.pomodoro.timer.ui.AppColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MIN_MIN = 5
private const val MAX_MIN = 60
private const val STEP_MIN = 5

@Composable
fun SessionKnob(minutes: Int, onMinutesChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val clamped = snapMinutes(minutes)
    var dragAngle by remember { mutableFloatStateOf(minutesToAngle(clamped)) }
    var lastSnapped by remember { mutableIntStateOf(clamped) }
    LaunchedEffect(clamped) {
        dragAngle = minutesToAngle(clamped)
        lastSnapped = clamped
    }

    Box(modifier.then(Modifier.size(160.dp)), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val m = snapMinutes(angleToMinutes(touchAngle(pos, size.width, size.height)))
                            dragAngle = minutesToAngle(m)
                            if (m != lastSnapped) {
                                tickHaptic(view)
                                lastSnapped = m
                                onMinutesChange(m)
                            }
                        },
                        onDrag = { change, _ ->
                            val m = snapMinutes(angleToMinutes(touchAngle(change.position, size.width, size.height)))
                            dragAngle = minutesToAngle(m)
                            if (m != lastSnapped) {
                                tickHaptic(view)
                                lastSnapped = m
                                onMinutesChange(m)
                            }
                        }
                    )
                }
        ) {
            val stroke = 10.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val arcTopLeft = Offset(stroke / 2, stroke / 2)
            val r = size.minDimension / 2f - stroke
            val sweep = (clamped / 60f) * 360f

            drawArc(
                color = AppColors.CreamDeep,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke),
                topLeft = arcTopLeft,
                size = arcSize
            )
            if (sweep > 0f) {
                drawArc(
                    color = AppColors.Terracotta,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = arcTopLeft,
                    size = arcSize
                )
            }

            val rad = Math.toRadians(minutesToAngle(clamped).toDouble())
            val cx = size.width / 2f
            val cy = size.height / 2f
            val knobR = r * 0.72f
            drawCircle(
                color = AppColors.Ink,
                radius = 14.dp.toPx(),
                center = Offset(
                    cx + knobR * cos(rad).toFloat(),
                    cy + knobR * sin(rad).toFloat()
                )
            )
        }
        Text("$clamped min", style = MaterialTheme.typography.titleMedium)
    }
}

private fun tickHaptic(view: View) {
    val strength = 30
    if (strength <= 0) return
    val vibrator = view.context.getSystemService(Vibrator::class.java)
    if (vibrator != null && vibrator.hasVibrator()) {
        val amp = (strength * 255 / 100).coerceIn(1, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(14, amp))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(14)
        }
        return
    }
    if (strength >= 50) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

private fun touchAngle(pos: Offset, w: Int, h: Int): Float {
    val cx = w / 2f
    val cy = h / 2f
    return Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toFloat()
}

private fun minutesToAngle(minutes: Int): Float {
    val m = minutes.coerceIn(MIN_MIN, MAX_MIN)
    return -90f + (m / 60f) * 360f
}

private fun snapMinutes(minutes: Int): Int {
    val m = minutes.coerceIn(MIN_MIN, MAX_MIN)
    return ((m.toFloat() / STEP_MIN).roundToInt() * STEP_MIN).coerceIn(MIN_MIN, MAX_MIN)
}

private fun angleToMinutes(angle: Float): Int {
    var a = angle
    while (a < 0f) a += 360f
    while (a >= 360f) a -= 360f
    val fromTop = (a + 90f) % 360f
    val raw = (fromTop / 360f * 60f).roundToInt()
    return snapMinutes(raw)
}
