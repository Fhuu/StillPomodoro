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
            val target = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(target - field) < 0.0005f) return
            progressAnimator?.cancel()
            if (target <= field) {
                field = target
                invalidate()
                return
            }
            val from = field
            progressAnimator = ValueAnimator.ofFloat(from, target).apply {
                val step = ProgressRefresh.PROGRESS_STEP
                duration = (2000f * ((target - from) / step)).toLong().coerceIn(600L, 2000L)
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    field = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
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
    private var progressAnimator: ValueAnimator? = null

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
            pulseAnimator = ValueAnimator.ofFloat(0.55f, 1f).apply {
                duration = 2000
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
        progressAnimator?.cancel()
        setPulsing(false)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = arc.strokeWidth / 2f + 1f
        rect.set(pad, pad, width - pad, height - pad)
        if (drawTrack) {
            canvas.drawArc(rect, -90f, 360f, false, track)
        }
        val sweep = 360f * progress.coerceIn(0f, 1f)
        arc.color = ringColor
        arc.alpha = (ringAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawArc(rect, -90f, sweep, false, arc)
    }
}
