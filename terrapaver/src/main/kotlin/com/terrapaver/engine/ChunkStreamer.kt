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
    val topSolidY: Int,
    val surfaceBlock: BlockState,
    val hasSnowCover: Boolean
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
        var hasSnowCover = false
        var groundY = (oceanFloor - 1).coerceAtLeast(minY)
        var surfaceBlock: BlockState = Blocks.GRASS_BLOCK.defaultBlockState()

        val scanStart = (topH + 2).coerceAtMost(level.maxBuildHeight - 1)
        val scanEnd = (minOf(groundY, topH) - 8).coerceAtLeast(minY)

        for (y in scanStart downTo scanEnd) {
            mutablePos.set(x, y, z)
            val state = level.getBlockState(mutablePos)
            if (state.`is`(Blocks.SNOW)) {
                hasSnowCover = true
                continue
            }
            if (!state.fluidState.isEmpty || isFrozenWaterSurface(state, x, y, z)) {
                if (!hasLiquid) {
                    hasLiquid = true
                    waterTopY = y
                }
            } else if (isTerrainSolid(state)) {
                groundY = y
                surfaceBlock = normalizeNaturalSurface(state)
                break
            }
        }

        if (groundY <= minY) {
            for (y in scanStart downTo minY) {
                mutablePos.set(x, y, z)
                val state = level.getBlockState(mutablePos)
                if (isTerrainSolid(state) && !isFrozenWaterSurface(state, x, y, z)) {
                    groundY = y
                    surfaceBlock = normalizeNaturalSurface(state)
                    break
                }
            }
        }

        val topSolidY = maxOf(groundY, topH - 1)
        return ColumnSample(
            groundY = groundY,
            waterTopY = waterTopY,
            hasLiquid = hasLiquid && waterTopY >= groundY,
            topSolidY = topSolidY,
            surfaceBlock = surfaceBlock,
            hasSnowCover = hasSnowCover
        )
    }

    private fun isFrozenWaterSurface(state: BlockState, x: Int, y: Int, z: Int): Boolean {
        if (!state.`is`(Blocks.ICE) && !state.`is`(Blocks.FROSTED_ICE) && !state.`is`(Blocks.PACKED_ICE)) {
            return false
        }
        // Check if water sits beneath the ice sheet (frozen river/lake!)
        for (dy in 1..3) {
            if (y - dy < level.minBuildHeight) break
            mutablePos.set(x, y - dy, z)
            val below = level.getBlockState(mutablePos)
            if (!below.fluidState.isEmpty) return true
        }
        return false
    }

    private fun normalizeNaturalSurface(state: BlockState): BlockState {
        val b = state.block
        return when {
            b == Blocks.DIRT || b == Blocks.COARSE_DIRT || b == Blocks.PODZOL || b == Blocks.GRASS_BLOCK ->
                Blocks.GRASS_BLOCK.defaultBlockState()
            b == Blocks.SNOW_BLOCK -> Blocks.SNOW_BLOCK.defaultBlockState()
            b == Blocks.SAND || b == Blocks.SANDSTONE -> Blocks.SAND.defaultBlockState()
            b == Blocks.RED_SAND || b == Blocks.RED_SANDSTONE -> Blocks.RED_SAND.defaultBlockState()
            b == Blocks.TERRACOTTA -> Blocks.TERRACOTTA.defaultBlockState()
            b == Blocks.STONE || b == Blocks.ANDESITE || b == Blocks.GRANITE || b == Blocks.DIORITE ->
                Blocks.STONE.defaultBlockState()
            b == Blocks.DEEPSLATE -> Blocks.DEEPSLATE.defaultBlockState()
            else -> state
        }
    }

    companion object {
        fun isTerrainSolid(state: BlockState): Boolean {
            if (state.isAir || !state.fluidState.isEmpty) return false
            if (state.`is`(Blocks.SNOW)) return false
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
