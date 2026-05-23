package com.nplus.input

/**
 * Immutable snapshot of one physics frame's input state.
 * Polled once per tick; never modified after creation.
 */
data class InputState(
    val left:  Boolean = false,
    val right: Boolean = false,
    val jump:  Boolean = false,
    val pause: Boolean = false
) {
    companion object {
        val EMPTY = InputState()
    }
}
