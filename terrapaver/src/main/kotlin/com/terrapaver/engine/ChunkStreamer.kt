package com.terrapaver.engine

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap

data class ColumnSample(
    val groundY: Int,
    val waterTopY: Int,
    val hasLiquid: Boolean,
    val topSolidY: Int
)

class ChunkStreamer(private val level: ServerLevel, private val allowUnloaded: Boolean) {
    private val forcedChunks = LinkedHashSet<Long>()
    private val mutablePos = BlockPos.MutableBlockPos()

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    fun ensureLoaded(blockX: Int, blockZ: Int): Boolean {
        val cx = blockX shr 4
        val cz = blockZ shr 4
        if (!allowUnloaded && !level.hasChunk(cx, cz)) {
            return false
        }
        val key = chunkKey(cx, cz)
        if (!forcedChunks.contains(key)) {
            level.chunkSource.getChunk(cx, cz, ChunkStatus.FULL, true)
            level.setChunkForced(cx, cz, true)
            forcedChunks.add(key)
        }
        return true
    }

    fun ensureAreaLoaded(centerX: Int, centerZ: Int, radiusBlocks: Int) {
        val minCx = (centerX - radiusBlocks) shr 4
        val maxCx = (centerX + radiusBlocks) shr 4
        val minCz = (centerZ - radiusBlocks) shr 4
        val maxCz = (centerZ + radiusBlocks) shr 4
        for (cx in minCx..maxCx) {
            for (cz in minCz..maxCz) {
                val key = chunkKey(cx, cz)
                if (!forcedChunks.contains(key)) {
                    if (!allowUnloaded && !level.hasChunk(cx, cz)) continue
                    level.chunkSource.getChunk(cx, cz, ChunkStatus.FULL, true)
                    level.setChunkForced(cx, cz, true)
                    forcedChunks.add(key)
                }
            }
        }
        trimDistantChunks(centerX shr 4, centerZ shr 4)
    }

    private fun trimDistantChunks(centerCx: Int, centerCz: Int) {
        if (forcedChunks.size <= 48) return
        val iter = forcedChunks.iterator()
        while (iter.hasNext() && forcedChunks.size > 32) {
            val key = iter.next()
            val cx = (key shr 32).toInt()
            val cz = key.toInt()
            if (kotlin.math.abs(cx - centerCx) > 4 || kotlin.math.abs(cz - centerCz) > 4) {
                level.setChunkForced(cx, cz, false)
                iter.remove()
            }
        }
    }

    fun releaseAll() {
        for (key in forcedChunks) {
            val cx = (key shr 32).toInt()
            val cz = key.toInt()
            level.setChunkForced(cx, cz, false)
        }
        forcedChunks.clear()
    }

    fun sampleColumn(x: Int, z: Int): ColumnSample {
        ensureLoaded(x, z)
        val topH = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
        val oceanFloor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z)
        val minY = level.minBuildHeight

        var waterTopY = minY
        var hasLiquid = false
        var groundY = (oceanFloor - 1).coerceAtLeast(minY)

        val scanStart = (topH + 2).coerceAtMost(level.maxBuildHeight - 1)
        val scanEnd = (minOf(groundY, topH) - 6).coerceAtLeast(minY)

        for (y in scanStart downTo scanEnd) {
            mutablePos.set(x, y, z)
            val state = level.getBlockState(mutablePos)
            if (!state.fluidState.isEmpty) {
                if (!hasLiquid) {
                    hasLiquid = true
                    waterTopY = y
                }
            } else if (isTerrainSolid(state)) {
                groundY = y
                break
            }
        }

        // Fallback if still in deep overhang or foliage
        if (groundY <= minY) {
            for (y in scanStart downTo minY) {
                mutablePos.set(x, y, z)
                val state = level.getBlockState(mutablePos)
                if (isTerrainSolid(state)) {
                    groundY = y
                    break
                }
            }
        }

        val topSolidY = maxOf(groundY, topH - 1)
        return ColumnSample(
            groundY = groundY,
            waterTopY = waterTopY,
            hasLiquid = hasLiquid && waterTopY >= groundY,
            topSolidY = topSolidY
        )
    }

    companion object {
        fun isTerrainSolid(state: BlockState): Boolean {
            if (state.isAir || !state.fluidState.isEmpty) return false
            if (state.isSolidRender(EmptyGetter, BlockPos.ZERO)) return true
            val b = state.block
            if (b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.SAND || b == Blocks.RED_SAND ||
                b == Blocks.GRAVEL || b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.NETHERRACK ||
                b == Blocks.END_STONE || b == Blocks.SNOW_BLOCK || b == Blocks.ICE || b == Blocks.PACKED_ICE ||
                b == Blocks.BLUE_ICE || b == Blocks.TERRACOTTA || b == Blocks.CLAY || b == Blocks.MUD
            ) {
                return true
            }
            @Suppress("DEPRECATION")
            return state.blocksMotion() && state.fluidState.`is`(FluidTags.WATER).not() && state.fluidState.`is`(FluidTags.LAVA).not()
        }

        private object EmptyGetter : net.minecraft.world.level.BlockGetter {
            override fun getBlockEntity(pos: BlockPos) = null
            override fun getBlockState(pos: BlockPos): BlockState = Blocks.AIR.defaultBlockState()
            override fun getFluidState(pos: BlockPos) = Blocks.AIR.defaultBlockState().fluidState
            override fun getHeight(): Int = 384
            override fun getMinBuildHeight(): Int = -64
        }
    }
}
