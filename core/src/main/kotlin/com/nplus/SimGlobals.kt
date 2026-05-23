package com.nplus

/** Port of AS3 sim_globals. All constants copied verbatim. */
object SimGlobals {

    /** Physics tick rate (steps per second). Original N+ App_MultiPurpose sets sim_rate=60. */
    const val SIM_RATE: Float = 60f

    const val DEATHTYPE_SUICIDE   = 0
    const val DEATHTYPE_FALL      = 1
    const val DEATHTYPE_CRUSH     = 2
    const val DEATHTYPE_TIME      = 3
    const val DEATHTYPE_EXPLOSIVE = 4
    const val DEATHTYPE_LASER     = 5
    const val DEATHTYPE_ELECTRIC  = 6
    const val DEATHTYPE_BULLET    = 7

    const val ENEMYTYPE_SUICIDE   = 0
    const val ENEMYTYPE_FALL      = 1
    const val ENEMYTYPE_CRUSH     = 2
    const val ENEMYTYPE_TIME      = 3
    const val ENEMYTYPE_ZAP       = 4
    const val ENEMYTYPE_CHAINGUN  = 5
    const val ENEMYTYPE_LASER     = 6
    const val ENEMYTYPE_TURRET    = 7
    const val ENEMYTYPE_ROCKET    = 8
    const val ENEMYTYPE_FLOORGUARD = 9
    const val ENEMYTYPE_THWOMP    = 10
    const val ENEMYTYPE_MINE      = 11
    const val ENEMYTYPE_DEBUG     = 12

    val ETYPE_TO_STRING: Array<String> = arrayOf(
        "suicide", "falling", "crushed", "out of time",
        "zap", "chaingun", "laser", "turret",
        "rocket", "floorguard", "thwomp", "mine", "debug"
    )

    val ETYPE_TO_DTYPE: IntArray = intArrayOf(
        DEATHTYPE_EXPLOSIVE,  // SUICIDE
        DEATHTYPE_FALL,       // FALL
        DEATHTYPE_CRUSH,      // CRUSH
        DEATHTYPE_EXPLOSIVE,  // TIME
        DEATHTYPE_ELECTRIC,   // ZAP
        DEATHTYPE_BULLET,     // CHAINGUN
        DEATHTYPE_LASER,      // LASER
        DEATHTYPE_BULLET,     // TURRET
        DEATHTYPE_EXPLOSIVE,  // ROCKET
        DEATHTYPE_ELECTRIC,   // FLOORGUARD
        DEATHTYPE_ELECTRIC,   // THWOMP
        DEATHTYPE_EXPLOSIVE,  // MINE
        DEATHTYPE_BULLET,     // DEBUG
    )
}
