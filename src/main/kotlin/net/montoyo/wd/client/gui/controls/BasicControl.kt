/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.montoyo.wd.client.gui.loading.JsonOWrapper

abstract class BasicControl : Control() {
    @JvmField
    protected var x = 0

    @JvmField
    protected var y = 0

    @JvmField
    protected var visible = true

    @JvmField
    protected var disabled = false

    override fun getX(): Int = x

    override fun getY(): Int = y

    override fun setPos(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    open fun isDisabled(): Boolean = disabled

    open fun setDisabled(disabled: Boolean) {
        this.disabled = disabled
    }

    open fun enable() {
        disabled = false
    }

    open fun disable() {
        disabled = true
    }

    open fun isVisible(): Boolean = visible

    open fun setVisible(visible: Boolean) {
        this.visible = visible
    }

    open fun show() {
        visible = true
    }

    open fun hide() {
        visible = false
    }

    override fun load(json: JsonOWrapper) {
        super.load(json)
        x = json.getInt("x", 0)
        y = json.getInt("y", 0)
        disabled = json.getBool("disabled", false)
        visible = json.getBool("visible", true)
    }
}
