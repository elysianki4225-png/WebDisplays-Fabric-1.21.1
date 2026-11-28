/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis.XN
import com.mojang.math.Axis.XP
import com.mojang.math.Axis.YN
import com.mojang.math.Axis.YP
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.core.ScreenResolution
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector3f
import net.montoyo.wd.utilities.Vector3i
import org.joml.Quaternionf
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

class ScreenRenderer : BlockEntityRenderer<TileEntityScreen> {
    class ScreenRendererProvider : BlockEntityRendererProvider<TileEntityScreen> {
        override fun create(arg: BlockEntityRendererProvider.Context): BlockEntityRenderer<TileEntityScreen> {
            return ScreenRenderer()
        }
    }

    private val mid = Vector3f()
    private val tmpi = Vector3i()
    private val tmpf = Vector3f()
    private val scratchRot = Quaternionf()

    override fun getViewDistance(): Int {
        return max(32, ceil(WebDisplays.INSTANCE.unloadDistance).toInt() + 8)
    }

    override fun render(
        te: TileEntityScreen,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        if (!te.isLoaded()) return

        val screenCount = te.screenCount()
        if (screenCount == 0) return

        val mc = Minecraft.getInstance()
        val camera = mc.gameRenderer.mainCamera
        val camPos = camera.position
        val camLook = if (mc.options.cameraType.isFirstPerson) camera.lookVector else null
        val bp = te.blockPos
        val nowMs = System.currentTimeMillis()
        val nowNs = System.nanoTime()
        if (nowNs - windowStartNs >= 16_666_667L) {
            windowStartNs = nowNs
            createdInWindow = 0
        }

        val mcefReady = MCEF.isInitialized()
        RenderSystem.disableBlend()

        for (i in 0 until screenCount) {
            val scr = te.getScreen(i)

            tmpi.set(scr.side.right)
            tmpi.mul(scr.size.x)
            tmpi.addMul(scr.side.up, scr.size.y)
            tmpf.set(tmpi)
            mid.set(0.5, 0.5, 0.5)
            mid.addMul(tmpf, 0.5f)
            tmpf.set(scr.side.left)
            mid.addMul(tmpf, 0.5f)
            tmpf.set(scr.side.down)
            mid.addMul(tmpf, 0.5f)

            val centerX = bp.x + mid.x
            val centerY = bp.y + mid.y
            val centerZ = bp.z + mid.z

            if (WebDisplays.INSTANCE.enableBackfaceCulling) {
                val planeX = centerX + scr.side.forward.x * 0.505
                val planeY = centerY + scr.side.forward.y * 0.505
                val planeZ = centerZ + scr.side.forward.z * 0.505
                val dot = (camPos.x - planeX) * scr.side.forward.x +
                    (camPos.y - planeY) * scr.side.forward.y +
                    (camPos.z - planeZ) * scr.side.forward.z

                if (dot < -0.1) {
                    if (WebDisplays.INSTANCE.pauseCulledScreens) {
                        val mb = scr.browser
                        if (mb is MCEFBrowser && nowMs - scr.lastVisibleMs > 500L) {
                            mb.setVisible(false)
                        }
                    }
                    continue
                }
            }

            if (camLook != null) {
                val toX = centerX - camPos.x
                val toY = centerY - camPos.y
                val toZ = centerZ - camPos.z
                val boundRadius = sqrt((scr.size.x * scr.size.x + scr.size.y * scr.size.y).toDouble()) * 0.5 + 1.5
                val forwardDist = toX * camLook.x + toY * camLook.y + toZ * camLook.z
                if (forwardDist < -boundRadius) {
                    if (WebDisplays.INSTANCE.pauseCulledScreens) {
                        val mb = scr.browser
                        if (mb is MCEFBrowser && nowMs - scr.lastVisibleMs > 500L) {
                            mb.setVisible(false)
                        }
                    }
                    continue
                }
            }

            scr.lastVisibleMs = nowMs

            val dx = camPos.x - centerX
            val dy = camPos.y - centerY
            val dz = camPos.z - centerZ
            val distSqr = dx * dx + dy * dy + dz * dz

            val targetLod = if (WebDisplays.INSTANCE.enableAdaptiveLod) {
                ScreenResolution.computeLodScale(distSqr, scr.size, scr.resolution.x, scr.resolution.y)
            } else {
                1.0f
            }

            if (scr.browser == null) {
                if (!mcefReady || createdInWindow >= WebDisplays.INSTANCE.maxBrowsersPerFrame) {
                    continue
                }
                createdInWindow++
                scr.createBrowser(true, targetLod)
                if (scr.browser == null) continue
            } else if (abs(targetLod - scr.currentLodScale) > 0.05f &&
                nowMs - scr.lastLodResizeMs >= WebDisplays.INSTANCE.lodCooldownMs
            ) {
                scr.syncBrowserSize(targetLod)
                scr.lastLodResizeMs = nowMs
            }

            val mb = scr.browser
            if (mb is MCEFBrowser) {
                mb.setVisible(true)
                val texId = mb.renderer.textureID
                if (texId == 0) continue

                poseStack.pushPose()
                poseStack.translate(mid.x, mid.y, mid.z)

                when (scr.side) {
                    BlockSide.BOTTOM -> poseStack.mulPose(QUAT_BOTTOM)
                    BlockSide.TOP -> poseStack.mulPose(QUAT_TOP)
                    BlockSide.NORTH -> poseStack.mulPose(QUAT_NORTH)
                    BlockSide.SOUTH -> {}
                    BlockSide.WEST -> poseStack.mulPose(QUAT_WEST)
                    BlockSide.EAST -> poseStack.mulPose(QUAT_EAST)
                }

                if (scr.doTurnOnAnim) {
                    val lt = nowMs - scr.turnOnTime
                    var ft = lt.toFloat() / 100.0f

                    if (ft >= 1.0f) {
                        ft = 1.0f
                        scr.doTurnOnAnim = false
                    }

                    poseStack.scale(ft, ft, 1.0f)
                }

                if (!scr.rotation.isNull) {
                    scratchRot.rotationZ(Math.toRadians(scr.rotation.angle.toDouble()).toFloat())
                    poseStack.mulPose(scratchRot)
                }

                var sw = scr.size.x.toFloat() * 0.5f - 2.0f / 16.0f
                var sh = scr.size.y.toFloat() * 0.5f - 2.0f / 16.0f

                if (scr.rotation.isVertical) {
                    val tmp = sw
                    sw = sh
                    sh = tmp
                }

                RenderSystem.enableDepthTest()
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
                RenderSystem.setShaderTexture(0, texId)
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
                val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
                val mat = poseStack.last().pose()
                builder.addVertex(mat, -sw, -sh, 0.505f).setUv(0.0f, 1.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f)
                builder.addVertex(mat, sw, -sh, 0.505f).setUv(1.0f, 1.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f)
                builder.addVertex(mat, sw, sh, 0.505f).setUv(1.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f)
                builder.addVertex(mat, -sw, sh, 0.505f).setUv(0.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f)
                BufferUploader.drawWithShader(builder.buildOrThrow())

                poseStack.popPose()
            }
        }

        RenderSystem.enableDepthTest()
    }

    companion object {
        private var windowStartNs = 0L
        private var createdInWindow = 0
        private val QUAT_BOTTOM = XP.rotation(90.0f + 49.8f)
        private val QUAT_TOP = XN.rotation(90.0f + 49.8f)
        private val QUAT_NORTH = YN.rotationDegrees(180.0f)
        private val QUAT_WEST = YN.rotationDegrees(90.0f)
        private val QUAT_EAST = YP.rotationDegrees(90.0f)
    }
}
