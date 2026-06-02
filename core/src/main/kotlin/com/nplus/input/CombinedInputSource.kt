package com.nplus.input

/**
 * OR-combines two or more [InputProvider]s into one.
 * A button is considered pressed if ANY source has it pressed.
 * Enables keyboard + gamepad to control the same player simultaneously.
 */
class CombinedInputSource(private val sources: List<InputProvider>) : InputProvider {

    constructor(vararg sources: InputProvider) : this(sources.toList())

    override var lastState: InputState = InputState.EMPTY
        private set

    private var jumpSuppressed = false

    /** Suppress jump input until the physical button is released (prevents a start/retry press from registering as a game jump). */
    fun suppressJump() { jumpSuppressed = true }

    override val isJumpDown: Boolean get() = if (jumpSuppressed) false else lastState.jump

    override fun poll(): InputState {
        val states = sources.map { it.poll() }
        lastState = InputState(
            left  = states.any { it.left },
            right = states.any { it.right },
            jump  = states.any { it.jump },
            pause = states.any { it.pause },
            quit  = states.any { it.quit }
        )
        if (jumpSuppressed && !lastState.jump) jumpSuppressed = false
        return lastState
    }

    companion object {
        /** Convenience factory: keyboard (primary) + gamepad (secondary). */
        fun keyboardAndGamepad(
            gamepadIndex: Int = 0,
            axisDeadzone: Float = 0.4f
        ): CombinedInputSource = CombinedInputSource(
            KeyboardInputSource(),
            GamepadInputSource(gamepadIndex, axisDeadzone)
        )
    }
}
