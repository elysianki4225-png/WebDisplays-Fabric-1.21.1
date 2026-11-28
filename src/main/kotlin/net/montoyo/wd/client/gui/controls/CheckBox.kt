/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import com.google.common.collect.Lists
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.montoyo.wd.client.gui.loading.JsonOWrapper

class CheckBox : BasicControl {
    class CheckedEvent(cb: CheckBox) : Event<CheckBox>() {
        private val checked: Boolean

        init {
            source = cb
            checked = cb.checked
        }

        fun isChecked(): Boolean = checked
    }

    private var label: String
    private var labelW = 0
    private var checked = false
    private var tooltip: kotlin.collections.List<String>? = null

    constructor() {
        label = ""
    }

    constructor(x: Int, y: Int, label: String) {
        this.label = label
        labelW = font.width(label)
        checked = false
        this.x = x
        this.y = y
    }

    constructor(x: Int, y: Int, label: String, valChecked: Boolean) {
        this.label = label
        labelW = font.width(label)
        checked = valChecked
        this.x = x
        this.y = y
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseButton: Int): Boolean {
        if (mouseButton == 0 && !disabled) {
            if (mouseX >= x && mouseX <= x + WIDTH + 2 + labelW && mouseY >= y && mouseY < y + HEIGHT) {
                checked = !checked
                mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f))
                parent!!.actionPerformed(CheckedEvent(this))
                return true
            }
        }
        return false
    }

    override fun draw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        if (visible) {
            poseStack.pose().pushPose()
            RenderSystem.enableBlend()
            poseStack.blit(
                if (checked) texChecked else texUnchecked, x, y, 0, 0f, 0f, WIDTH, HEIGHT, WIDTH, HEIGHT
            )
            RenderSystem.disableBlend()
            poseStack.pose().popPose()

            val inside = !disabled && mouseX >= x && mouseX <= x + WIDTH + 2 + labelW && mouseY >= y && mouseY < y + HEIGHT
            poseStack.drawString(Minecraft.getInstance().font, label, x + WIDTH + 2, y + 4, if (inside) 0xFF0080FF.toInt() else COLOR_WHITE, false)
        }
    }

    fun setLabel(label: String) {
        this.label = label
        labelW = font.width(label)
    }

    fun getLabel(): String = label

    fun isChecked(): Boolean = checked

    fun setChecked(checked: Boolean) {
        this.checked = checked
    }

    override fun getWidth(): Int = WIDTH + 2 + labelW

    override fun getHeight(): Int = HEIGHT

    override fun load(json: JsonOWrapper) {
        super.load(json)
        label = tr(json.getString("label", "") ?: "")
        labelW = font.width(label)
        checked = json.getBool("checked", false)

        val tt = tr(json.getString("tooltip", "") ?: "")
        if (tt.isNotEmpty()) {
            tooltip = Lists.newArrayList(*tt.split("\\\\n").toTypedArray())
            parent!!.requirePostDraw(this)
        }
    }

    override fun postDraw(poseStack: GuiGraphics, mouseX: Int, mouseY: Int, ptt: Float) {
        val tt = tooltip
        if (tt != null && !disabled && mouseX >= x && mouseX <= x + WIDTH + 2 + labelW && mouseY >= y && mouseY < y + HEIGHT) {
            parent!!.drawTooltip(poseStack, tt, mouseX, mouseY)
        }
    }

    companion object {
        private val texUnchecked = ResourceLocation.fromNamespaceAndPath("webdisplays", "textures/gui/checkbox.png")
        private val texChecked = ResourceLocation.fromNamespaceAndPath("webdisplays", "textures/gui/checkbox_checked.png")
        const val WIDTH = 16
        const val HEIGHT = 16
    }
}
