package com.nplus.physics.entities

class CollisionResultPhysical {
    var pen: Float = 0f
    var nx: Float = 0f
    var ny: Float = 0f
    var isHardCollision: Boolean = true

    fun clear() {
        pen = 0f; nx = 0f; ny = 0f; isHardCollision = true
    }
}
