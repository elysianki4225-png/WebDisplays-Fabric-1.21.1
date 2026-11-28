package net.montoyo.wd.client.gui.controls

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * Vanilla EditBox without Forge's setHeight.
 */
@Environment(EnvType.CLIENT)
class WDEditBox(
    font: Font,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component
) : EditBox(font, x, y, width, height, message) {
    override fun setHeight(height: Int) {
        this.height = height
    }
}
