package com.nplus.screens

import com.badlogic.gdx.Gdx

/**
 * Tracks episode unlock/beaten state and resume position.
 *
 * Unlock rules (matching original Flash game):
 * - Episodes 0, 10, 20, … 90 are always unlocked (first of each of the 10 normal columns).
 * - Beating episode N unlocks episode N+1.
 * - Secret episodes 100–119 are unlocked (and visible) only after all 100 normal episodes beaten.
 *
 * Progress is persisted via libGDX Preferences (SharedPreferences on Android, file on desktop).
 */
class ProgressManager {

    companion object {
        const val TOTAL_EPISODES  = 120
        const val NORMAL_EPISODES = 100
        private const val PREFS_NAME  = "nplus-progress"
        private const val KEY_BEATEN  = "beaten"
        private const val KEY_LAST_EP = "last_episode"
        private const val KEY_LAST_LV = "last_level"
        private const val KEY_HAS_SAVE = "has_save"
    }

    private val beaten = BooleanArray(TOTAL_EPISODES)

    var lastEpisode = 0
        private set
    var lastLevel = 0
        private set
    /** True once the player has started at least one level (enables "Continue"). */
    var hasSave = false
        private set

    val secretsUnlocked: Boolean
        get() = (0 until NORMAL_EPISODES).all { beaten[it] }

    /**
     * Returns true if [ep] can be selected and played.
     * Normal columns (0–9): first episode in each column always open; rest unlock sequentially.
     * Secret columns (10–11): locked until all 100 normal episodes are beaten.
     */
    fun isUnlocked(ep: Int): Boolean {
        if (ep < 0 || ep >= TOTAL_EPISODES) return false
        if (ep >= NORMAL_EPISODES) return secretsUnlocked
        return ep % 10 == 0 || beaten[ep - 1]
    }

    fun isBeaten(ep: Int) = ep in 0 until TOTAL_EPISODES && beaten[ep]

    /** Mark [ep] as beaten and unlock the next episode. */
    fun beatEpisode(ep: Int) {
        if (ep < 0 || ep >= TOTAL_EPISODES) return
        beaten[ep] = true
    }

    /** Record where the player last started playing (enables "Continue"). */
    fun setLastPlayed(episode: Int, level: Int) {
        lastEpisode = episode
        lastLevel   = level
        hasSave     = true
    }

    /** Reset all progress to the initial state and clear the save file. */
    fun newGame() {
        beaten.fill(false)
        lastEpisode = 0
        lastLevel   = 0
        hasSave     = false
        save()
    }

    fun save() {
        val prefs = Gdx.app.getPreferences(PREFS_NAME)
        prefs.putString(KEY_BEATEN,   beaten.joinToString("") { if (it) "1" else "0" })
        prefs.putInteger(KEY_LAST_EP, lastEpisode)
        prefs.putInteger(KEY_LAST_LV, lastLevel)
        prefs.putBoolean(KEY_HAS_SAVE, hasSave)
        prefs.flush()
    }

    fun load() {
        val prefs = Gdx.app.getPreferences(PREFS_NAME)
        val s = prefs.getString(KEY_BEATEN, "")
        if (s.length >= TOTAL_EPISODES) {
            for (i in 0 until TOTAL_EPISODES) beaten[i] = s[i] == '1'
        }
        lastEpisode = prefs.getInteger(KEY_LAST_EP, 0)
        lastLevel   = prefs.getInteger(KEY_LAST_LV, 0)
        hasSave     = prefs.getBoolean(KEY_HAS_SAVE, false)
    }
}
