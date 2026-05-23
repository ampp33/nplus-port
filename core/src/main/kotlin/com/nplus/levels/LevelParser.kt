package com.nplus.levels

import java.io.DataInputStream
import java.io.InputStream

/**
 * Port of AS3 StoreMetanetLevelData + Editor_State.Load_From_Bytes.
 *
 * ## Binary file format (levels.bin)
 * Outer container — each record:
 *   [i32 episode][i32 level][i32 data_size][data_size bytes]
 *
 * Individual level data:
 *   [u16 name_len][UTF-8 name][4 skip bytes][713 tile bytes][entity struct sections]
 *
 * Entity struct sections — one per struct type (18 total, 0-17):
 *   [i16 count][count × struct_bytes]
 * Special compound types (EXIT, DOOR_LOCKED, DOOR_TRAP) produce two entities per count unit.
 */
object LevelParser {

    // edat.num_cols × num_rows = 31 × 23 = 713
    private const val TILE_COUNT = 31 * 23

    // Struct type indices (edat.STRUCTTYPE_*)
    private const val STRUCTTYPE_EXIT        = 3
    private const val STRUCTTYPE_DOOR_LOCKED = 5
    private const val STRUCTTYPE_DOOR_TRAP   = 6

    // Bytes per struct (matches AS3 edat.STRUCT_SIZE[0..17])
    private val STRUCT_SIZE = intArrayOf(
        2, 2, 2, 4, 3, 5, 5, 3, 3, 4, 4, 4, 4, 2, 2, 2, 2, 3
    )

    // Struct type → edat.ETYPE_* (AS3 MAP_STRUCT_TO_ETYPE, indexed 0-17)
    private val STRUCT_TO_ETYPE = intArrayOf(
        EntityTypes.PLAYER,       // 0  PLAYER
        EntityTypes.MINE,         // 1  MINE
        EntityTypes.GOLD,         // 2  GOLD
        EntityTypes.EXIT_DOOR,    // 3  EXIT (door half; switch added separately)
        EntityTypes.DOOR_REGULAR, // 4  DOOR_REGULAR
        EntityTypes.DOOR_LOCKED,  // 5  DOOR_LOCKED (door half; switch added separately)
        EntityTypes.DOOR_TRAP,    // 6  DOOR_TRAP (door half; switch added separately)
        EntityTypes.LAUNCHPAD,    // 7  LAUNCHPAD
        EntityTypes.ONEWAY,       // 8  ONEWAY
        EntityTypes.CHAINGUN,     // 9  CHAINGUN
        EntityTypes.LASER,        // 10 LASER
        EntityTypes.ZAP,          // 11 ZAP
        EntityTypes.CHASER,       // 12 CHASER
        EntityTypes.FLOORGUARD,   // 13 FLOORGUARD
        EntityTypes.BOUNCEBLOCK,  // 14 BOUNCEBLOCK
        EntityTypes.ROCKET,       // 15 ROCKET
        EntityTypes.TURRET,       // 16 TURRET
        EntityTypes.THWOMP        // 17 THWOMP
    )

    /**
     * Parse the full levels.bin and return a flat list of all levels.
     * The returned list is in the order they appear in the binary
     * (typically episode-major, level-minor order).
     */
    fun parseBin(stream: InputStream): List<LevelData> {
        val dis = DataInputStream(stream)
        val result = mutableListOf<LevelData>()
        while (dis.available() > 0) {
            val episode  = dis.readInt()
            val level    = dis.readInt()
            val dataSize = dis.readInt()
            val bytes    = ByteArray(dataSize).also { dis.readFully(it) }
            result += parseLevelBytes(episode, level, bytes)
        }
        return result
    }

    /** Parse levels into a map keyed by (episode, level). */
    fun parseBinToMap(stream: InputStream): Map<Pair<Int,Int>, LevelData> =
        parseBin(stream).associateBy { it.episode to it.level }

    // --- Private ---

    private fun parseLevelBytes(episode: Int, level: Int, bytes: ByteArray): LevelData {
        val dis = DataInputStream(bytes.inputStream())

        // Name: u16 length-prefixed UTF-8 string
        val nameLen = dis.readUnsignedShort()
        val name = String(ByteArray(nameLen).also { dis.readFully(it) }, Charsets.UTF_8)

        // Editor_State.Load_From_Bytes preamble: skip 4 bytes
        repeat(4) { dis.readUnsignedByte() }

        // Tile IDs: 713 raw bytes (tile type 0=EMPTY, 1=FULL, etc.)
        val tileIds = IntArray(TILE_COUNT) { dis.readUnsignedByte() }

        // Entity structs: 18 sections, one per struct type
        val entities = mutableListOf<RawEntity>()
        for (structType in 0 until 18) {
            val count = dis.readShort().toInt()
            repeat(count) {
                when (structType) {
                    STRUCTTYPE_EXIT -> {
                        // EXIT: 2 bytes door pos + 2 bytes switch pos
                        val dx = dis.readUnsignedByte(); val dy = dis.readUnsignedByte()
                        val sx = dis.readUnsignedByte(); val sy = dis.readUnsignedByte()
                        entities += RawEntity(EntityTypes.EXIT_DOOR,    intArrayOf(dx, dy))
                        entities += RawEntity(EntityTypes.EXIT_SWITCH,  intArrayOf(sx, sy))
                    }
                    STRUCTTYPE_DOOR_LOCKED -> {
                        // DOOR_LOCKED: 3 bytes door + 2 bytes switch
                        val dx = dis.readUnsignedByte(); val dy = dis.readUnsignedByte()
                        val dd = dis.readUnsignedByte()
                        val sx = dis.readUnsignedByte(); val sy = dis.readUnsignedByte()
                        entities += RawEntity(EntityTypes.DOOR_LOCKED,   intArrayOf(dx, dy, dd))
                        entities += RawEntity(EntityTypes.SWITCH_LOCKED, intArrayOf(sx, sy))
                    }
                    STRUCTTYPE_DOOR_TRAP -> {
                        // DOOR_TRAP: 3 bytes door + 2 bytes switch
                        val dx = dis.readUnsignedByte(); val dy = dis.readUnsignedByte()
                        val dd = dis.readUnsignedByte()
                        val sx = dis.readUnsignedByte(); val sy = dis.readUnsignedByte()
                        entities += RawEntity(EntityTypes.DOOR_TRAP,    intArrayOf(dx, dy, dd))
                        entities += RawEntity(EntityTypes.SWITCH_TRAP,  intArrayOf(sx, sy))
                    }
                    else -> {
                        val props = IntArray(STRUCT_SIZE[structType]) { dis.readUnsignedByte() }
                        entities += RawEntity(STRUCT_TO_ETYPE[structType], props)
                    }
                }
            }
        }

        return LevelData(episode, level, name, tileIds, entities)
    }
}
