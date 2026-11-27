/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.montoyo.wd.utilities.Log;

import java.util.ArrayList;

//This is some experimental class used to generate images for the wiki...
public class RenderRecipe extends Screen {

    public RenderRecipe() {
        super(Component.nullToEmpty(null));
    }

    private static final Identifier CRAFTING_TABLE_GUI_TEXTURES = Identifier.parse("textures/gui/container/crafting_table.png");
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 166;
    private int x;
    private int y;
    private final ItemStack[] recipe = new ItemStack[3 * 3];
    private ItemStack recipeResult;
    private String recipeName;
    private final ArrayList<String> recipes = new ArrayList<>();

    @Override
    public void init() {
        x = (width - SIZE_X) / 2;
        y = (height - SIZE_Y) / 2;
        Log.info("Loaded %d recipes", recipes.size());
        nextRecipe();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        for(int sy = 0; sy < 3; sy++) {
            for(int sx = 0; sx < 3; sx++) {
                ItemStack is = recipe[sy * 3 + sx];

                if(is != null) {
                    int rx = this.x + 30 + sx * 18;
                    int ry = this.y + 17 + sy * 18;

                    graphics.item(is, rx, ry);
                    graphics.itemDecorations(font, is, rx, ry);
                }
            }
        }

        if(recipeResult != null) {
            graphics.item(recipeResult, x, y);
            graphics.itemDecorations(font, recipeResult, x, y);
        }
    }

    private void nextRecipe() {
        if(recipes.isEmpty())
            minecraft.gui.setScreen(null);
        else
            recipeName = recipes.remove(0);
    }

    @Override
    public void tick() {
        if(recipeName != null) {
            nextRecipe();
        }
    }

}
