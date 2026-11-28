package net.montoyo.wd.core

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LightBlock
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.item.ItemMinePad2
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DroppedPadLightManager {
    private const val LIGHT_LEVEL = 5

    private data class TrackedLight(
        val levelKey: ResourceKey<Level>,
        val pos: BlockPos
    )

    private val activeLights = ConcurrentHashMap<UUID, TrackedLight>()

    private fun isOurLight(state: BlockState): Boolean {
        return state.`is`(Blocks.LIGHT) && state.getValue(LightBlock.LEVEL) == LIGHT_LEVEL
    }

    private fun isPadLit(stack: ItemStack): Boolean {
        if (stack.isEmpty || stack.item !is ItemMinePad2) return false
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        return data.copyTag().contains("PadID")
    }

    @JvmStatic
    fun updateDroppedPad(entity: ItemEntity, lit: Boolean) {
        val level = entity.level() as? ServerLevel ?: return
        val id = entity.uuid

        if (!lit || !entity.isAlive || entity.isRemoved) {
            removeLightFor(level.server, id)
            return
        }

        val targetPos = findLightPos(level, entity.blockPosition())
        val prev = activeLights[id]

        if (prev != null && (prev.levelKey != level.dimension() || prev.pos != targetPos)) {
            val oldLevel = level.server.getLevel(prev.levelKey)
            if (oldLevel != null) {
                val oldState = oldLevel.getBlockState(prev.pos)
                if (isOurLight(oldState)) {
                    oldLevel.removeBlock(prev.pos, false)
                }
            }
            activeLights.remove(id)
        }

        if (targetPos != null) {
            val curState = level.getBlockState(targetPos)
            if (!isOurLight(curState)) {
                level.setBlock(targetPos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL), 3)
            }
            activeLights[id] = TrackedLight(level.dimension(), targetPos)
        }
    }

    private fun findLightPos(level: ServerLevel, basePos: BlockPos): BlockPos? {
        val baseState = level.getBlockState(basePos)
        if (baseState.isAir || isOurLight(baseState)) {
            return basePos
        }
        val above = basePos.above()
        val aboveState = level.getBlockState(above)
        if (aboveState.isAir || isOurLight(aboveState)) {
            return above
        }
        return null
    }

    private fun removeLightFor(server: MinecraftServer, entityId: UUID) {
        val prev = activeLights.remove(entityId) ?: return
        val oldLevel = server.getLevel(prev.levelKey) ?: return
        val state = oldLevel.getBlockState(prev.pos)
        if (isOurLight(state)) {
            oldLevel.removeBlock(prev.pos, false)
        }
    }

    @JvmStatic
    fun tickServer(server: MinecraftServer) {
        if (activeLights.isEmpty()) return
        val it = activeLights.entries.iterator()
        while (it.hasNext()) {
            val (entityId, tracked) = it.next()
            val level = server.getLevel(tracked.levelKey)
            val entity = level?.getEntity(entityId)
            val valid = entity is ItemEntity && entity.isAlive && !entity.isRemoved && isPadLit(entity.item)
            if (!valid) {
                if (level != null) {
                    val state = level.getBlockState(tracked.pos)
                    if (isOurLight(state)) {
                        level.removeBlock(tracked.pos, false)
                    }
                }
                it.remove()
            }
        }
    }

    @JvmStatic
    fun clearAll(server: MinecraftServer) {
        if (activeLights.isEmpty()) return
        val it = activeLights.entries.iterator()
        while (it.hasNext()) {
            val (_, tracked) = it.next()
            val level = server.getLevel(tracked.levelKey)
            if (level != null) {
                val state = level.getBlockState(tracked.pos)
                if (isOurLight(state)) {
                    level.removeBlock(tracked.pos, false)
                }
            }
            it.remove()
        }
    }
}
