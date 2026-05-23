package com.nplus.physics.collision

import com.nplus.physics.math.Vec2
import kotlin.math.*

/** Port of AS3 Grid_Segment — spatial hash for Segment objects; supports DDA raycasting. */
class GridSegment(numCols: Int, numRows: Int, cellSize: Float) : GridBase(numCols, numRows, cellSize) {

    private val cells: Array<MutableList<Segment>> = Array(numCells) { mutableListOf() }

    // Scratch vecs for raycasting — physics is single-threaded.
    private val rayPos  = Vec2()
    private val rayVec  = Vec2()
    private val tempP   = Vec2()
    private val tempN   = Vec2()

    fun clear() { cells.forEach { it.clear() } }

    fun addSegToCell(col: Int, row: Int, seg: Segment) {
        cells[cellIndex(col, row)].add(seg)
    }

    /** Adds [seg] to [index] only if not already present (used by doors). */
    fun doorAddSegment(index: Int, seg: Segment) {
        val list = cells[index]
        if (!list.contains(seg)) list.add(seg)
    }

    fun doorRemoveSegment(index: Int, seg: Segment) {
        cells[index].remove(seg)
    }

    fun doorGetCellIndex(col: Int, row: Int): Int = cellIndex(col, row)

    fun gatherCellContentsFromWorldspaceRegion(
        minX: Float, minY: Float, maxX: Float, maxY: Float,
        out: MutableList<Segment>
    ) {
        out.clear()
        val c0 = worldToGrid(minX); val c1 = worldToGrid(maxX)
        val r0 = worldToGrid(minY); val r1 = worldToGrid(maxY)
        for (r in r0..r1) for (c in c0..c1) out.addAll(cells[cellIndex(c, r)])
    }

    /**
     * DDA raycast. Returns world-space distance to first hit, or -1 on error.
     * Writes hit position/normal to [outPoint]/[outNormal].
     */
    fun getRaycastDistance(
        ox: Float, oy: Float,
        dx: Float, dy: Float,
        outPoint: Vec2, outNormal: Vec2
    ): Float {
        var col = worldToGrid(ox)
        var row = worldToGrid(oy)

        // DDA step setup — matches AS3 exactly
        var stepCol = 0; var tMaxX = 999999f; var tDeltaX = 0f
        var stepRow = 0; var tMaxY = 999999f; var tDeltaY = 0f

        if (dx < 0f) {
            stepCol = -1; tMaxX = (col * cellSize - ox) / dx; tDeltaX = cellSize / -dx
        } else if (dx > 0f) {
            stepCol = 1; tMaxX = ((col + 1) * cellSize - ox) / dx; tDeltaX = cellSize / dx
        }
        if (dy < 0f) {
            stepRow = -1; tMaxY = (row * cellSize - oy) / dy; tDeltaY = cellSize / -dy
        } else if (dy > 0f) {
            stepRow = 1; tMaxY = ((row + 1) * cellSize - oy) / dy; tDeltaY = cellSize / dy
        }
        if (stepCol == 0 && stepRow == 0) return -1f

        val scale = 2000f
        rayPos.set(ox, oy)
        rayVec.set(scale * dx, scale * dy)

        while (true) {
            val t = intersectRayVsCellContents(col, row, rayPos, rayVec, outPoint, outNormal)
            if (t == -1f) return -1f           // overlapping geometry
            if (t != 2f) return t * scale      // hit

            if (tMaxX < tMaxY) {
                tMaxX += tDeltaX; col += stepCol
                if (col < 0 || col >= numCols) return -1f
            } else {
                tMaxY += tDeltaY; row += stepRow
                if (row < 0 || row >= numRows) return -1f
            }
        }
    }

    /**
     * Returns true if the line segment [from] → [to] (expanded by [radius]) has no
     * geometry between them.
     */
    fun raycastVsPlayer(from: Vec2, to: Vec2, radius: Float, outPoint: Vec2, outNormal: Vec2): Boolean {
        var dx = to.x - from.x; var dy = to.y - from.y
        var dist = sqrt(dx * dx + dy * dy)
        if (dist < radius) return true
        dx /= dist; dy /= dist; dist -= radius
        val hit = getRaycastDistance(from.x, from.y, dx, dy, outPoint, outNormal)
        return dist < hit
    }

    private fun intersectRayVsCellContents(
        col: Int, row: Int,
        pos: Vec2, vel: Vec2,
        outPoint: Vec2, outNormal: Vec2
    ): Float {
        var best = 2f
        for (seg in cells[cellIndex(col, row)]) {
            val t = seg.intersectWithRay(pos, vel, 0f, tempP, tempN)
            if (t == -1f) return -1f
            if (t < best) {
                best = t; outPoint.set(tempP); outNormal.set(tempN)
            }
        }
        return best
    }
}
