package com.terrapaver.item

import com.terrapaver.client.TerraPaverClientHooks
import com.terrapaver.config.BiomeConfig
import com.terrapaver.config.BiomeShape
import com.terrapaver.network.TerraPaverNet
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult

class ItemBiomeEditor(properties: Properties) : Item(properties.stacksTo(1)) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.PASS
        if (player.isShiftKeyDown) {
            if (level.isClientSide) {
                TerraPaverClientHooks.openBiomeScreen()
            }
            return InteractionResult.SUCCESS
        }

        if (level.isClientSide) {
            dispatchClientClick(context.clickedPos)
        }
        player.swing(context.hand)
        return InteractionResult.SUCCESS
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown) {
            if (level.isClientSide) {
                TerraPaverClientHooks.openBiomeScreen()
            }
            return InteractionResultHolder.success(stack)
        }

        val start = player.getEyePosition(1.0f)
        val look = player.lookAngle
        val end = start.add(look.x * 96.0, look.y * 96.0, look.z * 96.0)
        val hit = level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))

        if (level.isClientSide) {
            val pos = if (hit.type == HitResult.Type.BLOCK) hit.blockPos else player.blockPosition()
            dispatchClientClick(pos)
        }
        player.swing(hand)
        return InteractionResultHolder.success(stack)
    }

    private fun dispatchClientClick(pos: BlockPos) {
        val cfg = BiomeConfig.clientConfig
        if (cfg.shape == BiomeShape.BOX_2POINT) {
            TerraPaverNet.sendToServer(TerraPaverNet.C2SSelectBiomePoint(pos, cfg.toJson()))
        } else {
            TerraPaverNet.sendToServer(TerraPaverNet.C2SApplyBiomeBrush(pos, cfg.toJson()))
        }
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        tooltipComponents.add(Component.translatable("item.terrapaver.biome_editor.tip1").withStyle(ChatFormatting.AQUA))
        tooltipComponents.add(Component.translatable("item.terrapaver.biome_editor.tip2").withStyle(ChatFormatting.GRAY))
        tooltipComponents.add(Component.translatable("item.terrapaver.biome_editor.tip3").withStyle(ChatFormatting.GOLD))
    }
}
