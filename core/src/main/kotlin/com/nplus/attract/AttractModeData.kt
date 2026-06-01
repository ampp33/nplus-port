package com.nplus.attract

import com.badlogic.gdx.Gdx
import java.io.DataInputStream

data class AttractEntry(val levelIndex: Int, val frames: ByteArray)

/**
 * Loads and cycles through the 79 pre-recorded attract-mode replays shipped with the original game.
 *
 * Binary format (big-endian, from StoreAttractModeReplays_Replays.bin):
 *   Repeat until EOF:
 *     Int32  levelIndex  (episode * 5 + levelWithinEpisode)
 *     Int32  byteCount N
 *     N bytes            input frames (one byte/tick: bit0=Jump, bit1=Left, bit2=Right)
 */
object AttractModeData {

    private val entries = mutableListOf<AttractEntry>()
    private var cursor  = 0

    fun load() {
        if (entries.isNotEmpty()) return
        try {
            val stream = DataInputStream(Gdx.files.internal("attract/replays.bin").read())
            stream.use {
                while (stream.available() >= 8) {
                    val levelIdx = stream.readInt()
                    val length   = stream.readInt()
                    val bytes    = ByteArray(length)
                    stream.readFully(bytes)
                    entries += AttractEntry(levelIdx, bytes)
                }
            }
            Gdx.app.log("AttractModeData", "Loaded ${entries.size} attract replays")
        } catch (e: Exception) {
            Gdx.app.error("AttractModeData", "Failed to load attract replays: ${e.message}")
        }
        cursor = (Math.random() * entries.size.coerceAtLeast(1)).toInt().coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    }

    fun hasEntries() = entries.isNotEmpty()

    fun randomEntry(): AttractEntry? {
        if (entries.isEmpty()) return null
        cursor = (Math.random() * entries.size).toInt().coerceIn(0, entries.size - 1)
        return entries[cursor]
    }

    fun nextEntry(): AttractEntry? {
        if (entries.isEmpty()) return null
        cursor = (cursor + 1) % entries.size
        return entries[cursor]
    }
}
