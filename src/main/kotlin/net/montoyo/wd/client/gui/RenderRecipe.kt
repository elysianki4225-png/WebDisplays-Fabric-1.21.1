/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.api.EnvType.CLIENT
import net.fabricmc.api.Environment
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.ShapedRecipe
import net.montoyo.wd.utilities.Log
import java.nio.ByteBuffer
import java.util.stream.IntStream

@Environment(CLIENT)
class RenderRecipe : Screen(Component.nullToEmpty(null)) {
    private class NameRecipePair(val name: String, val recipe: ShapedRecipe)

    private var x = 0
    private var y = 0
    private var renderItem: ItemRenderer? = null
    private val recipe = arrayOfNulls<ItemStack>(3 * 3)
    private var recipeResult: ItemStack? = null
    private var recipeName: String? = null
    private val recipes = ArrayList<NameRecipePair>()
    private var buffer: ByteBuffer? = null
    private var array: IntArray? = null

    override fun init() {
        x = (width - SIZE_X) / 2
        y = (height - SIZE_Y) / 2
        renderItem = minecraft!!.itemRenderer

        for (holder in minecraft!!.level!!.recipeManager.recipes) {
            val regName = holder.id()
            if (regName.namespace == "webdisplays") {
                val value = holder.value()
                if (value is ShapedRecipe) {
                    recipes.add(NameRecipePair(regName.path, value))
                } else {
                    Log.warning("Found non-shaped recipe %s", regName.toString())
                }
            }
        }

        Log.info("Loaded %d recipes", recipes.size)
        nextRecipe()
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(context, mouseX, mouseY, partialTick)

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
        RenderSystem.setShaderTexture(0, CRAFTING_TABLE_GUI_TEXTURES)

        Lighting.setupForFlatItems()

        for (sy in 0 until 3) {
            for (sx in 0 until 3) {
                val isItem = recipe[sy * 3 + sx]
                if (isItem != null) {
                    val rx = x + 30 + sx * 18
                    val ry = y + 17 + sy * 18
                    context.renderItem(isItem, rx, ry)
                    context.renderItemDecorations(font, isItem, rx, ry)
                }
            }
        }

        val res = recipeResult
        if (res != null) {
            context.renderItem(res, x, y)
            context.renderItemDecorations(font, res, x, y)
        }

        Lighting.setupFor3DItems()
    }

    private fun setRecipe(recipe: ShapedRecipe) {
        IntStream.range(0, this.recipe.size).forEach { i -> this.recipe[i] = null }
        val ingredients = recipe.ingredients
        var pos = 0

        for (y in 0 until recipe.height) {
            for (x in 0 until recipe.width) {
                val stacks = ingredients[pos++].items
                if (stacks.isNotEmpty()) {
                    this.recipe[y * 3 + x] = stacks[0]
                }
            }
        }
    }

    private fun nextRecipe() {
        if (recipes.isEmpty()) {
            minecraft!!.setScreen(null)
        } else {
            val pair = recipes.removeAt(0)
            setRecipe(pair.recipe)
            recipeName = pair.name
        }
    }

    private fun takeScreenshot() {
    }

    override fun tick() {
        if (recipeName != null) {
            try {
                takeScreenshot()
                nextRecipe()
            } catch (t: Throwable) {
                t.printStackTrace()
                minecraft!!.setScreen(null)
            }
        }
    }

    companion object {
        private val CRAFTING_TABLE_GUI_TEXTURES = ResourceLocation.parse("textures/gui/container/crafting_table.png")
        private const val SIZE_X = 176
        private const val SIZE_Y = 166
    }
}
