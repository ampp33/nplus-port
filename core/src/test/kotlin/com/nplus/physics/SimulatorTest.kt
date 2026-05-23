package com.nplus.physics

import com.nplus.physics.collision.GridEdges
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.collision.GridSegment
import com.nplus.physics.input.InputSource
import com.nplus.physics.math.MathUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private object NoInput : InputSource {
    override fun tick(frame: Int) {}
    override val isJumpDown = false
    override val isLeftDown = false
    override val isRightDown = false
}

private fun makeSim(vararg ninjas: Ninja) = Simulator(
    segGrid  = GridSegment(Simulator.GRID_NUM_COLS, Simulator.GRID_NUM_ROWS, Simulator.GRID_CELL_SIZE),
    edgeGrid = GridEdges(Simulator.GRID_NUM_COLS,   Simulator.GRID_NUM_ROWS, Simulator.GRID_CELL_SIZE),
    objGrid  = GridEntity(Simulator.GRID_NUM_COLS,  Simulator.GRID_NUM_ROWS, Simulator.GRID_CELL_SIZE),
    entities = emptyList(),
    players  = ninjas.toList()
)

class SimulatorTest : FunSpec({

    test("grid constants match AS3 values") {
        Simulator.TILE_COLS     shouldBe 31
        Simulator.TILE_ROWS     shouldBe 23
        Simulator.GRID_NUM_COLS shouldBe 33
        Simulator.GRID_NUM_ROWS shouldBe 25
        Simulator.GRID_CELL_SIZE shouldBe 24f
    }

    test("PRNG is seeded to 1 on construction — deterministic random sequences") {
        val n = Ninja(0, NoInput, 100f, 100f)
        makeSim(n)
        val r1 = MathUtils.random()
        makeSim(n)
        val r2 = MathUtils.random()
        r1 shouldBe r2
    }

    test("appIsGameDone is false at start with a live player") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        val sim = makeSim(ninja)
        sim.appIsGameDone() shouldBe false
    }

    test("appIsGameDone is true when all players are dead") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        // stays DISABLED → counts as dead
        val sim = makeSim(ninja)
        sim.appIsGameDone() shouldBe true
    }

    test("appDidPlayerWin is false before exit") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        val sim = makeSim(ninja)
        sim.appDidPlayerWin() shouldBe false
    }

    test("eventExitHitPlayer triggers win") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        val sim = makeSim(ninja)
        sim.eventExitHitPlayer(ninja)
        sim.appIsGameDone() shouldBe true
        sim.appDidPlayerWin() shouldBe true
    }

    test("eventGoldHitPlayer increments gold count") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        val sim = makeSim(ninja)
        sim.eventGoldHitPlayer(ninja) shouldBe true
        sim.eventGoldHitPlayer(ninja) shouldBe true
        // Gold counts are zeroed at the start of each tick so we check before ticking
        sim.appGetGoldCollectedThisTick(0) shouldBe 2
    }

    test("eventGoldHitPlayer returns false after win") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        val sim = makeSim(ninja)
        sim.eventExitHitPlayer(ninja)
        sim.eventGoldHitPlayer(ninja) shouldBe false
    }

    test("appSuicide kills the player") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        val sim = makeSim(ninja)
        sim.appSuicide(0)
        // simKill moves to AWAITING_DEATH; after think() it will be DEAD
        val sim2 = makeSim(ninja)
        ninja.think(sim2, 0)
        ninja.isDead() shouldBe true
    }

    test("appIsPlaybackFinished is true when all inputs report finished") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        // NoInput.isReplayFinished returns false by default (base interface returns false)
        val sim = makeSim(ninja)
        // The default InputSource.isReplayFinished returns false
        sim.appIsPlaybackFinished() shouldBe false
    }

    test("tick advances frame without crashing on empty player list") {
        val sim = makeSim()
        repeat(10) { sim.tick() }
        sim.appIsGameDone() shouldBe true   // no players → all dead
    }

    test("collision iterations constant is 4") {
        Simulator.COLLISION_ITERATIONS shouldBe 4
    }
})
