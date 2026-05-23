package com.nplus.physics.collision

import kotlin.math.*

/** Port of AS3 Grid_Base — shared bookkeeping for all spatial grids. */
abstract class GridBase(
    protected val numCols: Int,
    protected val numRows: Int,
    protected val cellSize: Float
) {
    protected val numCells: Int = numCols * numRows

    protected fun worldToGrid(world: Float): Int = floor(world / cellSize).toInt()

    protected fun cellIndex(col: Int, row: Int): Int {
        val c = col.coerceIn(0, numCols - 1)
        val r = row.coerceIn(0, numRows - 1)
        return r * numCols + c
    }

    protected fun cellIndexFromWorld(x: Float, y: Float): Int =
        cellIndex(worldToGrid(x), worldToGrid(y))

    fun debugGetCellCentre(index: Int): com.nplus.physics.math.Vec2? {
        if (index == -1) return null
        val col = index % numCols
        val row = index / numCols
        return com.nplus.physics.math.Vec2((col + 0.5f) * cellSize, (row + 0.5f) * cellSize)
    }
}
