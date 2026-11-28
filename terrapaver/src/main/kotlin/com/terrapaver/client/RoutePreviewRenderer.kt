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
        val wps = ClientPavingState.waypoints
        val preview = ClientPavingState.preview
        if (wps.isEmpty() && preview == null) return

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

        for (i in wps.indices) {
            val wp = wps[i]
            val (r, g, b) = when {
                i == 0 -> Triple(0.10f, 0.45f, 0.91f) // Start: Blue
                i == wps.lastIndex -> Triple(0.95f, 0.35f, 0.18f) // End: Coral Red
                else -> Triple(0.98f, 0.74f, 0.05f) // Intermediate waypoints: Gold
            }
            drawMarkerPillar(builder, matrix, wp.x + 0.5f, wp.y + 1.0f, wp.z + 0.5f, 0.20f, 3.2f, r, g, b, 0.88f)
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
                drawMarkerPillar(builder, matrix, px, py, pz, 0.20f, 0.45f, r, g, b, 0.72f)
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
