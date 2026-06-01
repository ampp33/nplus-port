package com.nplus

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx

/**
 * Platform identity and UI label strings for control hints.
 *
 * Retroid mode is active when running on Android, OR when the desktop env var
 * NPLUS_PLATFORM=retroid is set (useful for testing the layout on desktop).
 * System.getenv() returns null inside an Android APK, so auto-detection via
 * Gdx.app.type is the primary mechanism on device.
 *
 * PC:      confirm=Enter  back=Esc  quit=Q    pause=P
 * Retroid: confirm=A      back=X    quit=X    pause=Y
 */
object Platform {
    // Evaluated on each access so Gdx.app is guaranteed to be initialised.
    val isRetroid: Boolean
        get() = Gdx.app.type == Application.ApplicationType.Android ||
                System.getenv("NPLUS_PLATFORM")?.lowercase() == "retroid"

    val confirm: String get() = if (isRetroid) "A"   else "Enter"
    val back:    String get() = if (isRetroid) "X"   else "Esc"
    val quit:    String get() = if (isRetroid) "X"   else "Q"
    val pause:   String get() = if (isRetroid) "Y"   else "P"
}
