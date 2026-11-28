/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

enum class AdvancementIcon(private val iconName: String) {
    WEB_DISPLAYS("wd"),
    BROKEN_PAD("brokenpad"),
    PIGEON("pigeon");

    override fun toString(): String = iconName
}
