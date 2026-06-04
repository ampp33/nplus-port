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
        setWindowIcon("icon16.png", "icon32.png", "icon128.png")
        setBackBufferConfig(8, 8, 8, 8, 16, 0, 4)  // 4× MSAA — smooths all geometry edges
    }
    Lwjgl3Application(NPlusGame(), config)
}
