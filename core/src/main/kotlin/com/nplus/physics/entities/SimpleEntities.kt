package com.nplus.physics.entities

import com.nplus.SimGlobals
import com.nplus.physics.Ninja
import com.nplus.physics.Simulator
import com.nplus.physics.collision.ColUtils
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.math.Vec2
import kotlin.math.*

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

internal object EntityHelpers {
    private val zeroVec = Vec2()
    private val hitP    = Vec2()
    private val hitN    = Vec2()

    /** Returns the index of the closest visible alive player, or -1 if none. */
    fun tryToAcquireTarget(
        pos: Vec2,
        players: List<Ninja>,
        segGrid: com.nplus.physics.collision.GridSegment
    ): Int {
        var bestIdx = -1
        var bestDist = Float.MAX_VALUE
        for (i in players.indices) {
            val p = players[i]
            if (!p.isDead() && segGrid.raycastVsPlayer(pos, p.getPos(), p.getRadius(), hitP, hitN)) {
                val dx = p.getPos().x - pos.x; val dy = p.getPos().y - pos.y
                val d = dx * dx + dy * dy
                if (d < bestDist) { bestDist = d; bestIdx = i }
            }
        }
        return bestIdx
    }
}

// ---------------------------------------------------------------------------
// Gold
// ---------------------------------------------------------------------------

class GoldEntity(private val objGrid: GridEntity, x: Float, y: Float) : EntityBase() {
    private val pos = Vec2(x, y)
    private val r   = 12f * 0.5f
    private var collected = false

    init { objGrid.entityAdd(pos, this) }

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (ninja != null && !collected && ColUtils.overlapCircleVsCircle(this.pos, r, pos, radius)) {
            if (sim.eventGoldHitPlayer(ninja)) {
                collected = true
                sim.objGrid.entityRemove(this)
                sim.playSoundGold()
            }
        }
        return false
    }

    fun getPos(): Vec2 = pos
    fun isCollected() = collected
}

// ---------------------------------------------------------------------------
// Mine
// ---------------------------------------------------------------------------

class MineEntity(private val objGrid: GridEntity, x: Float, y: Float) : EntityBase() {
    private val pos     = Vec2(x, y)
    private val r       = 12f / 3f
    private var exploded = false

    init { objGrid.entityAdd(pos, this) }

    override fun isMine() = true

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        // Shift the effective trigger centre 2.2 units down (game y-down) to compensate for
        // the ninja sprite's 2.2-unit visual offset above its physics centre.  This makes the
        // mine fire when the *visual* ninja reaches the mine, matching the original game feel.
        val checkY = this.pos.y + 2.2f
        val cdx = pos.x - this.pos.x; val cdy = pos.y - checkY
        val triggerR = r + radius
        if (!exploded && cdx * cdx + cdy * cdy < triggerR * triggerR) {
            sim.spawnExplosion(this.pos.x, this.pos.y)
            sim.playSoundEntity("mine_explode")
            exploded = true
            objGrid.entityRemove(this)
            val dx = pos.x - this.pos.x; val dy = pos.y - this.pos.y
            val d = sqrt(dx * dx + dy * dy)
            val nx = dx / d; val ny = dy / d
            if (ninja == null) {
                result.vecX = nx * 16f; result.vecY = ny * 16f
                return true
            }
            sim.eventPlayerWasKilled(ninja, SimGlobals.ENEMYTYPE_MINE,
                pos.x - nx * radius, pos.y - ny * radius, nx * 16f, ny * 16f)
        }
        return false
    }

    fun getPos(): Vec2 = pos
    fun isExploded() = exploded
}

// ---------------------------------------------------------------------------
// ExitDoor — not in objGrid until opened by the paired switch
// ---------------------------------------------------------------------------

class ExitDoor(x: Float, y: Float) : EntityBase() {
    private val pos  = Vec2(x, y)
    private val r    = 12f
    private var open = false

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (ninja != null && ColUtils.overlapCircleVsCircle(this.pos, r, pos, radius))
            sim.eventExitHitPlayer(ninja)
        return false
    }

    fun openExit(objGrid: GridEntity) {
        open = true
        objGrid.entityAdd(pos, this)
    }

    fun isOpen() = open
    fun getPos(): Vec2 = pos
}

// ---------------------------------------------------------------------------
// ExitSwitch
// ---------------------------------------------------------------------------

class ExitSwitch(private val objGrid: GridEntity, x: Float, y: Float,
                 private val door: ExitDoor) : EntityBase() {
    private val pos = Vec2(x, y)
    private val r   = 12f * 0.5f

    init { objGrid.entityAdd(pos, this) }

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (ninja != null && !door.isOpen() && ColUtils.overlapCircleVsCircle(this.pos, r, pos, radius)) {
            sim.playSoundEntity("exit_switch")
            door.openExit(objGrid)
            objGrid.entityRemove(this)
        }
        return false
    }

    fun getPos(): Vec2 = pos
    fun isDoorOpen() = door.isOpen()
}

// ---------------------------------------------------------------------------
// Launchpad
// ---------------------------------------------------------------------------

class LaunchpadEntity(objGrid: GridEntity, x: Float, y: Float, nx: Float, ny: Float) : EntityBase() {
    private val pos      = Vec2(x, y)
    private val normal   = Vec2(nx, ny)
    private val r        = 12f * 0.5f
    private val strength = 12f * (3f / 7f)
    private var triggered = false

    init { objGrid.entityAdd(pos, this) }

    override fun collideVsCircleLogical(sim: Simulator, ninja: Ninja?, result: CollisionResultLogical,
        pos: Vec2, vel: Vec2, normal: Vec2, radius: Float, eps: Float): Boolean {
        if (ColUtils.overlapCircleVsCircle(this.pos, r, pos, radius)) {
            val cx = this.pos.x - (pos.x - this.normal.x * radius)
            val cy = this.pos.y - (pos.y - this.normal.y * radius)
            if (-eps <= this.normal.x * cx + this.normal.y * cy) {
                val upBias = if (this.normal.y < 0f) 1f + abs(this.normal.y) else 1f
                if (ninja == null) {
                    result.vecX = this.normal.x * 12f; result.vecY = this.normal.y * 12f
                    triggered = true; return true
                }
                sim.eventLaunchpadHitPlayer(ninja, this.normal.x * strength, this.normal.y * strength * upBias)
                sim.playSoundEntity("launchpad")
                triggered = true
            }
        }
        return false
    }

    fun isTriggered(): Boolean { return triggered }
    fun getPos(): Vec2 = pos
    fun getNormal(): Vec2 = normal
    fun consumeTrigger(): Boolean { val t = triggered; triggered = false; return t }
}
