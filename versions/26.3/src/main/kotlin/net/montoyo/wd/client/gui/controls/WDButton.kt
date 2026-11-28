package net.montoyo.wd.client.gui.controls

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import net.montoyo.wd.client.gui.WDTheme
import kotlin.math.min

/**
 * Modern 26.3-styled WebDisplays button with smooth hover feedback and semantic variants.
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
    enum class Variant {
        DEFAULT,
        PRIMARY,
        DANGER,
        SUBTLE
    }

    private var foregroundColor = 0xFFFFFFFF.toInt()
    private var variant = Variant.DEFAULT
    private var hoverProgress = 0.0f

    fun setHeight(height: Int) {
        this.height = height
    }

    fun setFGColor(color: Int) {
        foregroundColor = if ((color and 0xFF000000.toInt()) == 0) (0xFF000000.toInt() or color) else color
    }

    fun getFGColor(): Int = foregroundColor

    fun setVariant(variant: Variant?) {
        this.variant = variant ?: Variant.DEFAULT
    }

    fun getVariant(): Variant = variant

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val x = this.x
        val y = this.y
        val w = this.width
        val h = this.height

        val hovered = this.active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
        val target = if (hovered) 1.0f else 0.0f
        hoverProgress += (target - hoverProgress) * min(1.0f, 0.32f + partialTick * 0.12f)

        val bg: Int
        val border: Int
        val textCol: Int

        if (!this.active) {
            bg = 0x70141922
            border = 0xFF232B3A.toInt()
            textCol = WDTheme.TEXT_MUTED
        } else {
            when (variant) {
                Variant.PRIMARY -> {
                    bg = WDTheme.lerpColor(0xD91D579B.toInt(), 0xEC2771C7.toInt(), hoverProgress)
                    border = WDTheme.lerpColor(0xFF3A82D6.toInt(), 0xFF6AB8FF.toInt(), hoverProgress)
                }
                Variant.DANGER -> {
                    bg = WDTheme.lerpColor(0xD47F1D1D.toInt(), 0xEC9F2323.toInt(), hoverProgress)
                    border = WDTheme.lerpColor(0xFFB91C1C.toInt(), 0xFFF87171.toInt(), hoverProgress)
                }
                Variant.SUBTLE -> {
                    bg = WDTheme.lerpColor(0x90181E2B.toInt(), 0xCC242E42.toInt(), hoverProgress)
                    border = WDTheme.lerpColor(0xFF2A3447.toInt(), 0xFF4C6082.toInt(), hoverProgress)
                }
                Variant.DEFAULT -> {
                    bg = WDTheme.lerpColor(0xCC1E2634.toInt(), 0xE62A364B.toInt(), hoverProgress)
                    border = WDTheme.lerpColor(0xFF334057.toInt(), 0xFF5A739C.toInt(), hoverProgress)
                }
            }
            textCol = if (foregroundColor != 0xFFFFFFFF.toInt()) foregroundColor else WDTheme.TEXT_PRIMARY
        }

        graphics.fill(x, y, x + w, y + h, bg)
        WDTheme.drawBorder(graphics, x, y, w, h, border)
        if (this.active) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, if (hovered) 0x2EFFFFFF else 0x18FFFFFF)
        }

        graphics.centeredText(
            Minecraft.getInstance().font,
            message,
            x + w / 2,
            y + (h - 8) / 2,
            textCol
        )
    }
}
