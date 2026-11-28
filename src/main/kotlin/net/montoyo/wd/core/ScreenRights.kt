/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

object ScreenRights {
    const val CHANGE_URL = 1 // Change URL AND run JavaScript

    @Deprecated("use INTERACT instead", level = DeprecationLevel.WARNING)
    const val CLICK = 2 // Click AND type

    const val INTERACT = 2 // Click AND type
    const val MANAGE_FRIEND_LIST = 4
    const val MANAGE_OTHER_RIGHTS = 8
    const val MANAGE_UPGRADES = 16 // Manage upgrades AND peripherals AND autoVolume

    @Deprecated("use MODIFY_SCREEN instead", level = DeprecationLevel.WARNING)
    const val CHANGE_RESOLUTION = 32 // Change resolution AND rotation

    const val MODIFY_SCREEN = 32 // Change resolution AND rotation

    const val NONE = 0
    const val ALL = 0xFF
    const val DEFAULTS = CHANGE_URL or INTERACT or MANAGE_UPGRADES or MODIFY_SCREEN
}
