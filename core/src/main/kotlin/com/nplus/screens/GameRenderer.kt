package com.nplus.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.nplus.physics.Ninja
import com.nplus.physics.Simulator
import com.nplus.physics.entities.*
import com.nplus.physics.tiles.TileTypes
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

class GameRenderer : Disposable {

    companion object {
        val WORLD_W = Simulator.GRID_NUM_COLS * Simulator.GRID_CELL_SIZE   // 792f
        val WORLD_H = Simulator.GRID_NUM_ROWS * Simulator.GRID_CELL_SIZE   // 600f

        // Exterior area (outside the 792×600 level): matches floor tile color #797988
        private val COL_BG       = Color(121/255f, 121/255f, 136/255f, 1f)
        // Interior level background (inside the 792×600 canvas): original stage bg #CACAD0
        private val COL_LEVEL_BG = Color(202/255f, 202/255f, 208/255f, 1f)

        // Dead ninja ragdoll colour (ShapeRenderer sticks)
        private val COL_NINJA_DEAD = Color(0.40f, 0.40f, 0.40f, 1f)

        // Sprite animation rates (fps)
        private const val FPS_GOLD_COLLECT = 60f  // original Flash FPS; 14 frames ≈ 0.47 s
        private const val FPS_LAUNCHPAD    = 8f
        private const val FPS_FLOORGUARD   = 8f
        private const val FPS_DRONE        = 8f
        private const val FPS_ROCKET       = 30f

        // Turret: 17 frames cover 0..2π
        private const val TURRET_FRAMES = 17
    }

    private val camera   = OrthographicCamera()
    private val viewport = ExtendViewport(1280f, 720f, camera)
    private val shape    = ShapeRenderer()
    private val batch    = SpriteBatch()
    private var stateTime = 0f

    // Gold collection animations: entity → stateTime when it was first seen as collected.
    // Drives one-shot playback of frames 4–17 (the rising sparkle).
    private val goldCollectTimes = mutableMapOf<GoldEntity, Float>()

    // Launchpad one-shot animations: entity → stateTime when last triggered.
    // consumeTrigger() resets the entity flag; we store the moment here to time the one-shot.
    private val launchpadTriggerTimes = mutableMapOf<LaunchpadEntity, Float>()

    // ---------------------------------------------------------------------------
    // sprites.atlas — all sprites except ninja (Nearest filtering, 1:1 scale).
    //   Region names: "<folder>/<1-based-frame>" e.g. "tiles/3", "gold/5".
    // ninja.atlas   — ninja frames only (Linear filtering, rendered at 0.2× scale).
    //   Region names: "<1-based-frame>" e.g. "1", "13", "85".
    // See desktop:packAtlas Gradle task.
    // ---------------------------------------------------------------------------
    private lateinit var atlas: TextureAtlas
    private lateinit var ninjaAtlas: TextureAtlas

    // Tile sub-regions: each 72×72 atlas region has 24px transparent padding;
    // extract the centre 24×24 once at load time. Index = tile type (0 = EMPTY).
    private lateinit var sprTileRegions: Array<TextureRegion>

    // Per-ninja run animation state (updated every render frame via delta time)
    private data class NinjaRunState(var framePos: Int = 0, var leftovers: Float = 0f)
    private val ninjaRunState = mutableMapOf<Ninja, NinjaRunState>()

    // Tracks last observed state per ninja to detect STANDING entry for the settle animation
    private val ninjaLastState     = mutableMapOf<Ninja, Ninja.State>()
    private val ninjaStandEntryTime = mutableMapOf<Ninja, Float>()

    init {
        camera.setToOrtho(false, 1280f, 720f)
        camera.position.set(WORLD_W / 2f, WORLD_H / 2f, 0f)
        camera.update()
        loadSprites()
    }

    // ---------------------------------------------------------------------------
    // Sprite loading
    // ---------------------------------------------------------------------------

    private fun loadSprites() {
        atlas      = TextureAtlas(Gdx.files.internal("atlas/sprites.atlas"))
        ninjaAtlas = TextureAtlas(Gdx.files.internal("atlas/ninja.atlas"))
        sprTileRegions = Array(42) { t ->
            val r = atlas.findRegion("tiles/${t + 1}")
                ?: error("Atlas missing region: tiles/${t + 1}")
            TextureRegion(r.texture, r.regionX + 24, r.regionY + 24, 24, 24)
        }
    }

    /** Look up a 1-based frame. Ninja frames come from the Linear-filtered ninja atlas. */
    private fun fr(dir: String, frame: Int): TextureRegion =
        if (dir == "ninja")
            ninjaAtlas.findRegion("$frame") ?: error("ninja atlas missing region: $frame")
        else
            atlas.findRegion("$dir/$frame") ?: error("Atlas missing region: $dir/$frame")

    /** Return a cycling frame for time-driven animations. */
    private fun cycled(dir: String, count: Int, fps: Float): TextureRegion {
        val idx = (stateTime * fps).toInt().rem(count).let { if (it < 0) it + count else it } + 1
        return fr(dir, idx)
    }

    // ---------------------------------------------------------------------------
    // Coordinate flip: game y-down → libGDX y-up
    // ---------------------------------------------------------------------------
    private fun fy(y: Float) = WORLD_H - y

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    fun resize(width: Int, height: Int) {
        viewport.update(width, height, false)
        camera.position.set(WORLD_W / 2f, WORLD_H / 2f, 0f)
        camera.update()
    }

    fun render(sim: Simulator) {
        stateTime += Gdx.graphics.deltaTime

        Gdx.gl.glClearColor(COL_BG.r, COL_BG.g, COL_BG.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        viewport.apply()

        val cs   = Simulator.GRID_CELL_SIZE.toFloat()
        val cols = Simulator.GRID_NUM_COLS
        val rows = Simulator.GRID_NUM_ROWS

        // Pre-pass: fill level interior background only.
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeType.Filled)
        shape.color = COL_LEVEL_BG
        shape.rect(0f, 0f, WORLD_W, WORLD_H)
        shape.end()

        // Pass 1: entity sprites — rendered before solid walls so walls occlude them.
        batch.projectionMatrix = camera.combined
        batch.enableBlending()
        batch.begin()
        drawEntitySprites(sim.entityList())
        batch.end()

        // Pass 2: FULL (type-1) solid walls — drawn after entities so entity pixels
        // that overlap into a solid tile are correctly hidden behind the wall.
        // TileTypes.FULL has a transparent sprite; we paint it as a solid rect here.
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeType.Filled)
        shape.color = COL_BG
        for (row in 0 until rows) for (col in 0 until cols) {
            if (sim.tileGrid[col + row * cols] != TileTypes.FULL) continue
            shape.rect(col * cs, fy((row + 1) * cs), cs, cs)
        }
        shape.end()

        // Pass 3: non-FULL tile sprites + living ninja — on top of everything.
        batch.begin()
        drawTiles(sim.tileGrid)
        drawNinjaSprites(sim.players)
        batch.end()

        // Pass 4: dead ninja ragdoll.
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeType.Filled)
        for (ninja in sim.players) if (ninja.isDead()) drawRagdoll(ninja)
        shape.end()
    }

    override fun dispose() {
        shape.dispose()
        batch.dispose()
        atlas.dispose()
        ninjaAtlas.dispose()
    }

    // ---------------------------------------------------------------------------
    // Tile rendering (SpriteBatch)
    // ---------------------------------------------------------------------------

    private fun drawTiles(tileGrid: IntArray) {
        val cols = Simulator.GRID_NUM_COLS
        val rows = Simulator.GRID_NUM_ROWS
        val cs   = Simulator.GRID_CELL_SIZE
        for (row in 0 until rows) for (col in 0 until cols) {
            val t = tileGrid[col + row * cols]
            if (t == TileTypes.EMPTY || t == TileTypes.FULL) continue  // FULL drawn in pre-pass
            // sprTileRegions[t] is the pre-extracted 24×24 centre of the 72×72 tile canvas.
            batch.draw(sprTileRegions[t], col * cs, fy((row + 1) * cs))
        }
    }

    // ---------------------------------------------------------------------------
    // Entity sprite rendering (SpriteBatch)
    // ---------------------------------------------------------------------------

    /** Draw [reg] centred at game position (px, py). Canvas centre = registration point. */
    private fun drawCentered(reg: TextureRegion, px: Float, py: Float) {
        batch.draw(reg, px - reg.regionWidth / 2f, fy(py) - reg.regionHeight / 2f)
    }

    /**
     * Draw a gold sprite frame centred on the entity physics position.
     *
     * FFDec canvas is 6×54; registration = canvas centre (row 27). Idle coin
     * content is at rows 46–53 (centre row ≈ 49.5 = 3.5 px from canvas bottom).
     * Shifting 4 px below the entity screen-y centres the coin on the entity,
     * giving equal visual distance from floor and ceiling surfaces.
     */
    private fun drawGoldTex(reg: TextureRegion, px: Float, py: Float) {
        batch.draw(reg, px - reg.regionWidth / 2f, fy(py) - 4f)
    }

    /**
     * Draw a rocket projectile frame with right-centre registration, rotated toward travel direction.
     * Canvas is 61×11; body at x≈55.5, y-centre at 5.5. Rotation: negate atan2(dy,dx) for libGDX CCW.
     */
    private fun drawRocketTex(reg: TextureRegion, px: Float, py: Float, ornDeg: Float) {
        val w = reg.regionWidth.toFloat(); val h = reg.regionHeight.toFloat()
        val ox = 55.5f; val oy = h / 2f
        batch.draw(reg, px - ox, fy(py) - oy, ox, oy, w, h, 1f, 1f, ornDeg)
    }

    /**
     * Idle gold: static frame 1.
     * Collected gold: one-shot sparkle (frames 5–18) that rises then disappears.
     */
    private fun drawGold(e: GoldEntity) {
        val px = e.getPos().x
        val py = e.getPos().y
        if (!e.isCollected()) {
            goldCollectTimes.remove(e)
            drawGoldTex(fr("gold", 1), px, py)
        } else {
            val start   = goldCollectTimes.getOrPut(e) { stateTime }
            val elapsed = stateTime - start
            val frame   = 4 + (elapsed * FPS_GOLD_COLLECT).toInt()
            if (frame < 18) drawGoldTex(fr("gold", frame + 1), px, py)
            // else animation finished — nothing to draw
        }
    }

    /**
     * Draw [reg] centred at (px, py) with CCW [angleDeg] rotation.
     * Uses canvas centre as both the draw anchor and the rotation origin.
     */
    private fun drawRotated(reg: TextureRegion, px: Float, py: Float, angleDeg: Float) {
        val w = reg.regionWidth.toFloat(); val h = reg.regionHeight.toFloat()
        val ox = w / 2f;                  val oy = h / 2f
        batch.draw(reg, px - ox, fy(py) - oy, ox, oy, w, h, 1f, 1f, angleDeg)
    }

    /**
     * Convert a game-space (y-down) normal vector to a CCW rotation angle in
     * libGDX degrees measured from +x.  Used to orient sprites by surface normal.
     *
     * Sprite default long axis points along +y (up on screen).
     * • Launchpad: pass (normalAngle - 90) so the long axis aligns with the normal.
     * • Oneway:    pass (normalAngle)      so the long axis aligns with the surface.
     */
    private fun normalAngleDeg(nx: Float, ny: Float): Float =
        Math.toDegrees(atan2(-ny.toDouble(), nx.toDouble())).toFloat()

    private fun drawEntitySprites(entities: List<EntityBase>) {
        for (e in entities) when (e) {

            is GoldEntity -> drawGold(e)

            is MineEntity -> if (!e.isExploded()) {
                drawCentered(fr("mine", 1), e.getPos().x, e.getPos().y)
            }

            is ExitDoor -> {
                val reg = if (e.isOpen()) fr("exitdoor", 17) else fr("exitdoor", 1)
                drawCentered(reg, e.getPos().x, e.getPos().y)
            }

            is ExitSwitch -> {
                if (!e.isDoorOpen())
                    drawCentered(fr("exitswitch", 1), e.getPos().x, e.getPos().y)
                else
                    drawCentered(fr("exitswitch", 2), e.getPos().x, e.getPos().y)
            }

            is ThwompEntity -> {
                drawCentered(fr("thwomp", 1), e.getPos().x, e.getPos().y)
            }

            is BounceBlockEntity -> {
                drawCentered(fr("bounceblock", 1), e.getPos().x, e.getPos().y)
            }

            is FloorGuardEntity -> {
                drawCentered(cycled("floorguard", 3, FPS_FLOORGUARD), e.getPos().x, e.getPos().y)
            }

            is TurretEntity -> {
                val ap  = e.getAimPos()
                val pos = e.getPos()
                // atan2 in game y-down: negate y delta to get math-space angle
                val angle = atan2(-(ap.y - pos.y), ap.x - pos.x)
                // Map angle (-π..π) → frame 1..17
                val norm = ((angle / (2 * Math.PI) + 1.0) % 1.0)
                val idx  = (norm * TURRET_FRAMES).toInt().coerceIn(0, TURRET_FRAMES - 1)
                drawCentered(fr("turret", idx + 1), pos.x, pos.y)
            }

            is RocketEntity -> {
                // Base/launcher: frame 1 = idle; frame 5 = launched pose (fire anim plays 2→5
                // once on launch then stops at 5 per AS3 addFrameScript; we skip the one-shot).
                drawCentered(fr("rocket_base", if (e.getState() == 2) 5 else 1), e.getPos().x, e.getPos().y)
                // Flying projectile: only when state==2, rotated toward travel direction
                if (e.getState() == 2) {
                    val rp = e.getRocketPos()
                    val rd = e.getRocketDir()
                    val ornDeg = -Math.toDegrees(atan2(rd.y.toDouble(), rd.x.toDouble())).toFloat()
                    drawRocketTex(cycled("rocket", 13, FPS_ROCKET), rp.x, rp.y, ornDeg)
                }
            }

            is DroneChaser -> {
                drawCentered(cycled("drone_chaser", 3, FPS_DRONE), e.pos.x, e.pos.y)
            }

            is DroneZap -> {
                drawCentered(fr("drone_zap", 1), e.pos.x, e.pos.y)
            }

            is DroneLaser -> {
                drawCentered(cycled("drone_laser", 26, FPS_DRONE), e.pos.x, e.pos.y)
            }

            is DroneChaingun -> {
                drawCentered(cycled("drone_chaingun", 7, FPS_DRONE), e.pos.x, e.pos.y)
            }

            is LaunchpadEntity -> {
                val n = e.getNormal()
                if (e.consumeTrigger()) launchpadTriggerTimes[e] = stateTime
                val t = launchpadTriggerTimes[e]
                val frame = if (t != null && stateTime - t < 19f / FPS_LAUNCHPAD)
                    ((stateTime - t) * FPS_LAUNCHPAD).toInt().coerceIn(0, 18) + 1
                else
                    1
                drawRotated(fr("launchpad", frame), e.getPos().x, e.getPos().y, -normalAngleDeg(n.x, n.y))
            }

            is OnewayPlatformEntity -> {
                val n = e.getNormal()
                drawRotated(fr("oneway", 1), e.getPos().x, e.getPos().y, -normalAngleDeg(n.x, n.y))
            }

            is DoorRegular -> if (!e.isOpen()) {
                val orn = if (e.doorOrn() == 0f) 0f else -90f
                drawRotated(fr("door_regular", 2), e.doorPos().x, e.doorPos().y, orn)
            }

            is DoorLocked -> {
                val orn = if (e.doorOrn() == 0f) 0f else -90f
                if (!e.isOpen()) {
                    drawRotated(fr("door_locked", 1), e.doorPos().x, e.doorPos().y, orn)
                }
                // Switch always visible: frame 1 = waiting, frame 2 = triggered (door opened)
                drawCentered(fr("door_locked_switch", if (!e.isOpen()) 1 else 2),
                    e.getSwitchPos().x, e.getSwitchPos().y)
            }

            is DoorTrap -> {
                val orn = if (e.doorOrn() == 0f) 0f else -90f
                if (!e.isOpen()) {
                    drawRotated(fr("door_trap", 11), e.doorPos().x, e.doorPos().y, orn)
                }
                // Switch always visible: frame 1 = armed (isOpen), frame 2 = triggered (!isOpen)
                drawCentered(fr("door_trap_switch", if (e.isOpen()) 1 else 2),
                    e.getSwitchPos().x, e.getSwitchPos().y)
            }

            else -> {}
        }
    }

    // ---------------------------------------------------------------------------
    // Ninja sprite rendering (SpriteBatch)
    // ---------------------------------------------------------------------------

    private fun drawNinjaSprites(players: List<Ninja>) {
        for (ninja in players) {
            if (ninja.isDead()) continue
            val state = ninja.snapshotGfxState()
            // Track state transitions here so ninjaLastState is updated for every state,
            // not just when inside the STANDING branch (otherwise STANDING→RUNNING→STANDING
            // leaves lastState=STANDING and the settle entry time never resets).
            val lastSeen = ninjaLastState[ninja]
            if (lastSeen != state.state &&
                (state.state == Ninja.State.STANDING || state.state == Ninja.State.CELEBRATING)) {
                ninjaStandEntryTime[ninja] = stateTime
            }
            ninjaLastState[ninja] = state.state
            val reg = selectNinjaFrame(ninja, state) ?: continue
            val ornDeg = -Math.toDegrees(state.orientation.toDouble()).toFloat()
            // Wall-slide: sprite body extends 4.4 units past physics radius — shift toward normal
            val xOff = if (state.state == Ninja.State.WALL_SLIDING) state.wallNormalX * 4.4f else 0f
            drawNinjaTex(reg, state.posX + xOff, state.posY, state.facing, ornDeg)
        }
    }

    private fun drawNinjaTex(reg: TextureRegion, px: Float, py: Float, facing: Float, ornDeg: Float) {
        val w = reg.regionWidth * 0.2f; val h = reg.regionHeight * 0.2f
        val ox = w / 2f; val oy = h / 2f
        // Sprite canvas centre is at physics centre, but the solid foot pixels sit ~1.8 units
        // below the floor contact (r=10). Shift up by 2 to align feet with floor.
        batch.draw(reg, px - ox, fy(py) - oy + 2f, ox, oy, w, h, facing, 1f, ornDeg)
    }

    private fun selectNinjaFrame(ninja: Ninja, state: Ninja.GfxState): TextureRegion? {
        return when (state.state) {
            Ninja.State.DISABLED -> null
            Ninja.State.DEAD     -> null  // handled by ShapeRenderer ragdoll

            Ninja.State.WALL_SLIDING ->
                fr("ninja", 104)   // frame 104: all pixels right of canvas centre = body pressed against right wall

            Ninja.State.SKIDDING ->
                fr("ninja", 12)    // frame 12: skidding down a slope

            Ninja.State.STANDING, Ninja.State.CELEBRATING -> {
                // On entry: play frames 1-11 once at 24fps (settle-down animation), then hold frame 11.
                // Original: gotoAndPlay("STAND") plays once, stop() fires at frame 11.
                // Entry time is set in drawNinjaSprites on every state transition.
                val elapsed = stateTime - (ninjaStandEntryTime[ninja] ?: stateTime)
                val settleIdx = (elapsed * 24f).toInt().coerceIn(0, 10)  // 0..10 → frames 1..11
                fr("ninja", settleIdx + 1)
            }

            Ninja.State.RUNNING -> {
                val rs = ninjaRunState.getOrPut(ninja) { NinjaRunState() }
                val advance = state.animSpeed / 0.9f * Gdx.graphics.deltaTime * 40f
                val total   = advance + rs.leftovers
                val steps   = floor(total).toInt()
                rs.leftovers = total - steps
                rs.framePos  = (rs.framePos + steps).rem(72).let { if (it < 0) it + 72 else it }
                fr("ninja", 13 + rs.framePos)  // idx 12..83 → frames 13..84
            }

            // JUMPING, FALLING, AWAITING_DEATH — all use velocity-based in-air frame
            else -> {
                val velY = state.animSpeed   // positive = falling (game y-down)
                val lo = -(40f / 60f); val hi = 2.5f * (40f / 60f)
                val t = when {
                    velY < 0 && velY < lo -> -1f
                    velY < 0             -> -(velY / lo)
                    velY > hi            ->  1f
                    else                 ->  sqrt(velY / hi)
                }
                val offset = floor(t * 9).toInt().coerceIn(-9, 9)
                fr("ninja", (84 + 9 + offset).coerceIn(84, 102) + 1)  // idx 84..102 → frames 85..103
            }
        }
    }

    private fun drawRagdoll(ninja: Ninja) {
        val rag = ninja.ragdoll
        for (i in 0 until rag.getStickCount()) {
            val (p0, orn, _) = rag.getStickRenderData(i)
            shape.color = COL_NINJA_DEAD
            val ex = cos(orn) * 8f; val ey = sin(orn) * 8f
            shape.line(p0.x - ex, fy(p0.y) - ey, p0.x + ex, fy(p0.y) + ey)
        }
    }

}
