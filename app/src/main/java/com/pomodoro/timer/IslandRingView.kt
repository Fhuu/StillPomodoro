package com.pomodoro.timer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

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
    var drawTrack: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private var ringAlpha = 1f
    private var pulseAnimator: ValueAnimator? = null

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

    fun setPulsing(active: Boolean) {
        if (active) {
            if (pulseAnimator?.isRunning == true) return
            pulseAnimator?.cancel()
            pulseAnimator = ValueAnimator.ofFloat(0.4f, 1f).apply {
                duration = 900
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    ringAlpha = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            pulseAnimator?.cancel()
            pulseAnimator = null
            ringAlpha = 1f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        setPulsing(false)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = arc.strokeWidth / 2f + 1f
        rect.set(pad, pad, width - pad, height - pad)
        if (drawTrack) {
            canvas.drawArc(rect, -90f, 360f, false, track)
        }
        val sweep = if (progress > 0f) 360f * progress else 360f
        arc.color = ringColor
        arc.alpha = (ringAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawArc(rect, -90f, sweep, false, arc)
    }
}
