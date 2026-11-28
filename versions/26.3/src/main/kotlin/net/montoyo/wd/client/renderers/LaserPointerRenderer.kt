/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.renderers

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import net.montoyo.wd.client.ClientProxy
import net.montoyo.wd.client.ScreenInputController
import net.montoyo.wd.item.ItemLaserPointer

@Environment(EnvType.CLIENT)
object LaserPointerRenderer {
    @JvmStatic
    fun register() {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(::renderBeam)
    }

    @JvmStatic
    fun isOn(): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.gui.screen() != null) return false
        val player = mc.player ?: return false
        if (mc.level == null) return false

        return (ClientProxy.mouseOn || ScreenInputController.isAnyButtonPressed()) &&
            ScreenInputController.isLaserHeld(player)
    }

    private fun renderBeam(context: LevelRenderContext) {
        if (!isOn()) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val end = ScreenInputController.getBeamEnd(mc, ScreenInputController.LASER_REACH) ?: return

        val camera = context.levelState().cameraRenderState.pos
        val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val eye = player.getEyePosition(pt)
        val look = player.getViewVector(pt)
        val right = look.cross(Vec3(0.0, 1.0, 0.0))
        val rightNorm = if (right.lengthSqr() > 1.0e-6) right.normalize() else Vec3(1.0, 0.0, 0.0)
        val handSign = if (player.getItemInHand(InteractionHand.MAIN_HAND).item is ItemLaserPointer) 1.0 else -1.0
        val start = eye.add(rightNorm.scale(0.28 * handSign)).add(0.0, -0.18, 0.0).add(look.scale(0.35))

        val poseStack: PoseStack = context.poseStack()
        poseStack.pushPose()
        poseStack.translate(-camera.x, -camera.y, -camera.z)

        val r = 0.015f
        context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox()) { pose, vc ->
            val sx = start.x.toFloat()
            val sy = start.y.toFloat()
            val sz = start.z.toFloat()
            val ex = end.x.toFloat()
            val ey = end.y.toFloat()
            val ez = end.z.toFloat()

            vc.addVertex(pose, sx - r, sy - r, sz).setColor(255, 30, 30, 200)
            vc.addVertex(pose, sx + r, sy + r, sz).setColor(255, 30, 30, 200)
            vc.addVertex(pose, ex + r, ey + r, ez).setColor(255, 30, 30, 200)
            vc.addVertex(pose, ex - r, ey - r, ez).setColor(255, 30, 30, 200)

            vc.addVertex(pose, sx - r, sy + r, sz).setColor(255, 30, 30, 200)
            vc.addVertex(pose, sx + r, sy - r, sz).setColor(255, 30, 30, 200)
            vc.addVertex(pose, ex + r, ey - r, ez).setColor(255, 30, 30, 200)
            vc.addVertex(pose, ex - r, ey + r, ez).setColor(255, 30, 30, 200)
        }

        poseStack.popPose()
    }
}
