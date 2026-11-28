/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector3i
import java.util.function.Predicate

@Environment(EnvType.CLIENT)
open class ScreenBaker(private val materials: Array<Material.Baked>) : BlockStateModel {
    private data class Key(val d: Int, val u: Int, val n: Int, val s: Int, val w: Int, val e: Int)

    private val staticMaterialFlags: Int

    init {
        var flags = 0
        for (mat in materials) {
            if (mat.forceTranslucent() || mat.sprite().contents().computeTransparency(0.0f, 0.0f, 1.0f, 1.0f).hasTranslucent()) {
                flags = flags or BakedQuad.FLAG_TRANSLUCENT
            }
            if (mat.sprite().contents().isAnimated) {
                flags = flags or BakedQuad.FLAG_ANIMATED
            }
        }
        staticMaterialFlags = flags
    }

    override fun emitQuads(
        emitter: QuadEmitter,
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource,
        cullTest: Predicate<Direction?>
    ) {
        for (dirIdx in 0 until 6) {
            val dir = BLOCK_FACINGS[dirIdx]
            if (cullTest.test(dir)) continue

            val texIdx = textureIndexFor(state, level, pos, BLOCK_SIDES[dirIdx])
            emitter.square(dir, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f)
                .materialBake(materials[texIdx], MutableQuadView.BAKE_LOCK_UV)
                .emit()
        }
    }

    override fun createGeometryKey(
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        random: RandomSource
    ): Any {
        return Key(
            textureIndexFor(state, level, pos, BLOCK_SIDES[0]),
            textureIndexFor(state, level, pos, BLOCK_SIDES[1]),
            textureIndexFor(state, level, pos, BLOCK_SIDES[2]),
            textureIndexFor(state, level, pos, BLOCK_SIDES[3]),
            textureIndexFor(state, level, pos, BLOCK_SIDES[4]),
            textureIndexFor(state, level, pos, BLOCK_SIDES[5])
        )
    }

    private fun textureIndexFor(
        state: BlockState?,
        level: BlockAndTintGetter?,
        pos: BlockPos?,
        side: BlockSide
    ): Int {
        if (state == null || level == null || pos == null) return 15

        var res = when (check(state, level, pos, side.up).toInt()) {
            1 -> BAR_BOTTOM
            2 -> BAR_TOP
            3 -> BAR_TOP or BAR_BOTTOM
            else -> 0
        }

        res = res or when (check(state, level, pos, side.right).toInt()) {
            1 -> BAR_LEFT
            2 -> BAR_RIGHT
            3 -> BAR_LEFT or BAR_RIGHT
            else -> 0
        }

        return res
    }

    protected fun check(state: BlockState, level: BlockAndTintGetter, pos: BlockPos, dir: Vector3i): Byte {
        val u = level.getBlockState(pos.offset(dir.x, dir.y, dir.z))
        val d = level.getBlockState(pos.offset(-dir.x, -dir.y, -dir.z))
        val uSame = u.block === state.block
        val dSame = d.block === state.block
        if (uSame && !dSame) return 1
        if (dSame && !uSame) return 2
        if (!dSame && !uSame) return 3
        return 0
    }

    override fun collectParts(random: RandomSource, parts: MutableList<BlockStateModelPart>) {
    }

    override fun particleMaterial(): Material.Baked = materials[15]

    override fun materialFlags(): Int = staticMaterialFlags

    companion object {
        private val BLOCK_SIDES = BlockSide.entries.toTypedArray()
        private val BLOCK_FACINGS = Direction.entries.toTypedArray()

        private const val BAR_BOTTOM = 1
        private const val BAR_RIGHT = 2
        private const val BAR_TOP = 4
        private const val BAR_LEFT = 8
    }
}
