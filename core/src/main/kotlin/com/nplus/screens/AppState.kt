package com.nplus.screens

import com.nplus.SimGlobals

/**
 * Application-level state machine for N+.
 *
 * Top-level navigation states; in-game sub-states (running/paused/dead/won)
 * are managed internally by [GameScreen] and expressed through [Ninja.State].
 */
sealed class AppState {
    /** Main menu / episode+level select. */
    object MainMenu : AppState()

    /**
     * Active gameplay for a specific episode and level.
     * [startingTicks] carries the timer value from the previous level so it
     * persists across retries and advances to the next level within an episode.
     */
    data class Playing(
        val episode: Int,
        val level: Int,
        val startingTicks: Int = SimGlobals.DEFAULT_TIMER_TICKS
    ) : AppState()

    /** Settings screen (controls display, ninja colour picker). */
    object Settings : AppState()

    /** Full-screen attract mode: cycles pre-recorded replays until any input. */
    object AttractMode : AppState()
}
