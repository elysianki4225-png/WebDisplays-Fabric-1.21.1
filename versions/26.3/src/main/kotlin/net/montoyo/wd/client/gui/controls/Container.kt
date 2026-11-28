/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.montoyo.wd.client.gui.loading.GuiLoader
import net.montoyo.wd.client.gui.loading.JsonOWrapper

abstract class Container : BasicControl() {
    @JvmField
    protected var paddingX = 0

    @JvmField
    protected var paddingY = 0

    @JvmField
    protected val childs = ArrayList<Control>()

    fun <T : Control> addControl(ctrl: T): T {
        childs.add(ctrl)
        return ctrl
    }

    override fun keyTyped(keyCode: Int, modifiers: Int): Boolean {
        var typed = false
        if (!disabled) {
            for (ctrl in childs) {
                typed = typed || ctrl.keyTyped(keyCode, modifiers)
            }
        }
        return typed
    }

    override fun keyUp(key: Int, scanCode: Int, modifiers: Int): Boolean {
        var up = false
        if (!disabled) {
            for (ctrl in childs) {
                up = up || ctrl.keyUp(key, scanCode, modifiers)
            }
        }
        return up
    }

    override fun keyDown(key: Int, scanCode: Int, modifiers: Int): Boolean {
        var down = false
        if (!disabled) {
            for (ctrl in childs) {
                down = down || ctrl.keyDown(key, scanCode, modifiers)
            }
        }
        return down
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean {
        var clicked = false
        if (!disabled) {
            val mx = mouseX - (x + paddingX)
            val my = mouseY - (y + paddingY)
            for (ctrl in childs) {
                clicked = ctrl.mouseClicked(mx, my, mouseButton)
                if (clicked) break
            }
        }
        return clicked
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, state: Int): Boolean {
        var released = false
        if (!disabled) {
            val mx = mouseX - (x + paddingX)
            val my = mouseY - (y + paddingY)
            for (ctrl in childs) {
                released = released || ctrl.mouseReleased(mx, my, state)
            }
        }
        return released
    }

    override fun mouseClickMove(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        var clicked = false
        if (!disabled) {
            val mx = mouseX - (x + paddingX)
            val my = mouseY - (y + paddingY)
            for (ctrl in childs) {
                clicked = clicked || ctrl.mouseClickMove(mx, my, button, dragX, dragY)
            }
        }
        return clicked
    }

    override fun mouseMove(mouseX: Double, mouseY: Double): Boolean {
        var clicked = false
        if (!disabled) {
            val mx = mouseX - (x + paddingX)
            val my = mouseY - (y + paddingY)
            for (ctrl in childs) {
                clicked = clicked || ctrl.mouseMove(mx, my)
            }
        }
        return clicked
    }

    override fun mouseScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        var scrolled = false
        if (!disabled) {
            val mx = mouseX - (x + paddingX)
            val my = mouseY - (y + paddingY)
            for (ctrl in childs) {
                scrolled = scrolled || ctrl.mouseScroll(mx, my, amount)
            }
        }
        return scrolled
    }

    override fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        if (visible) {
            val mx = mouseX - (x + paddingX)
            val my = mouseY - (y + paddingY)

            graphics.pose().pushMatrix()
            graphics.pose().translate((x + paddingX).toFloat(), (y + paddingY).toFloat())

            if (disabled) {
                for (ctrl in childs) {
                    ctrl.draw(graphics, -1, -1, ptt)
                }
            } else {
                for (ctrl in childs) {
                    ctrl.draw(graphics, mx, my, ptt)
                }
            }

            graphics.pose().popMatrix()
        }
    }

    override fun destroy() {
        for (ctrl in childs) {
            ctrl.destroy()
        }
    }

    override fun load(json: JsonOWrapper) {
        super.load(json)
        val objs = json.getArray("childs")
        for (i in 0 until objs.size()) {
            childs.add(GuiLoader.create(objs.getObject(i)))
        }
    }

    fun getByName(name: String): Control? {
        for (ctrl in childs) {
            if (name == ctrl.name) return ctrl
            if (ctrl is Container) {
                val ret = ctrl.getByName(name)
                if (ret != null) return ret
            }
        }
        return null
    }

    override fun unfocus() {
        for (control in childs) {
            control.unfocus()
        }
    }
}
