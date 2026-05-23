package com.nplus.levels

/** Port of edat.ETYPE_* constants. Indices match the AS3 values exactly. */
object EntityTypes {
    const val PLAYER        = 0
    const val MINE          = 1
    const val GOLD          = 2
    const val DOOR_REGULAR  = 3
    const val DOOR_LOCKED   = 4
    const val SWITCH_LOCKED = 5
    const val DOOR_TRAP     = 6
    const val SWITCH_TRAP   = 7
    const val ONEWAY        = 8
    const val EXIT_DOOR     = 9
    const val EXIT_SWITCH   = 10
    const val CHAINGUN      = 11
    const val LASER         = 12
    const val ZAP           = 13
    const val CHASER        = 14
    const val FLOORGUARD    = 15
    const val LAUNCHPAD     = 16
    const val BOUNCEBLOCK   = 17
    const val ROCKET        = 18
    const val TURRET        = 19
    const val THWOMP        = 20

    /** Worldspace position = quantized_value × STEP (= 6 pixels per quantum). */
    const val QUANTIZE_STEP = 6
}

/** Port of edat.DIR_* constants — 8-direction enum. */
object DirTypes {
    const val R  = 0
    const val RD = 1
    const val D  = 2
    const val LD = 3
    const val L  = 4
    const val LU = 5
    const val U  = 6
    const val RU = 7

    private val VECS = arrayOf(
        floatArrayOf( 1f,  0f),
        floatArrayOf( 0.7071f,  0.7071f),
        floatArrayOf( 0f,  1f),
        floatArrayOf(-0.7071f,  0.7071f),
        floatArrayOf(-1f,  0f),
        floatArrayOf(-0.7071f, -0.7071f),
        floatArrayOf( 0f, -1f),
        floatArrayOf( 0.7071f, -0.7071f),
    )

    fun toVec(dir: Int): FloatArray = VECS[dir]
}

/**
 * A single parsed entity from the level binary.
 * [type] is an [EntityTypes] constant.
 * [props] are raw quantized values: props[0]=x, props[1]=y, props[2]=dir (if present), props[3]=move (if present).
 * Convert to worldspace: worldX = props[0] * EntityTypes.QUANTIZE_STEP.
 */
data class RawEntity(val type: Int, val props: IntArray) {
    val worldX: Float get() = props[0] * EntityTypes.QUANTIZE_STEP.toFloat()
    val worldY: Float get() = props[1] * EntityTypes.QUANTIZE_STEP.toFloat()
    val dir: Int get() = if (props.size > 2) props[2] else 0
    val move: Int get() = if (props.size > 3) props[3] else 0

    override fun equals(other: Any?) = other is RawEntity && type == other.type && props.contentEquals(other.props)
    override fun hashCode() = 31 * type + props.contentHashCode()
}

/**
 * All data needed to construct a Simulator for one level.
 *
 * [tileIds] is row-major: index = col + row * TILE_COLS (31 cols × 23 rows = 713 entries).
 * Tile type 0 = EMPTY, 1 = FULL; see [com.nplus.physics.tiles.TileTypes].
 */
data class LevelData(
    val episode: Int,
    val level: Int,
    val name: String,
    /** 713 tile type IDs, row-major: tileIds[col + row * 31] */
    val tileIds: IntArray,
    val entities: List<RawEntity>
) {
    val playerSpawns: List<RawEntity> get() = entities.filter { it.type == EntityTypes.PLAYER }

    override fun equals(other: Any?) =
        other is LevelData && episode == other.episode && level == other.level
    override fun hashCode() = 31 * episode + level
}
