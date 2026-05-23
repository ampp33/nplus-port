package com.nplus.physics.entities

import com.nplus.physics.Ninja
import com.nplus.physics.Simulator
import com.nplus.physics.collision.ColUtils
import com.nplus.physics.collision.GridEdges
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.collision.GridSegment
import com.nplus.physics.collision.Segment
import com.nplus.physics.math.Vec2
import kotlin.math.PI

// ---------------------------------------------------------------------------
// DoorBase — manages a segment + edge that is toggled open/closed
// ---------------------------------------------------------------------------

abstract class DoorBase(
    objGrid: GridEntity,
    protected val segGrid: GridSegment,
    private val segCellIndex: Int,
    protected val seg: Segment,
    private val edgeGrid: GridEdges,
    private val edgeIndices: IntArray,
    private val isHorizontal: Boolean,     // true = X-axis edges (vertical barrier)
    triggerX: Float, triggerY: Float, triggerR: Float,
    startsOpen: Boolean
) : EntityBase() {

    protected val triggerPos = Vec2(triggerX, triggerY)
    private val triggerRadius = triggerR
    private var open = startsOpen

    init {
        if (!open) addDoorToWorld()
        objGrid.entityAdd(triggerPos, this)
    }

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (ninja != null && ColUtils.overlapCircleVsCircle(triggerPos, triggerRadius, pos, radius))
            onCollision(sim)
        return false
    }

    protected abstract fun onCollision(sim: Simulator)

    protected fun isDoorOpen() = open

    protected fun changeDoorState(newOpen: Boolean) {
        if (open == newOpen) return
        open = newOpen
        if (open) removeDoorFromWorld() else addDoorToWorld()
    }

    private fun addDoorToWorld() {
        segGrid.doorAddSegment(segCellIndex, seg)
        for (idx in edgeIndices) edgeGrid.doorIncrementEdge(idx, isHorizontal)
    }

    private fun removeDoorFromWorld() {
        segGrid.doorRemoveSegment(segCellIndex, seg)
        for (idx in edgeIndices) edgeGrid.doorDecrementEdge(idx, isHorizontal)
    }

    protected fun getDoorCentre(): Vec2 {
        val aabb = seg.getAabb()
        return Vec2(0.5f * (aabb.minX + aabb.maxX), 0.5f * (aabb.minY + aabb.maxY))
    }

    /** Public accessor for the renderer. */
    fun doorPos(): Vec2 = getDoorCentre()

    protected fun getDoorOrientation(): Float = if (isHorizontal) 0f else PI.toFloat() / 2f

    /** 0 = vertical barrier (isHorizontal), π/2 = horizontal barrier. */
    fun doorOrn(): Float = getDoorOrientation()
}

// ---------------------------------------------------------------------------
// DoorRegular — opens when player touches trigger, auto-closes after delay
// ---------------------------------------------------------------------------

class DoorRegular(
    objGrid: GridEntity, segGrid: GridSegment, segCellIndex: Int, seg: Segment,
    edgeGrid: GridEdges, edgeIndices: IntArray, isHorizontal: Boolean, x: Float, y: Float
) : DoorBase(objGrid, segGrid, segCellIndex, seg, edgeGrid, edgeIndices, isHorizontal,
             x, y, 12f * (5f / 6f), false) {

    private var closeTimer = 0

    override fun onCollision(sim: Simulator) {
        closeTimer = 0
        if (!isDoorOpen()) changeDoorState(true)
    }

    override fun think(sim: Simulator) {
        if (isDoorOpen()) { if (++closeTimer > 5) changeDoorState(false) }
    }

    fun getDoorPos()  = getDoorCentre()
    fun getDoorOrn()  = getDoorOrientation()
    fun isOpen()      = isDoorOpen()
}

// ---------------------------------------------------------------------------
// DoorLocked — opens permanently when player touches switch; removes from grid
// ---------------------------------------------------------------------------

class DoorLocked(
    private val objGrid: GridEntity, segGrid: GridSegment, segCellIndex: Int, seg: Segment,
    edgeGrid: GridEdges, edgeIndices: IntArray, isHorizontal: Boolean,
    switchX: Float, switchY: Float
) : DoorBase(objGrid, segGrid, segCellIndex, seg, edgeGrid, edgeIndices, isHorizontal,
             switchX, switchY, 12f * (5f / 12f), false) {

    override fun onCollision(sim: Simulator) {
        objGrid.entityRemove(this)
        changeDoorState(true)
    }

    fun getDoorPos() = getDoorCentre()
    fun getDoorOrn() = getDoorOrientation()
    fun isOpen()     = isDoorOpen()
    fun getSwitchPos(): Vec2 = triggerPos
}

// ---------------------------------------------------------------------------
// DoorTrap — starts open; closes permanently when switch is touched
// ---------------------------------------------------------------------------

class DoorTrap(
    private val objGrid: GridEntity, segGrid: GridSegment, segCellIndex: Int, seg: Segment,
    edgeGrid: GridEdges, edgeIndices: IntArray, isHorizontal: Boolean,
    switchX: Float, switchY: Float
) : DoorBase(objGrid, segGrid, segCellIndex, seg, edgeGrid, edgeIndices, isHorizontal,
             switchX, switchY, 12f * (5f / 12f), true) {

    override fun onCollision(sim: Simulator) {
        objGrid.entityRemove(this)
        changeDoorState(false)
    }

    fun getDoorPos() = getDoorCentre()
    fun getDoorOrn() = getDoorOrientation()
    fun isOpen()     = isDoorOpen()
    fun getSwitchPos(): Vec2 = triggerPos
}
