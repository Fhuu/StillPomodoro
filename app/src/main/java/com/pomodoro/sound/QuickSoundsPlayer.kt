package com.pomodoro.sound

import android.content.Context
import com.pomodoro.timer.R

/** Standalone mix: no session state, shared YouTube player, or timer callbacks. */
internal class QuickSoundsPlayer(context: Context, private val onPlaybackEnded: () -> Unit) {
    private val output = BuiltInAudioOutput(context) { updateGains() }
    private val ocean = BreakAmbiencePlayer(context, output) { onLayerStopped() }
    private val boat = BoatHumPlayer(context, output) { onLayerStopped() }
    private val storm = BreakAmbiencePlayer(
        context, output,
        listOf(R.raw.roof_storm_roof, R.raw.roof_storm_roof_texture, R.raw.roof_storm_roof_rumble,
            R.raw.roof_storm_downpour, R.raw.roof_storm_wind_gusts, R.raw.roof_storm_wind_window,
            R.raw.roof_storm_thunder),
        { RoofStormEnvelope(it)::volumes },
    ) { onLayerStopped() }
    private val house = BreakAmbiencePlayer(
        context, output, listOf(R.raw.old_house_frames, R.raw.old_house_windows),
        { OldHouseEnvelope(it)::volumes },
    ) { onLayerStopped() }
    private val wind = BreakAmbiencePlayer(
        context, output, listOf(R.raw.coastal_wind), { CoastalWindEnvelope(it)::volumes },
    ) { onLayerStopped() }
    private var changing = false

    fun play(sounds: Set<BuiltInAmbientSound>) {
        changing = true
        try {
            output.activate()
            if (BuiltInAmbientSound.OCEAN_WAVES in sounds) ocean.start() else ocean.stop(immediate = true)
            if (BuiltInAmbientSound.BOAT_HUM in sounds) boat.start() else boat.stop(immediate = true)
            if (BuiltInAmbientSound.ROOF_STORM in sounds) storm.start() else storm.stop(immediate = true)
            if (BuiltInAmbientSound.OLD_HOUSE in sounds) house.start() else house.stop(immediate = true)
            if (BuiltInAmbientSound.COASTAL_WIND in sounds) wind.start() else wind.stop(immediate = true)
            updateGains()
        } finally {
            changing = false
        }
    }

    private fun updateGains() {
        val count = listOf(ocean.isActive, boat.isActive, storm.isActive, house.isActive, wind.isActive).count { it }
        val gain = 1f / count.coerceAtLeast(1)
        ocean.setMixGain(gain, immediate = true)
        boat.setMixGain(gain * output.boatCompensation, immediate = true)
        storm.setMixGain(gain, immediate = true)
        house.setMixGain(gain, immediate = true)
        wind.setMixGain(gain, immediate = true)
    }

    private fun onLayerStopped() {
        if (changing) return
        updateGains()
        if (!ocean.isActive && !boat.isActive && !storm.isActive && !house.isActive && !wind.isActive) {
            output.release()
            onPlaybackEnded()
        }
    }

    fun release() {
        changing = true
        try {
            ocean.stop(immediate = true)
            boat.stop(immediate = true)
            storm.stop(immediate = true)
            house.stop(immediate = true)
            wind.stop(immediate = true)
            output.release()
        } finally {
            changing = false
        }
    }
}
