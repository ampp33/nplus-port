package com.nplus.input

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Minimal fake that lets tests inject arbitrary [InputState]s without libGDX. */
private class FakeInput(private var next: InputState = InputState.EMPTY) : InputProvider {
    override var lastState: InputState = InputState.EMPTY; private set
    override fun poll(): InputState { lastState = next; return lastState }
    fun set(state: InputState) { next = state }
}

class InputTest : FunSpec({

    test("InputState defaults to all-false") {
        val s = InputState()
        s.left  shouldBe false
        s.right shouldBe false
        s.jump  shouldBe false
        s.pause shouldBe false
    }

    test("InputState.EMPTY is all-false") {
        InputState.EMPTY shouldBe InputState()
    }

    test("InputState data equality") {
        InputState(left = true) shouldBe InputState(left = true, right = false, jump = false, pause = false)
    }

    test("FakeInput tick() updates lastState") {
        val fake = FakeInput(InputState(jump = true))
        fake.tick(0)
        fake.isJumpDown  shouldBe true
        fake.isLeftDown  shouldBe false
        fake.isRightDown shouldBe false
        fake.isPauseDown shouldBe false
    }

    test("CombinedInputSource returns false when both sources inactive") {
        val a = FakeInput(InputState.EMPTY)
        val b = FakeInput(InputState.EMPTY)
        val combined = CombinedInputSource(a, b)
        combined.poll()
        combined.lastState shouldBe InputState.EMPTY
    }

    test("CombinedInputSource ORs left from first source") {
        val a = FakeInput(InputState(left = true))
        val b = FakeInput(InputState.EMPTY)
        val combined = CombinedInputSource(a, b)
        combined.poll()
        combined.lastState.left  shouldBe true
        combined.lastState.right shouldBe false
    }

    test("CombinedInputSource ORs jump from second source") {
        val a = FakeInput(InputState.EMPTY)
        val b = FakeInput(InputState(jump = true))
        val combined = CombinedInputSource(a, b)
        combined.poll()
        combined.lastState.jump shouldBe true
    }

    test("CombinedInputSource merges both sources simultaneously") {
        val a = FakeInput(InputState(left = true, jump = true))
        val b = FakeInput(InputState(right = true, pause = true))
        val combined = CombinedInputSource(a, b)
        combined.poll()
        combined.lastState shouldBe InputState(left = true, right = true, jump = true, pause = true)
    }

    test("CombinedInputSource tick() updates sub-sources") {
        val a = FakeInput(InputState(left = true))
        val combined = CombinedInputSource(a)
        combined.tick(42)
        combined.isLeftDown shouldBe true
    }

    test("InputProvider isReplayFinished defaults to false") {
        val fake = FakeInput()
        fake.isReplayFinished shouldBe false
    }

    test("CombinedInputSource with three sources") {
        val a = FakeInput(InputState(left = true))
        val b = FakeInput(InputState(right = true))
        val c = FakeInput(InputState(jump = true))
        val combined = CombinedInputSource(a, b, c)
        combined.poll()
        with(combined.lastState) {
            left  shouldBe true
            right shouldBe true
            jump  shouldBe true
            pause shouldBe false
        }
    }
})
