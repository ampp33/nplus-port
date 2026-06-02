package com.nplus.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.nplus.SimGlobals
import com.nplus.physics.Ninja
import com.nplus.physics.Simulator
import com.nplus.physics.entities.*
import com.nplus.physics.tiles.TileTypes
import kotlin.math.*

class GameRenderer : Disposable {

    companion object {
        val WORLD_W = Simulator.GRID_NUM_COLS * Simulator.GRID_CELL_SIZE   // 792f
        val WORLD_H = Simulator.GRID_NUM_ROWS * Simulator.GRID_CELL_SIZE   // 600f

        // Exterior area (outside the 792×600 level): matches floor tile color #797988
        private val COL_BG       = Color(121/255f, 121/255f, 136/255f, 1f)
        // Interior level background (inside the 792×600 canvas): original stage bg #CACAD0
        private val COL_LEVEL_BG = Color(202/255f, 202/255f, 208/255f, 1f)

        // Sprites are exported at 3× the original Flash canvas size.
        // Divide all region pixel dimensions by this factor to get game-world units.
        private const val SPRITE_SCALE = 3f

        // Ragdoll sprite names per stick index (body, R-arm, L-arm, R-leg, L-leg)
        private val RAGDOLL_SPRITE = arrayOf("ragdoll_body", "ragdoll_arm", "ragdoll_arm", "ragdoll_leg", "ragdoll_leg")
        // flipList[i] = scaleY sign from AS3 (body=-1, arms=+1, legs=-1)
        private val RAGDOLL_FLIP   = floatArrayOf(-1f, 1f, 1f, -1f, -1f)

        // Sprite animation rates (fps)
        private const val FPS_GOLD_COLLECT = 60f  // original Flash FPS; 14 frames ≈ 0.47 s
        private const val FPS_EXITDOOR     = 60f  // Flash timeline rate; 16-frame open anim ≈ 0.53 s
        private const val FPS_LAUNCHPAD    = 8f
        private const val FPS_FLOORGUARD   = 8f
        private const val FPS_DRONE        = 8f
        private const val FPS_ROCKET       = 60f
        private const val FPS_EFFECTS      = 60f
        private const val FPS_CELEBRATE    = 60f

        // Turret base: 17 animation frames.
        //   Frame 1       = idle (darkest)
        //   Frames 2–8    = prefire charge-up (progressively brighter)
        //   Frames 9–11   = peak / just-fired (brightest, identical pixels)
        //   Frames 12–17  = postfire recovery (dims back to frame 1)
        // Turret crosshair: 6 frames (aim_far=1, aim_mid=2, aim_near=3, prefire=4, aim_off≈5, hidden=6)
        private const val TURRET_PREFIRE_CHARGE_START = 2
        private const val TURRET_PREFIRE_CHARGE_END   = 8
        private const val TURRET_POSTFIRE_FRAME       = 9

        // Horizontal timer bar drawn inside the top tile border of the level.
        // Original TimeBar sprite is 625×12 px, so BAR_H = 12 world units.
        private const val BAR_H = 12f
        // Game area fills the full screen height; the level's own tile border provides
        // visual space for the timer bar (top) and level label (bottom).
        private const val GAME_PAD = 0f
        // BAR_NORMAL_W = bar fill width at fraction 1.0 (current == starting ticks).
        // Matches original timebar sprite proportion (~78% of world width).
        private val BAR_NORMAL_W = WORLD_W * 0.78f   // ≈ 617 units; gold extends beyond this

        // Overlay modal
        private val COL_MODAL_BG     = Color(0.90f, 0.90f, 0.92f, 1f)
        private val COL_MODAL_BORDER = Color(0.10f, 0.10f, 0.12f, 1f)
        private val COL_MODAL_TEXT   = Color(0.10f, 0.10f, 0.12f, 1f)
        private val COL_TIMER_TEXT   = Color(1f, 1f, 1f, 1f)

        // Color-replacement shader: discards sprite RGB, uses vertex color as flat tint,
        // keeps only the texture alpha. Mimics Flash ColorTransform.color absolute replacement.
        private val NINJA_VERT = """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            void main() {
                v_color = a_color;
                v_color.a = v_color.a * (255.0/254.0);
                v_texCoords = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }
        """.trimIndent()

        private val NINJA_FRAG = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            void main() {
                float alpha = texture2D(u_texture, v_texCoords).a;
                gl_FragColor = vec4(v_color.rgb, alpha * v_color.a);
            }
        """.trimIndent()
    }

    private val camera   = OrthographicCamera()
    private val viewport = ExtendViewport(WORLD_W + 2 * GAME_PAD, WORLD_H + 2 * GAME_PAD, camera)
    private val shape    = ShapeRenderer()
    private val batch    = SpriteBatch()
    private var stateTime = 0f

    // Size of one screen pixel in world units; updated each resize so HUD offsets stay
    // accurate in pixels regardless of screen resolution.
    private var unitsPerPx = WORLD_H / 1080f

    // Active turret shot lines: each fades from opaque to transparent over TURRET_SHOT_DURATION.
    // Mirrors AS3 HACKY_drawtimer (10 sim ticks) converted to real time.
    private data class TurretShot(val x0: Float, val y0: Float, val x1: Float, val y1: Float,
                                  val hnx: Float, val hny: Float, var elapsed: Float = 0f)
    private val turretShots = mutableListOf<TurretShot>()
    private val TURRET_SHOT_DURATION = 10f / SimGlobals.SIM_RATE  // 10 sim ticks → seconds

    // Gold collection animations: entity → stateTime when it was first seen as collected.
    // Drives one-shot playback of frames 4–17 (the rising sparkle).
    private val goldCollectTimes = mutableMapOf<GoldEntity, Float>()

    // Launchpad one-shot animations: entity → stateTime when last triggered.
    // consumeTrigger() resets the entity flag; we store the moment here to time the one-shot.
    private val launchpadTriggerTimes = mutableMapOf<LaunchpadEntity, Float>()

    // Exit door one-shot opening animation: entity → stateTime when first seen as open.
    // Plays frames 2–17 once at FPS_EXITDOOR, then holds frame 17.
    private val exitDoorOpenTimes = mutableMapOf<ExitDoor, Float>()

    // ---------------------------------------------------------------------------
    // sprites.atlas — all sprites except ninja (Nearest filtering, 1:1 scale).
    //   Region names: "<folder>/<1-based-frame>" e.g. "tiles/3", "gold/5".
    // ninja.atlas   — ninja frames only (Linear filtering, rendered at 0.2× scale).
    //   Region names: "<1-based-frame>" e.g. "1", "13", "85".
    // See desktop:packAtlas Gradle task.
    // ---------------------------------------------------------------------------
    private lateinit var atlas: TextureAtlas
    private lateinit var ninjaAtlas: TextureAtlas
    private lateinit var ragdollAtlas: TextureAtlas
    private lateinit var fxAtlas: TextureAtlas
    private lateinit var ninjaShader: ShaderProgram

    // Set by GameScreen when entering POST_WIN — selects which celebration clip to play.
    var celebStartFrame: Int = 106
    var celebEndFrame:   Int = 166

    // Tile sub-regions: each 72×72 atlas region has 24px transparent padding;
    // extract the centre 24×24 once at load time. Index = tile type (0 = EMPTY).
    private lateinit var sprTileRegions: Array<TextureRegion>

    // Per-ninja run animation state (updated every render frame via delta time)
    private data class NinjaRunState(var framePos: Int = 0, var leftovers: Float = 0f)
    private val ninjaRunState = mutableMapOf<Ninja, NinjaRunState>()

    // Tracks last observed state per ninja to detect STANDING entry for the settle animation
    private val ninjaLastState      = mutableMapOf<Ninja, Ninja.State>()
    private val ninjaStandEntryTime = mutableMapOf<Ninja, Float>()

    // ---------------------------------------------------------------------------
    // Sprite-effect system — one-shot Flash MC animations, ticked in render time
    // ---------------------------------------------------------------------------

    private data class SpriteEffect(
        val dir: String,        // fx atlas region prefix, e.g. "fx_dust0"
        val frameCount: Int,
        var elapsed: Float,     // seconds since spawn
        val x: Float,           // world x
        val y: Float,           // world y (game y-down)
        val rotation: Float,    // libGDX CCW degrees (already negated from Flash CW)
        val w: Float,           // display width  (|flashScaleX| * naturalWidth)
        val h: Float,           // display height (|flashScaleY| * naturalHeight)
        val sx: Float,          // ±1 horizontal flip
        val sy: Float,          // ±1 vertical flip
        val originX: Float,     // registration x from sprite bottom-left (world units)
        val originY: Float      // registration y from sprite bottom-left (world units)
    )
    private val effects = mutableListOf<SpriteEffect>()

    // Timer bar font (uni05, 16 px) — shows the formatted time value inside the bar
    private lateinit var timerFont: BitmapFont
    // Overlay / HUD font (uni05 pixel font, 20 px) — PRE_GAME / PAUSED / POST_GAME prompts
    private lateinit var overlayFont: BitmapFont
    private val glyphLayout = GlyphLayout()

    init {
        camera.setToOrtho(false, WORLD_W + 2 * GAME_PAD, WORLD_H + 2 * GAME_PAD)
        camera.position.set(WORLD_W / 2f, WORLD_H / 2f, 0f)
        camera.update()
        loadSprites()
    }

    // ---------------------------------------------------------------------------
    // Sprite loading
    // ---------------------------------------------------------------------------

    private fun loadSprites() {
        atlas        = TextureAtlas(Gdx.files.internal("atlas/sprites.atlas"))
        ninjaAtlas   = TextureAtlas(Gdx.files.internal("atlas/ninja.atlas"))
        ragdollAtlas = TextureAtlas(Gdx.files.internal("atlas/ragdoll.atlas"))
        fxAtlas      = TextureAtlas(Gdx.files.internal("atlas/fx.atlas"))
        // Sprites are at 3x resolution; atlas is packed with Linear filter.
        // Enforce it in case any GPU driver overrides the atlas hint.
        atlas.textures.forEach { it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        // Each tile PNG is 216×216 (3× original 72×72) with 72px transparent padding on each side.
        // Extract the centre 72×72 region and draw it at 24×24 game units (explicit in drawTiles).
        sprTileRegions = Array(42) { t ->
            val r = atlas.findRegion("tiles/${t + 1}")
                ?: error("Atlas missing region: tiles/${t + 1}")
            TextureRegion(r.texture, r.regionX + 72, r.regionY + 72, 72, 72)
        }
        val gen8 = FreeTypeFontGenerator(Gdx.files.internal("fonts/uni05_8.ttf"))
        val p8 = FreeTypeFontParameter().apply {
            size      = 16
            mono      = true
            minFilter = Texture.TextureFilter.Nearest
            magFilter = Texture.TextureFilter.Nearest
            color     = Color.WHITE
        }
        timerFont   = gen8.generateFont(p8)
        overlayFont = gen8.generateFont(p8)
        gen8.dispose()

        ShaderProgram.pedantic = false
        ninjaShader = ShaderProgram(NINJA_VERT, NINJA_FRAG)
        if (!ninjaShader.isCompiled)
            Gdx.app.error("GameRenderer", "Ninja shader error: ${ninjaShader.log}")
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
        unitsPerPx = WORLD_H / height.toFloat()
    }


    fun render(
        sim:           Simulator,
        playState:     PlayState = PlayState.GAME,
        currentTicks:  Int       = SimGlobals.DEFAULT_TIMER_TICKS,
        startingTicks: Int       = SimGlobals.DEFAULT_TIMER_TICKS,
        levelLabel:    String    = "",
        ninjaColor:    Color     = Color.WHITE
    ) {
        val dt = Gdx.graphics.deltaTime
        if (playState != PlayState.PRE_GAME) stateTime += dt

        // Drain spawn queue built up during this frame's physics ticks
        processSpawnEvents(sim)

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

        // Pass 2: non-FULL tile sprites.
        batch.begin()
        drawTiles(sim.tileGrid)
        batch.end()

        // Pass 3: living ninja.
        batch.begin()
        batch.setShader(ninjaShader)
        drawNinjaSprites(sim.players, ninjaColor)
        batch.setShader(null)
        batch.setColor(Color.WHITE)
        batch.end()

        // Pass 4: FULL (type-1) solid walls.
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeType.Filled)
        shape.color = COL_BG
        // Expand each tile by 0.5 units on all sides so adjacent tiles share pixels,
        // preventing 1px background bleed on high-DPI Android screens.
        val E = 0.5f
        for (row in 0 until rows) for (col in 0 until cols) {
            if (sim.tileGrid[col + row * cols] != TileTypes.FULL) continue
            shape.rect(col * cs - E, fy((row + 1) * cs) - E, cs + 2 * E, cs + 2 * E)
        }
        shape.end()

        // Pass 4: dead ninja ragdoll (color-replacement shader) + sprite effects.
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.setShader(ninjaShader)
        for (ninja in sim.players) if (ninja.isDead()) drawRagdoll(ninja, ninjaColor)
        batch.setShader(null)
        batch.setColor(Color.WHITE)
        tickAndDrawEffects(dt)
        batch.end()

        // Pass 5: fading turret shot lines (white, 10-sim-tick fade)
        if (turretShots.isNotEmpty()) drawTurretShots(dt)

        // Pass 6: timer bar (always visible, drawn above the level interior).
        drawTimerBar(currentTicks, startingTicks)

        // Pass 7: level label in bottom-left corner of the level area.
        if (levelLabel.isNotEmpty()) drawLevelLabel(levelLabel)

        // Pass 8: dim overlay + prompt text for non-GAME states.
        if (playState != PlayState.GAME) drawOverlay(playState)
    }

    // ---------------------------------------------------------------------------
    // Timer bar
    // ---------------------------------------------------------------------------

    private fun drawTimerBar(currentTicks: Int, startingTicks: Int) {
        // Unclamped fraction so gold overflow (fraction > 1.0) is visible
        val fraction = if (startingTicks > 0) currentTicks.toFloat() / startingTicks.toFloat() else 0f

        // Reserve space on the left for the timer number.
        glyphLayout.setText(timerFont, "0000.000")
        val numAreaW = glyphLayout.width + 8f   // 8 units gap between number and bar

        // Fill width: fraction=1.0 → BAR_NORMAL_W; gold extends to right edge.
        val fillW = (fraction * BAR_NORMAL_W).coerceIn(0f, WORLD_W - numAreaW)

        // Bar sits 10 px below the top of the window, inside the level's gray top tile border.
        val topPad = 7f * unitsPerPx
        val barY   = WORLD_H - BAR_H - topPad
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeType.Filled)
        shape.color = barFillColor(fraction)
        if (fillW > 0f) shape.rect(numAreaW, barY, fillW, BAR_H)
        shape.end()

        // Timer number: left-aligned, vertically centred in the bar strip
        val text = formatTimer(currentTicks)
        val rawTy = barY + BAR_H / 2f + timerFont.capHeight / 2f
        val ty = ceil(rawTy / unitsPerPx) * unitsPerPx
        batch.projectionMatrix = camera.combined
        batch.begin()
        timerFont.color = COL_TIMER_TEXT
        timerFont.draw(batch, text, 4f, ty + 1f)
        batch.end()
    }

    private fun drawLevelLabel(label: String) {
        // Level name: 10 px from the left and 10 px from the bottom of the window.
        val pad = 12f * unitsPerPx
        val tx  = pad
        val ty  = pad + timerFont.capHeight
        batch.projectionMatrix = camera.combined
        batch.begin()
        timerFont.color = COL_MODAL_TEXT
        timerFont.draw(batch, label, tx, ty)
        batch.end()
    }

    /**        val ty = barY + BAR_H / 2f + timerFont.capHeight / 2f

     * Color matches the original TimeBar MovieClip gradient (reverse-engineered from sprite frames).
     *
     * Frames 1–502 (fraction 0→1): bar grows, color: dark-red → magenta → dark-blue
     *   - 0.0→0.5: R=136/255, G=34/255, B rises  54→136/255
     *   - 0.5→1.0: R drops 136→34/255, G=34/255, B=136/255
     * Frames 502–750 (fraction 1→1.5): bar full, stays dark-blue  (34, 34, 136)/255
     * Frames 750–1004 (fraction 1.5→2.0+): bar full, shifts to cyan (170, 253, 253)/255
     */
    private fun barFillColor(fraction: Float): Color {
        fun lf(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
        return when {
            fraction <= 0.5f -> {
                val t = fraction / 0.5f
                Color(136/255f, 34/255f, lf(54/255f, 136/255f, t), 1f)
            }
            fraction <= 1.0f -> {
                val t = (fraction - 0.5f) / 0.5f
                Color(lf(136/255f, 34/255f, t), 34/255f, 136/255f, 1f)
            }
            fraction <= 1.5f -> Color(34/255f, 34/255f, 136/255f, 1f)   // dark blue plateau
            else -> {
                val t = (fraction - 1.5f) / 0.5f
                Color(lf(34/255f, 170/255f, t), lf(34/255f, 253/255f, t), lf(136/255f, 253/255f, t), 1f)
            }
        }
    }

    /** Format ticks matching original TimeFormatter: 4-char space-padded seconds + "." + 3-char millis. */
    private fun formatTimer(ticks: Int): String {
        val totalSeconds = ticks.toDouble() / SimGlobals.SIM_RATE
        val intPart = totalSeconds.toInt()
        val fracPart = ((totalSeconds - intPart) * 1000).toInt()
        return intPart.toString().padStart(4, ' ') + "." + fracPart.toString().padStart(3, '0')
    }

    // ---------------------------------------------------------------------------
    // State overlays (PRE_GAME / PAUSED / POST_GAME)
    // ---------------------------------------------------------------------------

    private fun drawOverlay(playState: PlayState) {
        val line1 = when (playState) {
            PlayState.PRE_GAME  -> "PRESS JUMP TO BEGIN"
            PlayState.PAUSED    -> "PAUSED"
            PlayState.POST_GAME -> "PRESS JUMP TO RETRY"
            else                -> return
        }
        val line2 = when (playState) {
            PlayState.PAUSED -> "PRESS JUMP TO CONTINUE, OR ${com.nplus.Platform.quit} TO QUIT"
            else             -> null
        }

        // Measure text to size the modal box (capHeight for true visual centering)
        val capH     = overlayFont.capHeight
        val lineGap  = capH * 0.8f
        val lineStep = capH + lineGap
        glyphLayout.setText(overlayFont, line1)
        var maxTextW = glyphLayout.width
        if (line2 != null) {
            glyphLayout.setText(overlayFont, line2)
            if (glyphLayout.width > maxTextW) maxTextW = glyphLayout.width
        }
        val lineCount = if (line2 != null) 2 else 1
        val textH = capH * lineCount + lineGap * (lineCount - 1)
        val padX  = 14f
        val padY  = 8f
        val boxW  = maxTextW + padX * 2f
        val boxH  = textH + padY * 2f
        val boxX  = (WORLD_W - boxW) / 2f
        val boxY  = (WORLD_H - boxH) / 2f

        // Draw the modal background and border over the unobscured play area
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeType.Filled)
        shape.color = COL_MODAL_BG
        shape.rect(boxX, boxY, boxW, boxH)
        shape.end()

        shape.begin(ShapeType.Line)
        shape.color = COL_MODAL_BORDER
        shape.rect(boxX, boxY, boxW, boxH)
        shape.end()

        // Text centered inside the modal box
        batch.projectionMatrix = camera.combined
        batch.begin()
        overlayFont.color = COL_MODAL_TEXT

        // y = box center + half of visible text block height (capHeight-based vertical centering)
        var y = boxY + boxH / 2f + textH / 2f
        glyphLayout.setText(overlayFont, line1)
        overlayFont.draw(batch, line1, boxX + (boxW - glyphLayout.width) / 2f, y)

        if (line2 != null) {
            y -= lineStep
            glyphLayout.setText(overlayFont, line2)
            overlayFont.draw(batch, line2, boxX + (boxW - glyphLayout.width) / 2f, y)
        }

        batch.end()
    }

    override fun dispose() {
        shape.dispose()
        batch.dispose()
        atlas.dispose()
        ninjaAtlas.dispose()
        if (::ragdollAtlas.isInitialized) ragdollAtlas.dispose()
        if (::fxAtlas.isInitialized)      fxAtlas.dispose()
        if (::timerFont.isInitialized)    timerFont.dispose()
        if (::overlayFont.isInitialized)  overlayFont.dispose()
        if (::ninjaShader.isInitialized)  ninjaShader.dispose()
    }

    // ---------------------------------------------------------------------------
    // Tile rendering (SpriteBatch)
    // ---------------------------------------------------------------------------

    private fun drawTiles(tileGrid: IntArray) {
        val cols = Simulator.GRID_NUM_COLS
        val rows = Simulator.GRID_NUM_ROWS
        val cs   = Simulator.GRID_CELL_SIZE
        // Same 0.5-unit expansion as the FULL-tile ShapeRenderer pass: prevents 1-pixel gaps
        // between adjacent tiles at non-integer scale factors on high-DPI Android screens.
        val E = 0.5f
        for (row in 0 until rows) for (col in 0 until cols) {
            val t = tileGrid[col + row * cols]
            if (t == TileTypes.EMPTY || t == TileTypes.FULL) continue  // FULL drawn in pre-pass
            batch.draw(sprTileRegions[t],
                col * cs - E, fy((row + 1) * cs) - E,
                cs + 2 * E, cs + 2 * E)
        }
    }

    // ---------------------------------------------------------------------------
    // Entity sprite rendering (SpriteBatch)
    // ---------------------------------------------------------------------------

    /** Draw [reg] centred at game position (px, py). Canvas centre = registration point. */
    private fun drawCentered(reg: TextureRegion, px: Float, py: Float) {
        val gw = reg.regionWidth / SPRITE_SCALE; val gh = reg.regionHeight / SPRITE_SCALE
        batch.draw(reg, px - gw / 2f, fy(py) - gh / 2f, gw, gh)
    }

    /**
     * Draw a gold sprite frame centred on the entity physics position.
     *
     * FFDec canvas is 18×160 (3× of original 6×54); the atlas preserves the full canvas.
     * Idle coin content sits in the bottom ~19px (bottom of canvas = libGDX y-up bottom).
     * At 3× the coin renders as a naturally proportioned ~18×19px shape, so we draw at
     * natural width (gw = 18/3 = 6f) and centre horizontally. The -4f y-offset keeps
     * the coin near the physics position (coin occupies bottom ~6 units of the 53-unit canvas).
     */
    private fun drawGoldTex(reg: TextureRegion, px: Float, py: Float) {
        val gw = reg.regionWidth / SPRITE_SCALE; val gh = reg.regionHeight / SPRITE_SCALE
        batch.draw(reg, px - gw / 2f, fy(py) - 4f, gw, gh)
    }

    /**
     * Draw a rocket projectile frame with right-centre registration, rotated toward travel direction.
     * Canvas is 61×11; body at x≈55.5, y-centre at 5.5. Rotation: negate atan2(dy,dx) for libGDX CCW.
     */
    private fun drawRocketTex(reg: TextureRegion, px: Float, py: Float, ornDeg: Float) {
        val w = reg.regionWidth / SPRITE_SCALE; val h = reg.regionHeight / SPRITE_SCALE
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
        val w = reg.regionWidth / SPRITE_SCALE; val h = reg.regionHeight / SPRITE_SCALE
        val ox = w / 2f;                        val oy = h / 2f
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
                val px = e.getPos().x; val py = e.getPos().y
                if (!e.isOpen()) {
                    exitDoorOpenTimes.remove(e)
                    drawCentered(fr("exitdoor", 1), px, py)
                } else {
                    val start   = exitDoorOpenTimes.getOrPut(e) { stateTime }
                    val elapsed = stateTime - start
                    // Frames 2–17: opening animation (16 frames), then hold frame 17.
                    val frame   = (2 + (elapsed * FPS_EXITDOOR).toInt()).coerceAtMost(17)
                    drawCentered(fr("exitdoor", frame), px, py)
                }
            }

            is ExitSwitch -> {
                if (!e.isDoorOpen())
                    drawCentered(fr("exitswitch", 1), e.getPos().x, e.getPos().y)
                else
                    drawCentered(fr("exitswitch", 2), e.getPos().x, e.getPos().y)
            }

            is ThwompEntity -> {
                // AS3: mc.rotation = _loc1_ * 180/PI where _loc1_ depends on isHorizontal/fallDir.
                // Convert Flash CW degrees → libGDX CCW degrees by negating.
                val flashAngle = if (e.isHorizontal()) {
                    if (e.getFallDir() < 0) 180f else 0f
                } else {
                    if (e.getFallDir() < 0) 270f else 90f
                }
                drawRotated(fr("thwomp", 1), e.getPos().x, e.getPos().y, -flashAngle)
            }

            is BounceBlockEntity -> {
                drawCentered(fr("bounceblock", 1), e.getPos().x, e.getPos().y)
            }

            is FloorGuardEntity -> {
                drawCentered(cycled("floorguard", 3, FPS_FLOORGUARD), e.getPos().x, e.getPos().y)
            }

            is TurretEntity -> {
                val pos   = e.getPos()
                val ap    = e.getAimPos()
                val state = e.getState()

                // Base sprite: state-driven animation frame (never rotates; crosshair shows aim).
                //   State 0/1 (idle/targeting): frame 1 (dark)
                //   State 2   (prefire):        frames 2–8 based on charge progress
                //   State 3   (postfire):       frame 9 (peak brightness, just fired)
                val baseFrame = when (state) {
                    2 -> {
                        val progress = e.getPrefireProgress()
                        val span = TURRET_PREFIRE_CHARGE_END - TURRET_PREFIRE_CHARGE_START
                        (TURRET_PREFIRE_CHARGE_START + (progress * span).toInt())
                            .coerceIn(TURRET_PREFIRE_CHARGE_START, TURRET_PREFIRE_CHARGE_END)
                    }
                    3 -> TURRET_POSTFIRE_FRAME
                    else -> 1
                }
                drawCentered(fr("turret", baseFrame), pos.x, pos.y)

                // Crosshair sprite: drawn at aim position.
                //   Frame 1 = aim_far, 2 = aim_mid, 3 = aim_near, 4 = prefire, 5+ = hidden
                val crosshairFrame = when (state) {
                    1 -> (e.getAimRegion() + 1).coerceIn(1, 3)  // region 0→1, 1→2, 2→3, 3→3
                    2 -> 4                                        // prefire: locked-on glow
                    else -> 0                                     // 0 = don't draw
                }
                if (crosshairFrame > 0) {
                    drawCentered(fr("turret_crosshair", crosshairFrame), ap.x, ap.y)
                }
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
                val ornC = e.gfxOrn.toDouble()
                drawCentered(fr("drone_eye", 1), e.pos.x + cos(ornC).toFloat() * 4f, e.pos.y + sin(ornC).toFloat() * 4f)
            }

            is DroneZap -> {
                drawCentered(fr("drone_zap", 1), e.pos.x, e.pos.y)
                val ornZ = e.gfxOrn.toDouble()
                drawCentered(fr("drone_eye", 1), e.pos.x + cos(ornZ).toFloat() * 4f, e.pos.y + sin(ornZ).toFloat() * 4f)
            }

            is DroneLaser -> {
                drawCentered(cycled("drone_laser", 26, FPS_DRONE), e.pos.x, e.pos.y)
            }

            is DroneChaingun -> {
                drawCentered(cycled("drone_chaingun", 7, FPS_DRONE), e.pos.x, e.pos.y)
                drawRotated(fr("drone_chaingun_eye", 1), e.pos.x, e.pos.y, -Math.toDegrees(e.gfxOrn.toDouble()).toFloat())
            }

            is LaunchpadEntity -> {
                val n = e.getNormal()
                if (e.consumeTrigger()) launchpadTriggerTimes[e] = stateTime
                val t = launchpadTriggerTimes[e]
                val frame = if (t != null && stateTime - t < 19f / FPS_LAUNCHPAD)
                    ((stateTime - t) * FPS_LAUNCHPAD).toInt().coerceIn(0, 18) + 1
                else
                    1
                val reg = fr("launchpad", frame)
                val gw = reg.regionWidth / SPRITE_SCALE; val gh = reg.regionHeight / SPRITE_SCALE
                // Flash registration at left-center (0, h/2): sprite extends along the normal direction.
                // Using +normalAngleDeg (not negated) with origin at left edge correctly maps the
                // sprite's x-extent (its 5px narrow dimension) outward along the surface normal.
                batch.draw(reg, e.getPos().x, fy(e.getPos().y) - gh / 2f,
                    0f, gh / 2f, gw, gh, 1f, 1f, normalAngleDeg(n.x, n.y))
            }

            is OnewayPlatformEntity -> {
                val n = e.getNormal()
                val halfThick = 2.5f
                drawRotated(fr("oneway", 1),
                    e.getPos().x - n.x * halfThick,
                    e.getPos().y - n.y * halfThick,
                    normalAngleDeg(n.x, n.y))
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

    private fun drawNinjaSprites(players: List<Ninja>, ninjaColor: Color) {
        batch.setColor(ninjaColor)
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
            // In-air frames extend up to 13 world units from centre (physics radius = 10),
            // so shift the sprite 3 units away from any wall the ninja is touching to keep
            // limbs inside the physics boundary.  Wall-slide uses 4.4 for the same reason.
            val xOff = when (state.state) {
                Ninja.State.WALL_SLIDING -> state.wallNormalX * 4.4f
                Ninja.State.JUMPING, Ninja.State.FALLING, Ninja.State.AWAITING_DEATH ->
                    if (state.wallNormalX != 0f) state.wallNormalX * 3f else 0f
                else -> 0f
            }
            drawNinjaTex(reg, state.posX + xOff, state.posY, state.facing, ornDeg)
        }
    }

    private fun drawNinjaTex(reg: TextureRegion, px: Float, py: Float, facing: Float, ornDeg: Float) {
        // Ninja frames are 1x (187×140 px), always downscaled to ~37×28 game units. No SPRITE_SCALE.
        val w = reg.regionWidth * 0.2f; val h = reg.regionHeight * 0.2f
        val ox = w / 2f; val oy = h / 2f - 2.9f
        batch.draw(reg, px - ox, fy(py) - oy, ox, oy, w, h, facing, 1f, ornDeg)
    }

    private fun selectNinjaFrame(ninja: Ninja, state: Ninja.GfxState): TextureRegion? {
        return when (state.state) {
            Ninja.State.DISABLED -> null
            Ninja.State.DEAD     -> null  // handled by ShapeRenderer ragdoll

            Ninja.State.WALL_SLIDING ->
                fr("ninja", 104)   // frame 104: all pixels right of canvas centre = body pressed against right wall

            Ninja.State.SKIDDING ->
                fr("ninja", 12)    // frame 12: skidding down a slope

            Ninja.State.STANDING -> {
                // On entry: play frames 1-11 once at 24fps (settle-down animation), then hold frame 11.
                val elapsed   = stateTime - (ninjaStandEntryTime[ninja] ?: stateTime)
                val settleIdx = (elapsed * 24f).toInt().coerceIn(0, 10)
                fr("ninja", settleIdx + 1)
            }

            Ninja.State.CELEBRATING -> {
                // Play the chosen celebration clip at 40fps (Flash timeline rate), then hold last frame.
                val elapsed    = stateTime - (ninjaStandEntryTime[ninja] ?: stateTime)
                val frameCount = celebEndFrame - celebStartFrame + 1
                val frameIdx   = (elapsed * FPS_CELEBRATE).toInt().coerceAtMost(frameCount - 1)
                fr("ninja", celebStartFrame + frameIdx)
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

    private fun drawRagdoll(ninja: Ninja, ninjaColor: Color) {
        val rag    = ninja.ragdoll
        val facing = ninja.getFacing()
        batch.setColor(ninjaColor)
        for (i in 0 until rag.getStickCount()) {
            val s      = rag.getStickRenderData(i)
            val spName = RAGDOLL_SPRITE[i]
            val reg    = ragdollAtlas.findRegion("$spName/${s.frame}") ?: continue
            // Ragdoll frames are 1x, same downscale as ninja. No SPRITE_SCALE.
            val w      = reg.regionWidth  * 0.2f
            val h      = reg.regionHeight * 0.2f
            // Registration at left-center (p0 = stick origin) in sprite local space.
            // In the atlas the sprite canvas has p0 at the left edge, center height.
            val ox = 0f; val oy = h / 2f
            // libGDX: negate Flash rotation for y-up, flip sign for scaleY due to y-axis inversion
            val rot    = -(s.ornRad * (180f / PI.toFloat()))
            val scaleY = RAGDOLL_FLIP[i] * facing
            batch.draw(reg, s.x0 - ox, fy(s.y0) - oy, ox, oy, w, h, 1f, scaleY, rot)
        }
    }

    // ---------------------------------------------------------------------------
    // Sprite-effect helpers
    // ---------------------------------------------------------------------------

    /** Draw all active effects and remove finished ones. */
    private fun tickAndDrawEffects(dt: Float) {
        val iter = effects.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.elapsed += dt
            val totalDuration = e.frameCount / FPS_EFFECTS
            if (e.elapsed >= totalDuration) { iter.remove(); continue }
            val frame = ((e.elapsed * FPS_EFFECTS).toInt() + 1).coerceIn(1, e.frameCount)
            val reg = fxAtlas.findRegion("${e.dir}/$frame") ?: continue
            batch.draw(reg,
                e.x - e.originX, fy(e.y) - e.originY,
                e.originX, e.originY,
                e.w, e.h,
                e.sx, e.sy,
                e.rotation)
        }
    }

    /** Spawn one dust-family particle (FXTYPE_JUMPDUST / FXTYPE_SKIDDUST). */
    private fun spawnDust(x: Float, y: Float, flashRot: Float, flashScaleX: Float, flashScaleY: Float) {
        val dir = if (Math.random() < 0.5) "fx_dust0" else "fx_dust1"
        val frameCount = if (dir == "fx_dust0") 33 else 31
        val reg = fxAtlas.findRegion("$dir/1") ?: return
        val natW = reg.regionWidth / SPRITE_SCALE; val natH = reg.regionHeight / SPRITE_SCALE
        val absW = abs(flashScaleX) * natW; val absH = abs(flashScaleY) * natH
        // Registration at left-center: dust origin at (p.x, p.y), spray extends right/away.
        effects += SpriteEffect(dir, frameCount, 0f, x, y,
            rotation = -flashRot,
            w = absW, h = absH,
            sx = if (flashScaleX >= 0) 1f else -1f,
            sy = if (flashScaleY >= 0) 1f else -1f,
            originX = 0f, originY = absH / 2f)
    }

    private fun rnd() = Math.random().toFloat()

    private fun processSpawnEvents(sim: Simulator) {
        for (e in sim.pendingSpawns) {
            when (e) {
                // --- JumpDust: 5 dust sprites fanning out from the foot ---
                is Simulator.SpawnEvent.JumpDust -> {
                    var side = 1
                    repeat(5) {
                        val rot = e.angleDeg - side * 20 + (rnd() * 20 - 10)
                        val sx  = side * (10 + rnd() * 8) / 100f
                        val sy  = (10 + rnd() * 5) / 100f
                        spawnDust(e.x, e.y, rot, sx, sy)
                        side *= -1
                    }
                }
                // --- LandDust: 5 dust sprites with wider fan ---
                is Simulator.SpawnEvent.LandDust -> {
                    var side = 1
                    repeat(5) {
                        val rot = e.angleDeg - side * 40 + (rnd() * 20 - 10)
                        val sx  = side * (5 + rnd() * 5 + e.speed) / 100f
                        val sy  = (15 + e.speed * 2) / 100f
                        spawnDust(e.x, e.y, rot, sx, sy)
                        side *= -1
                    }
                }
                // --- WallDust: 30% chance, 1 skid-dust sprite ---
                is Simulator.SpawnEvent.WallDust -> {
                    if (rnd() < 0.3f) {
                        val rot = 90f - e.nx * 8 + (rnd() * 10 - 5)
                        val sx  = (10 + e.speed * 20).coerceAtMost(64f) / 100f
                        val sy  = 10f / 100f
                        spawnDust(e.x, e.y, rot, sx, sy)
                    }
                }
                // --- BloodSpurt: count+1 blood sprites ---
                is Simulator.SpawnEvent.Blood -> {
                    val MAX = 64f
                    repeat(e.count + 1) {
                        val dir = if (rnd() < 0.5f) "fx_blood0" else "fx_blood1"
                        val fc  = if (dir == "fx_blood0") 19 else 32
                        val reg = fxAtlas.findRegion("$dir/1") ?: return@repeat
                        val natW = reg.regionWidth / SPRITE_SCALE; val natH = reg.regionHeight / SPRITE_SCALE
                        val bx = e.x - (rnd() * 8 - 4)
                        val by = e.y - (rnd() * 8 - 4)
                        val fsx = (e.vx * (6 + rnd() * 3) - (rnd() * 60 - 30))
                            .coerceIn(-MAX, MAX) / 100f
                        val fsy = (e.vy * (6 + rnd() * 3) - (rnd() * 60 - 30))
                            .coerceIn(-MAX, MAX) / 100f
                        val absW = abs(fsx) * natW; val absH = abs(fsy) * natH
                        if (absW < 0.1f || absH < 0.1f) return@repeat
                        effects += SpriteEffect(dir, fc, 0f, bx, by,
                            rotation = 0f,
                            w = absW, h = absH,
                            sx = if (fsx >= 0) 1f else -1f,
                            sy = if (fsy >= 0) 1f else -1f,
                            originX = absW / 2f, originY = absH / 2f)
                    }
                }
                // --- RocketSmoke: 20% chance, 1 smoke sprite ---
                is Simulator.SpawnEvent.RocketSmoke -> {
                    if (rnd() < 0.2f) {
                        val variant = (rnd() * 3).toInt().coerceIn(0, 2)
                        val dir = "fx_rocket_smoke$variant"
                        val fc  = when (variant) { 0 -> 28; 1 -> 23; else -> 27 }
                        val reg = fxAtlas.findRegion("$dir/1") ?: continue
                        val natW = reg.regionWidth / SPRITE_SCALE; val natH = reg.regionHeight / SPRITE_SCALE
                        val fsx = (20 + rnd() * 20) / 100f
                        val fsy = (20 + rnd() * 20) / 100f
                        val rot = e.angleDeg + (10 * (rnd() * 2 - 1))
                        val absW = fsx * natW; val absH = fsy * natH
                        // Registration at right-center: nozzle at (e.x,e.y), smoke extends left/back
                        effects += SpriteEffect(dir, fc, 0f, e.x, e.y,
                            rotation = -rot,
                            w = absW, h = absH,
                            sx = 1f, sy = 1f,
                            originX = absW, originY = absH / 2f)
                    }
                }
                // --- Zap: 5 fx_zap* sprites, centred at contact point, angled toward normal ---
                is Simulator.SpawnEvent.Zap -> {
                    repeat(5) {
                        val v = (rnd() * 3).toInt().coerceIn(0, 2)
                        val dir = "fx_zap$v"
                        val fc  = when (v) { 0 -> 10; 1 -> 13; else -> 15 }
                        val reg = fxAtlas.findRegion("$dir/1") ?: return@repeat
                        val nw = reg.regionWidth / SPRITE_SCALE; val nh = reg.regionHeight / SPRITE_SCALE
                        val sx = (30 + rnd() * 30) / 100f; val sy = (30 + rnd() * 20) / 100f
                        val rot = e.angleDeg + 20f * (rnd() * 2f - 1f)
                        effects += SpriteEffect(dir, fc, 0f, e.x, e.y,
                            rotation = -rot, w = sx*nw, h = sy*nh, sx = 1f, sy = 1f,
                            originX = sx*nw/2f, originY = sy*nh/2f)
                    }
                }
                // --- ZapThwompH: 5 fx_zap* sprites spread along thwomp face ---
                is Simulator.SpawnEvent.ZapThwompH -> {
                    repeat(5) {
                        val v = (rnd() * 3).toInt().coerceIn(0, 2)
                        val dir = "fx_zap$v"
                        val fc  = when (v) { 0 -> 10; 1 -> 13; else -> 15 }
                        val reg = fxAtlas.findRegion("$dir/1") ?: return@repeat
                        val nw = reg.regionWidth / SPRITE_SCALE; val nh = reg.regionHeight / SPRITE_SCALE
                        val px = e.x + e.vx; val py = e.y - e.vy + e.vy * rnd()
                        val sx = (4f * e.vx + 20f * (rnd() * 2f - 1f)) / 100f
                        val sy = (60 + 60 * rnd()) / 100f
                        effects += SpriteEffect(dir, fc, 0f, px, py,
                            rotation = 0f, w = abs(sx)*nw, h = sy*nh, sx = if (sx>=0) 1f else -1f, sy = 1f,
                            originX = abs(sx)*nw/2f, originY = sy*nh/2f)
                    }
                }
                // --- ZapThwompV: 5 fx_zapv* sprites spread along thwomp face ---
                is Simulator.SpawnEvent.ZapThwompV -> {
                    repeat(5) {
                        val v = (rnd() * 3).toInt().coerceIn(0, 2)
                        val dir = "fx_zapv$v"
                        val fc  = when (v) { 0 -> 10; 1 -> 13; else -> 15 }
                        val reg = fxAtlas.findRegion("$dir/1") ?: return@repeat
                        val nw = reg.regionWidth / SPRITE_SCALE; val nh = reg.regionHeight / SPRITE_SCALE
                        val px = e.x - e.vx + e.vx * rnd(); val py = e.y + e.vy
                        val sy = (4f * e.vy + 20f * (rnd() * 2f - 1f)) / 100f
                        val sx = (60 + 60 * rnd()) / 100f
                        effects += SpriteEffect(dir, fc, 0f, px, py,
                            rotation = 0f, w = sx*nw, h = abs(sy)*nh, sx = 1f, sy = if (sy>=0) 1f else -1f,
                            originX = sx*nw/2f, originY = abs(sy)*nh/2f)
                    }
                }
                // --- Explosion: 1 fireburst + 4 fireballs ---
                is Simulator.SpawnEvent.Explosion -> {
                    val r1 = rnd(); val r2 = rnd(); val r3 = rnd()
                    val r4 = rnd(); val r5 = rnd()
                    // Fireburst
                    run {
                        val v = (rnd() * 2).toInt().coerceIn(0, 1)
                        val dir = "fx_fireburst$v"; val fc = if (v == 0) 19 else 17
                        val reg = fxAtlas.findRegion("$dir/1") ?: return@run
                        val nw = reg.regionWidth / SPRITE_SCALE; val nh = reg.regionHeight / SPRITE_SCALE
                        val sw = (15 + r1 * 15) / 100f; val sh = (15 + r2 * 15) / 100f
                        effects += SpriteEffect(dir, fc, 0f, e.x, e.y,
                            rotation = 0f, w = sw*nw, h = sh*nh, sx = 1f, sy = 1f,
                            originX = sw*nw/2f, originY = sh*nh/2f)
                    }
                    // 4 fireballs
                    val fbRots = floatArrayOf(360f*r1, 360f*r2, 360f*r5, 360f*r3)
                    val fbSxArr = floatArrayOf(
                        (20 + r3 * 20) / 100f, (20 + r3 * 20) / 100f,
                        (20 + r4 * 30) / 100f, (20 + r4 * 30) / 100f)
                    val fbSyArr = floatArrayOf(
                        (20 + r5 * 20) / 100f, (20 + r1 * 10) / 100f,
                        (20 + r1 * 10) / 100f, (20 + r5 * 20) / 100f)
                    repeat(4) { j ->
                        val v = (rnd() * 3).toInt().coerceIn(0, 2)
                        val dir = "fx_fireball$v"
                        val fc  = when (v) { 0 -> 15; 1 -> 14; else -> 11 }
                        val reg = fxAtlas.findRegion("$dir/1") ?: return@repeat
                        val nw = reg.regionWidth / SPRITE_SCALE; val nh = reg.regionHeight / SPRITE_SCALE
                        val sw = fbSxArr[j]; val sh = fbSyArr[j]
                        effects += SpriteEffect(dir, fc, 0f, e.x, e.y,
                            rotation = -fbRots[j], w = sw*nw, h = sh*nh, sx = 1f, sy = 1f,
                            originX = 0f, originY = sh*nh/2f)
                    }
                }
                is Simulator.SpawnEvent.TurretBullet -> {
                    turretShots += TurretShot(e.x0, e.y0, e.x1, e.y1, e.hnx, e.hny)
                }
            }
        }
        sim.pendingSpawns.clear()
    }

    private fun drawTurretShots(dt: Float) {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glLineWidth(3f)
        shape.projectionMatrix = camera.combined
        shape.begin(ShapeType.Line)
        val iter = turretShots.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            s.elapsed += dt
            if (s.elapsed >= TURRET_SHOT_DURATION) { iter.remove(); continue }
            val alpha = 1f - s.elapsed / TURRET_SHOT_DURATION
            shape.color = Color(0.4f, 0.4f, 0.4f, alpha)
            shape.line(s.x0, fy(s.y0), s.x1, fy(s.y1))
            if (s.hnx != 0f || s.hny != 0f) {
                shape.line(s.x1, fy(s.y1),
                           s.x1 + 4f * s.hnx, fy(s.y1 + 4f * s.hny))
            }
        }
        shape.end()
        Gdx.gl.glLineWidth(1f)
    }

}
