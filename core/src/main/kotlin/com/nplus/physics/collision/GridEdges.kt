package com.nplus.physics.collision

import com.nplus.physics.tiles.EdgeDefs
import com.nplus.physics.tiles.EdgeTypes
import kotlin.math.*

/**
 * Port of AS3 Grid_Edges.
 * Stores per-cell edge solid/empty/partial state for the X (vertical) and Y (horizontal)
 * directions, plus a door reference counter that layers on top of tile edges.
 */
class GridEdges(numCols: Int, numRows: Int, cellSize: Float) : GridBase(numCols, numRows, cellSize) {

    private val edgesTileX = IntArray(numCells)
    private val edgesTileY = IntArray(numCells)
    private val edgesDoorX = IntArray(numCells)
    private val edgesDoorY = IntArray(numCells)

    fun clear() {
        for (i in 0 until numCells) {
            edgesTileX[i] = EdgeTypes.EMPTY; edgesTileY[i] = EdgeTypes.EMPTY
            edgesDoorX[i] = 0;               edgesDoorY[i] = 0
        }
    }

    fun doorGetCellIndex(col: Int, row: Int): Int = cellIndex(col, row)

    fun getGridCoordFromWorld1D(world: Float): Int = worldToGrid(world)

    fun getWorldCoordFromGridEdge1D(gridCoord: Int, offset: Int): Int =
        ((gridCoord + max(0, offset)) * cellSize).toInt()

    fun isSolid(col: Int, row: Int, edgeDir: Int, axis: Int): Boolean {
        val idx = indexFromGridspaceAndOffset(col, row, edgeDir, axis)
        return if (axis == 0) edgesTileX[idx] == EdgeTypes.SOLID || edgesDoorX[idx] != 0
               else           edgesTileY[idx] == EdgeTypes.SOLID || edgesDoorY[idx] != 0
    }

    fun isSolidIgnoreDoors(col: Int, row: Int, edgeDir: Int, axis: Int): Boolean {
        val idx = indexFromGridspaceAndOffset(col, row, edgeDir, axis)
        return if (axis == 0) edgesTileX[idx] == EdgeTypes.SOLID
               else           edgesTileY[idx] == EdgeTypes.SOLID
    }

    fun isEmpty(col: Int, row: Int, edgeDir: Int, axis: Int): Boolean {
        val idx = indexFromGridspaceAndOffset(col, row, edgeDir, axis)
        return if (axis == 0) edgesTileX[idx] == EdgeTypes.EMPTY && edgesDoorX[idx] == 0
               else           edgesTileY[idx] == EdgeTypes.EMPTY && edgesDoorY[idx] == 0
    }

    // --- Scan / sweep helpers used by FloorGuard AI ---

    fun scanHorizontal(rowMin: Int, rowMax: Int, colStart: Int, colEnd: Int): Boolean {
        val step = colEnd - colStart
        if (step == 0) return true
        return scanHorizontalDirected(rowMin, rowMax, colStart, colEnd, step / abs(step))
    }

    fun scanHorizontalDirected(rowMin: Int, rowMax: Int, colStart: Int, colEnd: Int, dir: Int): Boolean {
        var c = colStart
        while (c != colEnd) {
            if (!isEmptyColumn(c, rowMin, rowMax, dir)) return false
            c += dir
            if (abs(c) > 100) return false
        }
        return true
    }

    fun scanVertical(colMin: Int, colMax: Int, rowStart: Int, rowEnd: Int): Boolean {
        val step = rowEnd - rowStart
        if (step == 0) return true
        return scanVerticalDirected(colMin, colMax, rowStart, rowEnd, step / abs(step))
    }

    fun scanVerticalDirected(colMin: Int, colMax: Int, rowStart: Int, rowEnd: Int, dir: Int): Boolean {
        var r = rowStart
        while (r != rowEnd) {
            if (!isEmptyRow(r, colMin, colMax, dir)) return false
            r += dir
            if (abs(r) > 100) return false
        }
        return true
    }

    fun sweepHorizontal(rowMin: Int, rowMax: Int, colStart: Int, dir: Int): Int {
        var c = colStart
        while (isEmptyColumn(c, rowMin, rowMax, dir)) {
            c += dir
            if (abs(c) > 100) return colStart
        }
        return c
    }

    fun sweepVertical(colMin: Int, colMax: Int, rowStart: Int, dir: Int): Int {
        var r = rowStart
        while (isEmptyRow(r, colMin, colMax, dir)) {
            r += dir
            if (abs(r) > 100) return rowStart
        }
        return r
    }

    fun isEmptyColumn(col: Int, rowMin: Int, rowMax: Int, dir: Int): Boolean {
        for (r in rowMin..rowMax) if (!isEmpty(col, r, dir, 0)) return false
        return true
    }

    fun isEmptyRow(row: Int, colMin: Int, colMax: Int, dir: Int): Boolean {
        for (c in colMin..colMax) if (!isEmpty(c, row, 0, dir)) return false
        return true
    }

    // --- Level loading ---

    fun gameLoadTileEdges(tileCol: Int, tileRow: Int, tileType: Int) {
        val baseC = tileCol * 2; val baseR = tileRow * 2
        for (i in 0..2) for (j in 0..1) {
            val cX = cellIndex(baseC - 1 + i, baseR + j)
            val cY = cellIndex(baseC + j, baseR - 1 + i)
            val slot = i + j * 3
            loadEdgeStateX(cX, EdgeDefs.getEdgeStateX(tileType, slot))
            loadEdgeStateY(cY, EdgeDefs.getEdgeStateY(tileType, slot))
        }
    }

    fun doorIncrementEdge(index: Int, isXAxis: Boolean) {
        if (isXAxis) edgesDoorX[index]++ else edgesDoorY[index]++
    }

    fun doorDecrementEdge(index: Int, isXAxis: Boolean) {
        if (isXAxis) {
            if (edgesDoorX[index] > 0) edgesDoorX[index]--
        } else {
            if (edgesDoorY[index] > 0) edgesDoorY[index]--
        }
    }

    // --- Private helpers ---

    private fun loadEdgeStateX(idx: Int, state: Int) {
        edgesTileX[idx] = if (edgesTileX[idx] == EdgeTypes.SOLID && state == EdgeTypes.SOLID) EdgeTypes.EMPTY
                          else maxOf(edgesTileX[idx], state)
    }

    private fun loadEdgeStateY(idx: Int, state: Int) {
        edgesTileY[idx] = if (edgesTileY[idx] == EdgeTypes.SOLID && state == EdgeTypes.SOLID) EdgeTypes.EMPTY
                          else maxOf(edgesTileY[idx], state)
    }

    private fun indexFromGridspaceAndOffset(col: Int, row: Int, edgeDir: Int, axis: Int): Int {
        // Port of AS3 GetIndexFromGridspaceAndOffset — matches the exact branching logic.
        // When axis==0: edgeDir selects the X-axis cell boundary.
        // When axis!=0 and edgeDir==0: axis value selects the Y-axis cell boundary.
        return if (axis == 0) {
            when (edgeDir) {
                -1 -> cellIndex(col - 1, row)
                 1 -> cellIndex(col, row)
                else -> -1
            }
        } else if (edgeDir == 0) {
            when (axis) {
                -1 -> cellIndex(col, row - 1)
                 1 -> cellIndex(col, row)
                else -> -1
            }
        } else -1
    }
}
