package com.nplus.physics.tiles

import com.nplus.physics.collision.Segment
import com.nplus.physics.collision.SegmentCircular
import com.nplus.physics.collision.SegmentLinear

// ---------------------------------------------------------------------------
// Tile-edge archetypes — geometry in normalised [-1,1] space
// CCW 90° rotation: (x,y) → (-y, x)
// ---------------------------------------------------------------------------

private sealed class Archetype {
    abstract fun generate(tileX: Float, tileY: Float, hw: Float): Segment
    abstract fun perp(): Archetype

    class Lin(val p0x: Float, val p0y: Float, val p1x: Float, val p1y: Float) : Archetype() {
        override fun generate(tileX: Float, tileY: Float, hw: Float) =
            SegmentLinear(tileX + p0x*hw, tileY + p0y*hw, tileX + p1x*hw, tileY + p1y*hw)
        override fun perp() = Lin(-p0y, p0x, -p1y, p1x)
    }

    // Parameter order matches AS3 TileEdgeArchetype_Circular(p0x,p0y,p1x,p1y,centreX,centreY).
    class Circ(val p0x: Float, val p0y: Float, val p1x: Float, val p1y: Float,
               val cx: Float, val cy: Float) : Archetype() {
        override fun generate(tileX: Float, tileY: Float, hw: Float) =
            SegmentCircular(
                tileX + cx*hw, tileY + cy*hw,
                tileX + p0x*hw, tileY + p0y*hw,
                tileX + p1x*hw, tileY + p1y*hw
            )
        override fun perp() = Circ(-p0y, p0x, -p1y, p1x, -cy, cx)
    }
}

/**
 * Port of AS3 tiledefs — generates collision segments for each tile type.
 * All static data is initialised once in the companion object init block.
 */
object TileDefs {

    // 8-direction perpendicular index table (CCW rotation of boundary flag indices)
    private val perpIndex = intArrayOf(2, 3, 5, 4, 6, 7, 1, 0)

    // Per-tile interior shape archetype (null = no interior segment, e.g. FULL or EMPTY)
    private val segDefs = arrayOfNulls<Archetype>(TileTypes.NUM_TILE_TYPES)

    // Per-tile: 8 boundary flags indicating exposed edge halves
    private val bFlags = Array(TileTypes.NUM_TILE_TYPES) { BooleanArray(8) }

    // 12 boundary edge archetypes (4 sides × 3: half-A, half-B, full)
    private val bDefs = arrayOfNulls<Archetype>(12)

    init {
        initSegDefs()
        initBoundaryFlags()
        initBoundaryDefs()
    }

    // --- Public API ---

    /**
     * Port of AS3 GenerateTileSegments_Filtered.
     * [tileType] — the tile at this cell.
     * [neighbors] — 4 adjacent tile types: [0]=left, [1]=below, [2]=right, [3]=above.
     * [tileX],[tileY] — worldspace centre of this tile (col*24+12, row*24+12).
     * [halfWidth] — GRID_CELL_HALFWIDTH = 12f.
     */
    fun generateFiltered(
        tileType: Int, neighbors: IntArray,
        tileX: Float, tileY: Float, halfWidth: Float
    ): List<Segment> {
        if (tileType < 0 || tileType >= TileTypes.NUM_TILE_TYPES) return emptyList()
        val result = mutableListOf<Segment>()
        for (side in 0..3) {
            val f0 = bFlags[tileType][side * 2]     && !bFlags[neighbors[side]][(side * 2 + 4) % 8]
            val f1 = bFlags[tileType][side * 2 + 1] && !bFlags[neighbors[side]][(side * 2 + 1 + 4) % 8]
            val def = when {
                f0 && f1 -> bDefs[side * 3 + 2]
                f0       -> bDefs[side * 3]
                f1       -> bDefs[side * 3 + 1]
                else     -> null
            }
            def?.generate(tileX, tileY, halfWidth)?.let { result += it }
        }
        segDefs[tileType]?.generate(tileX, tileY, halfWidth)?.let { result += it }
        return result
    }

    // --- Init helpers ---

    private fun initSegDefs() {
        with(TileTypes) {
            // Half-tile linear segments
            segDefs[HALF_TOP]    = Archetype.Lin(-1f,0f,1f,0f)
            segDefs[HALF_RIGHT]  = segDefs[HALF_TOP]!!.perp()
            segDefs[HALF_BOTTOM] = segDefs[HALF_RIGHT]!!.perp()
            segDefs[HALF_LEFT]   = segDefs[HALF_BOTTOM]!!.perp()

            // Medium diagonal segments
            segDefs[MED_PP] = Archetype.Lin(-1f,1f,1f,-1f)
            segDefs[MED_NP] = segDefs[MED_PP]!!.perp()
            segDefs[MED_NN] = segDefs[MED_NP]!!.perp()
            segDefs[MED_PN] = segDefs[MED_NN]!!.perp()

            // Convex circular segments (arc, normal points away from centre)
            segDefs[CONVEX_PP] = Archetype.Circ(-1f,1f,1f,-1f,-1f,-1f)
            segDefs[CONVEX_NP] = segDefs[CONVEX_PP]!!.perp()
            segDefs[CONVEX_NN] = segDefs[CONVEX_NP]!!.perp()
            segDefs[CONVEX_PN] = segDefs[CONVEX_NN]!!.perp()

            // Concave circular segments (arc, normal points toward centre)
            segDefs[CONCAVE_PP] = Archetype.Circ(-1f,1f,1f,-1f,1f,1f)
            segDefs[CONCAVE_NP] = segDefs[CONCAVE_PP]!!.perp()
            segDefs[CONCAVE_NN] = segDefs[CONCAVE_NP]!!.perp()
            segDefs[CONCAVE_PN] = segDefs[CONCAVE_NN]!!.perp()

            // Small 22.5° slope segments
            segDefs[SMALL_22_PP] = Archetype.Lin(-1f,0f,1f,-1f)
            segDefs[SMALL_67_NP] = segDefs[SMALL_22_PP]!!.perp()
            segDefs[SMALL_22_NN] = segDefs[SMALL_67_NP]!!.perp()
            segDefs[SMALL_67_PN] = segDefs[SMALL_22_NN]!!.perp()

            segDefs[SMALL_67_PP] = Archetype.Lin(-1f,1f,0f,-1f)
            segDefs[SMALL_22_NP] = segDefs[SMALL_67_PP]!!.perp()
            segDefs[SMALL_67_NN] = segDefs[SMALL_22_NP]!!.perp()
            segDefs[SMALL_22_PN] = segDefs[SMALL_67_NN]!!.perp()

            // Large 22.5° slope segments
            segDefs[LARGE_22_PP] = Archetype.Lin(-1f,1f,1f,0f)
            segDefs[LARGE_67_NP] = segDefs[LARGE_22_PP]!!.perp()
            segDefs[LARGE_22_NN] = segDefs[LARGE_67_NP]!!.perp()
            segDefs[LARGE_67_PN] = segDefs[LARGE_22_NN]!!.perp()

            segDefs[LARGE_67_PP] = Archetype.Lin(0f,1f,1f,-1f)
            segDefs[LARGE_22_NP] = segDefs[LARGE_67_PP]!!.perp()
            segDefs[LARGE_67_NN] = segDefs[LARGE_22_NP]!!.perp()
            segDefs[LARGE_22_PN] = segDefs[LARGE_67_NN]!!.perp()
        }
    }

    private fun initBoundaryFlags() {
        with(TileTypes) {
            bFlags[FULL].fill(true)

            // Edge tiles: 2 flags each
            bFlags[EDGE_TOP][6] = true; bFlags[EDGE_TOP][7] = true
            setFlagsPerp(EDGE_TOP, EDGE_RIGHT); setFlagsPerp(EDGE_RIGHT, EDGE_BOTTOM)
            setFlagsPerp(EDGE_BOTTOM, EDGE_LEFT)

            // Half tiles: 4 flags each
            setFlags(HALF_TOP, 0, 4, 6, 7)
            setFlagsPerp(HALF_TOP, HALF_RIGHT); setFlagsPerp(HALF_RIGHT, HALF_BOTTOM)
            setFlagsPerp(HALF_BOTTOM, HALF_LEFT)

            // Medium (= concave = convex boundary flags): 4 flags
            setFlags(MED_PP, 0, 1, 6, 7)
            setFlagsPerp(MED_PP, MED_NP); setFlagsPerp(MED_NP, MED_NN); setFlagsPerp(MED_NN, MED_PN)
            setFlagsCopy(MED_PP, CONVEX_PP); setFlagsCopy(MED_NP, CONVEX_NP)
            setFlagsCopy(MED_NN, CONVEX_NN); setFlagsCopy(MED_PN, CONVEX_PN)
            setFlagsCopy(MED_PP, CONCAVE_PP); setFlagsCopy(MED_NP, CONCAVE_NP)
            setFlagsCopy(MED_NN, CONCAVE_NN); setFlagsCopy(MED_PN, CONCAVE_PN)

            // Small 22.5° slopes: 3 flags
            setFlags(SMALL_22_PP, 0, 6, 7)
            setFlagsPerp(SMALL_22_PP, SMALL_67_NP); setFlagsPerp(SMALL_67_NP, SMALL_22_NN)
            setFlagsPerp(SMALL_22_NN, SMALL_67_PN)

            setFlags(SMALL_67_PP, 0, 1, 6)
            setFlagsPerp(SMALL_67_PP, SMALL_22_NP); setFlagsPerp(SMALL_22_NP, SMALL_67_NN)
            setFlagsPerp(SMALL_67_NN, SMALL_22_PN)

            // Large 22.5° slopes: 5 flags
            setFlags(LARGE_22_PP, 0, 1, 4, 6, 7)
            setFlagsPerp(LARGE_22_PP, LARGE_67_NP); setFlagsPerp(LARGE_67_NP, LARGE_22_NN)
            setFlagsPerp(LARGE_22_NN, LARGE_67_PN)

            setFlags(LARGE_67_PP, 0, 1, 2, 6, 7)
            setFlagsPerp(LARGE_67_PP, LARGE_22_NP); setFlagsPerp(LARGE_22_NP, LARGE_67_NN)
            setFlagsPerp(LARGE_67_NN, LARGE_22_PN)
        }
    }

    private fun initBoundaryDefs() {
        // Side 0 (left, x = -1): base definitions
        bDefs[0] = Archetype.Lin(-1f, -1f, -1f,  0f)  // top half
        bDefs[1] = Archetype.Lin(-1f,  0f, -1f,  1f)  // bottom half
        bDefs[2] = Archetype.Lin(-1f, -1f, -1f,  1f)  // full

        // Side 3 (top) — one CCW rotation of side 0
        bDefs[9]  = bDefs[1]!!.perp()
        bDefs[10] = bDefs[0]!!.perp()
        bDefs[11] = bDefs[2]!!.perp()

        // Side 2 (right) — two CCW rotations of side 0
        bDefs[7] = bDefs[10]!!.perp()
        bDefs[6] = bDefs[9]!!.perp()
        bDefs[8] = bDefs[11]!!.perp()

        // Side 1 (bottom) — three CCW rotations of side 0
        bDefs[4] = bDefs[6]!!.perp()
        bDefs[3] = bDefs[7]!!.perp()
        bDefs[5] = bDefs[8]!!.perp()
    }

    private fun setFlags(type: Int, vararg indices: Int) {
        for (i in indices) bFlags[type][i] = true
    }

    private fun setFlagsPerp(src: Int, dst: Int) {
        for (i in 0..7) bFlags[dst][i] = bFlags[src][perpIndex[i]]
    }

    private fun setFlagsCopy(src: Int, dst: Int) {
        bFlags[src].copyInto(bFlags[dst])
    }
}
