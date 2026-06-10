package com.nplus.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.controllers.Controllers
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.nplus.levels.DirTypes
import com.nplus.levels.EntityTypes
import com.nplus.levels.RawEntity
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.viewport.ExtendViewport

/**
 * Menu screen covering main menu, episode grid, and level select.
 *
 * ## State machine
 * MAIN_MENU → CONFIRM_NEW_GAME (if "New Game" chosen)
 * MAIN_MENU → EPISODE_GRID     (if "Episodes" or "New Game" chosen)
 * EPISODE_GRID → gameplay      (confirm on unlocked episode starts at level 0)
 *
 * ## Episode unlock rules
 * Episodes 0, 10, 20 … 90 are always open (first of each of 10 normal columns).
 * Beating all 5 levels in episode N unlocks episode N+1.
 * Secret episodes 100–119 appear and become playable only after all 100 normal episodes beaten.
 *
 * ## Fonts (uni 05_53 pixel font from original game)
 * fontSm (8 px)  — hint lines, "select episode", small labels
 * fontMd (16 px) — "play game" header, episode cell labels
 * fontLg (20 px) — main menu items
 * fontXl (32 px) — title "n"
 */
class MenuScreen(private val appState: AppStateManager,
                 startAtGrid: Boolean = false,
                 startEpisode: Int = 0) : Screen {

    // --- Viewport ---
    private val camera   = OrthographicCamera()
    private val viewport = ExtendViewport(792f, 600f, camera)

    // --- Rendering ---
    private val batch  = SpriteBatch()
    private val shape  = ShapeRenderer()
    private val layout = GlyphLayout()

    // Fonts loaded in show() once GL context is guaranteed
    private lateinit var fontSm: BitmapFont   // 8 px  (uni05_8 — same font used throughout HUD)
    private lateinit var fontMd: BitmapFont   // 16 px
    private lateinit var fontLg: BitmapFont   // 20 px
    private lateinit var fontXl: BitmapFont   // 32 px

    // Tile atlas + per-type regions for level preview rendering (loaded in show())
    private lateinit var tileAtlas: TextureAtlas
    private val tileRegions   = arrayOfNulls<TextureRegion>(42)    // index = tileType - 1
    private val entityRegions = HashMap<Int, TextureRegion>()       // EntityTypes.* → region

    // --- Colours (light theme matching original game) ---
    private val COL_BG         = Color(0.792f, 0.792f, 0.816f, 1f)  // #CACAD0 content area
    private val COL_BORDER     = Color(0.475f, 0.475f, 0.533f, 1f)  // #797988 outer border
    private val COL_PANEL_BG   = Color(0.36f,  0.36f,  0.42f,  1f)  // dark thumbnail panel
    private val COL_CELL       = Color(0.70f,  0.70f,  0.73f,  1f)  // unlocked cell
    private val COL_CELL_SEL   = Color(0.94f,  0.95f,  0.97f,  1f)  // selected (near white)
    private val COL_CELL_LOCK  = Color(0.76f,  0.76f,  0.79f,  1f)  // locked (subtle)
    private val COL_CELL_BEAT  = Color(0.68f,  0.78f,  0.68f,  1f)  // beaten (light green)
    private val COL_MINI_BG    = Color(0.25f,  0.25f,  0.30f,  1f)  // minimap dark bg (unused but kept for reference)
    private val COL_TEXT_DARK  = Color(0.10f,  0.10f,  0.14f,  1f)  // dark text on light bg
    private val COL_TEXT_DIM   = Color(0.42f,  0.42f,  0.48f,  1f)  // dim text on light bg
    private val COL_TEXT_LOCK  = Color(0.60f,  0.60f,  0.64f,  1f)  // locked episode text
    private val COL_TEXT_BEAT  = Color(0.20f,  0.45f,  0.20f,  1f)  // beaten (dark green)
    private val COL_TEXT_PANEL = Color(0.90f,  0.90f,  0.94f,  1f)  // light text on dark panel

    // --- Episode grid geometry ---
    // Column-major: col = ep / GRID_ROWS, row = ep % GRID_ROWS
    private val GRID_ROWS = 10
    private val GRID_COLS = 12
    private val CELL_W    = 48f
    private val CELL_H    = 42f
    private val COL_PITCH = 52f
    private val ROW_PITCH = 46f
    private val GRID_LEFT = 20f
    private val GRID_TOP  = 530f   // top of row 0 (y-up); space above reserved for title header

    // --- Thumbnail panel ---
    // Full 33×25 tile grid (31×23 interior + 1-tile boundary on each side)
    private val PANEL_X   = 640f
    private val MINI_CELL = 4
    private val MINI_COLS = 33   // full grid width including boundary
    private val MINI_ROWS = 25   // full grid height including boundary
    private val MINI_W    = (MINI_COLS * MINI_CELL).toFloat()   // 132
    private val MINI_H    = (MINI_ROWS * MINI_CELL).toFloat()   // 100

    // --- Navigation state ---
    private enum class MenuState { MAIN_MENU, CONFIRM_NEW_GAME, EPISODE_GRID }
    private var menuState     = if (startAtGrid) MenuState.EPISODE_GRID else MenuState.MAIN_MENU
    private var mainCursor    = 0   // index within current main-menu item list
    private var confirmCursor = 0   // 0 = Yes, 1 = No
    private var episodeCursor = startEpisode

    // Gamepad edge-detect
    private var prevGpUp      = false
    private var prevGpDown    = false
    private var prevGpLeft    = false
    private var prevGpRight   = false
    private var prevGpConfirm = false
    private var prevGpBack    = false

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    override fun show() {
        appState.menuBackground.show()   // idempotent: only initializes GL resources once

        camera.setToOrtho(false, 792f, 600f)
        camera.update()
        fontSm = makeFont("fonts/uni05_8.ttf", 8)
        fontMd = makeFont("fonts/uni05_16.ttf", 16)
        fontLg = makeFont("fonts/uni05_20.ttf", 20)
        fontXl = makeFont("fonts/uni05_32.ttf", 256)

        // Load tile atlas with linear filter for smooth level preview downscaling
        tileAtlas = TextureAtlas(Gdx.files.internal("atlas/sprites.atlas"))
        tileAtlas.textures.forEach {
            it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
        // Extract the 72×72 content centre from each 216×216 canvas (tiles exported at 3×,
        // 72px transparent padding on each side — same offset as GameRenderer).
        for (i in 1..42) {
            val r = tileAtlas.findRegion("tiles/$i") ?: continue
            tileRegions[i - 1] = TextureRegion(r.texture, r.regionX + 72, r.regionY + 72, 72, 72)
        }

        // Entity sprite regions: frame 1 of each entity type's sprite directory.
        // Region names match the atlas packing: "<dir>/<frame>" with no whitespace strip.
        val entitySpriteNames = mapOf(
            EntityTypes.MINE          to "mine/1",
            EntityTypes.GOLD          to "gold/1",
            EntityTypes.DOOR_REGULAR  to "door_regular/1",
            EntityTypes.DOOR_LOCKED   to "door_locked/1",
            EntityTypes.SWITCH_LOCKED to "door_locked/1",
            EntityTypes.DOOR_TRAP     to "door_trap/1",
            EntityTypes.SWITCH_TRAP   to "door_trap/1",
            EntityTypes.ONEWAY        to "oneway/1",
            EntityTypes.EXIT_DOOR     to "exitdoor/1",
            EntityTypes.EXIT_SWITCH   to "exitswitch/1",
            EntityTypes.CHAINGUN      to "drone_chaingun/1",
            EntityTypes.LASER         to "drone_laser/1",
            EntityTypes.ZAP           to "drone_zap/1",
            EntityTypes.CHASER        to "drone_chaser/1",
            EntityTypes.FLOORGUARD    to "floorguard/1",
            EntityTypes.LAUNCHPAD     to "launchpad/1",
            EntityTypes.BOUNCEBLOCK   to "bounceblock/1",
            EntityTypes.ROCKET        to "rocket/1",
            EntityTypes.TURRET        to "turret/1",
            EntityTypes.THWOMP        to "thwomp/1",
        )
        for ((type, name) in entitySpriteNames) {
            tileAtlas.findRegion(name)?.let { entityRegions[type] = it }
        }
    }

    private fun makeFont(path: String, sizePx: Int): BitmapFont {
        val gen = FreeTypeFontGenerator(Gdx.files.internal(path))
        val p   = FreeTypeFontParameter().apply {
            size      = sizePx
            mono      = true
            minFilter = Texture.TextureFilter.Nearest
            magFilter = Texture.TextureFilter.Nearest
            color     = Color.WHITE
        }
        val f = gen.generateFont(p)
        gen.dispose()
        return f
    }

    override fun render(delta: Float) {
        handleInput()

        // Render attract-mode game in background (clears screen internally)
        appState.menuBackground.render(delta)

        // Re-assert menu viewport over the game render
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        viewport.apply()
        shape.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        // Translucent wash so menu items are readable over the game background
        shape.begin(ShapeRenderer.ShapeType.Filled)
        shape.setColor(COL_BG.r, COL_BG.g, COL_BG.b, 0.60f)
        shape.rect(0f, 0f, 792f, 600f)
        shape.end()

        when (menuState) {
            MenuState.MAIN_MENU        -> drawMainMenu()
            MenuState.CONFIRM_NEW_GAME -> drawConfirmNewGame()
            MenuState.EPISODE_GRID     -> drawEpisodeGrid()
        }
    }

    override fun resize(width: Int, height: Int) {
        appState.menuBackground.resize(width, height)
        viewport.update(width, height, false)
        camera.position.set(396f, 300f, 0f)
        camera.update()
    }

    override fun pause()  {}
    override fun resume() {}
    override fun hide()   {}

    override fun dispose() {
        batch.dispose()
        shape.dispose()
        if (::fontSm.isInitialized) { fontSm.dispose(); fontMd.dispose(); fontLg.dispose(); fontXl.dispose() }
        if (::tileAtlas.isInitialized) tileAtlas.dispose()
    }

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------

    private fun handleInput() {
        val ki = Gdx.input

        val kUp      = ki.isKeyJustPressed(Input.Keys.UP)    || ki.isKeyJustPressed(Input.Keys.W)
        val kDown    = ki.isKeyJustPressed(Input.Keys.DOWN)   || ki.isKeyJustPressed(Input.Keys.S)
        val kLeft    = ki.isKeyJustPressed(Input.Keys.LEFT)   || ki.isKeyJustPressed(Input.Keys.A)
        val kRight   = ki.isKeyJustPressed(Input.Keys.RIGHT)  || ki.isKeyJustPressed(Input.Keys.D)
        val kConfirm = ki.isKeyJustPressed(Input.Keys.ENTER)  || ki.isKeyJustPressed(Input.Keys.Z)
        val kBack    = ki.isKeyJustPressed(Input.Keys.ESCAPE) || ki.isKeyJustPressed(Input.Keys.X)

        val pad = Controllers.getControllers().takeIf { it.size > 0 }?.get(0)
        val m   = pad?.mapping
        val gpUp      = pad?.getButton(m?.buttonDpadUp    ?: -1) == true
        val gpDown    = pad?.getButton(m?.buttonDpadDown  ?: -1) == true
        val gpLeft    = pad?.getButton(m?.buttonDpadLeft  ?: -1) == true
        val gpRight   = pad?.getButton(m?.buttonDpadRight ?: -1) == true
        val gpConfirm = pad?.getButton(m?.buttonA         ?: -1) == true
        val gpBackBtn = if (com.nplus.Platform.isRetroid) m?.buttonX else m?.buttonB
        val gpBack    = pad?.getButton(gpBackBtn ?: -1) == true

        val up      = kUp      || (gpUp      && !prevGpUp)
        val down    = kDown    || (gpDown    && !prevGpDown)
        val left    = kLeft    || (gpLeft    && !prevGpLeft)
        val right   = kRight   || (gpRight   && !prevGpRight)
        val confirm = kConfirm || (gpConfirm && !prevGpConfirm)
        val back    = kBack    || (gpBack    && !prevGpBack)

        prevGpUp = gpUp; prevGpDown = gpDown
        prevGpLeft = gpLeft; prevGpRight = gpRight
        prevGpConfirm = gpConfirm; prevGpBack = gpBack

        when (menuState) {
            MenuState.MAIN_MENU        -> navigateMainMenu(up, down, confirm)
            MenuState.CONFIRM_NEW_GAME -> navigateConfirm(up, down, confirm, back)
            MenuState.EPISODE_GRID     -> navigateEpisodeGrid(up, down, left, right, confirm, back)
        }
    }

    // -----------------------------------------------------------------------
    // Navigation — main menu
    // -----------------------------------------------------------------------

    /** Items shown depend on whether a save exists. */
    private fun mainMenuItems(): List<String> = buildList {
        if (appState.progress.hasSave) add("Continue")
        add("New Game")
        add("Episodes")
        add("Settings")
    }

    private fun navigateMainMenu(up: Boolean, down: Boolean, confirm: Boolean) {
        val items = mainMenuItems()
        if (down) mainCursor = (mainCursor + 1).coerceAtMost(items.size - 1)
        if (up)   mainCursor = (mainCursor - 1).coerceAtLeast(0)
        if (confirm) {
            when (items[mainCursor]) {
                "Continue" -> appState.playLevel(appState.progress.lastEpisode,
                                                 appState.progress.lastLevel)
                "New Game" -> { confirmCursor = 1; menuState = MenuState.CONFIRM_NEW_GAME }
                "Episodes" -> { menuState = MenuState.EPISODE_GRID }
                "Settings" -> appState.goToSettings()
            }
        }
    }

    private fun navigateConfirm(up: Boolean, down: Boolean, confirm: Boolean, back: Boolean) {
        if (up || down) confirmCursor = 1 - confirmCursor  // toggle Yes/No
        if (confirm) {
            if (confirmCursor == 0) appState.newGame() else menuState = MenuState.MAIN_MENU
        }
        if (back) menuState = MenuState.MAIN_MENU
    }

    // -----------------------------------------------------------------------
    // Navigation — episode grid
    // -----------------------------------------------------------------------

    private fun navigateEpisodeGrid(up: Boolean, down: Boolean, left: Boolean, right: Boolean,
                                     confirm: Boolean, back: Boolean) {
        val total = maxVisibleEpisodes()
        val prog  = appState.progress
        fun tryMove(target: Int) {
            val clamped = target.coerceIn(0, total - 1)
            if (prog.isUnlocked(clamped)) episodeCursor = clamped
        }
        if (down)  tryMove(episodeCursor + 1)
        if (up)    tryMove(episodeCursor - 1)
        if (right) tryMove(episodeCursor + GRID_ROWS)
        if (left)  tryMove(episodeCursor - GRID_ROWS)
        // Confirm starts the episode at level 0 — no level picker, matching the original game
        if (confirm && prog.isUnlocked(episodeCursor)
                    && appState.levelMap.containsKey(episodeCursor to 0)) {
            appState.playLevel(episodeCursor, 0)
        }
        if (back) menuState = MenuState.MAIN_MENU
    }

    /** Secret episodes hidden until all 100 normal ones beaten. */
    private fun maxVisibleEpisodes(): Int =
        if (appState.progress.secretsUnlocked) ProgressManager.TOTAL_EPISODES
        else ProgressManager.NORMAL_EPISODES

    // -----------------------------------------------------------------------
    // Drawing — main menu
    // -----------------------------------------------------------------------

    private fun drawMainMenu() {
        val items = mainMenuItems()
        val centreX = 396f
        val startY  = 320f
        val rowH    = 44f

        // Title
        batch.begin()
        fontXl.color = COL_TEXT_DARK
        drawTextCentred(fontXl, "n", centreX, 545f)
        batch.end()

        // Item rows
        val rowW = 260f
        shape.begin(ShapeRenderer.ShapeType.Filled)
        for (i in items.indices) {
            val rowY = startY - i * rowH
            shape.color = if (i == mainCursor) COL_CELL_SEL else COL_CELL
            shape.rect(centreX - rowW / 2f, rowY - 28f, rowW, 36f)
        }
        shape.end()
        // Selected item border
        val selRowY = startY - mainCursor * rowH
        shape.begin(ShapeRenderer.ShapeType.Line)
        shape.color = COL_TEXT_DARK
        shape.rect(centreX - rowW / 2f, selRowY - 28f, rowW, 36f)
        shape.end()

        batch.begin()
        for (i in items.indices) {
            val rowY = startY - i * rowH
            fontLg.color = if (i == mainCursor) COL_TEXT_DARK else COL_TEXT_DIM
            drawTextCentred(fontLg, items[i], centreX, rowY - 10f + fontLg.capHeight / 2f)
        }
        fontMd.color = COL_TEXT_DIM
        drawTextCentred(fontMd, "${com.nplus.Platform.confirm} select", centreX, 38f)
        batch.end()
    }

    // -----------------------------------------------------------------------
    // Drawing — new game confirmation
    // -----------------------------------------------------------------------

    private fun drawConfirmNewGame() {
        val centreX = 396f

        batch.begin()
        fontXl.color = COL_TEXT_DARK
        drawTextCentred(fontXl, "n", centreX, 545f)
        fontLg.color = COL_TEXT_DARK
        drawTextCentred(fontLg, "Start a new game?", centreX, 350f)
        fontMd.color = COL_TEXT_DIM
        drawTextCentred(fontMd, "This will erase all progress.", centreX, 320f)
        batch.end()

        layout.setText(fontLg, "Yes, start over")
        val w0 = layout.width
        layout.setText(fontLg, "No, go back")
        val w1 = layout.width
        val rowW = maxOf(w0, w1) + 40f   // 20px padding each side
        val btnY = 280f
        shape.begin(ShapeRenderer.ShapeType.Filled)
        shape.color = if (confirmCursor == 0) COL_CELL_SEL else COL_CELL
        shape.rect(centreX - rowW / 2f, btnY - 28f, rowW, 36f)
        shape.color = if (confirmCursor == 1) COL_CELL_SEL else COL_CELL
        shape.rect(centreX - rowW / 2f, btnY - 28f - 44f, rowW, 36f)
        shape.end()
        // Selected button border
        val selBtnY = if (confirmCursor == 0) btnY else btnY - 44f
        shape.begin(ShapeRenderer.ShapeType.Line)
        shape.color = COL_TEXT_DARK
        shape.rect(centreX - rowW / 2f, selBtnY - 28f, rowW, 36f)
        shape.end()

        batch.begin()
        fontLg.color = if (confirmCursor == 0) COL_TEXT_DARK else COL_TEXT_DIM
        drawTextCentred(fontLg, "Yes, start over", centreX, btnY - 10f + fontLg.capHeight / 2f)
        fontLg.color = if (confirmCursor == 1) COL_TEXT_DARK else COL_TEXT_DIM
        drawTextCentred(fontLg, "No, go back", centreX, btnY - 54f + fontLg.capHeight / 2f)
        batch.end()
    }

    // -----------------------------------------------------------------------
    // Drawing — episode grid
    // -----------------------------------------------------------------------

    private fun drawEpisodeGrid() {
        val visible = maxVisibleEpisodes()
        val progress = appState.progress

        // Episode cells
        shape.begin(ShapeRenderer.ShapeType.Filled)
        for (ep in 0 until visible) {
            val (cx, cy) = cellPos(ep)
            shape.color = when {
                ep == episodeCursor        -> COL_CELL_SEL
                progress.isBeaten(ep)      -> COL_CELL_BEAT
                progress.isUnlocked(ep)    -> COL_CELL
                else                       -> COL_CELL_LOCK
            }
            shape.rect(cx, cy, CELL_W, CELL_H)
        }
        shape.end()

        // Selected cell outline
        val (selX, selY) = cellPos(episodeCursor)
        shape.begin(ShapeRenderer.ShapeType.Line)
        shape.color = COL_TEXT_DARK
        shape.rect(selX, selY, CELL_W, CELL_H)
        shape.end()

        // Title header
        batch.begin()
        fontMd.color = COL_TEXT_DARK
        drawText(fontMd, "play game", GRID_LEFT + 4f, 590f)
        fontMd.color = COL_TEXT_DIM
        drawText(fontMd, "select episode", GRID_LEFT + 4f, 566f)

        // Episode labels
        for (ep in 0 until visible) {
            val (cx, cy) = cellPos(ep)
            fontMd.color = when {
                ep == episodeCursor     -> COL_TEXT_DARK
                progress.isBeaten(ep)  -> COL_TEXT_BEAT
                progress.isUnlocked(ep)-> COL_TEXT_DARK
                else                   -> COL_TEXT_LOCK
            }
            drawTextCentred(fontMd, episodeLabel(ep), cx + CELL_W / 2f, cy + CELL_H / 2f + fontMd.capHeight / 2f)
        }

        // Episode record (only shown once a record exists)
        val rec = progress.getEpisodeRecord(episodeCursor)
        if (rec >= 0) {
            fontMd.color = COL_TEXT_DIM
            drawText(fontMd, "RECORD: ${formatTicks(rec)}", GRID_LEFT + 4f, 52f)
        }

        // Hint bar
        fontMd.color = COL_TEXT_DIM
        drawText(fontMd, "${com.nplus.Platform.confirm} play   ${com.nplus.Platform.back} back", GRID_LEFT, 20f)
        batch.end()

        // Right panel thumbnails
        drawEpisodeThumbnails(episodeCursor)
    }

    private fun drawEpisodeThumbnails(ep: Int) {
        // Distribute 5 previews with equal gaps across the full panel height (y=8..592).
        val panelBot = 8f
        val panelTop = 592f
        val gapH = (panelTop - panelBot - 5f * MINI_H) / 6f
        val previewX = PANEL_X + 6f

        val borderSz     = MINI_CELL.toFloat()          // 4f = 1 boundary tile
        val tileW        = MINI_W / MINI_COLS.toFloat() // 4f
        val tileH        = MINI_H / MINI_ROWS.toFloat() // 4f
        val interiorCols = MINI_COLS - 2                // 31
        val eps          = 0.35f   // closes sub-pixel gaps between adjacent solid-tile rects

        fun miniY(lv: Int) = panelBot + gapH + (4 - lv) * (MINI_H + gapH)

        // Pass 1 (ShapeRenderer): level backgrounds only.
        shape.begin(ShapeRenderer.ShapeType.Filled)
        for (lv in 0..4) {
            if (appState.levelMap[ep to lv] == null) continue
            val my = miniY(lv)
            shape.color = COL_BORDER
            shape.rect(previewX, my, MINI_W, MINI_H)
            shape.color = COL_BG
            shape.rect(previewX + borderSz, my + borderSz,
                       MINI_W - borderSz * 2f, MINI_H - borderSz * 2f)
        }
        shape.end()

        // Pass 2 (SpriteBatch): entity sprites — drawn before solid walls so walls occlude them.
        batch.begin()
        batch.setColor(Color.WHITE)
        for (lv in 0..4) {
            val data = appState.levelMap[ep to lv] ?: continue
            drawLevelEntities(data.entities, previewX, miniY(lv))
        }
        batch.end()

        // Pass 3 (ShapeRenderer): type-1 solid walls drawn on top of entities.
        shape.begin(ShapeRenderer.ShapeType.Filled)
        shape.color = COL_BORDER
        for (lv in 0..4) {
            val data = appState.levelMap[ep to lv] ?: continue
            val my   = miniY(lv)
            for (row in 1 until MINI_ROWS - 1) {
                for (col in 1 until MINI_COLS - 1) {
                    if (data.tileIds[(col - 1) + (row - 1) * interiorCols] != 1) continue
                    shape.rect(previewX + col * tileW - eps,
                               my + (MINI_ROWS - 1 - row) * tileH - eps,
                               tileW + eps * 2f, tileH + eps * 2f)
                }
            }
        }
        shape.end()

        // Pass 4 (SpriteBatch): type 2+ tile sprites on top.
        batch.begin()
        batch.setColor(Color.WHITE)
        for (lv in 0..4) {
            val data = appState.levelMap[ep to lv] ?: continue
            drawLevelTiles(data.tileIds, previewX, miniY(lv))
        }
        batch.end()
    }

    // -----------------------------------------------------------------------
    // Level preview renderer
    // -----------------------------------------------------------------------

    /**
     * Draws all non-empty tiles from [tileIds] (31×23 row-major) as sprite quads
     * at [x],[y] (bottom-left in y-up coords) scaled to MINI_W×MINI_H.
     * The atlas must already be loaded with linear filter so downscaling is smooth.
     */
    private fun drawLevelTiles(tileIds: IntArray, x: Float, y: Float) {
        val tileW = MINI_W / MINI_COLS.toFloat()   // = 4f
        val tileH = MINI_H / MINI_ROWS.toFloat()   // = 4f
        val interiorCols = MINI_COLS - 2   // 31
        val eps = 0.35f
        for (row in 1 until MINI_ROWS - 1) {
            for (col in 1 until MINI_COLS - 1) {
                val type = tileIds[(col - 1) + (row - 1) * interiorCols]
                if (type == 0 || type == 1) continue  // 0=empty, 1=solid drawn by ShapeRenderer
                val region = tileRegions.getOrNull(type) ?: continue
                batch.draw(region,
                    x + col * tileW - eps,
                    y + (MINI_ROWS - 1 - row) * tileH - eps,
                    tileW + eps * 2f, tileH + eps * 2f)
            }
        }
    }

    /**
     * Draws all non-player entities from [entities] as sprite dots on the minimap.
     * Coordinates are in game-world space (y-down, 0=top, includes 24px boundary),
     * interior range x=[24..768], y=[24..576]. Scale = 1/6.
     */
    private fun drawLevelEntities(entities: List<RawEntity>, x: Float, y: Float) {
        val scale = 1f / 6f
        for (e in entities) {
            if (e.type == EntityTypes.PLAYER) continue
            val region = entityRegions[e.type] ?: continue
            // Sprites are at 3× resolution; divide back to game-unit size before minimap scaling.
            val dispW = (region.regionWidth  / 3f) * scale
            val dispH = (region.regionHeight / 3f) * scale
            val drawX = x + e.worldX * scale - dispW / 2f
            // Gold canvas is tall (coin only at bottom); use the same -4 game-unit offset as
            // GameRenderer.drawGoldTex instead of centering by canvas height.
            val drawY = if (e.type == EntityTypes.GOLD)
                y + (600f - e.worldY) * scale - 4f * scale
            else
                y + (600f - e.worldY) * scale - dispH / 2f
            val rotDeg = if (e.type == EntityTypes.THWOMP) thwompRotDeg(e.dir) else 0f
            batch.draw(region, drawX, drawY, dispW / 2f, dispH / 2f, dispW, dispH, 1f, 1f, rotDeg)
        }
    }

    private fun thwompRotDeg(dir: Int): Float {
        val dv  = DirTypes.toVec(dir)
        val isH = dv[1] == 0f
        val fd  = if (isH) dv[0].toInt() else dv[1].toInt()
        val flashAngle = if (isH) { if (fd < 0) 180f else 0f }
                         else     { if (fd < 0) 270f else 90f }
        return -flashAngle
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Bottom-left screen position of an episode cell (column-major layout). */
    private fun cellPos(ep: Int): Pair<Float, Float> {
        val col = ep / GRID_ROWS
        val row = ep % GRID_ROWS
        return (GRID_LEFT + col * COL_PITCH) to (GRID_TOP - row * ROW_PITCH - CELL_H)
    }

    /** Episode label matching the original: 0→"00", 10→"10", 100→"a0", 110→"b0". */
    private fun episodeLabel(ep: Int): String {
        val tensChar: Char = when (val t = ep / 10) {
            in 0..9  -> '0' + t
            10       -> 'a'
            else     -> 'b'
        }
        return "$tensChar${ep % 10}"
    }

    private fun formatTicks(ticks: Int): String {
        val secs = ticks / com.nplus.SimGlobals.SIM_RATE
        val intPart = secs.toInt()
        val fracPart = ((secs - intPart) * 1000).toInt()
        return "$intPart.${fracPart.toString().padStart(3, '0')}"
    }

    private fun drawText(font: BitmapFont, text: String, x: Float, y: Float) {
        font.draw(batch, text, x, y)
    }

    private fun drawTextCentred(font: BitmapFont, text: String, cx: Float, cy: Float) {
        layout.setText(font, text)
        font.draw(batch, layout, cx - layout.width / 2f, cy)
    }
}
