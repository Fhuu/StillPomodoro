package com.pomodoro.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.time.Instant

object SessionNotificationBuilder {
    private const val TAG = "PomodoroNowBar"

    fun canUseLiveUpdate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
    }

    fun openPromotionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val intents = listOf(
            "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS",
            "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS",
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
        )
        for (action in intents) {
            val intent = Intent(action).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    fun openNowBarSettings(context: Context): Boolean {
        val intents = listOf(
            "android.settings.LOCK_SCREEN_SETTINGS",
            Settings.ACTION_DISPLAY_SETTINGS,
        )
        for (action in intents) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    fun build(
        context: Context,
        channelId: String,
        openIntent: PendingIntent,
        pauseIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Notification = when {
        Build.VERSION.SDK_INT >= 36 -> buildApi36(context, channelId, openIntent, pauseIntent, stopIntent)
        isSamsung() -> buildSamsungStandard(context, channelId, openIntent, pauseIntent, stopIntent)
        else -> buildStandard(context, channelId, openIntent, pauseIntent, stopIntent)
    }

    @RequiresApi(36)
    private fun buildApi36(
        context: Context,
        channelId: String,
        openIntent: PendingIntent,
        pauseIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Notification {
        val paused = TimerStore.isSessionPaused(context)
        val phaseLabel = if (paused) "Paused" else TimerStore.phaseLabel(context)
        val minutes = TimerStore.formatRemainingMinutes(context)
        val title = "$phaseLabel · $minutes min"
        val progressPct = (TimerStore.phaseProgress(context) * 100f).toInt().coerceIn(0, 100)
        val pauseLabel = if (paused) "Resume" else "Pause"
        val chip = if (paused) "Pause" else "${minutes}m"

        val sessionIcon = NowBarArt.icon(context)
        val extras = Bundle().apply {
            putAll(samsungNowBarExtras(context, sessionIcon))
            putBoolean("android.requestPromotedOngoing", true)
        }

        val builder = Notification.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(TimerStore.formatRemaining(context))
            .setSmallIcon(R.drawable.ic_stat_pomodoro)
            .setLargeIcon(sessionIcon)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .addAction(Notification.Action.Builder(null, pauseLabel, pauseIntent).build())
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .setShortCriticalText(chip)
            .addExtras(extras)

        if (Build.VERSION.SDK_INT >= 37 && applyMetricStyle(builder, context, paused, progressPct)) {
            // MetricStyle is the timer Live Update template on Android 17 / One UI 9 Now Bar.
        } else {
            applyCountdownChronometer(builder, context, paused)
            builder.setStyle(
                Notification.ProgressStyle()
                    .setProgress(progressPct)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))
            )
        }

        runCatching {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        }

        return builder.build()
    }

    private fun applyMetricStyle(
        builder: Notification.Builder,
        context: Context,
        paused: Boolean,
        progressPct: Int,
    ): Boolean {
        if (Build.VERSION.SDK_INT < 37 || paused) return false
        return runCatching {
            val end = Instant.ofEpochMilli(TimerStore.endAtMillis(context))
            val timeDiff = Class.forName("android.app.Notification\$Metric\$TimeDifference")
            val format = timeDiff.getField("FORMAT_CHRONOMETER_AUTOMATIC").getInt(null)
            val forTimer = timeDiff.getMethod("forTimer", Instant::class.java, Int::class.javaPrimitiveType)
            val value = forTimer.invoke(null, end, format)
            val metricClass = Class.forName("android.app.Notification\$Metric")
            val metric = metricClass
                .getConstructor(Class.forName("android.app.Notification\$Metric\$MetricValue"), CharSequence::class.java)
                .newInstance(value, "Left")
            val style = Class.forName("android.app.Notification\$MetricStyle")
                .getConstructor()
                .newInstance()
            val styled = style.javaClass
                .getMethod("addMetric", metricClass)
                .invoke(style, metric)
            val setStyle = Notification.Builder::class.java.getMethod(
                "setStyle",
                Class.forName("android.app.Notification\$Style"),
            )
            setStyle.invoke(builder, styled)
            true
        }.getOrElse {
            Log.w(TAG, "MetricStyle unavailable", it)
            false
        }
    }

    /** Samsung Now Bar: standard ongoing + Samsung extras (pre-API 36). */
    private fun buildSamsungStandard(
        context: Context,
        channelId: String,
        openIntent: PendingIntent,
        pauseIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Notification {
        val paused = TimerStore.isSessionPaused(context)
        val phaseLabel = if (paused) "Paused" else TimerStore.phaseLabel(context)
        val minutes = TimerStore.formatRemainingMinutes(context)
        val title = "$phaseLabel · $minutes min"
        val pauseLabel = if (paused) "Resume" else "Pause"

        val sessionIcon = NowBarArt.icon(context)
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(TimerStore.formatRemaining(context))
            .setSmallIcon(R.drawable.ic_stat_pomodoro)
            .setLargeIcon(sessionIcon)
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, pauseLabel, pauseIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(!paused)
            .setWhen(countdownWhen(context, paused))
            .setShowWhen(false)
            .setExtras(samsungNowBarExtras(context, sessionIcon))
            .build()
    }

    private fun buildStandard(
        context: Context,
        channelId: String,
        openIntent: PendingIntent,
        pauseIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Notification {
        val pauseLabel = if (TimerStore.isSessionPaused(context)) "Resume" else "Pause"
        val phaseLabel = if (TimerStore.isSessionPaused(context)) "Paused" else TimerStore.phaseLabel(context)
        val minutes = TimerStore.formatRemainingMinutes(context)
        val detail = "${TimerStore.formatRemaining(context)} · ${ambientLine(context)}"
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("$phaseLabel · $minutes min")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSmallIcon(R.drawable.ic_stat_pomodoro)
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, pauseLabel, pauseIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun samsungNowBarExtras(context: Context, icon: Icon): Bundle {
        val paused = TimerStore.isSessionPaused(context)
        val minutes = TimerStore.formatRemainingMinutes(context)
        val phase = if (paused) "Paused" else TimerStore.phaseLabel(context)
        val clock = TimerStore.formatRemaining(context)
        val progressPct = (TimerStore.phaseProgress(context) * 100f).toInt().coerceIn(0, 100)
        return Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 2)
            putString("android.ongoingActivityNoti.primaryInfo", phase)
            putString("android.ongoingActivityNoti.secondaryInfo", "$minutes min · $clock")
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", clock)
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", phase)
            putInt("android.ongoingActivityNoti.actionType", 1)
            putInt("android.ongoingActivityNoti.progress", progressPct)
            putInt("android.ongoingActivityNoti.progressMax", 100)
            putParcelable("android.ongoingActivityNoti.nowbarIcon", icon)
            putParcelable("android.ongoingActivityNoti.chipIcon", icon)
            putParcelable("android.ongoingActivityNoti.firstIcon", icon)
        }
    }

    @RequiresApi(36)
    private fun applyCountdownChronometer(builder: Notification.Builder, context: Context, paused: Boolean) {
        if (paused) return
        val remainingMs = TimerStore.remainingSeconds(context) * 1000L
        if (remainingMs <= 0) return
        builder.setWhen(System.currentTimeMillis() + remainingMs)
        builder.setShowWhen(false)
        builder.setUsesChronometer(true)
        runCatching {
            Notification.Builder::class.java
                .getMethod("setChronometerCountDown", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        }
    }

    private fun countdownWhen(context: Context, paused: Boolean): Long {
        if (paused) return System.currentTimeMillis()
        return System.currentTimeMillis() + TimerStore.remainingSeconds(context) * 1000L
    }

    private fun isSamsung(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun logPromotionState(context: Context, notification: Notification) {
        if (Build.VERSION.SDK_INT < 36) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val active = nm.activeNotifications.firstOrNull { it.packageName == context.packageName }
        val posted = active?.notification ?: notification
        val promoted = (posted.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
        val promotable = posted.hasPromotableCharacteristics()
        val requested = posted.extras?.getBoolean("android.requestPromotedOngoing", false) == true
        val template = posted.extras?.getString("android.template")
        val canPost = runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
        Log.i(
            TAG,
            "sdk=${Build.VERSION.SDK_INT} samsung=${isSamsung()} template=$template " +
                "requestPromoted=$requested promotable=$promotable promoted=$promoted canPostPromoted=$canPost",
        )
    }

    private fun ambientLine(context: Context): String {
        val label = TimerStore.ambientLabel(context)
        return when (TimerStore.ambientMode(context)) {
            AmbientMode.OFF -> "No ambient sound"
            else -> label ?: "Ambient sound"
        }
    }
}
