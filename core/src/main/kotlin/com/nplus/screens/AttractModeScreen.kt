package com.nplus.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.controllers.Controllers
import com.nplus.SimGlobals
import com.nplus.attract.AttractModeData
import com.nplus.audio.AudioManager
import com.nplus.levels.LevelBuilder
import com.nplus.physics.Simulator
import com.nplus.physics.input.InputSourcePlayback

/**
 * Full-screen attract mode: cycles through pre-recorded replays while waiting for any input.
 *
 * Any key or gamepad button exits to the main menu.
 * When a replay finishes (all input frames consumed or the level ends), the next replay starts
 * automatically after a short pause.
 *
 * Audio is muted (AudioManager.NULL) — this is a silent visual demo.
 */
class AttractModeScreen(private val appState: AppStateManager) : Screen {

    companion object {
        private const val TICK_DT    = 1f / SimGlobals.SIM_RATE
        private const val MAX_DELTA  = 0.1f
        // Fixed-rate ticks to hold at the end of a replay before loading the next one
        private const val END_HOLD_TICKS = (SimGlobals.SIM_RATE * 1.5f).toInt()   // ~1.5 s
        // Render frames to ignore input after loading, preventing carry-over button presses
        private const val INPUT_GRACE_FRAMES = 10
    }

    private lateinit var renderer: GameRenderer

    private var sim:         Simulator?          = null
    private var playbackSrc: InputSourcePlayback? = null

    private var accumulator    = 0f
    private var endHoldCounter = -1   // -1 = replay still running
    private var graceCounter   = INPUT_GRACE_FRAMES

    // -----------------------------------------------------------------------

    override fun show() {
        renderer = GameRenderer()
        AttractModeData.load()
        graceCounter = INPUT_GRACE_FRAMES
        loadReplay(random = true)
    }

    override fun render(delta: Float) {
        // Grace period: skip input detection for the first few frames after show()
        if (graceCounter > 0) {
            graceCounter--
        } else if (anyInputJustPressed()) {
            appState.goToMenu()
            return
        }

        val currentSim = sim ?: return
        val safeDelta  = delta.coerceAtMost(MAX_DELTA)

        if (endHoldCounter >= 0) {
            // Holding at end of replay — keep ticking physics so ragdoll/particles settle
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

    // -----------------------------------------------------------------------

    private fun loadReplay(random: Boolean) {
        val entry = if (random) AttractModeData.randomEntry() else AttractModeData.nextEntry()
        if (entry == null) {
            // No attract data — go straight to menu
            appState.goToMenu()
            return
        }

        val ep        = entry.levelIndex / 5
        val lv        = entry.levelIndex % 5
        val levelData = appState.levelMap[ep to lv]

        if (levelData == null) {
            // Level missing from catalogue — skip to next
            loadReplay(random = false)
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

    private fun anyInputJustPressed(): Boolean {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ANY_KEY)) return true
        val pads = Controllers.getControllers()
        if (pads.size == 0) return false
        val ctrl = pads[0]
        val m    = ctrl.mapping
        return ctrl.getButton(m.buttonA)  ||
               ctrl.getButton(m.buttonB)  ||
               ctrl.getButton(m.buttonX)  ||
               ctrl.getButton(m.buttonY)  ||
               ctrl.getButton(m.buttonStart)
    }

    // -----------------------------------------------------------------------

    override fun resize(w: Int, h: Int) {
        if (::renderer.isInitialized) renderer.resize(w, h)
    }

    override fun pause()  {}
    override fun resume() {}

    private var disposed = false

    override fun hide()    = dispose()
    override fun dispose() {
        if (disposed) return
        disposed = true
        if (::renderer.isInitialized) renderer.dispose()
    }
}
