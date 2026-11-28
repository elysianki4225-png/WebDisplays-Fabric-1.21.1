/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import net.minecraft.advancements.triggers.PlayerTrigger
import net.minecraft.resources.Identifier

class Criterion(name: String) : PlayerTrigger() {
    private val id: Identifier = Identifier.fromNamespaceAndPath("webdisplays", name)

    fun getId(): Identifier = id
}
