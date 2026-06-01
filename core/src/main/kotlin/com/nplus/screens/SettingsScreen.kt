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
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.viewport.ExtendViewport

class SettingsScreen(private val appState: AppStateManager) : Screen {

    private val camera   = OrthographicCamera()
    private val viewport = ExtendViewport(792f, 600f, camera)
    private val batch    = SpriteBatch()
    private val shape    = ShapeRenderer()
    private val layout   = GlyphLayout()

    private lateinit var fontSm: BitmapFont
    private lateinit var fontMd: BitmapFont
    private lateinit var fontLg: BitmapFont
    private lateinit var fontXl: BitmapFont

    private val COL_BG        = Color(0.792f, 0.792f, 0.816f, 1f)
    private val COL_BORDER    = Color(0.475f, 0.475f, 0.533f, 1f)
    private val COL_TEXT_DARK = Color(0.10f,  0.10f,  0.14f,  1f)
    private val COL_TEXT_DIM  = Color(0.42f,  0.42f,  0.48f,  1f)

    private val SWATCH_SIZE  = 36f
    private val SWATCH_PAD   = 8f
    private val SWATCH_COUNT = ProgressManager.NINJA_COLORS.size
    private val SWATCH_ROW_W = SWATCH_COUNT * (SWATCH_SIZE + SWATCH_PAD) - SWATCH_PAD
    private val SWATCH_LEFT  = (792f - SWATCH_ROW_W) / 2f
    private val SWATCH_Y     = 450f

    private var colorCursor = appState.progress.selectedNinjaColorIndex

    private var prevGpLeft    = false
    private var prevGpRight   = false
    private var prevGpConfirm = false
    private var prevGpBack    = false

    override fun show() {
        appState.menuBackground.show()   // idempotent: only initializes GL resources once

        camera.setToOrtho(false, 792f, 600f)
        camera.update()
        fontSm = makeFont("fonts/uni05_8.ttf",  8)
        fontMd = makeFont("fonts/uni05_16.ttf", 16)
        fontLg = makeFont("fonts/uni05_20.ttf", 20)
        fontXl = makeFont("fonts/uni05_32.ttf", 32)
    }

    private fun makeFont(path: String, sizePx: Int): BitmapFont {
        val gen = FreeTypeFontGenerator(Gdx.files.internal(path))
        val p   = FreeTypeFontParameter().apply {
            size = sizePx; mono = true
            minFilter = Texture.TextureFilter.Nearest
            magFilter = Texture.TextureFilter.Nearest
            color = Color.WHITE
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

        drawSettings()
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
    }

    private fun handleInput() {
        val ki = Gdx.input
        val kLeft    = ki.isKeyJustPressed(Input.Keys.LEFT)   || ki.isKeyJustPressed(Input.Keys.A)
        val kRight   = ki.isKeyJustPressed(Input.Keys.RIGHT)  || ki.isKeyJustPressed(Input.Keys.D)
        val kConfirm = ki.isKeyJustPressed(Input.Keys.ENTER)  || ki.isKeyJustPressed(Input.Keys.Z)
        val kBack    = ki.isKeyJustPressed(Input.Keys.ESCAPE) || ki.isKeyJustPressed(Input.Keys.X)

        val pad = Controllers.getControllers().takeIf { it.size > 0 }?.get(0)
        val m   = pad?.mapping
        val gpLeft    = pad?.getButton(m?.buttonDpadLeft  ?: -1) == true
        val gpRight   = pad?.getButton(m?.buttonDpadRight ?: -1) == true
        val gpConfirm = pad?.getButton(m?.buttonA         ?: -1) == true
        val gpBackBtn = if (com.nplus.Platform.isRetroid) m?.buttonX else m?.buttonB
        val gpBack    = pad?.getButton(gpBackBtn ?: -1) == true

        val left    = kLeft    || (gpLeft    && !prevGpLeft)
        val right   = kRight   || (gpRight   && !prevGpRight)
        val confirm = kConfirm || (gpConfirm && !prevGpConfirm)
        val back    = kBack    || (gpBack    && !prevGpBack)

        prevGpLeft = gpLeft; prevGpRight = gpRight
        prevGpConfirm = gpConfirm; prevGpBack = gpBack

        if (left)  colorCursor = (colorCursor - 1).coerceAtLeast(0)
        if (right) colorCursor = (colorCursor + 1).coerceAtMost(SWATCH_COUNT - 1)
        if (confirm) {
            val prog = appState.progress
            if (prog.isNinjaColorUnlocked(colorCursor)) {
                prog.setNinjaColor(colorCursor)
                prog.save()
            }
        }
        if (back) appState.goToMenu()
    }

    private fun drawSettings() {
        batch.begin()
        fontXl.color = COL_TEXT_DARK
        drawTextCentred(fontXl, "SETTINGS", 396f, 560f)
        batch.end()

        drawNinjaColorPicker()
        drawControlsSection()

        batch.begin()
        fontMd.color = COL_TEXT_DIM
        drawTextCentred(fontMd, "${com.nplus.Platform.confirm} select   ${com.nplus.Platform.back} back", 396f, 28f)
        batch.end()
    }

    private fun drawNinjaColorPicker() {
        val prog = appState.progress

        batch.begin()
        fontMd.color = COL_TEXT_DARK
        drawTextCentred(fontMd, "NINJA COLOR", 396f, 510f)
        batch.end()

        // Filled swatches
        shape.begin(ShapeRenderer.ShapeType.Filled)
        for (i in 0 until SWATCH_COUNT) {
            val sx = SWATCH_LEFT + i * (SWATCH_SIZE + SWATCH_PAD)
            val rawColor = ProgressManager.hexToColor(ProgressManager.NINJA_COLORS[i])
            shape.color = if (prog.isNinjaColorUnlocked(i)) rawColor else {
                Color((rawColor.r + COL_BG.r) / 2f, (rawColor.g + COL_BG.g) / 2f,
                      (rawColor.b + COL_BG.b) / 2f, 1f)
            }
            shape.rect(sx, SWATCH_Y, SWATCH_SIZE, SWATCH_SIZE)
        }
        shape.end()

        // Active color: inner white border so it reads on any background
        val activeIdx = prog.selectedNinjaColorIndex
        val ax = SWATCH_LEFT + activeIdx * (SWATCH_SIZE + SWATCH_PAD)
        shape.begin(ShapeRenderer.ShapeType.Line)
        shape.color = Color.WHITE
        shape.rect(ax + 3f, SWATCH_Y + 3f, SWATCH_SIZE - 6f, SWATCH_SIZE - 6f)
        shape.end()

        // Cursor border
        val cx = SWATCH_LEFT + colorCursor * (SWATCH_SIZE + SWATCH_PAD)
        shape.begin(ShapeRenderer.ShapeType.Line)
        shape.color = COL_TEXT_DARK
        shape.rect(cx - 2f, SWATCH_Y - 2f, SWATCH_SIZE + 4f, SWATCH_SIZE + 4f)
        shape.end()

        // Description / lock notice under the browsed swatch
        batch.begin()
        fontMd.color = COL_TEXT_DIM
        drawTextCentred(fontMd, swatchDescription(colorCursor, prog), 396f, SWATCH_Y - 18f)
        batch.end()
    }

    private fun swatchDescription(idx: Int, prog: ProgressManager): String {
        if (!prog.isNinjaColorUnlocked(idx)) {
            val col    = idx - 1
            var colName = "%X".format(col)
            return "Win all episodes in column $colName to unlock"
        }
        val name = COLOR_NAMES.getOrElse(idx) { "Color $idx" }
        return if (idx == prog.selectedNinjaColorIndex) "$name  (active)" else name
    }

    private val COLOR_NAMES = arrayOf(
        "Black", "Dark Red", "Magenta", "Purple", "Teal",
        "Green", "Gold", "Orange", "Brown", "White", "Dark Slate", "Gray", "Ghost"
    )

    private fun drawControlsSection() {
        val headerY = 375f
        val firstRowY = headerY - 30f
        val rowH = 20f

        val p = com.nplus.Platform
        batch.begin()
        fontMd.color = COL_TEXT_DARK
        drawTextCentred(fontMd, if (p.isRetroid) "CONTROLS (GAMEPAD)" else "CONTROLS (KEYBOARD)", 396f, headerY)

        val bindings = if (p.isRetroid) listOf(
            "Move"        to "D-pad L / R",
            "Jump"        to "A",
            "Pause"       to "Y",
            "Quit level"  to "X  (while paused)",
        ) else listOf(
            "Move"        to "Left / Right",
            "Jump"        to "Z / Space",
            "Pause"       to "P / Esc",
            "Retry level" to "R",
            "Suicide"     to "K",
            "Quit level"  to "Q  (while paused)",
        )
        val col1X = 210f
        val col2X = 420f
        for ((i, pair) in bindings.withIndex()) {
            val (action, key) = pair
            val rowY = firstRowY - i * rowH
            fontMd.color = COL_TEXT_DARK
            drawText(fontMd, action, col1X, rowY)
            fontMd.color = COL_TEXT_DIM
            drawText(fontMd, key, col2X, rowY)
        }
        batch.end()
    }

    private fun drawText(font: BitmapFont, text: String, x: Float, y: Float) {
        font.draw(batch, text, x, y)
    }

    private fun drawTextCentred(font: BitmapFont, text: String, cx: Float, cy: Float) {
        layout.setText(font, text)
        font.draw(batch, layout, cx - layout.width / 2f, cy)
    }
}
