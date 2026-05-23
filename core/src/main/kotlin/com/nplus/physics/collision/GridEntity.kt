package com.nplus.physics.collision

import com.nplus.physics.entities.EntityBase
import com.nplus.physics.math.Vec2

/** Port of AS3 Grid_Entity — spatial hash for EntityBase objects (3×3 neighbourhood query). */
class GridEntity(numCols: Int, numRows: Int, cellSize: Float) : GridBase(numCols, numRows, cellSize) {

    private val cells: Array<MutableList<EntityBase>> = Array(numCells) { mutableListOf() }

    fun clear() { cells.forEach { it.clear() } }

    fun entityAdd(pos: Vec2, entity: EntityBase) {
        check(entity.gridGetIndex() == -1) {
            "GridEntity.entityAdd: entity ${entity.getUid()} is already in the grid"
        }
        val idx = cellIndexFromWorld(pos.x, pos.y)
        cells[idx].add(entity)
        entity.gridSetIndex(idx)
    }

    fun entityRemove(entity: EntityBase) {
        val idx = entity.gridGetIndex()
        check(idx != -1) { "GridEntity.entityRemove: entity ${entity.getUid()} is not in the grid" }
        cells[idx].remove(entity)
        entity.gridSetIndex(-1)
    }

    fun entityMove(newPos: Vec2, entity: EntityBase) {
        val oldIdx = entity.gridGetIndex()
        val newIdx = cellIndexFromWorld(newPos.x, newPos.y)
        if (oldIdx != newIdx) {
            check(oldIdx != -1) { "GridEntity.entityMove: entity ${entity.getUid()} is not in the grid" }
            cells[oldIdx].remove(entity)
            cells[newIdx].add(entity)
            entity.gridSetIndex(newIdx)
        }
    }

    /** Gathers all entities in the 3×3 neighbourhood of [pos]. */
    fun gatherNeighbourhood(pos: Vec2, out: MutableList<EntityBase>) {
        out.clear()
        val c = worldToGrid(pos.x); val r = worldToGrid(pos.y)
        for (dr in -1..1) for (dc in -1..1) out.addAll(cells[cellIndex(c + dc, r + dr)])
    }
}
