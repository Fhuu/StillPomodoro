package com.pomodoro.timer

import android.content.Context
import kotlin.math.max
import kotlin.math.roundToInt

enum class Phase { IDLE, FOCUS, BREAK }

enum class AmbientMode { OFF, LIBRARY, YOUTUBE }

data class SessionControlFingerprint(val phase: Phase, val paused: Boolean, val endAtMillis: Long)

object TimerStore {
    private const val PREFS = "pomodoro"

    /** Knob snap step in minutes (5 min steps, 5–60 min). */
    val FOCUS_KNOB_STEP_MINUTES = 5f

    private fun focusKnobStepSeconds(): Int = (FOCUS_KNOB_STEP_MINUTES * 60f).roundToInt().coerceAtLeast(1)

    fun snapFocusSeconds(seconds: Int): Int {
        val step = focusKnobStepSeconds()
        val min = step
        val max = 60 * 60
        val s = seconds.coerceIn(min, max)
        return ((s.toFloat() / step).roundToInt() * step).coerceIn(min, max)
    }

    fun focusDurationSeconds(context: Context): Int {
        val p = prefs(context)
        val raw = if (p.contains("focus_sec")) {
            p.getInt("focus_sec", 25 * 60)
        } else {
            p.getInt("focus_min", 25) * 60
        }
        return snapFocusSeconds(raw)
    }

    fun setFocusDurationSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt("focus_sec", snapFocusSeconds(seconds)).apply()
    }

    fun formatFocusDurationLabel(seconds: Int): String {
        val s = snapFocusSeconds(seconds)
        if (s < 60) return "${s}s"
        if (s % 60 == 0) return "${s / 60} min"
        if (s % 30 == 0) return "${s / 60}.5 min"
        return "%d:%02d".format(s / 60, s % 60)
    }

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

    /** Length of the current phase run (set when the phase starts). */
    fun runningPhaseTotalSeconds(context: Context): Int {
        val stored = prefs(context).getInt("phase_total_sec", 0)
        if (stored > 0 && endAtMillis(context) > 0L) return stored
        return phaseDurationSeconds(context)
    }

    fun setRunningPhaseTotalSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt("phase_total_sec", seconds.coerceAtLeast(0)).apply()
    }

    fun clearRunningPhaseTotalSeconds(context: Context) {
        prefs(context).edit().remove("phase_total_sec").apply()
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

    fun breakMinutes(context: Context) = prefs(context).getInt("break_min", 5)

    fun setFocusMinutes(context: Context, minutes: Int) {
        setFocusDurationSeconds(context, minutes * 60)
    }

    /** How often island / widget refresh on minute boundaries (1 or 5). */
    fun uiRefreshMinuteStep(context: Context): Int {
        val v = prefs(context).getInt("ui_refresh_minute_step", 1)
        return if (v == 1) 1 else 5
    }

    fun setUiRefreshMinuteStep(context: Context, minutes: Int) {
        prefs(context).edit().putInt("ui_refresh_minute_step", if (minutes == 1) 1 else 5).apply()
    }

    fun ambientMode(context: Context): AmbientMode {
        val raw = prefs(context).getString("ambient_mode", AmbientMode.OFF.name)!!
        return when (raw) {
            "FILE", "FOLDER" -> AmbientMode.LIBRARY
            else -> runCatching { AmbientMode.valueOf(raw) }.getOrDefault(AmbientMode.OFF)
        }
    }

    fun libraryUri(context: Context): String? =
        prefs(context).getString("music_library_uri", null)?.takeIf { it.isNotBlank() }
            ?: prefs(context).getString("ambient_uri", null)?.takeIf { it.isNotBlank() }

    fun selectedTrackUri(context: Context): String? =
        prefs(context).getString("selected_track_uri", null)?.takeIf { it.isNotBlank() }

    fun youtubeVideoId(context: Context): String? =
        prefs(context).getString("youtube_video_id", null)?.takeIf { it.isNotBlank() }

    fun ambientLabel(context: Context): String? = when (ambientMode(context)) {
        AmbientMode.YOUTUBE -> prefs(context).getString("youtube_title", null)
            ?: prefs(context).getString("ambient_label", null)
        AmbientMode.LIBRARY -> prefs(context).getString("selected_track_label", null)
            ?: prefs(context).getString("ambient_label", null)
        AmbientMode.OFF -> null
    }

    fun nowPlayingUri(context: Context): String? =
        prefs(context).getString("now_playing_uri", null)

    fun setNowPlayingUri(context: Context, uri: String?) {
        prefs(context).edit().apply {
            if (uri == null) remove("now_playing_uri") else putString("now_playing_uri", uri)
        }.apply()
    }

    fun setMusicLibrary(context: Context, treeUri: String, label: String) {
        prefs(context).edit()
            .putString("ambient_mode", AmbientMode.LIBRARY.name)
            .putString("music_library_uri", treeUri)
            .putString("ambient_label", label)
            .apply()
    }

    fun setSelectedTrack(context: Context, uri: String, title: String) {
        prefs(context).edit()
            .putString("ambient_mode", AmbientMode.LIBRARY.name)
            .putString("selected_track_uri", uri)
            .putString("selected_track_label", title)
            .apply()
    }

    fun setYoutubeSource(context: Context, videoId: String, title: String) {
        prefs(context).edit()
            .putString("ambient_mode", AmbientMode.YOUTUBE.name)
            .putString("youtube_video_id", videoId)
            .putString("youtube_title", title)
            .putString("ambient_label", title)
            .apply()
    }

    fun clearYoutubeSelection(context: Context) {
        prefs(context).edit()
            .remove("youtube_video_id")
            .remove("youtube_title")
            .apply()
        if (ambientMode(context) == AmbientMode.YOUTUBE) {
            prefs(context).edit()
                .putString("ambient_mode", AmbientMode.OFF.name)
                .remove("ambient_label")
                .apply()
        }
    }

    fun clearAmbient(context: Context) {
        prefs(context).edit()
            .putString("ambient_mode", AmbientMode.OFF.name)
            .remove("music_library_uri")
            .remove("ambient_uri")
            .remove("ambient_label")
            .remove("selected_track_uri")
            .remove("selected_track_label")
            .remove("youtube_video_id")
            .remove("youtube_title")
            .apply()
    }

    fun formatSecondsRemaining(sec: Int, showIdlePlaceholder: Boolean = false): String {
        if (showIdlePlaceholder && sec == 0) return "--:--"
        return "%02d:%02d".format(sec / 60, sec % 60)
    }

    fun formatRemaining(context: Context): String {
        val sec = remainingSeconds(context)
        return formatSecondsRemaining(sec, showIdlePlaceholder = phase(context) == Phase.IDLE)
    }

    /** Minutes left for island, widget, and notification (step matches timer update setting). */
    fun formatRemainingMinutes(context: Context): String {
        val sec = remainingSeconds(context)
        if (phase(context) == Phase.IDLE && sec == 0) return "--"
        if (sec <= 0) return "0"
        val minuteLeft = (sec + 59) / 60
        val step = uiRefreshMinuteStep(context)
        if (step <= 1 || minuteLeft <= step) return minuteLeft.toString()
        val snapped = ((minuteLeft - 1) / step + 1) * step
        return snapped.toString()
    }

    /** Notification / widget copy for stepped minutes remaining. */
    fun minutesLeftPhrase(context: Context): String {
        val min = formatRemainingMinutes(context)
        if (min == "--") return "--"
        if (min == "0") return "less than 1 minute left"
        return if (min == "1") "less than 1 minute left" else "less than $min minutes left"
    }

    fun phaseLabel(context: Context): String = when (phase(context)) {
        Phase.FOCUS -> "Focus"
        Phase.BREAK -> "Break"
        Phase.IDLE -> "Ready"
    }

    fun phaseDurationSeconds(context: Context): Int = when (phase(context)) {
        Phase.FOCUS -> focusDurationSeconds(context)
        Phase.BREAK -> breakMinutes(context) * 60
        Phase.IDLE -> 0
    }

    /** 0 = just started, 1 = phase complete (millisecond-accurate while running). */
    fun phaseProgress(context: Context): Float {
        val totalSec = when {
            isSessionPaused(context) -> when (pausedPhase(context)) {
                Phase.FOCUS, Phase.BREAK -> runningPhaseTotalSeconds(context).takeIf { it > 0 }
                    ?: phaseDurationSeconds(context)
                Phase.IDLE -> 0
            }
            else -> runningPhaseTotalSeconds(context)
        }
        if (totalSec <= 0) return 0f
        val totalMs = totalSec * 1000L
        if (isSessionPaused(context)) {
            val rem = pausedRemainingSeconds(context)
            return ((totalSec - rem).toFloat() / totalSec).coerceIn(0f, 1f)
        }
        val end = endAtMillis(context)
        if (end <= 0L) return 0f
        val msLeft = (end - System.currentTimeMillis()).coerceAtLeast(0L)
        return ((totalMs - msLeft).toFloat() / totalMs).coerceIn(0f, 1f)
    }

    /** Horizontal nudge of island from screen center (dp). */
    fun cutoutOffsetXDp(context: Context) =
        cutoutOffsetXDp(context, islandCompactStyle(context))

    fun cutoutOffsetXDp(context: Context, style: IslandCompactStyle): Int =
        islandInt(context, style, "x", "cutout_offset_x_dp", 0).coerceIn(-80, 80)

    fun setCutoutOffsetXDp(context: Context, dp: Int) =
        setCutoutOffsetXDp(context, islandCompactStyle(context), dp)

    fun setCutoutOffsetXDp(context: Context, style: IslandCompactStyle, dp: Int) {
        prefs(context).edit().putInt(islandKey(style, "x"), dp.coerceIn(-80, 80)).apply()
    }

    /** Vertical nudge from default pill position (dp). Positive = down. */
    fun cutoutOffsetYDp(context: Context) =
        cutoutOffsetYDp(context, islandCompactStyle(context))

    fun cutoutOffsetYDp(context: Context, style: IslandCompactStyle): Int =
        islandInt(context, style, "y", "cutout_offset_y_dp", 0).coerceIn(-80, 80)

    fun setCutoutOffsetYDp(context: Context, dp: Int) =
        setCutoutOffsetYDp(context, islandCompactStyle(context), dp)

    fun setCutoutOffsetYDp(context: Context, style: IslandCompactStyle, dp: Int) {
        prefs(context).edit().putInt(islandKey(style, "y"), dp.coerceIn(-80, 80)).apply()
    }

    enum class IslandCompactStyle { BAR, RING, RING_ONLY }

    private fun legacyIslandScale(context: Context): Int =
        prefs(context).getInt("island_scale_pct", 100).coerceIn(60, 160)

    fun islandScaleWidthPercent(context: Context) =
        islandScaleWidthPercent(context, islandCompactStyle(context))

    fun islandScaleWidthPercent(context: Context, style: IslandCompactStyle): Int =
        islandInt(context, style, "scale_w", "island_scale_w_pct", legacyIslandScale(context))
            .coerceIn(60, 160)

    fun setIslandScaleWidthPercent(context: Context, pct: Int) =
        setIslandScaleWidthPercent(context, islandCompactStyle(context), pct)

    fun setIslandScaleWidthPercent(context: Context, style: IslandCompactStyle, pct: Int) {
        prefs(context).edit().putInt(islandKey(style, "scale_w"), pct.coerceIn(60, 160)).apply()
    }

    fun islandScaleHeightPercent(context: Context) =
        islandScaleHeightPercent(context, islandCompactStyle(context))

    fun islandScaleHeightPercent(context: Context, style: IslandCompactStyle): Int =
        islandInt(context, style, "scale_h", "island_scale_h_pct", legacyIslandScale(context))
            .coerceIn(60, 160)

    fun setIslandScaleHeightPercent(context: Context, pct: Int) =
        setIslandScaleHeightPercent(context, islandCompactStyle(context), pct)

    fun setIslandScaleHeightPercent(context: Context, style: IslandCompactStyle, pct: Int) {
        prefs(context).edit().putInt(islandKey(style, "scale_h"), pct.coerceIn(60, 160)).apply()
    }

    private fun islandKey(style: IslandCompactStyle, field: String): String =
        "island_${style.name.lowercase()}_$field"

    private fun islandInt(
        context: Context,
        style: IslandCompactStyle,
        field: String,
        legacyKey: String,
        default: Int,
    ): Int {
        val p = prefs(context)
        val key = islandKey(style, field)
        if (!p.contains(key)) {
            val seed = when (field) {
                "scale_w" -> p.getInt(legacyKey, legacyIslandScale(context))
                "scale_h" -> p.getInt(legacyKey, legacyIslandScale(context))
                else -> p.getInt(legacyKey, default)
            }
            p.edit().putInt(key, seed).apply()
            return seed
        }
        return p.getInt(key, default)
    }

    fun islandCompactStyle(context: Context): IslandCompactStyle {
        val name = prefs(context).getString("island_compact_style", IslandCompactStyle.BAR.name)!!
        return runCatching { IslandCompactStyle.valueOf(name) }.getOrDefault(IslandCompactStyle.BAR)
    }

    fun setIslandCompactStyle(context: Context, style: IslandCompactStyle) {
        prefs(context).edit().putString("island_compact_style", style.name).apply()
    }

    fun islandRingColor(context: Context): Int =
        islandRingColor(context, islandCompactStyle(context))

    fun islandRingColor(context: Context, style: IslandCompactStyle): Int =
        islandInt(context, style, "ring_color", "island_ring_color", 0xFFC56B4A.toInt())

    fun setIslandRingColor(context: Context, color: Int) =
        setIslandRingColor(context, islandCompactStyle(context), color)

    fun setIslandRingColor(context: Context, style: IslandCompactStyle, color: Int) {
        prefs(context).edit().putInt(islandKey(style, "ring_color"), color).apply()
    }

    fun isSessionActive(context: Context): Boolean {
        if (isSessionPaused(context)) return pausedRemainingSeconds(context) > 0
        return phase(context) != Phase.IDLE && remainingSeconds(context) > 0
    }

    fun sessionControlFingerprint(context: Context) = SessionControlFingerprint(
        phase(context),
        isSessionPaused(context),
        endAtMillis(context),
    )
}
