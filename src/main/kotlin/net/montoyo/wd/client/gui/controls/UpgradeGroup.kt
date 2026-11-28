/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.client.gui.loading.JsonOWrapper

class UpgradeGroup : BasicControl() {
    private var width = 0
    private var height = 0
    private var upgrades: ArrayList<ItemStack>? = null
    private var overStack: ItemStack? = null
    private var clickStack: ItemStack? = null
    private val renderItem: ItemRenderer = Minecraft.getInstance().itemRenderer

    init {
        parent!!.requirePostDraw(this)
    }

    override fun draw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        val upg = upgrades
        if (upg != null) {
            var x = this.x
            for (isItem in upg) {
                if (isItem === overStack && !disabled) {
                    fillRect(poseStack.bufferSource(), x, y.toDouble(), 16, 16, 0x80FF0000.toInt())
                }
                poseStack.renderItem(isItem, x, y)
                poseStack.renderItemDecorations(font, isItem, x, y)
                x += 18
            }
        }
    }

    override fun postDraw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        val os = overStack
        if (os != null) {
            parent!!.drawItemStackTooltip(poseStack, os, mouseX, mouseY)
        }
    }

    override fun getWidth(): Int = width

    override fun getHeight(): Int = height

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
        width = json.getInt("width", 0)
        height = json.getInt("height", 16)
    }

    override fun mouseMove(mouseX: Double, mouseY: Double): Boolean {
        val upg = upgrades
        if (upg != null) {
            overStack = null
            if (mouseY >= y && mouseY <= y + 16 && mouseX >= x) {
                val mx = mouseX - x
                val sel = (mx / 18).toInt()
                if (sel < upg.size && mx % 18 <= 16) {
                    overStack = upg[sel]
                }
                return true
            }
        }
        return false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean {
        if (mouseButton == 0) {
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseX <= y + height) {
                clickStack = overStack
                return true
            }
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, state: Int): Boolean {
        if (state == 0 && clickStack != null) {
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
