package com.pomodoro.timer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class IslandRingView(context: Context) : View(context) {
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var ringColor: Int = 0xFFC56B4A.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = 0xFF3A3A3A.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val pad = track.strokeWidth / 2f + 1f
        rect.set(pad, pad, width - pad, height - pad)
        canvas.drawArc(rect, -90f, 360f, false, track)
        if (progress > 0f) {
            arc.color = ringColor
            canvas.drawArc(rect, -90f, 360f * progress, false, arc)
        }
    }
}
