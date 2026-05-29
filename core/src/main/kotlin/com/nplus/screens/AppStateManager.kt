package com.nplus.screens

import com.badlogic.gdx.Gdx
import com.nplus.NPlusGame
import com.nplus.SimGlobals
import com.nplus.levels.LevelData
import com.nplus.levels.LevelParser

/**
 * Central application state machine.
 *
 * Owns all screen transitions, the shared level catalogue, and player progress.
 * Screens must not call [com.badlogic.gdx.Game.setScreen] directly —
 * they call the transition methods here instead.
 */
class AppStateManager(private val game: NPlusGame) {

    companion object {
        const val LEVELS_PER_EPISODE = 5
    }

    // --- Shared data (loaded once) ---
    var levelMap: Map<Pair<Int, Int>, LevelData> = emptyMap()
        private set
    var maxEpisode: Int = 0
        private set

    // --- Player progress (save/load) ---
    val progress = ProgressManager()

    // --- Current state ---
    var state: AppState = AppState.MainMenu
        private set

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    fun initialize() {
        Gdx.app.log("AppStateManager", "Loading level catalogue…")
        levelMap = Gdx.files.internal("levels/levels.bin").read()
            .use { LevelParser.parseBinToMap(it) }
        maxEpisode = levelMap.keys.maxOfOrNull { it.first } ?: 0
        Gdx.app.log("AppStateManager", "${levelMap.size} levels loaded, maxEpisode=$maxEpisode")
        progress.load()
    }

    // -----------------------------------------------------------------------
    // Navigation API (called by screens)
    // -----------------------------------------------------------------------

    fun goToMenu() = transition(AppState.MainMenu)

    fun goToSettings() = transition(AppState.Settings)

    /** Go straight to the episode grid with the cursor on [episode]. */
    fun goToEpisodeGrid(episode: Int) {
        state = AppState.MainMenu
        game.setScreen(MenuScreen(this, startAtGrid = true, startEpisode = episode))
    }

    fun playLevel(
        episode: Int,
        level: Int,
        startingTicks: Int = SimGlobals.DEFAULT_TIMER_TICKS
    ) {
        val ep = episode.coerceIn(0, maxEpisode)
        val lv = level.coerceIn(0, LEVELS_PER_EPISODE - 1)
        if (!levelMap.containsKey(ep to lv)) {
            Gdx.app.error("AppStateManager", "Level $ep-$lv not in catalogue; falling back to 0-0")
            transition(AppState.Playing(0, 0, SimGlobals.DEFAULT_TIMER_TICKS))
        } else {
            progress.setLastPlayed(ep, lv)
            progress.save()
            transition(AppState.Playing(ep, lv, startingTicks))
        }
    }

    /**
     * Reset all progress and go to the episode grid so the player can pick a starting episode.
     * Called by "New Game" confirmation in the menu.
     */
    fun newGame() {
        progress.newGame()   // also calls save()
        state = AppState.MainMenu
        game.setScreen(MenuScreen(this, startAtGrid = true))
    }

    /**
     * Called when the player wins a level.
     * - Mid-episode (level < 4): auto-advance to the next level, carrying the timer.
     * - Episode complete (level == 4): mark beaten, return to the episode grid; timer resets.
     */
    fun levelComplete(episode: Int, level: Int, timerTicks: Int = SimGlobals.DEFAULT_TIMER_TICKS) {
        val nextLv = level + 1
        if (nextLv < LEVELS_PER_EPISODE) {
            // Still mid-episode — carry the timer to the next level
            playLevel(episode, nextLv, timerTicks)
        } else {
            // Last level of this episode finished; timer resets for the next episode
            progress.beatEpisode(episode)
            progress.setEpisodeRecord(episode, timerTicks)
            val nextEp = episode + 1
            if (levelMap.containsKey(nextEp to 0)) {
                progress.setLastPlayed(nextEp, 0)
            }
            progress.save()
            goToEpisodeGrid(nextEp.coerceAtMost(maxEpisode))
        }
    }

    /**
     * Called when all players die. Restarts the same level with the same starting timer
     * value that was in effect when this level began (not the current depleted value).
     */
    fun levelFailed(
        episode: Int,
        level: Int,
        startingTicks: Int = SimGlobals.DEFAULT_TIMER_TICKS
    ) = playLevel(episode, level, startingTicks)

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private fun transition(newState: AppState) {
        Gdx.app.log("AppStateManager", "$state → $newState")
        state = newState
        game.setScreen(
            when (newState) {
                is AppState.MainMenu -> MenuScreen(this)
                is AppState.Playing  -> GameScreen(this, newState.episode, newState.level, newState.startingTicks)
                is AppState.Settings -> SettingsScreen(this)
            }
        )
    }
}
