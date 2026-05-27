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

    // name → Sound.loop() instance ID for currently playing loops
    private val activeLoops = mutableMapOf<String, Long>()
    // loops that were running when pause() was called, restarted by resume()
    private var pausedLoops: Set<String> = emptySet()

    companion object {
        // Flash symbol sequence (confirmed by decomp/sounds file names):
        //   524 = gold, 525-533 = ragdollMC (symbol 534), 710-711 = playerLoopMC (symbol 712),
        //   727-734 = playerMC (symbol 735).
        //
        // USER-CONFIRMED (2026-05-24):
        //   525-528 = hard-fall impacts; 529 = "hitting ground"; 530 = launchpad entity sound
        //   (NOT ragdoll); 531 = unknown/soft; 532-533 = "zapped"
        //   727 = jump, 728 = land, 729 = "hard fall die", 731 = "death time/kill", 733 = "hard fall
        //   die", 734 = "smashed die"; 907/939/948/950/953/957/973 = entity one-shots

        /**
         * Maps AS3 sound-cue names to WAV filenames in assets/sounds/.
         * Remap by ear as needed.
         */
        val SOUND_MAP: Map<String, String> = mapOf(
            // Confirmed
            "gold"      to "sounds/gold.wav",

            // Ragdoll impact sounds
            // NOTE: 530 is the launchpad entity sound, not a ragdoll sound.
            "hard1"     to "sounds/525.wav",
            "hard2"     to "sounds/526.wav",
            "hard3"     to "sounds/527.wav",
            "med1"      to "sounds/528.wav",
            "med2"      to "sounds/529.wav",  // "hitting ground"
            "soft1"     to "sounds/531.wav",  // unconfirmed best-guess
            "soft2"     to "sounds/531.wav",  // reuses 531 until better candidate found
            "zap1"      to "sounds/532.wav",  // "zapped"; also ninja zap-death
            "zap2"      to "sounds/533.wav",

            // Player one-shot sounds (playerMC symbol 735, embedded 727-734)
            "jump"      to "sounds/727.wav",
            "land"      to "sounds/728.wav",
            "explode1"  to "sounds/729.wav",  // "hard fall die"
            "explode2"  to "sounds/730.wav",
            "fall"      to "sounds/731.wav",  // "death (time up / kill button)"
            "laser"     to "sounds/732.wav",
            "shot1"     to "sounds/733.wav",  // "hard fall die"
            "shot2"     to "sounds/734.wav",  // "smashed die"

            // Entity one-shot sounds (triggered via playSoundEntity from entity state changes)
            "launchpad"      to "sounds/898.wav",
            "mine_explode"   to "sounds/950.wav",
            "rocket_fire"    to "sounds/948.wav",
            "turret_prefire" to "sounds/907.wav",
            "guard_chase"    to "sounds/939.wav",
            "drone_chase"    to "sounds/953.wav",
            "laser_charge"   to "sounds/957.wav",
            "exit_switch"    to "sounds/973.wav",
            // "door_open"   to "sounds/910.wav",  // 910.wav absent from assets — TODO
        )

        // Loop sounds — playerLoopMC (symbol 712), embedded sounds 710–711.
        // Best-guess assignment: verify by ear.
        val LOOP_MAP: Map<String, String> = mapOf(
            "wallslide" to "sounds/710.wav",
            "skid"      to "sounds/711.wav",
        )

        /** All effect file paths that should be preloaded on startup. */
        val PRELOAD_EFFECTS: List<String> = (SOUND_MAP.values + LOOP_MAP.values).distinct()
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
        activeLoops.keys.toList().forEach { stopLoopSound(it) }
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

    override fun startLoopSound(name: String) {
        if (activeLoops.containsKey(name)) return
        val path = LOOP_MAP[name] ?: return
        if (!assets.isLoaded(path, Sound::class.java)) return
        val id = assets.get(path, Sound::class.java).loop(masterVolume * 0.7f)
        activeLoops[name] = id
    }

    override fun stopLoopSound(name: String) {
        val id = activeLoops.remove(name) ?: return
        val path = LOOP_MAP[name] ?: return
        if (assets.isLoaded(path, Sound::class.java)) {
            assets.get(path, Sound::class.java).stop(id)
        }
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
        // Stop looping sounds; remember which ones were active so resume() can restart them.
        pausedLoops = activeLoops.keys.toSet()
        pausedLoops.forEach { stopLoopSound(it) }
        currentMusic?.pause()
    }

    override fun resume() {
        currentMusic?.play()
        pausedLoops.forEach { startLoopSound(it) }
        pausedLoops = emptySet()
    }
}
