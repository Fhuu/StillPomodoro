package com.pomodoro.timer

import android.content.Context
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/** Display-only Dynamic Island pill; controls live in the notification shade. */
object IslandOverlay {
    private const val COMPACT_W_DP = 94
    private const val COMPACT_H_DP = 34
    private const val RING_ONLY_BASE_DP = 30
    private const val PILL_TOP_BASE_DP = 36

    private var wm: WindowManager? = null
    private var shell: FrameLayout? = null
    private var compactPanel: LinearLayout? = null
    private var compactRow: LinearLayout? = null
    private var compactTime: TextView? = null
    private var compactProgress: IslandProgressBar? = null
    private var compactRing: IslandRingView? = null
    private var compactSpacer: View? = null
    private var pillBg: PillBackgroundView? = null
    private var appContext: Context? = null
    private var compactW = 0
    private var compactH = 0

    fun canDraw(context: Context): Boolean =
        Settings.canDrawOverlays(context.applicationContext)

    fun show(context: Context) {
        if (!canDraw(context)) return
        val ctx = context.applicationContext
        if (shell != null) {
            applyPosition(ctx)
            refresh(ctx)
            return
        }
        appContext = ctx
        wm = ctx.getSystemService(WindowManager::class.java)
        measureSizes(ctx)
        buildViews(ctx)
        try {
            val view = shell!!
            view.scaleX = 0.5f
            view.scaleY = 0.5f
            view.alpha = 0f
            wm!!.addView(view, layoutParams(displayMetricsContext(ctx), ctx, compactW, compactH))
            view.post { updateShellLook(ctx, compactW, compactH) }
            view.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(340)
                .setInterpolator(OvershootInterpolator(1.25f))
                .start()
            refresh(ctx)
        } catch (_: Exception) {
            hide()
        }
    }

    fun ensureVisible(context: Context) {
        if (!canDraw(context)) return
        if (shell == null) show(context) else {
            applyPosition(context)
            refresh(context)
        }
    }

    fun hide() {
        compactRing?.setPulsing(false)
        shell?.let { wm?.removeView(it) }
        shell = null
        wm = null
        appContext = null
        compactPanel = null
        compactRow = null
        compactTime = null
        compactProgress = null
        compactRing = null
        compactSpacer = null
        pillBg = null
    }

    fun applyAppearance(context: Context) {
        val ctx = appContext ?: context.applicationContext
        if (shell == null) return
        measureSizes(ctx)
        syncCompactStyle(ctx)
        updateRingSize(ctx)
        updateShellLook(ctx, compactW, compactH)
        applyPosition(ctx)
        refresh(ctx)
    }

    fun applyPosition(context: Context) {
        val prefsCtx = appContext ?: context.applicationContext
        val view = shell ?: return
        val windowManager = wm ?: return
        val metricsCtx = displayMetricsContext(prefsCtx)
        val lp = layoutParams(metricsCtx, prefsCtx, compactW, compactH)
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: Exception) {
            try {
                windowManager.removeView(view)
                windowManager.addView(view, lp)
            } catch (_: Exception) {
            }
        }
        view.post { updateShellLook(prefsCtx, compactW, compactH) }
    }

    fun refresh(context: Context) {
        val ctx = appContext ?: context.applicationContext
        val phase = TimerStore.phase(ctx)
        val style = TimerStore.islandCompactStyle(ctx)
        val time = TimerStore.formatRemainingMinutes(ctx)
        val prog = TimerStore.phaseProgress(ctx)
        val barColor = accentColor(phase)
        val ringColor = TimerStore.islandRingColor(ctx)
        val running = TimerStore.isSessionActive(ctx) && !TimerStore.isSessionPaused(ctx)
        compactTime?.text = time
        compactProgress?.progress = prog
        compactProgress?.barColor = barColor
        compactRing?.progress = prog
        compactRing?.ringColor = ringColor
        compactRing?.drawTrack = style != TimerStore.IslandCompactStyle.RING_ONLY
        compactRing?.setPulsing(style == TimerStore.IslandCompactStyle.RING_ONLY && running)
    }

    fun reloadArt(context: Context) {
        // Expanded / art UI removed; notification controls session.
    }

    private fun displayMetricsContext(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val display = context.getSystemService(DisplayManager::class.java)
                    ?.getDisplay(Display.DEFAULT_DISPLAY)
                if (display != null) return context.createDisplayContext(display)
            } catch (_: Exception) {
            }
        }
        return context
    }

    private fun buildViews(context: Context) {
        compactTime = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.05f
            isClickable = false
        }
        compactSpacer = View(context).apply {
            isClickable = false
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        compactProgress = IslandProgressBar(context).apply {
            isClickable = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 4)
            ).apply { topMargin = dp(context, 5) }
        }
        compactRing = IslandRingView(context).apply {
            isClickable = false
            layoutParams = LinearLayout.LayoutParams(dp(context, 20), dp(context, 20))
            visibility = View.GONE
        }
        val compactRowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(
                compactTime,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(compactSpacer)
            addView(compactRing)
        }
        compactRow = compactRowLayout
        compactPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = false
            isFocusable = false
            setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
            addView(compactRowLayout)
            addView(compactProgress)
        }
        shell = FrameLayout(context).apply {
            isClickable = false
            isFocusable = false
            setBackgroundColor(0)
        }
        pillBg = PillBackgroundView(context).apply { isClickable = false }
        shell!!.addView(pillBg, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        shell!!.addView(compactPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        syncCompactStyle(context)
        updateShellLook(context, compactW, compactH)
    }

    private fun measureSizes(context: Context) {
        val sw = TimerStore.islandScaleWidthPercent(context) / 100f
        val sh = TimerStore.islandScaleHeightPercent(context) / 100f
        if (TimerStore.islandCompactStyle(context) == TimerStore.IslandCompactStyle.RING_ONLY) {
            val side = dp(context, (RING_ONLY_BASE_DP * sw).roundToInt())
            compactW = side
            compactH = side
            return
        }
        compactW = dp(context, (COMPACT_W_DP * sw).roundToInt())
        val scaledH = dp(context, (COMPACT_H_DP * sh).roundToInt())
        compactH = max(scaledH, compactContentMinHeight(context))
    }

    private fun compactContentMinHeight(context: Context): Int {
        val style = TimerStore.islandCompactStyle(context)
        if (style == TimerStore.IslandCompactStyle.RING_ONLY) {
            return dp(context, RING_ONLY_BASE_DP)
        }
        val padV = dp(context, 10)
        val bar = style == TimerStore.IslandCompactStyle.BAR
        val row = dp(context, 18)
        val progress = if (bar) dp(context, 9) else 0
        val ring = if (bar) 0 else dp(context, 22)
        return padV + max(row, ring) + progress
    }

    private fun syncCompactStyle(context: Context) {
        val style = TimerStore.islandCompactStyle(context)
        val bar = style == TimerStore.IslandCompactStyle.BAR
        val ringOnly = style == TimerStore.IslandCompactStyle.RING_ONLY
        pillBg?.visibility = if (ringOnly) View.GONE else View.VISIBLE
        compactProgress?.visibility = if (bar) View.VISIBLE else View.GONE
        compactRing?.visibility = if (bar) View.GONE else View.VISIBLE
        compactTime?.visibility = if (ringOnly) View.GONE else View.VISIBLE
        compactSpacer?.visibility = if (style == TimerStore.IslandCompactStyle.RING) View.VISIBLE else View.GONE
        val pad = if (ringOnly) 0 else dp(context, 10)
        val padV = if (ringOnly) 0 else dp(context, 5)
        compactPanel?.setPadding(pad, padV, pad, padV)
        val rowLp = compactRow?.layoutParams as? LinearLayout.LayoutParams
        if (rowLp != null) {
            rowLp.height = if (ringOnly) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT
            compactRow?.layoutParams = rowLp
            compactRow?.gravity = if (ringOnly) Gravity.CENTER else Gravity.CENTER_VERTICAL
        }
        val timeLp = compactTime?.layoutParams as? LinearLayout.LayoutParams
        if (timeLp != null) {
            if (bar) {
                timeLp.width = LinearLayout.LayoutParams.MATCH_PARENT
                timeLp.weight = 0f
                compactTime?.gravity = Gravity.CENTER
            } else if (!ringOnly) {
                timeLp.width = LinearLayout.LayoutParams.WRAP_CONTENT
                timeLp.weight = 0f
                compactTime?.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            compactTime?.layoutParams = timeLp
        }
        val ringLp = compactRing?.layoutParams as? LinearLayout.LayoutParams ?: return
        if (ringOnly) {
            ringLp.width = LinearLayout.LayoutParams.MATCH_PARENT
            ringLp.height = LinearLayout.LayoutParams.MATCH_PARENT
            ringLp.weight = 0f
            compactPanel?.gravity = Gravity.CENTER
        } else {
            compactPanel?.gravity = Gravity.CENTER_VERTICAL
            updateRingSize(context)
        }
        compactRing?.layoutParams = ringLp
    }

    private fun updateRingSize(context: Context) {
        if (TimerStore.islandCompactStyle(context) != TimerStore.IslandCompactStyle.RING) return
        val size = (compactH * 0.58f).roundToInt().coerceIn(dp(context, 16), dp(context, 26))
        compactRing?.layoutParams = LinearLayout.LayoutParams(size, size)
    }

    private fun updateShellLook(context: Context, widthPx: Int, heightPx: Int) {
        if (TimerStore.islandCompactStyle(context) == TimerStore.IslandCompactStyle.RING_ONLY) return
        val radius = if (widthPx > 0 && heightPx > 0) minOf(widthPx, heightPx) / 2f
        else dp(context, COMPACT_H_DP) / 2f
        pillBg?.fillColor = 0xFF000000.toInt()
        pillBg?.cornerRadiusPx = radius
    }

    private fun layoutParams(
        windowCtx: Context,
        prefsCtx: Context,
        width: Int,
        height: Int
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dp(windowCtx, TimerStore.cutoutOffsetXDp(prefsCtx))
            y = pillTopY(windowCtx, prefsCtx)
        }
    }

    private fun pillTopY(windowCtx: Context, prefsCtx: Context): Int =
        dp(windowCtx, PILL_TOP_BASE_DP + TimerStore.cutoutOffsetYDp(prefsCtx))

    private fun accentColor(phase: Phase) = when (phase) {
        Phase.FOCUS -> 0xFFC56B4A.toInt()
        Phase.BREAK -> 0xFF8A9A7B.toInt()
        Phase.IDLE -> 0xFF888888.toInt()
    }

    private fun dp(context: Context, v: Int): Int =
        (v * context.resources.displayMetrics.density).roundToInt()

    private const val MATCH_PARENT = FrameLayout.LayoutParams.MATCH_PARENT
}
