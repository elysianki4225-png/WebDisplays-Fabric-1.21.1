/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

abstract class Event<T : Control> {
    @JvmField
    protected var source: T? = null

    fun getSource(): T? = source
}
