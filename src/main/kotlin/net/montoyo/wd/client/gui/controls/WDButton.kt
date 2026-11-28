package net.montoyo.wd.client.gui.controls

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * Vanilla Button without Forge's extra accessors: adjustable height and text colour.
 */
@Environment(EnvType.CLIENT)
class WDButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    onPress: OnPress
) : Button(x, y, width, height, message, onPress, DEFAULT_NARRATION) {
    private var foregroundColor = 0xFFFFFF

    fun setHeight(height: Int) {
        this.height = height
    }

    fun setFGColor(color: Int) {
        foregroundColor = color
    }

    fun getFGColor(): Int = foregroundColor

    public override fun renderString(guiGraphics: GuiGraphics, font: Font, color: Int) {
        super.renderString(guiGraphics, font, foregroundColor)
    }
}
