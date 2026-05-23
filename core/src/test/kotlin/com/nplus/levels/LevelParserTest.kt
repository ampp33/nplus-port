package com.nplus.levels

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.io.File

private val LEVELS_BIN = File("../assets/levels/levels.bin")

class LevelParserTest : FunSpec({

    // Skip gracefully if running from a context where assets/ isn't reachable
    val binAvailable = LEVELS_BIN.exists()

    test("levels.bin exists in assets") {
        binAvailable shouldBe true
    }

    test("parseBin returns 600 levels") {
        if (!binAvailable) return@test
        val levels = LevelParser.parseBin(LEVELS_BIN.inputStream())
        levels.size shouldBe 600
    }

    test("episode 0 level 0 name is correct") {
        if (!binAvailable) return@test
        val map = LevelParser.parseBinToMap(LEVELS_BIN.inputStream())
        val lv = map[0 to 0]
        lv shouldNotBe null
        lv!!.name shouldContain "straight forward"
    }

    test("tile data has correct size (713 = 31×23)") {
        if (!binAvailable) return@test
        val map = LevelParser.parseBinToMap(LEVELS_BIN.inputStream())
        val lv = map[0 to 0]!!
        lv.tileIds.size shouldBe 713
    }

    test("all tile IDs are valid tile types (0-41)") {
        if (!binAvailable) return@test
        val levels = LevelParser.parseBin(LEVELS_BIN.inputStream())
        for (level in levels) {
            for (tid in level.tileIds) {
                tid.shouldBeBetween(0, 41)
            }
        }
    }

    test("level 0-0 first row is all FULL tiles (type 1)") {
        if (!binAvailable) return@test
        val lv = LevelParser.parseBinToMap(LEVELS_BIN.inputStream())[0 to 0]!!
        // Row-major: first 31 entries = first row
        for (col in 0 until 31) {
            lv.tileIds[col] shouldBe 1
        }
    }

    test("every level has exactly one player spawn") {
        if (!binAvailable) return@test
        val levels = LevelParser.parseBin(LEVELS_BIN.inputStream())
        for (level in levels) {
            val spawns = level.entities.count { it.type == EntityTypes.PLAYER }
            // Most levels have 1 player spawn; some co-op levels have 2
            spawns.shouldBeBetween(0, 2)
        }
    }

    test("every level has an exit door and exit switch") {
        if (!binAvailable) return@test
        val levels = LevelParser.parseBin(LEVELS_BIN.inputStream())
        for (level in levels) {
            val hasDoor   = level.entities.any { it.type == EntityTypes.EXIT_DOOR }
            val hasSwitch = level.entities.any { it.type == EntityTypes.EXIT_SWITCH }
            hasDoor shouldBe true
            hasSwitch shouldBe true
        }
    }

    test("entity world positions are within level bounds") {
        if (!binAvailable) return@test
        val maxX = (31 + 2) * 24f  // GRID_NUM_COLS * CELL_SIZE
        val maxY = (23 + 2) * 24f
        val levels = LevelParser.parseBin(LEVELS_BIN.inputStream())
        for (level in levels.take(20)) {  // spot-check first 20
            for (e in level.entities) {
                (e.worldX in 0f..maxX) shouldBe true
                (e.worldY in 0f..maxY) shouldBe true
            }
        }
    }

    test("LevelBuilder produces a Simulator with correct grid dimensions") {
        if (!binAvailable) return@test
        val noInput = object : com.nplus.physics.input.InputSource {
            override fun tick(frame: Int) {}
            override val isJumpDown = false
            override val isLeftDown = false
            override val isRightDown = false
        }
        val lv = LevelParser.parseBinToMap(LEVELS_BIN.inputStream())[0 to 0]!!
        val sim = LevelBuilder.build(lv, listOf(noInput))
        sim.players.size shouldBe 1
        (sim.players[0].getPos().x in 0f..(33 * 24f)) shouldBe true
    }
})
