package com.pomodoro.timer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max

object NowBarArt {
    private const val SIZE_PX = 192

    fun icon(context: Context): Icon =
        Icon.createWithBitmap(render(context.applicationContext, TimerStore.phaseProgress(context)))

    private fun render(context: Context, progress: Float): Bitmap {
        val size = SIZE_PX
        val density = context.resources.displayMetrics.density
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val ringInset = 6f * density
        val artDiameter = size - ringInset * 2 - 8f * density
        val artLeft = (size - artDiameter) / 2f
        val artTop = artLeft

        val art = AmbientArt.currentBitmap()?.let { circleCrop(it, artDiameter.toInt()) }
            ?: circleCrop(fallbackBitmap(context, artDiameter.toInt()), artDiameter.toInt())
        canvas.drawBitmap(art, artLeft, artTop, null)
        art.recycle()

        drawRing(canvas, size.toFloat(), progress.coerceIn(0f, 1f), density)
        return out
    }

    private fun fallbackBitmap(context: Context, size: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_logo)
            ?: ContextCompat.getDrawable(context, R.drawable.ic_stat_pomodoro)!!
        return drawable.toBitmap(size, size)
    }

    private fun circleCrop(source: Bitmap, diameter: Int): Bitmap {
        val output = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(diameter.toFloat() / source.width, diameter.toFloat() / source.height)
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            (diameter - source.width * scale) / 2f,
            (diameter - source.height * scale) / 2f,
        )
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        canvas.drawCircle(diameter / 2f, diameter / 2f, diameter / 2f, paint)
        return output
    }

    private fun drawRing(canvas: Canvas, size: Float, progress: Float, density: Float) {
        val stroke = 4f * density
        val pad = stroke / 2f + 2f * density
        val rect = RectF(pad, pad, size - pad, size - pad)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = 0x553A3A3A
        }
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = stroke
            color = 0xFFC56B4A.toInt()
        }
        canvas.drawArc(rect, -90f, 360f, false, track)
        if (progress > 0f) {
            canvas.drawArc(rect, -90f, 360f * progress, false, arc)
        }
    }
}
