/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemStack

interface IItemRenderer {
    fun render(
        pose: PoseStack,
        stack: ItemStack,
        handSideSign: Float,
        swingProgress: Float,
        equipProgress: Float,
        multiBufferSource: MultiBufferSource,
        packedLight: Int
    ): Boolean
}
