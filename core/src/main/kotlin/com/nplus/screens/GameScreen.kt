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
 * Fixed-step accumulator: physics ticks at [TICK_DT] = 1/SIM_RATE;
 * rendering runs at the display refresh rate. [MAX_DELTA] caps the accumulator
 * to prevent the spiral-of-death on slow frames.
 *
 * ## Play states (see [PlayState])
 * PRE_GAME  → level shown frozen, "press jump to begin" overlay; Jump starts game
 * GAME      → simulation + timer running; ESC pauses, K suicides
 * PAUSED    → simulation frozen, "paused" overlay; Jump/ESC resumes, Q quits
 * POST_GAME → ninja dead, ragdoll running, "press jump to retry" overlay;
 *             Jump retries (with same startingTicks), ESC quits to menu
 *
 * ## Timer
 * Counts down from [startingTicks] at 1 tick/physics-step.  Collecting gold
 * adds [SimGlobals.TICKS_PER_GOLD] per coin.  When the timer hits 0 the ninja
 * is killed via [Simulator.appTimeUp].  On retry [startingTicks] is the value
 * that was in effect when this level began; on level advance the current value
 * carries forward.
 *
 * ## Level flow
 * All level transitions go through [AppStateManager].
 */
class GameScreen(
    private val appState:      AppStateManager,
    private val episode:       Int = 0,
    private val level:         Int = 0,
    private val startingTicks: Int = SimGlobals.DEFAULT_TIMER_TICKS
) : Screen {

    companion object {
        private const val TICK_DT             = 1f / SimGlobals.SIM_RATE
        private const val MAX_DELTA           = 0.1f
        // Frames before jump input is accepted in POST_GAME (prevents instant retry on death)
        private const val POST_GAME_COOLDOWN  = (SimGlobals.SIM_RATE * 0.5f).toInt()  // 30
    }

    // --- Subsystems ---
    private val audio    = LibGdxAudioManager()
    private val inputSrc = CombinedInputSource.keyboardAndGamepad()
    private lateinit var renderer: GameRenderer

    // --- Simulator ---
    private var sim: Simulator? = null

    // --- Play state ---
    private var playState          = PlayState.PRE_GAME
    private var postGameCooldown   = 0

    // --- Timer ---
    private var currentTicks       = startingTicks
    private var timerCalledTimeUp  = false   // guard: only call appTimeUp() once

    // --- Fixed-step accumulator ---
    private var accumulator        = 0f

    // --- Edge-detect for all keys ---
    private var prevJump   = false
    private var prevPause  = false
    private var prevK      = false
    private var prevQ      = false
    private var prevR      = false

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

        // Poll input once per frame (idempotent; sim also polls during tick)
        val inp      = inputSrc.poll()
        val nowJump  = inp.jump
        val nowPause = inp.pause || Gdx.input.isKeyPressed(Input.Keys.ESCAPE)
        val nowK     = Gdx.input.isKeyPressed(Input.Keys.K)
        val nowQ     = Gdx.input.isKeyPressed(Input.Keys.Q)
        val nowR     = Gdx.input.isKeyPressed(Input.Keys.R)

        when (playState) {

            PlayState.PRE_GAME -> {
                audio.tick()
                if (nowJump && !prevJump) {
                    playState = PlayState.GAME
                }
                if (nowPause && !prevPause) {
                    // ESC before starting → back to menu immediately
                    appState.goToMenu(); return
                }
                // R in pre-game: restart (resets to same startingTicks)
                if (nowR && !prevR) {
                    appState.levelFailed(episode, level, startingTicks); return
                }
            }

            PlayState.GAME -> {
                audio.tick()

                // ESC → pause
                if (nowPause && !prevPause) {
                    playState = PlayState.PAUSED
                    audio.pause()
                }

                // K → suicide (kills ninja immediately)
                if (nowK && !prevK) currentSim.appSuicide(0)

                // R → instant restart
                if (nowR && !prevR) {
                    appState.levelFailed(episode, level, startingTicks); return
                }

                // Fixed-step physics + timer
                accumulator += safeDelta
                while (accumulator >= TICK_DT) {
                    currentSim.tick()

                    // Timer: decrement then apply gold bonus (mirrors AS3 tickSimulator order)
                    currentTicks--
                    if (currentTicks <= 0) {
                        currentTicks = 0
                        if (!timerCalledTimeUp) {
                            timerCalledTimeUp = true
                            currentSim.appTimeUp()
                        }
                    } else {
                        currentTicks += currentSim.appGetGoldCollectedThisTick(0) * SimGlobals.TICKS_PER_GOLD
                    }

                    accumulator -= TICK_DT
                }

                // Check for end-of-level (win or death)
                if (currentSim.appIsGameDone()) {
                    if (currentSim.appDidPlayerWin()) {
                        appState.levelComplete(episode, level, currentTicks)
                        return
                    } else {
                        playState        = PlayState.POST_GAME
                        postGameCooldown = POST_GAME_COOLDOWN
                    }
                }
            }

            PlayState.PAUSED -> {
                // Q → quit to menu
                if (nowQ && !prevQ) { appState.goToMenu(); return }
                // Jump or ESC → resume
                if ((nowJump && !prevJump) || (nowPause && !prevPause)) {
                    playState = PlayState.GAME
                    audio.resume()
                }
            }

            PlayState.POST_GAME -> {
                audio.tick()

                // Keep ticking sim so the ragdoll simulates
                accumulator += safeDelta
                while (accumulator >= TICK_DT) {
                    currentSim.tick()
                    accumulator -= TICK_DT
                }

                // Cooldown before accepting any input (prevents accidental instant-retry)
                if (postGameCooldown > 0) {
                    postGameCooldown--
                } else {
                    // Jump → retry same level with the timer value it had at level start
                    if (nowJump && !prevJump) {
                        appState.levelFailed(episode, level, startingTicks); return
                    }
                    // ESC → quit to menu
                    if (nowPause && !prevPause) { appState.goToMenu(); return }
                }
            }
        }

        prevJump  = nowJump
        prevPause = nowPause
        prevK     = nowK
        prevQ     = nowQ
        prevR     = nowR

        renderer.render(currentSim, playState, currentTicks, startingTicks)
    }

    override fun resize(width: Int, height: Int) {
        if (::renderer.isInitialized) renderer.resize(width, height)
    }

    override fun pause() {
        if (playState == PlayState.GAME) {
            playState = PlayState.PAUSED
        }
        audio.pause()
    }

    override fun resume() {
        audio.resume()
        // Stay in PAUSED; player unpauses explicitly with Jump or ESC
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
        currentTicks      = startingTicks
        timerCalledTimeUp = false
        accumulator       = 0f
        playState         = PlayState.PRE_GAME
        prevJump = false; prevPause = false; prevK = false; prevQ = false; prevR = false
    }
}
