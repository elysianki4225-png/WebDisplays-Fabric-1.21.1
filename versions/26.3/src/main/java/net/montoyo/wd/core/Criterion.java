/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core;

import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class Criterion extends PlayerTrigger {

    private final Identifier id;

    public Criterion(@NotNull String name) {
        this.id = Identifier.fromNamespaceAndPath("webdisplays", name);
    }

    @NotNull
    public Identifier getId() {
        return id;
    }
}
