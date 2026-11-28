package com.terrapaver.engine

import com.terrapaver.config.BiomeConfig
import com.terrapaver.config.BiomeShape
import com.terrapaver.config.BiomeSplitMode
import com.terrapaver.config.BiomeVerticalMode
import com.terrapaver.network.TerraPaverNet
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.QuartPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeResolver
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object ServerBiomeManager {
    private data class QuartBackup(
        val qx: Int,
        val qy: Int,
        val qz: Int,
        val oldBiome: Holder<Biome>
    )

    private data class UndoEntry(
        val level: ServerLevel,
        val chunkBackups: Map<ChunkPos, List<QuartBackup>>,
        val blockBackups: Map<Long, BlockState>
    )

    private val boxCorner1 = HashMap<UUID, BlockPos>()
    private val undoStacks = HashMap<UUID, ArrayDeque<UndoEntry>>()

    fun selectBoxPoint(player: ServerPlayer, pos: BlockPos, cfg: BiomeConfig) {
        val prev = boxCorner1[player.uuid]
        if (prev == null) {
            boxCorner1[player.uuid] = pos
            syncState(player)
            player.displayClientMessage(
                Component.translatable("terrapaver.biome.msg.corner1", pos.x, pos.y, pos.z),
                true
            )
        } else {
            boxCorner1.remove(player.uuid)
            applyBox(player, prev, pos, cfg)
        }
    }

    fun clearSelection(player: ServerPlayer) {
        boxCorner1.remove(player.uuid)
        syncState(player)
        player.displayClientMessage(Component.translatable("terrapaver.biome.msg.cleared"), true)
    }

    private fun resolveTargetLevel(player: ServerPlayer, cfg: BiomeConfig): ServerLevel {
        if (cfg.targetDimension.isBlank()) return player.serverLevel()
        val rl = ResourceLocation.tryParse(cfg.targetDimension) ?: return player.serverLevel()
        val dimKey = ResourceKey.create(Registries.DIMENSION, rl)
        return player.server.getLevel(dimKey) ?: player.serverLevel()
    }

    fun applyBrush(player: ServerPlayer, center: BlockPos, rawCfg: BiomeConfig) {
        val cfg = rawCfg.copySanitized()
        val level = resolveTargetLevel(player, cfg)
        when (cfg.shape) {
            BiomeShape.CHUNK_GRID -> {
                val cPos = ChunkPos(center)
                val cr = cfg.chunkRadius
                val minX = (cPos.x - cr) shl 4
                val minZ = (cPos.z - cr) shl 4
                val maxX = ((cPos.x + cr) shl 4) + 15
                val maxZ = ((cPos.z + cr) shl 4) + 15
                val cx = (minX + maxX) / 2
                val cz = (minZ + maxZ) / 2
                executeEdit(
                    player = player,
                    level = level,
                    cfg = cfg,
                    minX = minX,
                    maxX = maxX,
                    minZ = minZ,
                    maxZ = maxZ,
                    splitCenterX = cx,
                    splitCenterZ = cz,
                    inShapeFn = { _, _ -> true },
                    edgeAlphaFn = { _, _ -> 1.0 }
                )
            }
            BiomeShape.SQUARE_BRUSH -> {
                val r = cfg.radius
                executeEdit(
                    player = player,
                    level = level,
                    cfg = cfg,
                    minX = center.x - r,
                    maxX = center.x + r,
                    minZ = center.z - r,
                    maxZ = center.z + r,
                    splitCenterX = center.x,
                    splitCenterZ = center.z,
                    inShapeFn = { bx, bz ->
                        abs(bx - center.x) <= r && abs(bz - center.z) <= r
                    },
                    edgeAlphaFn = { bx, bz ->
                        val dist = max(abs(bx - center.x), abs(bz - center.z)).toDouble()
                        computeFeatherAlpha(dist, r.toDouble(), cfg.edgeFeather.toDouble())
                    }
                )
            }
            BiomeShape.RADIUS_BRUSH, BiomeShape.BOX_2POINT -> {
                val r = cfg.radius
                executeEdit(
                    player = player,
                    level = level,
                    cfg = cfg,
                    minX = center.x - r,
                    maxX = center.x + r,
                    minZ = center.z - r,
                    maxZ = center.z + r,
                    splitCenterX = center.x,
                    splitCenterZ = center.z,
                    inShapeFn = { bx, bz ->
                        hypot((bx - center.x).toDouble(), (bz - center.z).toDouble()) <= r + 0.5
                    },
                    edgeAlphaFn = { bx, bz ->
                        val dist = hypot((bx - center.x).toDouble(), (bz - center.z).toDouble())
                        computeFeatherAlpha(dist, r.toDouble(), cfg.edgeFeather.toDouble())
                    }
                )
            }
        }
    }

    fun applyBox(player: ServerPlayer, a: BlockPos, b: BlockPos, rawCfg: BiomeConfig) {
        val cfg = rawCfg.copySanitized()
        val level = resolveTargetLevel(player, cfg)
        val minX = min(a.x, b.x)
        val maxX = max(a.x, b.x)
        val minZ = min(a.z, b.z)
        val maxZ = max(a.z, b.z)

        executeEdit(
            player = player,
            level = level,
            cfg = cfg,
            minX = minX,
            maxX = maxX,
            minZ = minZ,
            maxZ = maxZ,
            splitCenterX = (minX + maxX) / 2,
            splitCenterZ = (minZ + maxZ) / 2,
            inShapeFn = { bx, bz -> bx in minX..maxX && bz in minZ..maxZ },
            edgeAlphaFn = { bx, bz ->
                if (cfg.edgeFeather <= 0) {
                    1.0
                } else {
                    val edgeDist = minOf(bx - minX, maxX - bx, bz - minZ, maxZ - bz).toDouble()
                    if (edgeDist >= cfg.edgeFeather) 1.0 else (edgeDist + 0.5) / cfg.edgeFeather.toDouble()
                }
            }
        )
    }

    private fun executeEdit(
        player: ServerPlayer,
        level: ServerLevel,
        cfg: BiomeConfig,
        minX: Int,
        maxX: Int,
        minZ: Int,
        maxZ: Int,
        splitCenterX: Int,
        splitCenterZ: Int,
        inShapeFn: (Int, Int) -> Boolean,
        edgeAlphaFn: (Int, Int) -> Double
    ) {
        val startNs = System.nanoTime()
        val biomeReg = level.registryAccess().registryOrThrow(Registries.BIOME)
        val targetId = ResourceLocation.tryParse(cfg.targetBiome)
        val targetHolder = if (targetId != null) {
            biomeReg.getHolder(ResourceKey.create(Registries.BIOME, targetId)).orElse(null)
        } else null

        if (targetHolder == null) {
            player.displayClientMessage(
                Component.translatable("terrapaver.biome.msg.invalid_biome", cfg.targetBiome),
                true
            )
            return
        }

        val secondaryId = ResourceLocation.tryParse(cfg.secondaryBiome)
        val secondaryHolder = (if (secondaryId != null) {
            biomeReg.getHolder(ResourceKey.create(Registries.BIOME, secondaryId)).orElse(null)
        } else null) ?: targetHolder

        val mixHolders = ArrayList<Holder.Reference<Biome>>()
        for (entry in cfg.splitPalette) {
            val rl = ResourceLocation.tryParse(entry) ?: continue
            val h = biomeReg.getHolder(ResourceKey.create(Registries.BIOME, rl)).orElse(null)
            if (h != null) mixHolders.add(h)
        }
        if (mixHolders.isEmpty()) mixHolders.add(targetHolder)

        val sourceFilterId = if (cfg.filterSourceBiome) ResourceLocation.tryParse(cfg.sourceBiomeFilter) else null

        val minCx = minX shr 4
        val maxCx = maxX shr 4
        val minCz = minZ shr 4
        val maxCz = maxZ shr 4

        val yLoBound = when (cfg.verticalMode) {
            BiomeVerticalMode.FULL_HEIGHT -> level.minBuildHeight
            BiomeVerticalMode.CUSTOM_Y -> max(level.minBuildHeight, cfg.customMinY)
            BiomeVerticalMode.SURFACE_BAND -> level.minBuildHeight
        }
        val yHiBound = when (cfg.verticalMode) {
            BiomeVerticalMode.FULL_HEIGHT -> level.maxBuildHeight - 1
            BiomeVerticalMode.CUSTOM_Y -> min(level.maxBuildHeight - 1, cfg.customMaxY)
            BiomeVerticalMode.SURFACE_BAND -> level.maxBuildHeight - 1
        }

        val modifiedChunks = ArrayList<ChunkAccess>()
        val chunkBackups = LinkedHashMap<ChunkPos, List<QuartBackup>>()
        val blockBackups = LinkedHashMap<Long, BlockState>()
        val sampler = level.chunkSource.randomState().sampler()
        var changedQuarts = 0
        var changedBlocks = 0
        var alreadyTargetQuarts = 0
        var filteredOutQuarts = 0
        var yOutOfRangeQuarts = 0
        var waterProtectedQuarts = 0
        var checkedChunks = 0

        fun pickHolderAt(bx: Int, bz: Int): Holder.Reference<Biome> {
            return when (cfg.splitMode) {
                BiomeSplitMode.SINGLE -> targetHolder
                BiomeSplitMode.SPLIT_X -> {
                    val d = bx - splitCenterX
                    if (cfg.edgeFeather > 0 && abs(d) < cfg.edgeFeather / 2.0) {
                        val p = (d + cfg.edgeFeather / 2.0) / cfg.edgeFeather.toDouble()
                        if (deterministicNoise(bx, bz) < p) secondaryHolder else targetHolder
                    } else {
                        if (d < 0) targetHolder else secondaryHolder
                    }
                }
                BiomeSplitMode.SPLIT_Z -> {
                    val d = bz - splitCenterZ
                    if (cfg.edgeFeather > 0 && abs(d) < cfg.edgeFeather / 2.0) {
                        val p = (d + cfg.edgeFeather / 2.0) / cfg.edgeFeather.toDouble()
                        if (deterministicNoise(bx, bz) < p) secondaryHolder else targetHolder
                    } else {
                        if (d < 0) targetHolder else secondaryHolder
                    }
                }
                BiomeSplitMode.VORONOI_MIX -> {
                    if (mixHolders.size == 1) return mixHolders[0]
                    val idx = voronoiIndex(bx, bz, cfg.splitPatchScale, mixHolders.size)
                    mixHolders[idx]
                }
            }
        }

        for (cx in minCx..maxCx) {
            for (cz in minCz..maxCz) {
                val chunk = level.chunkSource.getChunk(cx, cz, ChunkStatus.FULL, true) ?: continue
                checkedChunks++
                val backups = ArrayList<QuartBackup>(64)

                val quartSurfaceY = if (cfg.verticalMode == BiomeVerticalMode.SURFACE_BAND) {
                    IntArray(16) { idx ->
                        val lx = (idx and 3) * 4 + 2
                        val lz = (idx shr 2) * 4 + 2
                        chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz)
                    }
                } else null

                val resolver = BiomeResolver { qx, qy, qz, _ ->
                    val existing = chunk.getNoiseBiome(qx, qy, qz)
                    val bx = QuartPos.toBlock(qx) + 2
                    val by = QuartPos.toBlock(qy) + 2
                    val bz = QuartPos.toBlock(qz) + 2

                    if (bx < minX || bx > maxX || bz < minZ || bz > maxZ) return@BiomeResolver existing
                    if (!inShapeFn(bx, bz)) return@BiomeResolver existing

                    val loY = if (quartSurfaceY != null) {
                        val lqx = (qx and 3)
                        val lqz = (qz and 3)
                        quartSurfaceY[lqz * 4 + lqx] - cfg.surfaceDepthDown
                    } else {
                        yLoBound
                    }
                    val hiY = if (quartSurfaceY != null) {
                        val lqx = (qx and 3)
                        val lqz = (qz and 3)
                        quartSurfaceY[lqz * 4 + lqx] + cfg.surfaceHeightUp
                    } else {
                        yHiBound
                    }
                    if (by + 1 < loY || by - 2 > hiY) {
                        yOutOfRangeQuarts++
                        return@BiomeResolver existing
                    }

                    if (cfg.preserveRiversAndOceans && (existing.`is`(BiomeTags.IS_RIVER) || existing.`is`(BiomeTags.IS_OCEAN))) {
                        waterProtectedQuarts++
                        return@BiomeResolver existing
                    }
                    if (sourceFilterId != null) {
                        val existingId = existing.unwrapKey().map { it.location() }.orElse(null)
                        if (existingId != sourceFilterId) {
                            filteredOutQuarts++
                            return@BiomeResolver existing
                        }
                    }

                    if (cfg.edgeFeather > 0) {
                        val alpha = edgeAlphaFn(bx, bz)
                        if (alpha <= 0.0) return@BiomeResolver existing
                        if (alpha < 1.0 && deterministicNoise(bx, bz) > alpha) {
                            return@BiomeResolver existing
                        }
                    }

                    val chosen = pickHolderAt(bx, bz)
                    if (existing != chosen) {
                        backups.add(QuartBackup(qx, qy, qz, existing))
                        changedQuarts++
                    } else {
                        alreadyTargetQuarts++
                    }
                    chosen
                }

                chunk.fillBiomesFromNoise(resolver, sampler)
                if (backups.isNotEmpty()) {
                    chunk.setUnsaved(true)
                    modifiedChunks.add(chunk)
                    chunkBackups[ChunkPos(cx, cz)] = backups
                }

                if (cfg.adaptSurfaceBlocks || cfg.adaptWeatherSnow) {
                    val cMinX = max(minX, cx shl 4)
                    val cMaxX = min(maxX, (cx shl 4) + 15)
                    val cMinZ = max(minZ, cz shl 4)
                    val cMaxZ = min(maxZ, (cz shl 4) + 15)
                    val chunkBlocks = adaptChunkSurface(
                        level = level,
                        chunk = chunk,
                        pickHolderFn = { bx, bz -> pickHolderAt(bx, bz) },
                        cfg = cfg,
                        sourceFilterId = sourceFilterId,
                        cMinX = cMinX,
                        cMaxX = cMaxX,
                        cMinZ = cMinZ,
                        cMaxZ = cMaxZ,
                        inShapeFn = inShapeFn,
                        edgeAlphaFn = edgeAlphaFn,
                        blockBackups = blockBackups
                    )
                    if (chunkBlocks > 0) {
                        changedBlocks += chunkBlocks
                        chunk.setUnsaved(true)
                    }
                }
            }
        }

        if (modifiedChunks.isNotEmpty()) {
            level.chunkSource.chunkMap.resendBiomesForChunks(modifiedChunks)
        }
        if (chunkBackups.isNotEmpty() || blockBackups.isNotEmpty()) {
            pushUndo(player.uuid, UndoEntry(level, chunkBackups, blockBackups))
        }
        syncState(player)

        val ms = max(1L, (System.nanoTime() - startNs) / 1_000_000L)
        val biomeDisplayName = Component.translatable("biome.${targetId!!.namespace}.${targetId.path}")
        when {
            changedQuarts > 0 -> {
                player.displayClientMessage(
                    Component.translatable(
                        "terrapaver.biome.msg.applied",
                        biomeDisplayName,
                        changedQuarts * 64,
                        modifiedChunks.size,
                        ms
                    ),
                    true
                )
            }
            changedBlocks > 0 -> {
                player.displayClientMessage(
                    Component.translatable(
                        "terrapaver.biome.msg.surface_only",
                        biomeDisplayName,
                        changedBlocks,
                        ms
                    ),
                    true
                )
            }
            filteredOutQuarts > 0 && alreadyTargetQuarts == 0 && sourceFilterId != null -> {
                val filterName = Component.translatable("biome.${sourceFilterId.namespace}.${sourceFilterId.path}")
                player.displayClientMessage(
                    Component.translatable("terrapaver.biome.msg.filtered_zero", filterName),
                    true
                )
            }
            yOutOfRangeQuarts > 0 && alreadyTargetQuarts == 0 -> {
                player.displayClientMessage(
                    Component.translatable("terrapaver.biome.msg.y_empty", yLoBound, yHiBound),
                    true
                )
            }
            waterProtectedQuarts > 0 && alreadyTargetQuarts == 0 -> {
                player.displayClientMessage(
                    Component.translatable("terrapaver.biome.msg.water_protected"),
                    true
                )
            }
            else -> {
                player.displayClientMessage(
                    Component.translatable("terrapaver.biome.msg.already_target", checkedChunks),
                    true
                )
            }
        }
    }

    private fun adaptChunkSurface(
        level: ServerLevel,
        chunk: ChunkAccess,
        pickHolderFn: (Int, Int) -> Holder.Reference<Biome>,
        cfg: BiomeConfig,
        sourceFilterId: ResourceLocation?,
        cMinX: Int,
        cMaxX: Int,
        cMinZ: Int,
        cMaxZ: Int,
        inShapeFn: (Int, Int) -> Boolean,
        edgeAlphaFn: (Int, Int) -> Double,
        blockBackups: MutableMap<Long, BlockState>
    ): Int {
        var count = 0
        val pos = BlockPos.MutableBlockPos()

        for (bx in cMinX..cMaxX) {
            for (bz in cMinZ..cMaxZ) {
                if (!inShapeFn(bx, bz)) continue
                if (cfg.edgeFeather > 0) {
                    val alpha = edgeAlphaFn(bx, bz)
                    if (alpha < 1.0 && deterministicNoise(bx, bz) > alpha) continue
                }
                val groundY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx and 15, bz and 15) - 1
                if (groundY < level.minBuildHeight) continue
                pos.set(bx, groundY, bz)

                val curBiome = level.getBiome(pos)
                if (cfg.preserveRiversAndOceans && (curBiome.`is`(BiomeTags.IS_RIVER) || curBiome.`is`(BiomeTags.IS_OCEAN))) {
                    continue
                }
                if (sourceFilterId != null && curBiome.unwrapKey().map { it.location() }.orElse(null) != sourceFilterId) {
                    continue
                }

                val targetHolder = pickHolderFn(bx, bz)
                val targetId = targetHolder.key().location()
                val path = targetId.path
                val isDesert = path.contains("desert") || path.contains("beach")
                val isBadlands = path.contains("badlands")
                val isMushroom = path.contains("mushroom")
                val isSnowy = path.contains("snowy") || path.contains("frozen") || path.contains("ice") || path.contains("peaks")
                val isSwamp = path.contains("swamp") || path.contains("mangrove")

                val curState = level.getBlockState(pos)
                if (cfg.adaptSurfaceBlocks && curState.fluidState.isEmpty) {
                    val replacement: BlockState? = when {
                        isDesert && (curState.`is`(Blocks.GRASS_BLOCK) || curState.`is`(Blocks.DIRT) || curState.`is`(Blocks.MYCELIUM)) ->
                            Blocks.SAND.defaultBlockState()
                        isBadlands && (curState.`is`(Blocks.GRASS_BLOCK) || curState.`is`(Blocks.DIRT)) ->
                            Blocks.RED_SAND.defaultBlockState()
                        isMushroom && (curState.`is`(Blocks.GRASS_BLOCK) || curState.`is`(Blocks.DIRT) || curState.`is`(Blocks.SAND)) ->
                            Blocks.MYCELIUM.defaultBlockState()
                        isSwamp && curState.`is`(Blocks.SAND) ->
                            Blocks.GRASS_BLOCK.defaultBlockState()
                        !isDesert && !isBadlands && !isMushroom && (curState.`is`(Blocks.SAND) || curState.`is`(Blocks.RED_SAND) || curState.`is`(Blocks.MYCELIUM)) ->
                            Blocks.GRASS_BLOCK.defaultBlockState()
                        else -> null
                    }
                    if (replacement != null && replacement != curState) {
                        blockBackups.putIfAbsent(pos.asLong(), curState)
                        level.setBlock(pos, replacement, 2)
                        count++
                    }
                }

                if (cfg.adaptWeatherSnow) {
                    pos.set(bx, groundY + 1, bz)
                    val above = level.getBlockState(pos)
                    val shouldSnow = isSnowy || targetHolder.value().coldEnoughToSnow(pos)
                    if (shouldSnow && above.isAir && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                        blockBackups.putIfAbsent(pos.asLong(), above)
                        level.setBlock(pos, Blocks.SNOW.defaultBlockState(), 2)
                        count++
                    } else if (!shouldSnow && above.`is`(Blocks.SNOW)) {
                        blockBackups.putIfAbsent(pos.asLong(), above)
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)
                        count++
                    }
                }
            }
        }
        return count
    }

    fun undoLast(player: ServerPlayer) {
        val stack = undoStacks[player.uuid]
        val entry = if (stack != null && stack.isNotEmpty()) stack.removeLast() else null
        if (entry == null) {
            player.displayClientMessage(Component.translatable("terrapaver.biome.msg.nothing_undo"), true)
            syncState(player)
            return
        }

        val level = entry.level
        val sampler = level.chunkSource.randomState().sampler()
        val restoredChunks = ArrayList<ChunkAccess>(entry.chunkBackups.size)

        for ((cPos, list) in entry.chunkBackups) {
            val chunk = level.chunkSource.getChunk(cPos.x, cPos.z, ChunkStatus.FULL, true) ?: continue
            val map = HashMap<Long, Holder<Biome>>(list.size * 2)
            for (b in list) {
                val k = ((b.qx.toLong() and 0xFFFFFL) shl 40) or
                    ((b.qy.toLong() and 0xFFFFFL) shl 20) or
                    (b.qz.toLong() and 0xFFFFFL)
                map[k] = b.oldBiome
            }
            val resolver = BiomeResolver { qx, qy, qz, _ ->
                val k = ((qx.toLong() and 0xFFFFFL) shl 40) or
                    ((qy.toLong() and 0xFFFFFL) shl 20) or
                    (qz.toLong() and 0xFFFFFL)
                map[k] ?: chunk.getNoiseBiome(qx, qy, qz)
            }
            chunk.fillBiomesFromNoise(resolver, sampler)
            chunk.setUnsaved(true)
            restoredChunks.add(chunk)
        }

        for ((posLong, state) in entry.blockBackups) {
            val pos = BlockPos.of(posLong)
            level.setBlock(pos, state, 2)
        }

        if (restoredChunks.isNotEmpty()) {
            level.chunkSource.chunkMap.resendBiomesForChunks(restoredChunks)
        }
        syncState(player)
        player.displayClientMessage(Component.translatable("terrapaver.biome.msg.undone", restoredChunks.size), true)
    }

    fun syncState(player: ServerPlayer) {
        val c1 = boxCorner1[player.uuid]
        val canUndo = undoStacks[player.uuid]?.isNotEmpty() == true
        TerraPaverNet.sendToPlayer(
            player,
            TerraPaverNet.S2CBiomeState(
                hasCorner1 = c1 != null,
                corner1 = c1 ?: BlockPos.ZERO,
                canUndo = canUndo
            )
        )
        syncCatalog(player)
    }

    fun syncCatalog(player: ServerPlayer) {
        val dims = ArrayList<String>()
        val mins = ArrayList<Int>()
        val maxs = ArrayList<Int>()
        for (lvl in player.server.allLevels) {
            dims.add(lvl.dimension().location().toString())
            mins.add(lvl.minBuildHeight)
            maxs.add(lvl.maxBuildHeight - 1)
        }
        val cur = player.serverLevel().dimension().location().toString()
        TerraPaverNet.sendToPlayer(
            player,
            TerraPaverNet.S2CBiomeCatalog(cur, dims, mins.toIntArray(), maxs.toIntArray())
        )
    }

    fun onDisconnect(player: ServerPlayer) {
        boxCorner1.remove(player.uuid)
    }

    fun shutdown(@Suppress("UNUSED_PARAMETER") server: MinecraftServer) {
        boxCorner1.clear()
        undoStacks.clear()
    }

    private fun pushUndo(uuid: UUID, entry: UndoEntry) {
        val stack = undoStacks.getOrPut(uuid) { ArrayDeque() }
        stack.addLast(entry)
        while (stack.size > 16) {
            stack.removeFirst()
        }
    }

    private fun computeFeatherAlpha(dist: Double, radius: Double, feather: Double): Double {
        if (feather <= 0.0) return if (dist <= radius) 1.0 else 0.0
        val inner = radius - feather
        if (dist <= inner) return 1.0
        if (dist >= radius) return 0.0
        val t = ((dist - inner) / feather).coerceIn(0.0, 1.0)
        return 1.0 - (t * t * (3.0 - 2.0 * t))
    }

    private fun voronoiIndex(bx: Int, bz: Int, scale: Int, count: Int): Int {
        val s = scale.coerceAtLeast(4).toDouble()
        val gx = floor(bx / s).toInt()
        val gz = floor(bz / s).toInt()
        var bestDist = Double.POSITIVE_INFINITY
        var bestHash = 0
        for (ox in -1..1) {
            for (oz in -1..1) {
                val cx = gx + ox
                val cz = gz + oz
                val rx = (cx + 0.15 + 0.7 * deterministicNoise(cx * 17 + 3, cz * 31 + 7)) * s
                val rz = (cz + 0.15 + 0.7 * deterministicNoise(cx * 53 + 11, cz * 19 + 5)) * s
                val d = (bx - rx) * (bx - rx) + (bz - rz) * (bz - rz)
                if (d < bestDist) {
                    bestDist = d
                    var h = (cx * 374761393) + (cz * 668265263) + 0x9E3779B9.toInt()
                    h = (h xor (h shr 13)) * 1274126177
                    bestHash = (h xor (h shr 16)) and 0x7FFFFFFF
                }
            }
        }
        return bestHash % count
    }

    private fun deterministicNoise(x: Int, z: Int): Double {
        var h = (x * 374761393) xor (z * 668265263)
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return ((h ushr 8) and 0xFFFFFF).toDouble() / 0x1000000.toDouble()
    }
}
