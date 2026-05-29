package com.nplus.screens

/** In-game play states managed by [GameScreen] and consumed by [GameRenderer]. */
enum class PlayState {
    PRE_GAME,   // Level loaded, timer frozen; waiting for player to press Jump to begin
    GAME,       // Simulation running, timer counting down
    PAUSED,     // Simulation frozen; press Jump/ESC to resume or Q to quit
    POST_GAME,  // Ninja dead, ragdoll simulating; press Jump to retry or ESC to quit
    POST_WIN    // Ninja celebrating; sim still ticking; auto-advances after countdown
}
