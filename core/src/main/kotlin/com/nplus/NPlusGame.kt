package com.nplus

import com.badlogic.gdx.Game
import com.nplus.screens.AppStateManager

class NPlusGame : Game() {

    /** Shared state machine — injected into screens via their constructors. */
    lateinit var appState: AppStateManager
        private set

    override fun create() {
        appState = AppStateManager(this)
        appState.initialize()   // loads 570 KB level binary (~50 ms)
        appState.goToMenu()
    }

    override fun dispose() {
        screen?.dispose()
        appState.dispose()
    }
}
