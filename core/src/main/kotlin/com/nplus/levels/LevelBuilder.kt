package com.nplus.levels

import com.nplus.audio.AudioManager
import com.nplus.physics.Ninja
import com.nplus.physics.Simulator
import com.nplus.physics.collision.GridEdges
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.collision.GridSegment
import com.nplus.physics.entities.EntityBase
import com.nplus.levels.EntityFactory
import com.nplus.physics.input.InputSource
import com.nplus.physics.math.MathUtils
import com.nplus.physics.tiles.TileDefs
import com.nplus.physics.tiles.TileTypes

/**
 * Converts a [LevelData] into a fully-initialised [Simulator].
 *
 * Mirrors the logic of AS3 sim_loader.LoadLevel_EditorState_Tiles and
 * LoadLevel_InitTileIDGridWithBoundaryEdges.
 *
 * Entity instantiation is deferred to [EntityFactory] (step 11). Pass an
 * [EntityFactory] implementation to create real entities; the default no-op
 * factory leaves the entity list empty so tile geometry can be tested first.
 */
object LevelBuilder {

    private val CELL_SIZE = Simulator.GRID_CELL_SIZE
    private val HALF      = Simulator.GRID_CELL_HALF
    private val COLS      = Simulator.GRID_NUM_COLS   // 33
    private val ROWS      = Simulator.GRID_NUM_ROWS   // 25
    private val TILE_COLS = Simulator.TILE_COLS        // 31
    private val TILE_ROWS = Simulator.TILE_ROWS        // 23

    fun interface EntityFactoryFn {
        /** Called once per level. Produce entity list from raw parsed data. */
        fun create(data: LevelData, segGrid: GridSegment, edgeGrid: GridEdges, objGrid: GridEntity): List<EntityBase>
    }

    fun interface NinjaFactory {
        fun create(data: LevelData, inputSources: List<InputSource>): List<Ninja>
    }

    /**
     * Build a [Simulator] from [data].
     *
     * [inputSources] length determines number of players; if [data] has fewer spawn
     * points they are padded/trimmed to match.
     *
     * [entityFactory] defaults to empty — supply it after step 11 to create real entities.
     */
    fun build(
        data: LevelData,
        inputSources: List<InputSource>,
        audio: AudioManager = AudioManager.NULL,
        entityFactory: EntityFactoryFn = EntityFactoryFn { d, sg, eg, og -> EntityFactory.create(d, sg, eg, og) },
        ninjaFactory: NinjaFactory? = null
    ): Simulator {
        MathUtils.generateNewRandomSeed()

        val tileGrid = IntArray(COLS * ROWS) { TileTypes.EMPTY }
        initBoundary(tileGrid)
        loadInteriorTiles(tileGrid, data.tileIds)

        val segGrid  = GridSegment(COLS, ROWS, CELL_SIZE)
        val edgeGrid = GridEdges(COLS * 2, ROWS * 2, CELL_SIZE * 0.5f)
        val objGrid  = GridEntity(COLS, ROWS, CELL_SIZE)

        segGrid.clear(); edgeGrid.clear()
        buildTileGeometry(tileGrid, segGrid, edgeGrid)

        val entities = entityFactory.create(data, segGrid, edgeGrid, objGrid)

        val players = if (ninjaFactory != null) {
            ninjaFactory.create(data, inputSources)
        } else {
            buildNinjas(data, inputSources, audio)
        }

        // Subclass overrides sound/particle hooks with AudioManager calls
        return object : Simulator(segGrid, edgeGrid, objGrid, entities, players, tileGrid) {
            override fun playSoundGold()                  = audio.playGold()
            override fun playSoundRagdoll(name: String)   = audio.playSound(name)
            override fun playSoundEntity(name: String)    = audio.playSound(name)
            override fun startLoopSoundEntity(name: String) = audio.startLoopSound(name)
            override fun stopLoopSoundEntity(name: String)  = audio.stopLoopSound(name)
        }
    }

    // --- Tile grid initialisation ---

    private fun initBoundary(grid: IntArray) {
        // Top and bottom boundary rows
        for (col in 1 until COLS - 1) {
            grid[col] = TileTypes.EDGE_BOTTOM                     // row 0
            grid[col + (ROWS - 1) * COLS] = TileTypes.EDGE_TOP   // row ROWS-1
        }
        // Left and right boundary columns
        for (row in 1 until ROWS - 1) {
            grid[row * COLS] = TileTypes.EDGE_RIGHT               // col 0
            grid[COLS - 1 + row * COLS] = TileTypes.EDGE_LEFT     // col COLS-1
        }
        // Corners
        grid[0]                          = TileTypes.EDGE_CORNER_UL
        grid[COLS - 1]                   = TileTypes.EDGE_CORNER_UR
        grid[(ROWS - 1) * COLS]          = TileTypes.EDGE_CORNER_DL
        grid[ROWS * COLS - 1]            = TileTypes.EDGE_CORNER_DR
    }

    /** Copy 31×23 interior tile IDs (row-major) into the full grid (offset +1 in each dimension). */
    private fun loadInteriorTiles(grid: IntArray, tileIds: IntArray) {
        for (idx in tileIds.indices) {
            val col = 1 + idx % TILE_COLS
            val row = 1 + idx / TILE_COLS
            grid[col + row * COLS] = tileIds[idx]
        }
    }

    // --- Segment + edge generation ---

    private val neighborScratch = IntArray(4)

    private fun buildTileGeometry(grid: IntArray, segGrid: GridSegment, edgeGrid: GridEdges) {
        for (flatIdx in grid.indices) {
            val col = flatIdx % COLS
            val row = flatIdx / COLS
            val tileType = grid[flatIdx]

            // Neighbours: [0]=left, [1]=below, [2]=right, [3]=above
            neighborScratch[0] = if (col > 0)        grid[flatIdx - 1]    else TileTypes.EMPTY
            neighborScratch[1] = if (row < ROWS - 1) grid[flatIdx + COLS] else TileTypes.EMPTY
            neighborScratch[2] = if (col < COLS - 1) grid[flatIdx + 1]    else TileTypes.EMPTY
            neighborScratch[3] = if (row > 0)        grid[flatIdx - COLS] else TileTypes.EMPTY

            val tileX = col * CELL_SIZE + HALF
            val tileY = row * CELL_SIZE + HALF

            val segs = TileDefs.generateFiltered(tileType, neighborScratch, tileX, tileY, HALF)
            for (seg in segs) segGrid.addSegToCell(col, row, seg)

            edgeGrid.gameLoadTileEdges(col, row, tileType)
        }
    }

    // --- Player construction ---

    private fun buildNinjas(data: LevelData, inputSources: List<InputSource>, audio: AudioManager = AudioManager.NULL): List<Ninja> {
        val spawns = data.playerSpawns.map { it.worldX to it.worldY }.toMutableList()
        if (spawns.isEmpty()) spawns += (Simulator.GRID_CELL_SIZE * 1.5f) to (Simulator.GRID_CELL_SIZE * 1.5f)

        // Trim or pad spawns to match player count
        while (spawns.size < inputSources.size) spawns += spawns[0]
        while (spawns.size > inputSources.size) spawns.removeAt(spawns.lastIndex)

        // Two-player offset: spread spawns slightly apart
        if (inputSources.size == 2 && spawns.size == 2) {
            spawns[0] = spawns[0].first - 4f to spawns[0].second
            spawns[1] = spawns[1].first + 4f to spawns[1].second
        }

        return inputSources.mapIndexed { i, input ->
            Ninja(
                i, input, spawns[i].first, spawns[i].second,
                onSound     = audio::playSound,
                onLoopSound = { name, active ->
                    if (active) audio.startLoopSound(name) else audio.stopLoopSound(name)
                }
            )
        }
    }
}
