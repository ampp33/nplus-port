package com.nplus.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color

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
        private const val PREFS_NAME      = "nplus-progress"
        private const val KEY_BEATEN      = "beaten"
        private const val KEY_LAST_EP     = "last_episode"
        private const val KEY_LAST_LV     = "last_level"
        private const val KEY_HAS_SAVE    = "has_save"
        private const val KEY_NINJA_COLOR = "ninja_color"
        private const val KEY_RECORDS     = "episode_records"

        // Ninja colour palette — pixel-exact from Flavours_NinjaFlavours.png (P1 row, 0-indexed).
        // Bitmap is 13×2: row 0 = P1 colors, column index = flavour index.
        // Index 0 (black) is always unlocked — the original game's default.
        // Index N (1–12) unlocks when episode column N-1 is fully beaten (all 10 episodes).
        // Index 12 (#cacad0) matches the level background, making the ninja invisible.
        val NINJA_COLORS = longArrayOf(
            0x000000L, // 0  black       — always unlocked (original default)
            0x72190bL, // 1  dark red    — beat column 0  (ep  0– 9)
            0xe210a4L, // 2  magenta     — beat column 1  (ep 10–19)
            0x6a19afL, // 3  purple      — beat column 2  (ep 20–29)
            0x0e6c93L, // 4  teal        — beat column 3  (ep 30–39)
            0x7a972aL, // 5  green       — beat column 4  (ep 40–49)
            0xd3ad0aL, // 6  gold        — beat column 5  (ep 50–59)
            0xdb8c0fL, // 7  orange      — beat column 6  (ep 60–69)
            0x503f1eL, // 8  brown       — beat column 7  (ep 70–79)
            0xffffffL, // 9  white       — beat column 8  (ep 80–89)
            0x4e4e5aL, // 10 dark slate  — beat column 9  (ep 90–99)
            0x7a7989L, // 11 gray        — beat column 10 (ep 100–109)
            0xcacad0L, // 12 ghost       — beat column 11 (ep 110–119)
        )
        const val DEFAULT_NINJA_COLOR_INDEX = 0

        fun hexToColor(hex: Long): Color =
            Color((hex shr 16 and 0xFF) / 255f,
                  (hex shr  8 and 0xFF) / 255f,
                  (hex        and 0xFF) / 255f, 1f)
    }

    private val beaten  = BooleanArray(TOTAL_EPISODES)
    // Best (lowest) timer ticks when the episode was completed; -1 = no record yet.
    private val records = IntArray(TOTAL_EPISODES) { -1 }

    var selectedNinjaColorIndex: Int = DEFAULT_NINJA_COLOR_INDEX
        private set

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

    // ---------------------------------------------------------------------------
    // Ninja colour
    // ---------------------------------------------------------------------------

    /** True if all 10 episodes in [col] (0–11) have been beaten. */
    fun isColumnBeaten(col: Int): Boolean {
        if (col < 0 || col > 11) return false
        val start = col * 10
        val end   = start + 10
        if (end > TOTAL_EPISODES) return false
        return (start until end).all { beaten[it] }
    }

    fun isNinjaColorUnlocked(idx: Int): Boolean = when {
        idx == 0       -> true                  // black — always unlocked
        idx in 1..12   -> isColumnBeaten(idx - 1)
        else           -> false
    }

    fun setNinjaColor(idx: Int) {
        if (idx in NINJA_COLORS.indices && isNinjaColorUnlocked(idx))
            selectedNinjaColorIndex = idx
    }

    fun getNinjaColor(): Color = hexToColor(NINJA_COLORS[selectedNinjaColorIndex])

    /** Mark [ep] as beaten and unlock the next episode. */
    fun beatEpisode(ep: Int) {
        if (ep < 0 || ep >= TOTAL_EPISODES) return
        beaten[ep] = true
    }

    /** Returns the best (lowest) timer ticks for [ep], or -1 if never completed. */
    fun getEpisodeRecord(ep: Int): Int =
        if (ep in 0 until TOTAL_EPISODES) records[ep] else -1

    /** Updates the record for [ep] if [ticks] is better (lower) than the existing record. */
    fun setEpisodeRecord(ep: Int, ticks: Int) {
        if (ep < 0 || ep >= TOTAL_EPISODES) return
        if (records[ep] < 0 || ticks < records[ep]) records[ep] = ticks
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
        records.fill(-1)
        lastEpisode             = 0
        lastLevel               = 0
        hasSave                 = false
        selectedNinjaColorIndex = DEFAULT_NINJA_COLOR_INDEX
        save()
    }

    fun save() {
        val prefs = Gdx.app.getPreferences(PREFS_NAME)
        prefs.putString(KEY_BEATEN,       beaten.joinToString("") { if (it) "1" else "0" })
        prefs.putInteger(KEY_LAST_EP,     lastEpisode)
        prefs.putInteger(KEY_LAST_LV,     lastLevel)
        prefs.putBoolean(KEY_HAS_SAVE,    hasSave)
        prefs.putInteger(KEY_NINJA_COLOR, selectedNinjaColorIndex)
        prefs.putString(KEY_RECORDS,      records.joinToString(","))
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
        val savedColor = prefs.getInteger(KEY_NINJA_COLOR, DEFAULT_NINJA_COLOR_INDEX)
        selectedNinjaColorIndex = if (savedColor in NINJA_COLORS.indices) savedColor
                                  else DEFAULT_NINJA_COLOR_INDEX
        val recStr = prefs.getString(KEY_RECORDS, "").split(",")
        for (i in 0 until TOTAL_EPISODES) {
            records[i] = recStr.getOrNull(i)?.toIntOrNull() ?: -1
        }
    }
}
