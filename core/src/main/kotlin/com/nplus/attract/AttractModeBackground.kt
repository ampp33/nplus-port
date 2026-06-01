package com.nplus.attract

import com.nplus.SimGlobals
import com.nplus.audio.AudioManager
import com.nplus.levels.LevelBuilder
import com.nplus.levels.LevelData
import com.nplus.physics.Simulator
import com.nplus.physics.input.InputSourcePlayback
import com.nplus.screens.GameRenderer

/**
 * Manages a looping attract-mode simulation that renders as a background behind menu screens.
 *
 * Usage:
 *   val bg = AttractModeBackground(appState.levelMap)
 *   // in show():  bg.show()
 *   // in render(): bg.render(delta)   ← clears + draws game; re-apply menu viewport after this
 *   // in resize(): bg.resize(w, h)
 *   // in dispose(): bg.dispose()
 */
class AttractModeBackground(private val levelMap: Map<Pair<Int, Int>, LevelData>) {

    companion object {
        private const val TICK_DT        = 1f / SimGlobals.SIM_RATE
        private const val MAX_DELTA      = 0.1f
        private const val END_HOLD_TICKS = (SimGlobals.SIM_RATE * 1.5f).toInt()
    }

    private lateinit var renderer: GameRenderer

    private var sim:         Simulator?          = null
    private var playbackSrc: InputSourcePlayback? = null

    private var accumulator    = 0f
    private var endHoldCounter = -1

    // -----------------------------------------------------------------------

    private var initialized = false

    /**
     * Initializes GL resources and starts the first replay.
     * Idempotent — safe to call from multiple screens; only runs once.
     * Must be called while a GL context is active (i.e. from a Screen.show()).
     */
    fun show() {
        if (initialized) return
        initialized = true
        AttractModeData.load()
        renderer = GameRenderer()
        loadReplay(random = true)
    }

    /** Ticks physics and renders the game. Call this BEFORE re-applying the menu viewport. */
    fun render(delta: Float) {
        val currentSim = sim ?: return
        val safeDelta  = delta.coerceAtMost(MAX_DELTA)

        if (endHoldCounter >= 0) {
            accumulator += safeDelta
            while (accumulator >= TICK_DT) {
                currentSim.tick()
                endHoldCounter++
                accumulator -= TICK_DT
            }
            if (endHoldCounter >= END_HOLD_TICKS) loadReplay(random = false)
        } else {
            accumulator += safeDelta
            while (accumulator >= TICK_DT) {
                currentSim.tick()
                accumulator -= TICK_DT
            }
            if (playbackSrc?.isReplayFinished == true || currentSim.appIsGameDone()) {
                endHoldCounter = 0
            }
        }

        renderer.render(currentSim)
    }

    fun resize(w: Int, h: Int) {
        if (::renderer.isInitialized) renderer.resize(w, h)
    }

    fun dispose() {
        if (::renderer.isInitialized) renderer.dispose()
    }

    // -----------------------------------------------------------------------

    private fun loadReplay(random: Boolean) {
        val entry = (if (random) AttractModeData.randomEntry() else AttractModeData.nextEntry()) ?: return

        val ep        = entry.levelIndex / 5
        val lv        = entry.levelIndex % 5
        val levelData = levelMap[ep to lv] ?: run {
            loadReplay(random = false)   // level missing — try next
            return
        }

        val pb = InputSourcePlayback(entry.frames)
        playbackSrc    = pb
        sim            = LevelBuilder.build(
            data         = levelData,
            inputSources = listOf(pb),
            audio        = AudioManager.NULL
        )
        sim!!.appEnableAllPlayers()
        accumulator    = 0f
        endHoldCounter = -1
    }
}
