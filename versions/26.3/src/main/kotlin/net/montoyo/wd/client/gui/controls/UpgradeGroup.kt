/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.client.gui.WDTheme
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import kotlin.math.max

class UpgradeGroup : BasicControl() {
    private var width = 0
    private var height = 0
    private var slotCount = 4
    private var upgrades: ArrayList<ItemStack>? = null
    private var overStack: ItemStack? = null
    private var clickStack: ItemStack? = null

    init {
        parent!!.requirePostDraw(this)
    }

    override fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        val upg = upgrades
        val slots = max(slotCount, upg?.size ?: 0)
        var x = this.x

        for (i in 0 until slots) {
            val isItem = if (upg != null && i < upg.size) upg[i] else null
            val hovered = isItem != null && isItem === overStack && !disabled

            val bg = if (hovered) WDTheme.DANGER_SOFT else WDTheme.INSET_BG
            val border = if (hovered) WDTheme.DANGER else WDTheme.INSET_BORDER
            graphics.fill(x, y, x + 18, y + 18, bg)
            WDTheme.drawBorder(graphics, x, y, 18, 18, border)

            if (isItem != null) {
                graphics.item(isItem, x + 1, y + 1)
                graphics.itemDecorations(font, isItem, x + 1, y + 1)
            }

            x += 20
        }
    }

    override fun postDraw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        val os = overStack
        if (os != null) {
            parent!!.drawItemStackTooltip(graphics, os, mouseX, mouseY)
        }
    }

    override fun getWidth(): Int {
        val slots = max(slotCount, upgrades?.size ?: 0)
        return max(width, slots * 20 - 2)
    }

    override fun getHeight(): Int = max(height, 18)

    fun setWidth(w: Int) {
        width = w
    }

    fun setHeight(h: Int) {
        height = h
    }

    fun setUpgrades(upgrades: ArrayList<ItemStack>?) {
        this.upgrades = upgrades
    }

    fun getUpgrades(): ArrayList<ItemStack>? = upgrades

    override fun load(json: JsonOWrapper) {
        super.load(json)
        slotCount = json.getInt("slots", 4)
        width = json.getInt("width", slotCount * 20 - 2)
        height = json.getInt("height", 18)
    }

    override fun mouseMove(mouseX: Double, mouseY: Double): Boolean {
        val upg = upgrades
        if (upg != null) {
            overStack = null
            if (mouseY >= y && mouseY <= y + 18 && mouseX >= x) {
                val mx = mouseX - x
                val sel = (mx / 20).toInt()
                if (sel < upg.size && mx % 20 <= 18) {
                    overStack = upg[sel]
                }
                return true
            }
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean {
        if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
            if (mouseX >= x && mouseX <= x + getWidth() && mouseY >= y && mouseY <= y + getHeight()) {
                clickStack = overStack
                return true
            }
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, state: Int): Boolean {
        if (state == InputConstants.MOUSE_BUTTON_LEFT && clickStack != null) {
            if (clickStack === overStack && !disabled && upgrades!!.contains(clickStack)) {
                parent!!.actionPerformed(ClickEvent(this))
            }
            clickStack = null
            return true
        }
        return false
    }

    fun getMouseOverUpgrade(): ItemStack? = overStack

    class ClickEvent(src: UpgradeGroup) : Event<UpgradeGroup>() {
        private val clickStack: ItemStack?

        init {
            source = src
            clickStack = src.clickStack
        }

        fun getMouseOverStack(): ItemStack? = clickStack
    }
}
