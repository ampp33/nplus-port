package com.nplus.screens

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
     * [episode] and [level] are 0-based indices into the level catalogue.
     */
    data class Playing(val episode: Int, val level: Int) : AppState()
}
