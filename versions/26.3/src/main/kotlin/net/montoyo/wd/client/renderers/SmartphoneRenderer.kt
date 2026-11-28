package net.montoyo.wd.client.renderers

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.mojang.serialization.MapCodec
import org.joml.Matrix4f
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.special.SpecialModelRenderer
import net.minecraft.client.renderer.special.SpecialModelRenderers
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.ClientProxy
import net.montoyo.wd.utilities.NbtCompat
import org.joml.Vector3fc
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.PI
import kotlin.math.sin

@Environment(EnvType.CLIENT)
class SmartphoneRenderer : SpecialModelRenderer<UUID> {
    private val model = ModelSmartphone()

    override fun extractArgument(stack: ItemStack): UUID? {
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = data.copyTag()
        return if (NbtCompat.hasUUID(tag, "PadID")) NbtCompat.getUUID(tag, "PadID") else null
    }

    override fun submit(
        padId: UUID?,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        overlayCoords: Int,
        hasFoil: Boolean,
        outlineColor: Int
    ) {
        poseStack.pushPose()
        val tapProgress = if (padId != null) ClientProxy.getSmartphoneTapProgress() else 0f
        if (tapProgress > 0f) {
            val tap = sin(tapProgress * PI.toFloat())
            poseStack.translate(0.0f, -0.02f * tap, -0.015f * tap)
            poseStack.mulPose(Matrix4f().rotate(Axis.XP.rotationDegrees(-6.0f * tap)))
        }
        poseStack.translate(0.5f - ModelSmartphone.BODY_W * 0.5f, 0.5f - ModelSmartphone.BODY_H * 0.5f, 0.501f)
        model.submit(submitNodeCollector, poseStack, lightCoords, padId != null)
        renderScreenQuad(padId, poseStack, submitNodeCollector)
        poseStack.popPose()
    }

    fun renderDropped(
        padId: UUID?,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        spinYaw: Float
    ) {
        poseStack.pushPose()
        poseStack.translate(0.0f, 0.025f, 0.0f)
        poseStack.mulPose(Matrix4f().rotate(Axis.YP.rotation(-spinYaw)))
        poseStack.mulPose(Matrix4f().rotate(Axis.XP.rotationDegrees(-90.0f)))
        poseStack.scale(0.48f, 0.48f, 0.48f)
        poseStack.translate(-ModelSmartphone.BODY_W * 0.5f, -ModelSmartphone.BODY_H * 0.5f, 0.0f)

        model.submit(submitNodeCollector, poseStack, lightCoords, padId != null)
        renderScreenQuad(padId, poseStack, submitNodeCollector)

        poseStack.popPose()
    }

    private fun renderScreenQuad(padId: UUID?, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector) {
        if (padId != null && MCEF.isInitialized()) {
            val clientProxy = WebDisplays.PROXY
            if (clientProxy is ClientProxy) {
                val pd = clientProxy.getPadByID(padId)
                val view = pd?.view
                if (view is MCEFBrowser && view.renderer.isReady) {
                    val texLoc = view.renderer.textureIdentifier
                    if (texLoc != null) {
                        submitNodeCollector.order(1).submitCustomGeometry(poseStack, RenderTypes.armorCutoutNoCull(texLoc)) { pose, vc ->
                            vc.addVertex(pose, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y1, 0.002f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f)
                            vc.addVertex(pose, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y1, 0.002f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f)
                            vc.addVertex(pose, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y2, 0.002f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f)
                            vc.addVertex(pose, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y2, 0.002f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f)
                        }
                    }
                }
            }
        }
    }

    override fun getExtents(output: Consumer<Vector3fc>) {
    }

    class Unbaked : SpecialModelRenderer.Unbaked<UUID> {
        override fun type(): MapCodec<Unbaked> = MAP_CODEC

        override fun bake(context: SpecialModelRenderer.BakingContext): SpecialModelRenderer<UUID> {
            return SmartphoneRenderer()
        }

        override fun equals(other: Any?): Boolean = other is Unbaked

        override fun hashCode(): Int = javaClass.hashCode()

        companion object {
            @JvmField
            val MAP_CODEC: MapCodec<Unbaked> = MapCodec.unit(Unbaked())
        }
    }

    companion object {
        @JvmField
        val RENDERER_ID: Identifier = Identifier.fromNamespaceAndPath("webdisplays", "smartphone")

        @JvmStatic
        fun register() {
            SpecialModelRenderers.ID_MAPPER.put(RENDERER_ID, Unbaked.MAP_CODEC)
        }
    }
}
