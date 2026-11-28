package com.terrapaver.item

import com.terrapaver.client.TerraPaverClientHooks
import com.terrapaver.engine.ServerPavingManager
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
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

class ItemPavingSurveyor(properties: Properties) : Item(properties.stacksTo(1)) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.PASS
        if (player.isShiftKeyDown) {
            if (level.isClientSide) {
                TerraPaverClientHooks.openSettingsScreen()
            }
            return InteractionResult.SUCCESS
        }

        if (!level.isClientSide && player is ServerPlayer) {
            ServerPavingManager.handleSurveyorClick(player, context.clickedPos)
        }
        player.swing(context.hand)
        return InteractionResult.SUCCESS
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown) {
            if (level.isClientSide) {
                TerraPaverClientHooks.openSettingsScreen()
            }
            return InteractionResultHolder.success(stack)
        }

        val start = player.getEyePosition(1.0f)
        val look = player.lookAngle
        val end = start.add(look.x * 64.0, look.y * 64.0, look.z * 64.0)
        val hit = level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))

        if (!level.isClientSide && player is ServerPlayer) {
            if (hit.type == HitResult.Type.BLOCK) {
                ServerPavingManager.handleSurveyorClick(player, hit.blockPos)
            } else {
                ServerPavingManager.handleSurveyorAirClick(player)
            }
        }
        player.swing(hand)
        return InteractionResultHolder.success(stack)
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        tooltipComponents.add(Component.translatable("item.terrapaver.paving_surveyor.tip1").withStyle(ChatFormatting.AQUA))
        tooltipComponents.add(Component.translatable("item.terrapaver.paving_surveyor.tip2").withStyle(ChatFormatting.GRAY))
        tooltipComponents.add(Component.translatable("item.terrapaver.paving_surveyor.tip3").withStyle(ChatFormatting.GOLD))
    }
}
