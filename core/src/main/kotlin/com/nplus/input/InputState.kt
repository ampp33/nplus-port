package com.nplus.input

/**
 * Immutable snapshot of one physics frame's input state.
 * Polled once per tick; never modified after creation.
 */
data class InputState(
    val left:  Boolean = false,
    val right: Boolean = false,
    val jump:  Boolean = false,
    val pause: Boolean = false,  // Y button / Start / P key — pause toggle
    val quit:  Boolean = false   // X button / ESC — back/exit (pause in-game, quit when paused)
) {
    companion object {
        val EMPTY = InputState()
    }
}
