package com.terrapaver.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.terrapaver.config.PavingConfig
import com.terrapaver.engine.StationType
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f

object RoutePreviewRenderer {
    fun render(context: WorldRenderContext) {
        if (!PavingConfig.clientConfig.showRoutePreview) return
        val hasStart = ClientPavingState.hasStart
        val hasEnd = ClientPavingState.hasEnd
        val preview = ClientPavingState.preview
        if (!hasStart && !hasEnd && preview == null) return

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

        if (hasStart) {
            val s = ClientPavingState.startPos
            drawMarkerPillar(builder, matrix, s.x + 0.5f, s.y + 1.0f, s.z + 0.5f, 0.18f, 3.0f, 0.10f, 0.45f, 0.91f, 0.85f)
            hasVerts = true
        }
        if (hasEnd) {
            val e = ClientPavingState.endPos
            drawMarkerPillar(builder, matrix, e.x + 0.5f, e.y + 1.0f, e.z + 0.5f, 0.18f, 3.0f, 0.95f, 0.35f, 0.18f, 0.85f)
            hasVerts = true
        }

        if (preview != null && preview.xs.isNotEmpty()) {
            for (i in preview.xs.indices) {
                val px = preview.xs[i] + 0.5f
                val py = preview.ys[i] + 0.25f
                val pz = preview.zs[i] + 0.5f
                val kind = preview.kinds[i]
                val (r, g, b) = when (kind) {
                    StationType.TUNNEL.code -> Triple(0.62f, 0.32f, 0.96f)
                    StationType.VIADUCT.code -> Triple(0.05f, 0.72f, 0.88f)
                    StationType.CUT.code -> Triple(0.96f, 0.65f, 0.15f)
                    else -> Triple(0.18f, 0.82f, 0.42f)
                }
                drawMarkerPillar(builder, matrix, px, py, pz, 0.22f, 0.45f, r, g, b, 0.75f)
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

    private fun drawMarkerPillar(
        bb: com.mojang.blaze3d.vertex.BufferBuilder,
        m: Matrix4f,
        cx: Float, y0: Float, cz: Float,
        halfW: Float, height: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val x0 = cx - halfW
        val x1 = cx + halfW
        val z0 = cz - halfW
        val z1 = cz + halfW
        val y1 = y0 + height

        // Front / back / left / right / top
        bb.addVertex(m, x0, y0, z1).setColor(r, g, b, a)
        bb.addVertex(m, x1, y0, z1).setColor(r, g, b, a)
        bb.addVertex(m, x1, y1, z1).setColor(r, g, b, a)
        bb.addVertex(m, x0, y1, z1).setColor(r, g, b, a)

        bb.addVertex(m, x1, y0, z0).setColor(r, g, b, a)
        bb.addVertex(m, x0, y0, z0).setColor(r, g, b, a)
        bb.addVertex(m, x0, y1, z0).setColor(r, g, b, a)
        bb.addVertex(m, x1, y1, z0).setColor(r, g, b, a)

        bb.addVertex(m, x0, y0, z0).setColor(r, g, b, a)
        bb.addVertex(m, x0, y0, z1).setColor(r, g, b, a)
        bb.addVertex(m, x0, y1, z1).setColor(r, g, b, a)
        bb.addVertex(m, x0, y1, z0).setColor(r, g, b, a)

        bb.addVertex(m, x1, y0, z1).setColor(r, g, b, a)
        bb.addVertex(m, x1, y0, z0).setColor(r, g, b, a)
        bb.addVertex(m, x1, y1, z0).setColor(r, g, b, a)
        bb.addVertex(m, x1, y1, z1).setColor(r, g, b, a)

        bb.addVertex(m, x0, y1, z1).setColor(r, g, b, a)
        bb.addVertex(m, x1, y1, z1).setColor(r, g, b, a)
        bb.addVertex(m, x1, y1, z0).setColor(r, g, b, a)
        bb.addVertex(m, x0, y1, z0).setColor(r, g, b, a)
    }
}
