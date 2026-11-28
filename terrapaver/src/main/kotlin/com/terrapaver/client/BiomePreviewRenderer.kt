package com.terrapaver.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.terrapaver.TerraPaverMod
import com.terrapaver.config.BiomeConfig
import com.terrapaver.config.BiomeShape
import com.terrapaver.config.BiomeSplitMode
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object BiomePreviewRenderer {
    fun render(context: WorldRenderContext) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        val holdingBiome = player.mainHandItem.`is`(TerraPaverMod.BIOME_EDITOR) ||
            player.offhandItem.`is`(TerraPaverMod.BIOME_EDITOR)
        if (!holdingBiome) return

        val cfg = BiomeConfig.clientConfig
        val start = player.getEyePosition(1.0f)
        val look = player.lookAngle
        val end = start.add(look.x * 96.0, look.y * 96.0, look.z * 96.0)
        val hit = level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        val target = if (hit.type == HitResult.Type.BLOCK) hit.blockPos else player.blockPosition()

        val cam = context.camera().position
        val matrix = Matrix4f(context.matrixStack()?.last()?.pose() ?: return)
        matrix.translate((-cam.x).toFloat(), (-cam.y).toFloat(), (-cam.z).toFloat())

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)

        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        var hasVerts = false

        when (cfg.shape) {
            BiomeShape.RADIUS_BRUSH -> {
                val r = cfg.radius.toFloat()
                val segs = 56
                val y = target.y + 1.08f
                val cx = target.x + 0.5f
                val cz = target.z + 0.5f
                for (i in 0 until segs) {
                    val a0 = (2.0 * PI * i / segs).toFloat()
                    val a1 = (2.0 * PI * (i + 1) / segs).toFloat()
                    drawBeam(
                        builder, matrix,
                        cx + cos(a0) * r, y, cz + sin(a0) * r,
                        cx + cos(a1) * r, y, cz + sin(a1) * r,
                        0.18f, 0.12f, 0.85f, 0.62f, 0.82f
                    )
                }
                if (cfg.splitMode == BiomeSplitMode.SPLIT_X) {
                    drawBeam(builder, matrix, cx, y, cz - r, cx, y, cz + r, 0.14f, 0.96f, 0.65f, 0.12f, 0.88f)
                } else if (cfg.splitMode == BiomeSplitMode.SPLIT_Z) {
                    drawBeam(builder, matrix, cx - r, y, cz, cx + r, y, cz, 0.14f, 0.96f, 0.65f, 0.12f, 0.88f)
                }
                drawVerticalBeacon(builder, matrix, cx, y, cz, 0.18f, 2.5f, 0.12f, 0.92f, 0.65f, 0.85f)
                hasVerts = true
            }
            BiomeShape.SQUARE_BRUSH -> {
                val r = cfg.radius.toFloat() + 0.5f
                val y = target.y + 1.08f
                val cx = target.x + 0.5f
                val cz = target.z + 0.5f
                drawRectOutline(builder, matrix, cx - r, cx + r, y, cz - r, cz + r, 0.18f, 0.12f, 0.85f, 0.62f, 0.82f)
                if (cfg.splitMode == BiomeSplitMode.SPLIT_X) {
                    drawBeam(builder, matrix, cx, y, cz - r, cx, y, cz + r, 0.14f, 0.96f, 0.65f, 0.12f, 0.88f)
                } else if (cfg.splitMode == BiomeSplitMode.SPLIT_Z) {
                    drawBeam(builder, matrix, cx - r, y, cz, cx + r, y, cz, 0.14f, 0.96f, 0.65f, 0.12f, 0.88f)
                }
                drawVerticalBeacon(builder, matrix, cx, y, cz, 0.18f, 2.5f, 0.12f, 0.92f, 0.65f, 0.85f)
                hasVerts = true
            }
            BiomeShape.CHUNK_GRID -> {
                val cp = ChunkPos(target)
                val cr = cfg.chunkRadius
                val x0 = ((cp.x - cr) shl 4).toFloat()
                val z0 = ((cp.z - cr) shl 4).toFloat()
                val x1 = (((cp.x + cr) shl 4) + 16).toFloat()
                val z1 = (((cp.z + cr) shl 4) + 16).toFloat()
                val y = target.y + 1.08f
                drawRectOutline(builder, matrix, x0, x1, y, z0, z1, 0.18f, 0.16f, 0.76f, 0.95f, 0.80f)
                hasVerts = true
            }
            BiomeShape.BOX_2POINT -> {
                if (ClientBiomeState.hasCorner1) {
                    val c1 = ClientBiomeState.corner1
                    val x0 = min(c1.x, target.x).toFloat()
                    val x1 = max(c1.x, target.x).toFloat() + 1f
                    val z0 = min(c1.z, target.z).toFloat()
                    val z1 = max(c1.z, target.z).toFloat() + 1f
                    val y = max(c1.y, target.y).toFloat() + 1.12f
                    drawRectOutline(builder, matrix, x0, x1, y, z0, z1, 0.18f, 0.95f, 0.62f, 0.18f, 0.82f)
                    drawVerticalBeacon(builder, matrix, c1.x + 0.5f, c1.y + 1f, c1.z + 0.5f, 0.22f, 3.0f, 0.95f, 0.62f, 0.18f, 0.9f)
                } else {
                    drawVerticalBeacon(builder, matrix, target.x + 0.5f, target.y + 1f, target.z + 0.5f, 0.18f, 2.2f, 0.12f, 0.85f, 0.62f, 0.8f)
                }
                hasVerts = true
            }
        }

        if (hasVerts) {
            val mesh = builder.build()
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh)
            }
        }

        RenderSystem.enableCull()
        RenderSystem.disableBlend()
    }

    private fun drawRectOutline(
        bb: BufferBuilder, m: Matrix4f,
        x0: Float, x1: Float, y: Float, z0: Float, z1: Float,
        thick: Float, r: Float, g: Float, b: Float, a: Float
    ) {
        drawBeam(bb, m, x0, y, z0, x1, y, z0, thick, r, g, b, a)
        drawBeam(bb, m, x1, y, z0, x1, y, z1, thick, r, g, b, a)
        drawBeam(bb, m, x1, y, z1, x0, y, z1, thick, r, g, b, a)
        drawBeam(bb, m, x0, y, z1, x0, y, z0, thick, r, g, b, a)
    }

    private fun drawBeam(
        bb: BufferBuilder, m: Matrix4f,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        halfW: Float, r: Float, g: Float, b: Float, a: Float
    ) {
        val dx = x1 - x0
        val dz = z1 - z0
        val len = kotlin.math.hypot(dx, dz).coerceAtLeast(0.001f)
        val px = (-dz / len) * halfW
        val pz = (dx / len) * halfW
        bb.addVertex(m, x0 - px, y0, z0 - pz).setColor(r, g, b, a)
        bb.addVertex(m, x0 + px, y0, z0 + pz).setColor(r, g, b, a)
        bb.addVertex(m, x1 + px, y1, z1 + pz).setColor(r, g, b, a)
        bb.addVertex(m, x1 - px, y1, z1 - pz).setColor(r, g, b, a)
    }

    private fun drawVerticalBeacon(
        bb: BufferBuilder, m: Matrix4f,
        cx: Float, y0: Float, cz: Float,
        halfW: Float, height: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val x0 = cx - halfW
        val x1 = cx + halfW
        val z0 = cz - halfW
        val z1 = cz + halfW
        val y1 = y0 + height

        bb.addVertex(m, x0, y0, z1).setColor(r, g, b, a)
        bb.addVertex(m, x1, y0, z1).setColor(r, g, b, a)
        bb.addVertex(m, x1, y1, z1).setColor(r, g, b, a)
        bb.addVertex(m, x0, y1, z1).setColor(r, g, b, a)

        bb.addVertex(m, x1, y0, z0).setColor(r, g, b, a)
        bb.addVertex(m, x0, y0, z0).setColor(r, g, b, a)
        bb.addVertex(m, x0, y1, z0).setColor(r, g, b, a)
        bb.addVertex(m, x1, y1, z0).setColor(r, g, b, a)
    }
}
