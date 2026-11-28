/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.utilities.Log

class RenderRecipe : Screen(Component.nullToEmpty(null)) {
    private var x = 0
    private var y = 0
    private val recipe = arrayOfNulls<ItemStack>(3 * 3)
    private var recipeResult: ItemStack? = null
    private var recipeName: String? = null
    private val recipes = ArrayList<String>()

    override fun init() {
        x = (width - SIZE_X) / 2
        y = (height - SIZE_Y) / 2
        Log.info("Loaded %d recipes", recipes.size)
        nextRecipe()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        for (sy in 0 until 3) {
            for (sx in 0 until 3) {
                val isItem = recipe[sy * 3 + sx]
                if (isItem != null) {
                    val rx = x + 30 + sx * 18
                    val ry = y + 17 + sy * 18
                    graphics.item(isItem, rx, ry)
                    graphics.itemDecorations(font, isItem, rx, ry)
                }
            }
        }

        val res = recipeResult
        if (res != null) {
            graphics.item(res, x, y)
            graphics.itemDecorations(font, res, x, y)
        }
    }

    private fun nextRecipe() {
        if (recipes.isEmpty()) {
            minecraft.gui.setScreen(null)
        } else {
            recipeName = recipes.removeAt(0)
        }
    }

    override fun tick() {
        if (recipeName != null) {
            nextRecipe()
        }
    }

    companion object {
        private val CRAFTING_TABLE_GUI_TEXTURES = Identifier.parse("textures/gui/container/crafting_table.png")
        private const val SIZE_X = 176
        private const val SIZE_Y = 166
    }
}
