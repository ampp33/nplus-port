package com.nplus.input

import com.badlogic.gdx.controllers.Controller
import com.badlogic.gdx.controllers.Controllers
import kotlin.math.abs

/**
 * Gamepad [InputProvider] using libGDX Controllers extension.
 *
 * Targets Retroid Pocket (Android) as primary device but works with any gamepad
 * whose driver exposes a standard `ControllerMapping`.
 *
 * Primary mappings (standard layout):
 *   Left/Right → D-pad left/right OR left-stick horizontal axis
 *   Jump       → A / Cross button
 *   Pause      → Start / Options button
 *
 * Falls back to [InputState.EMPTY] when no controller is connected.
 *
 * [controllerIndex] selects which connected controller to read (0 = first).
 * [axisDeadzone]    ignores stick values below this threshold (prevents drift).
 */
class GamepadInputSource(
    private val controllerIndex: Int   = 0,
    private val axisDeadzone:    Float = 0.4f
) : InputProvider {

    override var lastState: InputState = InputState.EMPTY
        private set

    override fun poll(): InputState {
        val all = Controllers.getControllers()  // libGDX Array<Controller>
        val c = if (all.size > controllerIndex) all[controllerIndex] else null
        lastState = if (c == null) InputState.EMPTY else readController(c)
        return lastState
    }

    private fun readController(c: Controller): InputState {
        val m = c.mapping

        // D-pad buttons
        val dLeft  = c.getButton(m.buttonDpadLeft)
        val dRight = c.getButton(m.buttonDpadRight)

        // Left stick horizontal axis (values range -1..1)
        val stickX = c.getAxis(m.axisLeftX)
        val stickLeft  = stickX < -axisDeadzone
        val stickRight = stickX >  axisDeadzone

        return InputState(
            left  = dLeft  || stickLeft,
            right = dRight || stickRight,
            jump  = c.getButton(m.buttonA),
            pause = c.getButton(m.buttonStart)
        )
    }

    /** True when at least one controller is connected. */
    fun isConnected(): Boolean = Controllers.getControllers().size > controllerIndex
}
