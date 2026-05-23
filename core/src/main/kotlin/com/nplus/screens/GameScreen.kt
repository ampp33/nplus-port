package com.nplus.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.nplus.SimGlobals
import com.nplus.audio.LibGdxAudioManager
import com.nplus.input.CombinedInputSource
import com.nplus.levels.LevelBuilder
import com.nplus.physics.Simulator

/**
 * Main gameplay screen.
 *
 * ## Physics loop
 * Fixed-step accumulator: physics ticks at [TICK_DT] = 1/SIM_RATE = 25 ms;
 * rendering runs at the display refresh rate. [MAX_DELTA] caps the accumulator
 * to prevent the spiral-of-death on slow frames.
 *
 * ## Level flow
 * All level transitions go through [AppStateManager] — GameScreen never calls
 * `game.setScreen()` directly.
 *
 * - Win       → [AppStateManager.levelComplete]
 * - All dead  → [AppStateManager.levelFailed]
 * - Pause (1st Escape) → toggle pause flag
 * - Pause (2nd Escape) → [AppStateManager.goToMenu]
 * - R key     → reload current level via [AppStateManager.levelFailed]
 */
class GameScreen(
    private val appState:     AppStateManager,
    private val episode:      Int = 0,
    private val level:        Int = 0
) : Screen {

    companion object {
        private const val TICK_DT   = 1f / SimGlobals.SIM_RATE   // 0.025 s
        private const val MAX_DELTA = 0.1f                        // spiral-of-death guard
    }

    // --- Subsystems ---
    private val audio    = LibGdxAudioManager()
    private val inputSrc = CombinedInputSource.keyboardAndGamepad()
    private lateinit var renderer: GameRenderer

    // --- Runtime state ---
    private var sim: Simulator? = null
    private var accumulator  = 0f
    private var paused       = false
    private var levelDone    = false   // one-frame debounce for level completion

    // Key edge-detect
    private var prevPause   = false
    private var prevRestart = false

    // -----------------------------------------------------------------------
    // Screen lifecycle
    // -----------------------------------------------------------------------

    override fun show() {
        renderer = GameRenderer()
        audio.preload()
        loadLevel()
    }

    override fun render(delta: Float) {
        val safeDelta  = delta.coerceAtMost(MAX_DELTA)
        val currentSim = sim ?: return

        // --- Input (edge detect) ---
        val nowPause   = inputSrc.isPauseDown || Gdx.input.isKeyPressed(Input.Keys.ESCAPE)
        val nowRestart = Gdx.input.isKeyPressed(Input.Keys.R)

        if (nowPause && !prevPause) {
            if (paused) appState.goToMenu()   // second Escape → back to menu
            else        paused = true          // first Escape → pause
        }
        if (nowRestart && !prevRestart) { appState.levelFailed(episode, level); return }
        prevPause   = nowPause
        prevRestart = nowRestart

        // --- Physics tick (fixed timestep accumulator) ---
        audio.tick()
        if (!paused) {
            accumulator += safeDelta
            while (accumulator >= TICK_DT) {
                currentSim.tick()
                accumulator -= TICK_DT
            }
        }

        // --- Render ---
        renderer.render(currentSim)

        // --- Level completion (debounced one frame to let renderer show final state) ---
        if (levelDone) {
            levelDone = false
            if (currentSim.appDidPlayerWin()) appState.levelComplete(episode, level)
            else                              appState.levelFailed(episode, level)
            return
        }
        if (currentSim.appIsGameDone()) levelDone = true
    }

    override fun resize(width: Int, height: Int) {
        if (::renderer.isInitialized) renderer.resize(width, height)
    }

    override fun pause() {
        paused = true
        audio.pause()
    }

    override fun resume() {
        audio.resume()
        // Keep paused = true; player unpauses explicitly with Escape
    }

    override fun hide() {}

    override fun dispose() {
        if (::renderer.isInitialized) renderer.dispose()
        audio.dispose()
    }

    // -----------------------------------------------------------------------
    // Level management
    // -----------------------------------------------------------------------

    private fun loadLevel() {
        val data = appState.levelMap[episode to level]
        if (data == null) {
            Gdx.app.error("GameScreen", "Level $episode-$level not in catalogue")
            appState.goToMenu()
            return
        }
        Gdx.app.log("GameScreen", "ep=$episode lv=$level: ${data.name}")
        sim = LevelBuilder.build(
            data         = data,
            inputSources = listOf(inputSrc),
            audio        = audio
        )
        sim!!.appEnableAllPlayers()
        accumulator = 0f
        levelDone   = false
        paused      = false
    }
}
