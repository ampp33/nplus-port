package com.nplus.physics.tiles

/**
 * Port of AS3 edgedefs. Populates per-tile edge states for the X and Y grid directions.
 * 6 edge slots per tile type; indexed as [tileType * 6 + slot].
 */
object EdgeDefs {

    private val edgestatesX = IntArray(TileTypes.NUM_TILE_TYPES * 6)
    private val edgestatesY = IntArray(TileTypes.NUM_TILE_TYPES * 6)

    init {
        with(TileTypes) {
            setX(FULL,          2,0,2, 2,0,2); setY(FULL,          2,0,2, 2,0,2)
            setY(EDGE_BOTTOM,   0,0,2, 0,0,2)
            rot90(EDGE_BOTTOM, EDGE_LEFT);  rot180(EDGE_BOTTOM, EDGE_TOP)
            rot270(EDGE_BOTTOM, EDGE_RIGHT)
            setX(HALF_BOTTOM,   0,0,0, 2,0,2); setY(HALF_BOTTOM,   0,2,2, 0,2,2)
            rot90(HALF_BOTTOM, HALF_LEFT);  rot180(HALF_BOTTOM, HALF_TOP)
            rot270(HALF_BOTTOM, HALF_RIGHT)
            setX(CONCAVE_PP,    2,0,1, 2,1,0); setY(CONCAVE_PP,    2,0,1, 2,1,0)
            rot90(CONCAVE_PP, CONCAVE_NP); rot180(CONCAVE_PP, CONCAVE_NN)
            rot270(CONCAVE_PP, CONCAVE_PN)
            setX(CONVEX_PP,     2,0,1, 2,0,1); setY(CONVEX_PP,     2,0,1, 2,0,1)
            rot90(CONVEX_PP, CONVEX_NP); rot180(CONVEX_PP, CONVEX_NN)
            rot270(CONVEX_PP, CONVEX_PN)
            copy(CONCAVE_PP, MED_PP); copy(CONCAVE_NP, MED_NP)
            copy(CONCAVE_NN, MED_NN); copy(CONCAVE_PN, MED_PN)
            setX(SMALL_22_PP,   2,0,1, 0,0,0); setY(SMALL_22_PP,   2,1,0, 2,1,0)
            rot90(SMALL_22_PP, SMALL_67_NP); rot180(SMALL_22_PP, SMALL_22_NN)
            rot270(SMALL_22_PP, SMALL_67_PN)
            setX(SMALL_67_PP,   2,1,0, 2,1,0); setY(SMALL_67_PP,   2,0,1, 0,0,0)
            rot90(SMALL_67_PP, SMALL_22_NP); rot180(SMALL_67_PP, SMALL_67_NN)
            rot270(SMALL_67_PP, SMALL_22_PN)
            setX(LARGE_22_PP,   2,0,2, 2,0,1); setY(LARGE_22_PP,   2,0,1, 2,0,1)
            rot90(LARGE_22_PP, LARGE_67_NP); rot180(LARGE_22_PP, LARGE_22_NN)
            rot270(LARGE_22_PP, LARGE_67_PN)
            setX(LARGE_67_PP,   2,0,1, 2,0,1); setY(LARGE_67_PP,   2,0,2, 2,0,1)
            rot90(LARGE_67_PP, LARGE_22_NP); rot180(LARGE_67_PP, LARGE_67_NN)
            rot270(LARGE_67_PP, LARGE_22_PN)
        }
    }

    fun getEdgeStateX(tileType: Int, slot: Int): Int = edgestatesX[tileType * 6 + slot]
    fun getEdgeStateY(tileType: Int, slot: Int): Int = edgestatesY[tileType * 6 + slot]

    private fun setX(t: Int, e0: Int, e1: Int, e2: Int, e3: Int, e4: Int, e5: Int) {
        val b = t * 6
        edgestatesX[b]=e0; edgestatesX[b+1]=e1; edgestatesX[b+2]=e2
        edgestatesX[b+3]=e3; edgestatesX[b+4]=e4; edgestatesX[b+5]=e5
    }

    private fun setY(t: Int, e0: Int, e1: Int, e2: Int, e3: Int, e4: Int, e5: Int) {
        val b = t * 6
        edgestatesY[b]=e0; edgestatesY[b+1]=e1; edgestatesY[b+2]=e2
        edgestatesY[b+3]=e3; edgestatesY[b+4]=e4; edgestatesY[b+5]=e5
    }

    private fun copy(src: Int, dst: Int) {
        val s = src * 6; val d = dst * 6
        for (i in 0..5) { edgestatesX[d+i] = edgestatesX[s+i]; edgestatesY[d+i] = edgestatesY[s+i] }
    }

    private fun rot90(src: Int, dst: Int) {
        val s = src * 6; val d = dst * 6
        edgestatesY[d+0]=edgestatesX[s+3]; edgestatesY[d+1]=edgestatesX[s+4]; edgestatesY[d+2]=edgestatesX[s+5]
        edgestatesY[d+3]=edgestatesX[s+0]; edgestatesY[d+4]=edgestatesX[s+1]; edgestatesY[d+5]=edgestatesX[s+2]
        edgestatesX[d+0]=edgestatesY[s+2]; edgestatesX[d+1]=edgestatesY[s+1]; edgestatesX[d+2]=edgestatesY[s+0]
        edgestatesX[d+3]=edgestatesY[s+5]; edgestatesX[d+4]=edgestatesY[s+4]; edgestatesX[d+5]=edgestatesY[s+3]
    }

    private fun rot180(src: Int, dst: Int) {
        val s = src * 6; val d = dst * 6
        for (i in 0..5) {
            edgestatesX[d+i] = edgestatesX[s + (5-i)]
            edgestatesY[d+i] = edgestatesY[s + (5-i)]
        }
    }

    private fun rot270(src: Int, dst: Int) {
        val s = src * 6; val d = dst * 6
        edgestatesY[d+0]=edgestatesX[s+2]; edgestatesY[d+1]=edgestatesX[s+1]; edgestatesY[d+2]=edgestatesX[s+0]
        edgestatesY[d+3]=edgestatesX[s+5]; edgestatesY[d+4]=edgestatesX[s+4]; edgestatesY[d+5]=edgestatesX[s+3]
        edgestatesX[d+0]=edgestatesY[s+3]; edgestatesX[d+1]=edgestatesY[s+4]; edgestatesX[d+2]=edgestatesY[s+5]
        edgestatesX[d+3]=edgestatesY[s+0]; edgestatesX[d+4]=edgestatesY[s+1]; edgestatesX[d+5]=edgestatesY[s+2]
    }
}
