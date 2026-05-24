package com.nplus.audio

/**
 * Audio system interface.
 *
 * All sound events from the physics simulation reach here as named strings
 * (matching the AS3 MovieClip frame labels). The implementation decides
 * which WAV file to play; it must never throw if a sound is missing.
 *
 * Call [tick] once per game frame (not physics tick) to debounce polyphonic
 * sounds like gold collection that can trigger multiple times per frame.
 */
interface AudioManager {

    /** Called once per game render frame — resets per-frame deduplication. */
    fun tick()

    /**
     * Play a short one-shot sound effect by name.
     * Names match the AS3 MovieClip frame labels:
     *   "jump", "land",
     *   "explode1", "explode2", "fall", "laser", "zap1", "zap2", "shot1", "shot2",
     *   "hard1", "hard2", "hard3", "med1", "med2", "soft1", "soft2"
     */
    fun playSound(name: String)

    /**
     * Start a looping ambient sound.
     * [name] is one of: "wallslide", "skid".
     * Calling this while the loop is already playing is a no-op.
     */
    fun startLoopSound(name: String)

    /**
     * Stop a looping ambient sound started with [startLoopSound].
     * No-op if the loop is not currently playing.
     */
    fun stopLoopSound(name: String)

    /**
     * Play the gold-collection chime.
     * Debounced per frame — collecting multiple golds in one tick plays only once.
     */
    fun playGold()

    /**
     * Start a looping background music track.
     * Stops any currently playing music first.
     * [name] is a track identifier (e.g. "game", "menu").
     */
    fun playMusic(name: String)

    /** Stop background music immediately. */
    fun stopMusic()

    /** Set master volume for all sounds in [0,1]. */
    fun setVolume(volume: Float)

    /** Pause all audio (e.g. on Android lifecycle pause). */
    fun pause()

    /** Resume all audio. */
    fun resume()

    /** Release all libGDX resources — called when the game exits. */
    fun dispose()

    companion object {
        /** Drop-in no-op implementation for tests and headless builds. */
        val NULL: AudioManager = object : AudioManager {
            override fun tick() {}
            override fun playSound(name: String) {}
            override fun playGold() {}
            override fun startLoopSound(name: String) {}
            override fun stopLoopSound(name: String) {}
            override fun playMusic(name: String) {}
            override fun stopMusic() {}
            override fun setVolume(volume: Float) {}
            override fun pause() {}
            override fun resume() {}
            override fun dispose() {}
        }
    }
}
