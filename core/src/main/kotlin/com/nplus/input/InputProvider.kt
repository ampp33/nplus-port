package com.nplus.input

import com.nplus.physics.input.InputSource

/**
 * Higher-level input interface used by the GameScreen.
 * Polls hardware once per frame and returns an [InputState] snapshot.
 *
 * Also adapts to [InputSource] so a single provider can serve both
 * the game screen (pause logic) and the Ninja's physics Think().
 */
interface InputProvider : InputSource {

    /** Read current hardware state and return a snapshot. Called once per frame. */
    fun poll(): InputState

    // --- InputSource bridge (delegates to poll() result stored in lastState) ---

    val lastState: InputState

    override fun tick(frame: Int) { poll() }   // poll() updates lastState as a side effect
    override val isJumpDown:  Boolean get() = lastState.jump
    override val isLeftDown:  Boolean get() = lastState.left
    override val isRightDown: Boolean get() = lastState.right
    val isPauseDown: Boolean          get() = lastState.pause
}
