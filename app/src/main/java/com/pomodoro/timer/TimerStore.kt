package com.pomodoro.timer

import android.content.Context
import kotlin.math.max

enum class Phase { IDLE, FOCUS, BREAK }

enum class AmbientMode { OFF, FILE, FOLDER }

object TimerStore {
    private const val PREFS = "pomodoro"

    fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun phase(context: Context): Phase =
        Phase.valueOf(prefs(context).getString("phase", Phase.IDLE.name)!!)

    fun setPhase(context: Context, phase: Phase) {
        prefs(context).edit().putString("phase", phase.name).apply()
    }

    fun endAtMillis(context: Context): Long =
        prefs(context).getLong("end_at", 0L)

    fun setEndAtMillis(context: Context, value: Long) {
        prefs(context).edit().putLong("end_at", value).apply()
    }

    fun remainingSeconds(context: Context): Int {
        if (isSessionPaused(context)) {
            return prefs(context).getInt("paused_remaining", 0)
        }
        val end = endAtMillis(context)
        if (end <= 0L) return 0
        return max(0, ((end - System.currentTimeMillis()) / 1000).toInt())
    }

    fun isSessionPaused(context: Context) = prefs(context).getBoolean("session_paused", false)

    fun setSessionPaused(context: Context, paused: Boolean, phase: Phase, remainingSec: Int) {
        prefs(context).edit()
            .putBoolean("session_paused", paused)
            .putString("paused_phase", phase.name)
            .putInt("paused_remaining", remainingSec)
            .apply()
    }

    fun clearSessionPaused(context: Context) {
        prefs(context).edit()
            .putBoolean("session_paused", false)
            .remove("paused_phase")
            .remove("paused_remaining")
            .apply()
    }

    fun pausedPhase(context: Context): Phase {
        val name = prefs(context).getString("paused_phase", Phase.IDLE.name)!!
        return Phase.valueOf(name)
    }

    fun pausedRemainingSeconds(context: Context) =
        prefs(context).getInt("paused_remaining", 0)

    fun focusMinutes(context: Context) = prefs(context).getInt("focus_min", 25)
    fun breakMinutes(context: Context) = prefs(context).getInt("break_min", 5)

    fun setFocusMinutes(context: Context, minutes: Int) {
        val step = 5
        val m = ((minutes.coerceIn(5, 60) + step / 2) / step * step).coerceIn(5, 60)
        prefs(context).edit().putInt("focus_min", m).apply()
    }

    fun ambientMode(context: Context): AmbientMode =
        AmbientMode.valueOf(prefs(context).getString("ambient_mode", AmbientMode.OFF.name)!!)

    fun ambientUri(context: Context): String? =
        prefs(context).getString("ambient_uri", null)?.takeIf { it.isNotBlank() }

    fun ambientLabel(context: Context): String? =
        prefs(context).getString("ambient_label", null)

    fun nowPlayingUri(context: Context): String? =
        prefs(context).getString("now_playing_uri", null)

    fun setNowPlayingUri(context: Context, uri: String?) {
        prefs(context).edit().apply {
            if (uri == null) remove("now_playing_uri") else putString("now_playing_uri", uri)
        }.apply()
    }

    fun setAmbientFile(context: Context, uri: String, label: String) {
        prefs(context).edit()
            .putString("ambient_mode", AmbientMode.FILE.name)
            .putString("ambient_uri", uri)
            .putString("ambient_label", label)
            .apply()
    }

    fun setAmbientFolder(context: Context, treeUri: String, label: String) {
        prefs(context).edit()
            .putString("ambient_mode", AmbientMode.FOLDER.name)
            .putString("ambient_uri", treeUri)
            .putString("ambient_label", label)
            .apply()
    }

    fun clearAmbient(context: Context) {
        prefs(context).edit()
            .putString("ambient_mode", AmbientMode.OFF.name)
            .remove("ambient_uri")
            .remove("ambient_label")
            .apply()
    }

    fun formatRemaining(context: Context): String {
        val sec = remainingSeconds(context)
        if (phase(context) == Phase.IDLE && sec == 0) return "--:--"
        return "%02d:%02d".format(sec / 60, sec % 60)
    }

    /** Minutes left (rounded up), for compact island. */
    fun formatRemainingMinutes(context: Context): String {
        val sec = remainingSeconds(context)
        if (phase(context) == Phase.IDLE && sec == 0) return "--"
        if (sec <= 0) return "0"
        return ((sec + 59) / 60).toString()
    }

    fun phaseLabel(context: Context): String = when (phase(context)) {
        Phase.FOCUS -> "Focus"
        Phase.BREAK -> "Break"
        Phase.IDLE -> "Ready"
    }

    fun phaseDurationSeconds(context: Context): Int = when (phase(context)) {
        Phase.FOCUS -> focusMinutes(context) * 60
        Phase.BREAK -> breakMinutes(context) * 60
        Phase.IDLE -> 0
    }

    /** 0 = just started, 1 = phase complete */
    fun phaseProgress(context: Context): Float {
        val total = when {
            isSessionPaused(context) -> when (pausedPhase(context)) {
                Phase.FOCUS -> focusMinutes(context) * 60
                Phase.BREAK -> breakMinutes(context) * 60
                Phase.IDLE -> 0
            }
            else -> phaseDurationSeconds(context)
        }
        if (total <= 0) return 0f
        val done = total - remainingSeconds(context)
        return (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Horizontal nudge of island from screen center (dp). */
    fun cutoutOffsetXDp(context: Context) = prefs(context).getInt("cutout_offset_x_dp", 0)

    fun setCutoutOffsetXDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("cutout_offset_x_dp", dp.coerceIn(-80, 80)).apply()
    }

    /** Vertical nudge from default pill position (dp). Positive = down. */
    fun cutoutOffsetYDp(context: Context) = prefs(context).getInt("cutout_offset_y_dp", 0)

    fun setCutoutOffsetYDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("cutout_offset_y_dp", dp.coerceIn(-80, 80)).apply()
    }

    enum class IslandCompactStyle { BAR, RING }

    private fun legacyIslandScale(context: Context): Int =
        prefs(context).getInt("island_scale_pct", 100).coerceIn(60, 160)

    fun islandScaleWidthPercent(context: Context) =
        prefs(context).getInt("island_scale_w_pct", legacyIslandScale(context)).coerceIn(60, 160)

    fun setIslandScaleWidthPercent(context: Context, pct: Int) {
        prefs(context).edit().putInt("island_scale_w_pct", pct.coerceIn(60, 160)).apply()
    }

    fun islandScaleHeightPercent(context: Context) =
        prefs(context).getInt("island_scale_h_pct", legacyIslandScale(context)).coerceIn(60, 160)

    fun setIslandScaleHeightPercent(context: Context, pct: Int) {
        prefs(context).edit().putInt("island_scale_h_pct", pct.coerceIn(60, 160)).apply()
    }

    fun islandCompactStyle(context: Context): IslandCompactStyle {
        val name = prefs(context).getString("island_compact_style", IslandCompactStyle.BAR.name)!!
        return runCatching { IslandCompactStyle.valueOf(name) }.getOrDefault(IslandCompactStyle.BAR)
    }

    fun setIslandCompactStyle(context: Context, style: IslandCompactStyle) {
        prefs(context).edit().putString("island_compact_style", style.name).apply()
    }

    fun isSessionActive(context: Context): Boolean {
        if (isSessionPaused(context)) return pausedRemainingSeconds(context) > 0
        return phase(context) != Phase.IDLE && remainingSeconds(context) > 0
    }
}
