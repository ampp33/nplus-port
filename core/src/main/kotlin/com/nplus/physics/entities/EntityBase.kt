package com.nplus.physics.entities

import com.nplus.physics.math.Vec2

// Forward refs — filled in during steps 6 and 8 respectively.
// Importing via the full class name avoids import cycles since all are in :core.
typealias Simulator = com.nplus.physics.Simulator
typealias Ninja     = com.nplus.physics.Ninja

/**
 * Port of AS3 Entity_Base. All game entities extend this.
 * UID and grid-index bookkeeping are final here; collision/think/move are open for override.
 */
open class EntityBase {
    private var uid: Int = -1
    private var gridIndex: Int = -1

    fun setUid(id: Int) { uid = id }
    fun getUid(): Int = uid

    fun gridGetIndex(): Int = gridIndex
    fun gridSetIndex(i: Int) { gridIndex = i }

    open fun collideVsCirclePhysical(
        result: CollisionResultPhysical,
        pos: Vec2, vel: Vec2, normal: Vec2,
        radius: Float
    ): Boolean = false

    open fun collideVsCircleLogical(
        sim: Simulator, ninja: Ninja?,  // null when called from ragdoll
        result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2,
        radius: Float, time: Float
    ): Boolean = false

    open fun think(sim: Simulator) {}
    open fun move(sim: Simulator) {}

    // Type queries used by Ragdoll and Ninja for special collision reactions
    open fun isMine(): Boolean = false
    open fun isZapper(): Boolean = false
    open fun isCrushable(): Boolean = false
}
