package com.nplus.screens

import com.badlogic.gdx.Gdx
import com.nplus.NPlusGame
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

    fun playLevel(episode: Int, level: Int) {
        val ep = episode.coerceIn(0, maxEpisode)
        val lv = level.coerceIn(0, LEVELS_PER_EPISODE - 1)
        if (!levelMap.containsKey(ep to lv)) {
            Gdx.app.error("AppStateManager", "Level $ep-$lv not in catalogue; falling back to 0-0")
            transition(AppState.Playing(0, 0))
        } else {
            progress.setLastPlayed(ep, lv)
            progress.save()
            transition(AppState.Playing(ep, lv))
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
     * - Mid-episode (level < 4): auto-advance to the next level with no menu break.
     * - Episode complete (level == 4): mark beaten, point Continue at the next episode,
     *   save, and return to the episode grid so the player can choose what to play next.
     */
    fun levelComplete(episode: Int, level: Int) {
        val nextLv = level + 1
        if (nextLv < LEVELS_PER_EPISODE) {
            // Still mid-episode — play next level immediately
            playLevel(episode, nextLv)
        } else {
            // Last level of this episode finished
            progress.beatEpisode(episode)
            // Advance the resume pointer so "Continue" goes to the next episode
            val nextEp = episode + 1
            if (levelMap.containsKey(nextEp to 0)) {
                progress.setLastPlayed(nextEp, 0)
            }
            progress.save()
            goToMenu()
        }
    }

    /** Called when all players die. Restarts the same level. */
    fun levelFailed(episode: Int, level: Int) = playLevel(episode, level)

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private fun transition(newState: AppState) {
        Gdx.app.log("AppStateManager", "$state → $newState")
        state = newState
        game.setScreen(
            when (newState) {
                is AppState.MainMenu -> MenuScreen(this)
                is AppState.Playing  -> GameScreen(this, newState.episode, newState.level)
            }
        )
    }
}
