package com.nplus.physics.input

/** Port of AS3 InputSource_Base interface. */
interface InputSource {
    /** Called once per physics tick before any state reads. [frame] is the tick counter. */
    fun tick(frame: Int)

    val isJumpDown: Boolean
    val isLeftDown: Boolean
    val isRightDown: Boolean

    /** True when a recorded replay has played back all stored frames. */
    val isReplayFinished: Boolean get() = false

    /** Serialise recorded input for replay storage. Returns empty string by default. */
    fun dumpReplayString(): String = ""
}
