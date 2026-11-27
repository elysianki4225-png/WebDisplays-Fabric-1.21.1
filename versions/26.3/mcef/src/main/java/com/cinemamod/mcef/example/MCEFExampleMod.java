package com.cinemamod.mcef.example;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MCEFExampleMod {
    private static final Minecraft minecraft = Minecraft.getInstance();

    public static final KeyMapping KEY_MAPPING = new KeyMapping(
            "Open Browser", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10, KeyMapping.Category.MISC
    );

    public MCEFExampleMod() {
        ClientTickEvents.START_CLIENT_TICK.register((client) -> onTick());
    }

    public void onTick() {
        if (KEY_MAPPING.isDown() && !(minecraft.gui.screen() instanceof ExampleScreen)) {
            minecraft.gui.setScreen(new ExampleScreen(Component.literal("Example Screen")));
        }
    }
}
