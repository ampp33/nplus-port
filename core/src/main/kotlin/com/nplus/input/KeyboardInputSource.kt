package com.nplus.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input

/**
 * Keyboard + gamepad-as-keyboard [InputProvider].
 *
 * Default bindings:
 *   Left  → LEFT arrow or A
 *   Right → RIGHT arrow or D
 *   Jump  → Z, SPACE, or gamepad A button
 *   Pause → ESCAPE, P, or gamepad Y button
 *   Quit  → gamepad X button (pause→quit flow)
 *
 * Note: keyboard X was removed from jump bindings because the gamepad X button
 * sends Input.Keys.BUTTON_X, and having keyboard-X on jump would conflict.
 */
class KeyboardInputSource(
    private val keyLeft1:  Int = Input.Keys.LEFT,
    private val keyLeft2:  Int = Input.Keys.A,
    private val keyRight1: Int = Input.Keys.RIGHT,
    private val keyRight2: Int = Input.Keys.D,
    private val keyJump1:  Int = Input.Keys.Z,
    private val keyJump2:  Int = Input.Keys.SPACE,
    private val keyJump3:  Int = Input.Keys.BUTTON_A,   // gamepad A button
    private val keyPause:  Int = Input.Keys.ESCAPE,
    private val keyQuit:   Int = Input.Keys.BUTTON_X    // gamepad X button
) : InputProvider {

    override var lastState: InputState = InputState.EMPTY
        private set

    override fun poll(): InputState {
        val inp = Gdx.input
        lastState = InputState(
            left  = inp.isKeyPressed(keyLeft1)  || inp.isKeyPressed(keyLeft2),
            right = inp.isKeyPressed(keyRight1) || inp.isKeyPressed(keyRight2),
            jump  = inp.isKeyPressed(keyJump1)  || inp.isKeyPressed(keyJump2) || inp.isKeyPressed(keyJump3),
            pause = inp.isKeyPressed(keyPause)  || inp.isKeyPressed(Input.Keys.BUTTON_Y),
            quit  = inp.isKeyPressed(keyQuit)
        )
        return lastState
    }

    // Allow external rebinding at runtime
    fun withBindings(
        left1: Int = keyLeft1, left2: Int = keyLeft2,
        right1: Int = keyRight1, right2: Int = keyRight2,
        jump1: Int = keyJump1, jump2: Int = keyJump2, jump3: Int = keyJump3,
        pause: Int = keyPause
    ) = KeyboardInputSource(left1, left2, right1, right2, jump1, jump2, jump3, pause)
}
