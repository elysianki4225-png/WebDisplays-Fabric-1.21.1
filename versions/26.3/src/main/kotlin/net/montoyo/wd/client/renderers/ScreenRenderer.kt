/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis.XN
import com.mojang.math.Axis.XP
import com.mojang.math.Axis.YN
import com.mojang.math.Axis.YP
import com.mojang.math.Axis.ZP
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.phys.Vec3
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.core.ScreenResolution
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector3f
import net.montoyo.wd.utilities.Vector3i
import org.joml.Matrix4f
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

class ScreenRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<TileEntityScreen, ScreenRenderer.ScreenRenderState> {
    class ScreenRendererProvider : BlockEntityRendererProvider<TileEntityScreen, ScreenRenderState> {
        override fun create(arg: BlockEntityRendererProvider.Context): BlockEntityRenderer<TileEntityScreen, ScreenRenderState> {
            return ScreenRenderer(arg)
        }
    }

    data class ScreenQuad(
        val textureId: Identifier,
        val side: BlockSide,
        val midX: Float,
        val midY: Float,
        val midZ: Float,
        val sw: Float,
        val sh: Float,
        val turnOnScale: Float,
        val hasRotation: Boolean,
        val rotationAngle: Float
    )

    class ScreenRenderState : BlockEntityRenderState() {
        @JvmField
        val quads = ArrayList<ScreenQuad>()
    }

    private val mid = Vector3f()
    private val tmpi = Vector3i()
    private val tmpf = Vector3f()

    override fun getViewDistance(): Int {
        return max(32, ceil(WebDisplays.INSTANCE.unloadDistance).toInt() + 8)
    }

    override fun shouldRenderOffScreen(): Boolean = true

    override fun createRenderState(): ScreenRenderState = ScreenRenderState()

    override fun extractRenderState(
        te: TileEntityScreen,
        state: ScreenRenderState,
        partialTick: Float,
        camPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(te, state, partialTick, camPos, crumblingOverlay)
        state.quads.clear()

        if (!te.isLoaded()) return

        val screenCount = te.screenCount()
        if (screenCount == 0) return

        val bp = te.blockPos
        val nowMs = System.currentTimeMillis()
        val nowNs = System.nanoTime()
        if (nowNs - windowStartNs >= 16_666_667L) {
            windowStartNs = nowNs
            createdInWindow = 0
        }

        val mcefReady = MCEF.isInitialized()

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

            if (WebDisplays.INSTANCE.enableBackfaceCulling) {
                val planeX = bp.x + mid.x + scr.side.forward.x * 0.505
                val planeY = bp.y + mid.y + scr.side.forward.y * 0.505
                val planeZ = bp.z + mid.z + scr.side.forward.z * 0.505
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

            scr.lastVisibleMs = nowMs

            val dx = camPos.x - (bp.x + mid.x)
            val dy = camPos.y - (bp.y + mid.y)
            val dz = camPos.z - (bp.z + mid.z)
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
            } else if (abs(targetLod - scr.currentLodScale) >= 0.1f &&
                nowMs - scr.lastLodResizeMs >= WebDisplays.INSTANCE.lodCooldownMs
            ) {
                scr.syncBrowserSize(targetLod)
                scr.lastLodResizeMs = nowMs
            }

            val mb = scr.browser
            if (mb is MCEFBrowser && mb.renderer.isReady) {
                mb.setVisible(true)
                val texLoc = mb.renderer.textureIdentifier ?: continue

                var turnOnScale = 1.0f
                if (scr.doTurnOnAnim) {
                    val lt = nowMs - scr.turnOnTime
                    var ft = lt.toFloat() / 100.0f

                    if (ft >= 1.0f) {
                        ft = 1.0f
                        scr.doTurnOnAnim = false
                    }
                    turnOnScale = ft
                }

                var sw = scr.size.x.toFloat() * 0.5f - 2.0f / 16.0f
                var sh = scr.size.y.toFloat() * 0.5f - 2.0f / 16.0f

                if (scr.rotation.isVertical) {
                    val tmp = sw
                    sw = sh
                    sh = tmp
                }

                state.quads.add(
                    ScreenQuad(
                        texLoc,
                        scr.side,
                        mid.x, mid.y, mid.z,
                        sw, sh,
                        turnOnScale,
                        !scr.rotation.isNull,
                        scr.rotation.angle
                    )
                )
            }
        }
    }

    override fun submit(
        state: ScreenRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        for (q in state.quads) {
            poseStack.pushPose()
            poseStack.translate(q.midX, q.midY, q.midZ)

            when (q.side) {
                BlockSide.BOTTOM -> poseStack.mulPose(Matrix4f().rotate(XP.rotation(90.0f + 49.8f)))
                BlockSide.TOP -> poseStack.mulPose(Matrix4f().rotate(XN.rotation(90.0f + 49.8f)))
                BlockSide.NORTH -> poseStack.mulPose(Matrix4f().rotate(YN.rotationDegrees(180.0f)))
                BlockSide.SOUTH -> {}
                BlockSide.WEST -> poseStack.mulPose(Matrix4f().rotate(YN.rotationDegrees(90.0f)))
                BlockSide.EAST -> poseStack.mulPose(Matrix4f().rotate(YP.rotationDegrees(90.0f)))
            }

            if (q.turnOnScale < 1.0f) {
                poseStack.scale(q.turnOnScale, q.turnOnScale, 1.0f)
            }

            if (q.hasRotation) {
                poseStack.mulPose(Matrix4f().rotate(ZP.rotationDegrees(q.rotationAngle)))
            }

            val sw = q.sw
            val sh = q.sh
            submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.armorCutoutNoCull(q.textureId)) { pose, vc ->
                vc.addVertex(pose, -sw, -sh, 0.505f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f)
                vc.addVertex(pose, sw, -sh, 0.505f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f)
                vc.addVertex(pose, sw, sh, 0.505f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f)
                vc.addVertex(pose, -sw, sh, 0.505f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f)
            }

            poseStack.popPose()
        }
    }

    companion object {
        private var windowStartNs = 0L
        private var createdInWindow = 0
    }
}
