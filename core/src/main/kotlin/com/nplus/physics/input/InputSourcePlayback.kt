package com.nplus.physics.input

import com.nplus.SimGlobals

/**
 * Port of AS3 InputSource_Playback.
 *
 * Replay data is recorded at 60 fps (1 byte per 60 Hz tick).
 * [stride] converts physics ticks to replay frames so playback speed is always
 * 60 fps regardless of SIM_RATE (e.g. stride=2 at SIM_RATE=120).
 *
 * Byte layout: bit 0 = Jump, bit 1 = Left, bit 2 = Right.
 */
class InputSourcePlayback(private val frames: ByteArray) : InputSource {

    private val stride = (SimGlobals.SIM_RATE / 60f).toInt().coerceAtLeast(1)

    private var _jump     = false
    private var _left     = false
    private var _right    = false
    private var _finished = false

    override val isJumpDown:       Boolean get() = _jump
    override val isLeftDown:       Boolean get() = _left
    override val isRightDown:      Boolean get() = _right
    override val isReplayFinished: Boolean get() = _finished

    override fun tick(frame: Int) {
        val replayFrame = frame / stride
        if (replayFrame >= frames.size) {
            _jump     = false
            _left     = false
            _right    = false
            _finished = true
        } else {
            val b  = frames[replayFrame].toInt() and 0xFF
            _jump  = (b and 0x01) != 0
            _left  = (b and 0x02) != 0
            _right = (b and 0x04) != 0
        }
    }
}
