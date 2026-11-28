/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers

import com.google.common.collect.ImmutableList
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.renderer.v1.RendererAccess
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.Material
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector3f
import net.montoyo.wd.utilities.Vector3i
import java.util.function.Function
import java.util.function.Supplier

@Environment(EnvType.CLIENT)
open class ScreenBaker(spriteGetter: Function<Material, TextureAtlasSprite>) : BakedModel, FabricBakedModel {
    private val texs = arrayOfNulls<TextureAtlasSprite>(16)
    private val prebakedQuads = Array(6) { arrayOfNulls<BakedQuad>(16) }
    private val prebakedLists: Array<Array<List<BakedQuad>>> = Array(6) { Array(16) { NO_QUADS } }
    private val standardMaterial: RenderMaterial?

    init {
        for (i in texs.indices) {
            texs[i] = spriteGetter.apply(ScreenModelLoader.MATERIALS_SIDES[i]!!)
        }

        for (dirIdx in 0 until 6) {
            val quadSide = BLOCK_SIDES[BlockSide.reverse(dirIdx)]
            for (texIdx in 0 until 16) {
                val quad = bakeSide(quadSide, texs[texIdx]!!)
                prebakedQuads[dirIdx][texIdx] = quad
                prebakedLists[dirIdx][texIdx] = ImmutableList.of(quad)
            }
        }

        val renderer = RendererAccess.INSTANCE.renderer
        standardMaterial = renderer?.materialById(RenderMaterial.MATERIAL_STANDARD)
    }

    private fun putVertex(
        buf: IntArray,
        posIndex: Int,
        vpos: Vector3f,
        tex: TextureAtlasSprite,
        uv: Vector3f,
        normal: Vector3i
    ) {
        val pos = posIndex * 8
        buf[pos] = java.lang.Float.floatToRawIntBits(vpos.x)
        buf[pos + 1] = java.lang.Float.floatToRawIntBits(vpos.y)
        buf[pos + 2] = java.lang.Float.floatToRawIntBits(vpos.z)
        buf[pos + 3] = 0xFFFFFFFF.toInt()
        buf[pos + 4] = java.lang.Float.floatToRawIntBits(tex.getU(uv.x / 16.0f))
        buf[pos + 5] = java.lang.Float.floatToRawIntBits(tex.getV(uv.y / 16.0f))

        val nx = (normal.x * 127) and 0xFF
        val ny = (normal.y * 127) and 0xFF
        val nz = (normal.z * 127) and 0xFF
        buf[pos + 7] = nx or (ny shl 8) or (nz shl 16)
    }

    private fun rotateVec(vec: Vector3f, side: BlockSide): Vector3f = when (side) {
        BlockSide.BOTTOM -> Vector3f(vec.x, 1.0f, 1.0f - vec.z)
        BlockSide.TOP -> Vector3f(vec.x, 0.0f, vec.z)
        BlockSide.NORTH -> Vector3f(vec.x, vec.z, 1.0f)
        BlockSide.SOUTH -> Vector3f(vec.x, 1.0f - vec.z, 0.0f)
        BlockSide.WEST -> Vector3f(1.0f, vec.x, vec.z)
        BlockSide.EAST -> Vector3f(0.0f, 1.0f - vec.x, vec.z)
    }

    private fun rotateTex(side: BlockSide, u: Float, v: Float): Vector3f = when (side) {
        BlockSide.BOTTOM, BlockSide.NORTH -> Vector3f(16.0f - u, 16.0f - v, 0.0f)
        BlockSide.TOP -> Vector3f(16.0f - u, v, 0.0f)
        BlockSide.SOUTH -> Vector3f(u, v, 0.0f)
        BlockSide.WEST -> Vector3f(16.0f - v, u, 0.0f)
        BlockSide.EAST -> Vector3f(v, 16.0f - u, 0.0f)
    }

    private fun bakeSide(side: BlockSide, tex: TextureAtlasSprite): BakedQuad {
        val data = IntArray(8 * 4)

        val rotation = when (side) {
            BlockSide.NORTH, BlockSide.TOP, BlockSide.BOTTOM -> 2
            BlockSide.SOUTH -> 0
            BlockSide.EAST -> 1
            BlockSide.WEST -> 3
        }

        putVertex(data, (rotation + 3) % 4, rotateVec(Vector3f(0.0f, 0.0f, 0.0f), side), tex, rotateTex(side, 16.0f, 0.0f), side.backward)
        putVertex(data, (rotation + 2) % 4, rotateVec(Vector3f(0.0f, 0.0f, 1.0f), side), tex, rotateTex(side, 16.0f, 16.0f), side.backward)
        putVertex(data, (rotation + 1) % 4, rotateVec(Vector3f(1.0f, 0.0f, 1.0f), side), tex, rotateTex(side, 0.0f, 16.0f), side.backward)
        putVertex(data, rotation % 4, rotateVec(Vector3f(1.0f, 0.0f, 0.0f), side), tex, rotateTex(side, 0.0f, 0.0f), side.backward)

        return BakedQuad(data, 0xFFFFFFFF.toInt(), BLOCK_FACINGS[side.ordinal].opposite, tex, true)
    }

    override fun isVanillaAdapter(): Boolean = false

    override fun emitBlockQuads(
        blockView: BlockAndTintGetter,
        state: BlockState,
        pos: BlockPos,
        randomSupplier: Supplier<RandomSource>,
        context: RenderContext
    ) {
        val emitter = context.emitter
        for (dirIdx in 0 until 6) {
            val dir = BLOCK_FACINGS[dirIdx]
            val texIdx = textureIndexFor(state, blockView, pos, BLOCK_SIDES[dirIdx])
            emitter.fromVanilla(prebakedQuads[dirIdx][texIdx]!!, standardMaterial, dir)
            emitter.emit()
        }
    }

    override fun emitItemQuads(
        stack: ItemStack,
        randomSupplier: Supplier<RandomSource>,
        context: RenderContext
    ) {
        val emitter = context.emitter
        for (dirIdx in 0 until 6) {
            val dir = BLOCK_FACINGS[dirIdx]
            emitter.fromVanilla(prebakedQuads[dirIdx][15]!!, standardMaterial, dir)
            emitter.emit()
        }
    }

    override fun getQuads(state: BlockState?, side: Direction?, random: RandomSource): List<BakedQuad> {
        if (side == null) return NO_QUADS

        val dirIdx = side.ordinal
        val texIdx = textureIndexFor(state, ScreenModelContext.level(), ScreenModelContext.pos(), BLOCK_SIDES[dirIdx])
        return prebakedLists[dirIdx][texIdx]
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

    override fun useAmbientOcclusion(): Boolean = true

    override fun isGui3d(): Boolean = true

    override fun usesBlockLight(): Boolean = false

    override fun isCustomRenderer(): Boolean = false

    override fun getParticleIcon(): TextureAtlasSprite = texs[15]!!

    override fun getTransforms(): ItemTransforms = ItemTransforms.NO_TRANSFORMS

    override fun getOverrides(): ItemOverrides = ItemOverrides.EMPTY

    companion object {
        private val NO_QUADS: List<BakedQuad> = ImmutableList.of()
        private val BLOCK_SIDES = BlockSide.entries.toTypedArray()
        private val BLOCK_FACINGS = Direction.entries.toTypedArray()

        private const val BAR_BOTTOM = 1
        private const val BAR_RIGHT = 2
        private const val BAR_TOP = 4
        private const val BAR_LEFT = 8
    }
}
