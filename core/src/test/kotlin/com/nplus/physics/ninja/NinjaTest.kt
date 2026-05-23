package com.nplus.physics.ninja

import com.nplus.SimGlobals
import com.nplus.physics.Ninja
import com.nplus.physics.Simulator
import com.nplus.physics.collision.GridEdges
import com.nplus.physics.collision.GridEntity
import com.nplus.physics.collision.GridSegment
import com.nplus.physics.input.InputSource
import com.nplus.physics.math.Vec2
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.pow

// Minimal no-op input for tests
private object NoInput : InputSource {
    override fun tick(frame: Int) {}
    override val isJumpDown = false
    override val isLeftDown = false
    override val isRightDown = false
}

// Input that holds right
private object HoldRight : InputSource {
    override fun tick(frame: Int) {}
    override val isJumpDown = false
    override val isLeftDown = false
    override val isRightDown = true
}

// Simulator with real grids for physics tests
private fun testSim(vararg ninjas: Ninja): Simulator = Simulator(
    segGrid  = GridSegment(Simulator.GRID_NUM_COLS, Simulator.GRID_NUM_ROWS, Simulator.GRID_CELL_SIZE),
    edgeGrid = GridEdges(Simulator.GRID_NUM_COLS, Simulator.GRID_NUM_ROWS, Simulator.GRID_CELL_SIZE),
    objGrid  = GridEntity(Simulator.GRID_NUM_COLS, Simulator.GRID_NUM_ROWS, Simulator.GRID_CELL_SIZE),
    entities = emptyList(),
    players  = ninjas.toList()
)

class NinjaTest : FunSpec({

    val eps = 1e-4f

    test("starts in DISABLED state") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.state shouldBe Ninja.State.DISABLED
    }

    test("enable transitions to STANDING") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        ninja.state shouldBe Ninja.State.STANDING
    }

    test("gravity constant scales with sim_rate") {
        // normGrav = 0.15 * (40/SIM_RATE)^2; normDrag = 0.99^(40/SIM_RATE)
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        val sim = testSim(ninja)
        ninja.preCollision()
        ninja.collideVsTiles(sim)
        ninja.collideVsObjects(sim)
        ninja.solveInternalConstraints()
        ninja.postCollision(sim)
        ninja.think(sim, 0)
        val velBefore = ninja.getVel()
        ninja.integrate()
        val velAfter = ninja.getVel()
        val expectedGrav = 0.15f * (40f / SimGlobals.SIM_RATE).pow(2)
        val expectedDrag = 0.99f.pow(40f / SimGlobals.SIM_RATE)
        (velAfter.y - velBefore.y * expectedDrag) shouldBe expectedGrav.plusOrMinus(0.005f)
    }

    test("radius is 10") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.getRadius() shouldBe 10f
    }

    test("simKill returns false when already dead") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        ninja.simKill(0, 0f, 0f, 0f, 0f)  // triggers AWAITING_DEATH
        val result = ninja.simKill(0, 0f, 0f, 0f, 0f)
        result shouldBe false
    }

    test("simWin returns false when disabled") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.simWin() shouldBe false
    }

    test("simWin returns true when standing") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        ninja.simWin() shouldBe true
        ninja.state shouldBe Ninja.State.CELEBRATING
    }

    test("simWin returns false when already celebrating") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        ninja.simWin()
        ninja.simWin() shouldBe false
    }

    test("debugRespawn resets position and velocity") {
        val ninja = Ninja(0, NoInput, 100f, 100f)
        ninja.enable()
        ninja.integrate()
        ninja.debugRespawn(Vec2(50f, 50f))
        val pos = ninja.getPos()
        pos.x shouldBe 50f.plusOrMinus(eps)
        pos.y shouldBe 50f.plusOrMinus(eps)
        val vel = ninja.getVel()
        vel.x shouldBe 0f.plusOrMinus(eps)
        vel.y shouldBe 0f.plusOrMinus(eps)
    }

    test("NinjaState.isGroundState") {
        Ninja.State.STANDING.isGroundState    shouldBe true
        Ninja.State.RUNNING.isGroundState     shouldBe true
        Ninja.State.SKIDDING.isGroundState    shouldBe true
        Ninja.State.JUMPING.isGroundState     shouldBe false
        Ninja.State.FALLING.isGroundState     shouldBe false
        Ninja.State.WALL_SLIDING.isGroundState shouldBe false
        Ninja.State.DEAD.isGroundState        shouldBe false
    }

    test("onSound callback fires on simKill then think") {
        val sounds = mutableListOf<String>()
        val ninja = Ninja(0, NoInput, 100f, 100f, onSound = { sounds.add(it) })
        ninja.enable()
        ninja.simKill(0, 0f, 0f, 0f, 0f)   // → AWAITING_DEATH
        val sim = testSim(ninja)
        ninja.think(sim, 0)                  // → DEAD, fires death sound
        (sounds.isNotEmpty()) shouldBe true
    }
})
