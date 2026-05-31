package com.nplus.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.nplus.NPlusGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("n")
        setWindowedMode(2560, 1440)
        setForegroundFPS(144)
        useVsync(true)
        setResizable(false)
    }
    Lwjgl3Application(NPlusGame(), config)
}
