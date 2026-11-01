package com.pomodoro.sound

import android.content.Context

object QuickSoundsStore {
    private fun prefs(context: Context) = context.getSharedPreferences("quick_sounds", Context.MODE_PRIVATE)

    fun cyberpunkPanel(context: Context): Boolean = prefs(context).getBoolean("cyberpunk_panel", true)

    fun setCyberpunkPanel(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("cyberpunk_panel", enabled).apply()
    }

    fun blurredPanel(context: Context): Boolean = prefs(context).getBoolean("blurred_panel", false)

    fun setBlurredPanel(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("blurred_panel", enabled).apply()
    }

    fun selection(context: Context): Set<BuiltInAmbientSound> {
        val names = prefs(context).getStringSet("selection", setOf(BuiltInAmbientSound.OCEAN_WAVES.name)).orEmpty()
        return BuiltInAmbientSound.entries.filter { it.name in names }.toSet()
    }

    fun setSelection(context: Context, sounds: Set<BuiltInAmbientSound>) {
        prefs(context).edit().putStringSet("selection", sounds.map { it.name }.toSet()).apply()
    }
}
