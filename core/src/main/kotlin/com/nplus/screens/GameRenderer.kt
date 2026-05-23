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
        private const val FPS_GOLD_COLLECT = 30f  // original Flash FPS; 14 frames ≈ 0.47 s
        private const val FPS_LAUNCHPAD    = 8f
        private const val FPS_FLOORGUARD   = 8f
        private const val FPS_DRONE        = 8f
        private const val FPS_ROCKET       = 10f

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

    // ---------------------------------------------------------------------------
    // All sprites packed into one atlas. Region names: "<folder>/<1-based-frame>"
    // e.g. "ninja/1", "tiles/3", "gold/5". See desktop:packAtlas Gradle task.
    // ---------------------------------------------------------------------------
    private lateinit var atlas: TextureAtlas

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
        atlas = TextureAtlas(Gdx.files.internal("atlas/sprites.atlas"))
        sprTileRegions = Array(42) { t ->
            val r = atlas.findRegion("tiles/${t + 1}")
                ?: error("Atlas missing region: tiles/${t + 1}")
            TextureRegion(r.texture, r.regionX + 24, r.regionY + 24, 24, 24)
        }
    }

    /** Look up a 1-based frame from the atlas (frame number matches the .png filename). */
    private fun fr(dir: String, frame: Int): TextureRegion =
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

        // Pre-pass: fill level interior, then paint FULL (type-1) solid tiles.
        // TileTypes.FULL has a fully transparent sprite (Flash used a plain color fill, not
        // a sprite for solid tiles), so we must draw them explicitly as solid rects.
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeType.Filled)
        shape.color = COL_LEVEL_BG
        shape.rect(0f, 0f, WORLD_W, WORLD_H)
        shape.color = COL_BG   // tile color = #797988 = same as exterior
        val cs   = Simulator.GRID_CELL_SIZE.toFloat()
        val cols = Simulator.GRID_NUM_COLS
        val rows = Simulator.GRID_NUM_ROWS
        for (row in 0 until rows) for (col in 0 until cols) {
            if (sim.tileGrid[col + row * cols] != TileTypes.FULL) continue
            shape.rect(col * cs, fy((row + 1) * cs), cs, cs)
        }
        shape.end()

        // Pass 1: entities + tiles + living ninja via SpriteBatch
        batch.projectionMatrix = camera.combined
        batch.enableBlending()
        batch.begin()
        drawEntitySprites(sim.entityList())  // entities behind tiles
        drawTiles(sim.tileGrid)              // tiles on top of entities
        drawNinjaSprites(sim.players)        // ninja always on top
        batch.end()

        // Pass 2: dead ninja ragdoll via ShapeRenderer
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeType.Filled)
        for (ninja in sim.players) if (ninja.isDead()) drawRagdoll(ninja)
        shape.end()
    }

    override fun dispose() {
        shape.dispose()
        batch.dispose()
        atlas.dispose()
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
     * Draw a gold sprite frame with bottom-centre registration.
     *
     * The FFDec canvas is 6×54 but the idle shape occupies only the bottom 8 px
     * (y=46–54). Anchoring the canvas bottom at entity position puts the coin
     * visually just above the physics centre, which matches the original game.
     */
    private fun drawGoldTex(reg: TextureRegion, px: Float, py: Float) {
        batch.draw(reg, px - reg.regionWidth / 2f, fy(py))
    }

    /**
     * Draw a rocket sprite frame with right-centre registration (rocket body/nose).
     *
     * The canvas is 61×11; the body is consistently at x≈55.5 while the smoke
     * trail grows leftward across frames.
     */
    private fun drawRocketTex(reg: TextureRegion, px: Float, py: Float) {
        batch.draw(reg, px - 55.5f, fy(py) - reg.regionHeight / 2f)
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

            is ExitSwitch -> if (!e.isDoorOpen()) {
                drawCentered(fr("exitswitch", 1), e.getPos().x, e.getPos().y)
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

            is RocketEntity -> if (e.getState() == 2) {
                val rp = e.getRocketPos()
                drawRocketTex(cycled("rocket", 13, FPS_ROCKET), rp.x, rp.y)
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
                drawRotated(cycled("launchpad", 19, FPS_LAUNCHPAD), e.getPos().x, e.getPos().y, -normalAngleDeg(n.x, n.y))
            }

            is OnewayPlatformEntity -> {
                val n = e.getNormal()
                drawRotated(fr("oneway", 1), e.getPos().x, e.getPos().y, -normalAngleDeg(n.x, n.y))
            }

            is DoorRegular -> if (!e.isOpen()) {
                val orn = if (e.doorOrn() == 0f) 0f else -90f
                drawRotated(fr("door_regular", 2), e.doorPos().x, e.doorPos().y, orn)
            }

            is DoorLocked -> if (!e.isOpen()) {
                val orn = if (e.doorOrn() == 0f) 0f else -90f
                drawRotated(fr("door_locked", 1), e.doorPos().x, e.doorPos().y, orn)
                drawCentered(fr("door_locked_switch", 1), e.getSwitchPos().x, e.getSwitchPos().y)
            }

            is DoorTrap -> {
                val orn = if (e.doorOrn() == 0f) 0f else -90f
                if (!e.isOpen()) {
                    drawRotated(fr("door_trap", 11), e.doorPos().x, e.doorPos().y, orn)
                } else {
                    drawCentered(fr("door_trap_switch", 1), e.getSwitchPos().x, e.getSwitchPos().y)
                }
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
