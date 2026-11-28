/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager

object Log {
    @JvmStatic
    fun info(what: String, vararg data: Any?) {
        LogManager.getLogger("WebDisplays").log(Level.INFO, String.format(what, *data))
    }

    @JvmStatic
    fun warning(what: String, vararg data: Any?) {
        LogManager.getLogger("WebDisplays").log(Level.WARN, String.format(what, *data))
    }

    @JvmStatic
    fun error(what: String, vararg data: Any?) {
        LogManager.getLogger("WebDisplays").log(Level.ERROR, String.format(what, *data))
    }

    @JvmStatic
    fun infoEx(what: String, e: Throwable?, vararg data: Any?) {
        LogManager.getLogger("WebDisplays").log(Level.INFO, String.format(what, *data), e)
    }

    @JvmStatic
    fun warningEx(what: String, e: Throwable?, vararg data: Any?) {
        LogManager.getLogger("WebDisplays").log(Level.WARN, String.format(what, *data), e)
    }

    @JvmStatic
    fun errorEx(what: String, e: Throwable?, vararg data: Any?) {
        LogManager.getLogger("WebDisplays").log(Level.ERROR, String.format(what, *data), e)
    }
}
