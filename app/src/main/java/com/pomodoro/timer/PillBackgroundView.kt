package com.pomodoro.timer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class PillBackgroundView(context: Context) : View(context) {
    var cornerRadiusPx: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    var fillColor: Int = 0xFF000000.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cornerRadiusPx <= 0f && w > 0 && h > 0) cornerRadiusPx = minOf(w, h) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        paint.color = fillColor
        val r = cornerRadiusPx.coerceIn(0f, minOf(width, height) / 2f)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
    }
}
