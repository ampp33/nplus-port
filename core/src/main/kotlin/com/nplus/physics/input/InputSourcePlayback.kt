package com.nplus.physics.input

/**
 * Port of AS3 InputSource_Playback.
 *
 * One byte per physics tick: bit 0 = Jump, bit 1 = Left, bit 2 = Right.
 * isReplayFinished becomes true once the frame index exceeds the stored data.
 */
class InputSourcePlayback(private val frames: ByteArray) : InputSource {

    private var _jump     = false
    private var _left     = false
    private var _right    = false
    private var _finished = false

    override val isJumpDown:       Boolean get() = _jump
    override val isLeftDown:       Boolean get() = _left
    override val isRightDown:      Boolean get() = _right
    override val isReplayFinished: Boolean get() = _finished

    override fun tick(frame: Int) {
        if (frame >= frames.size) {
            _jump     = false
            _left     = false
            _right    = false
            _finished = true
        } else {
            val b  = frames[frame].toInt() and 0xFF
            _jump  = (b and 0x01) != 0
            _left  = (b and 0x02) != 0
            _right = (b and 0x04) != 0
        }
    }
}
