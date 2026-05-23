package com.nplus.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound

/**
 * libGDX [AudioManager] implementation.
 *
 * ## Sound file mapping
 * The AS3 game embedded sounds inside a SWF MovieClip with frame labels.
 * The frame-to-filename mapping was lost during decompilation; we have 39 numbered
 * WAV files and only one labelled file (`gold.wav`). The table below is a best-effort
 * assignment; sounds can be remapped by editing [SOUND_MAP] without touching callers.
 *
 * ## Asset loading strategy
 * - Short effects use libGDX [Sound] (loaded once via [AssetManager], kept in RAM).
 * - Music uses libGDX [Music] (streamed, loaded on demand, one track at a time).
 * - Missing sounds log a warning and play silence — they never crash.
 *
 * ## Gold debouncing
 * Collecting many golds in one tick would cause an ear-splitting chime flood.
 * [playGold] plays at most once per call to [tick] (which should be once per render frame).
 */
class LibGdxAudioManager : AudioManager {

    private val assets = AssetManager()
    private var currentMusic: Music? = null
    private var masterVolume = 1f
    private var goldTriggered = false

    // ---------------------------------------------------------------------------
    // Sound name → WAV file mapping
    // The gold file is the only one whose identity is confirmed.
    // All others are assigned by best-guess; re-map as you identify them by ear.
    // ---------------------------------------------------------------------------
    companion object {
        /**
         * Maps AS3 sound-cue names to WAV filenames in assets/sounds/.
         * Update this table when you identify sounds by listening.
         */
        val SOUND_MAP: Map<String, String> = mapOf(
            // Confirmed
            "gold"      to "sounds/gold.wav",

            // Player one-shot sounds (best-guess numbering — verify by ear)
            "jump"      to "sounds/529.wav",
            "land"      to "sounds/530.wav",
            "explode1"  to "sounds/531.wav",
            "explode2"  to "sounds/532.wav",
            "fall"      to "sounds/533.wav",
            "laser"     to "sounds/710.wav",
            "zap1"      to "sounds/711.wav",
            "zap2"      to "sounds/727.wav",
            "shot1"     to "sounds/728.wav",
            "shot2"     to "sounds/729.wav",

            // Ragdoll impact sounds
            "hard1"     to "sounds/525.wav",
            "hard2"     to "sounds/526.wav",
            "hard3"     to "sounds/527.wav",
            "med1"      to "sounds/528.wav",
            "med2"      to "sounds/730.wav",
            "soft1"     to "sounds/731.wav",
            "soft2"     to "sounds/732.wav",
        )

        /** All effect file paths that should be preloaded on startup. */
        val PRELOAD_EFFECTS: List<String> = SOUND_MAP.values.distinct()
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    /** Call once after construction (e.g. in GameScreen.show()) to preload effects. */
    fun preload() {
        for (path in PRELOAD_EFFECTS) {
            if (Gdx.files.internal(path).exists()) {
                assets.load(path, Sound::class.java)
            } else {
                Gdx.app.log("Audio", "Sound file not found, will be silent: $path")
            }
        }
        assets.finishLoading()
    }

    override fun dispose() {
        currentMusic?.stop()
        currentMusic?.dispose()
        assets.dispose()
    }

    // ---------------------------------------------------------------------------
    // AudioManager implementation
    // ---------------------------------------------------------------------------

    override fun tick() {
        goldTriggered = false
    }

    override fun playSound(name: String) {
        val path = SOUND_MAP[name]
        if (path == null) {
            Gdx.app.log("Audio", "No mapping for sound '$name'")
            return
        }
        if (!assets.isLoaded(path, Sound::class.java)) return
        assets.get(path, Sound::class.java).play(masterVolume * 0.8f)
    }

    override fun playGold() {
        if (goldTriggered) return
        goldTriggered = true
        val path = SOUND_MAP["gold"] ?: return
        if (!assets.isLoaded(path, Sound::class.java)) return
        assets.get(path, Sound::class.java).play(masterVolume * 0.25f)
    }

    override fun playMusic(name: String) {
        val path = "sounds/music_$name.ogg"
        if (!Gdx.files.internal(path).exists()) {
            Gdx.app.log("Audio", "Music not found: $path")
            return
        }
        currentMusic?.stop(); currentMusic?.dispose()
        currentMusic = Gdx.audio.newMusic(Gdx.files.internal(path)).also { m ->
            m.isLooping = true
            m.volume   = masterVolume * 0.6f
            m.play()
        }
    }

    override fun stopMusic() {
        currentMusic?.stop()
    }

    override fun setVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        currentMusic?.volume = masterVolume * 0.6f
    }

    override fun pause() {
        currentMusic?.pause()
        // libGDX Sound instances don't pause cleanly on all platforms; this is acceptable
    }

    override fun resume() {
        currentMusic?.play()
    }
}
