package net.montoyo.wd.client.renderers

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.math.Axis.XP
import com.mojang.math.Axis.YP
import com.mojang.math.Axis.ZP
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.ClientProxy
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@Environment(EnvType.CLIENT)
class SmartphoneRenderer : IItemRenderer {
    private val mc = Minecraft.getInstance()
    private val model = ModelSmartphone()
    private val clientProxy = WebDisplays.PROXY as ClientProxy
    private val padIdCache = Collections.synchronizedMap(WeakHashMap<CustomData, UUID>())

    private var sinSqrtSwingProg1 = 0f
    private var sinSqrtSwingProg2 = 0f
    private var sinSwingProg1 = 0f
    private var sinSwingProg2 = 0f

    private fun extractPadId(data: CustomData): UUID? {
        val cached = padIdCache[data]
        if (cached != null) return cached

        val tag = data.copyTag()
        if (!tag.contains("PadID")) return null

        val id = tag.getUUID("PadID")
        padIdCache[data] = id
        return id
    }

    override fun render(
        stack: PoseStack,
        isItem: ItemStack,
        handSideSign: Float,
        swingProgress: Float,
        equipProgress: Float,
        multiBufferSource: MultiBufferSource,
        packedLight: Int
    ): Boolean {
        val piF = PI.toFloat()
        val sqrtSwingProg = sqrt(swingProgress.toDouble()).toFloat()
        sinSqrtSwingProg1 = sin((sqrtSwingProg * piF).toDouble()).toFloat()
        sinSqrtSwingProg2 = sin((sqrtSwingProg * piF * 2.0f).toDouble()).toFloat()
        sinSwingProg1 = sin((swingProgress * piF).toDouble()).toFloat()
        sinSwingProg2 = sin((swingProgress * swingProgress * piF).toDouble()).toFloat()

        val player = mc.player
        val tapProg = ClientProxy.getSmartphoneTapProgress()
        val tapSin = sin((tapProg * piF).toDouble()).toFloat()

        if (player != null && !player.isInvisible) {
            stack.pushPose()
            stack.translate(handSideSign * -0.04f * tapSin, 0.05f * tapSin, -0.05f * tapSin)
            renderArmFirstPerson(stack, multiBufferSource, packedLight, equipProgress, handSideSign)
            stack.popPose()
        }

        stack.pushPose()
        stack.translate(handSideSign * (-0.4f * sinSqrtSwingProg1 - 0.04f * tapSin), 0.2f * sinSqrtSwingProg2 + 0.05f * tapSin, -0.2f * sinSwingProg1 - 0.05f * tapSin)
        stack.translate(handSideSign * 0.52f, -0.48f - equipProgress * 0.6f, -0.78f)
        stack.mulPose(YP.rotationDegrees(handSideSign * (18.0f - sinSwingProg2 * 12.0f - tapSin * 6.0f)))
        stack.mulPose(ZP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * -14.0f))
        stack.mulPose(XP.rotationDegrees(sinSqrtSwingProg1 * -55.0f - 8.0f - tapSin * 14.0f))

        if (handSideSign >= 0) {
            stack.translate(-0.24f, 0.06f, 0.0f)
        } else {
            stack.translate(-0.18f, 0.06f, 0.0f)
        }

        model.render(multiBufferSource, stack, packedLight)

        if (multiBufferSource is MultiBufferSource.BufferSource) {
            multiBufferSource.endBatch()
        }

        val data = isItem.get(DataComponents.CUSTOM_DATA)
        if (MCEF.isInitialized() && data != null && data.contains("PadID")) {
            val padId = extractPadId(data)
            val pd = if (padId == null) null else clientProxy.getPadByID(padId)
            val view = pd?.view

            if (view is MCEFBrowser) {
                val texId = view.renderer.textureID
                if (texId != 0) {
                    RenderSystem.enableDepthTest()
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
                    RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
                    RenderSystem.setShaderTexture(0, texId)
                    val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
                    val mat = stack.last().pose()
                    buffer.addVertex(mat, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y1, 0.002f).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y1, 0.002f).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y2, 0.002f).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y2, 0.002f).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255)
                    BufferUploader.drawWithShader(buffer.buildOrThrow())
                }
            }
        }

        stack.popPose()
        RenderSystem.enableCull()

        return true
    }

    private fun renderArmFirstPerson(
        stack: PoseStack,
        buffer: MultiBufferSource,
        combinedLight: Int,
        equipProgress: Float,
        handSideSign: Float
    ) {
        val player = mc.player!!
        val tx = -0.3f * sinSqrtSwingProg1
        val ty = 0.4f * sinSqrtSwingProg2
        val tz = -0.4f * sinSwingProg1

        stack.translate(handSideSign * (tx + 0.64000005f), ty - 0.6f - equipProgress * 0.6f, tz - 0.71999997f)
        stack.mulPose(YP.rotationDegrees(handSideSign * 45.0f))
        stack.mulPose(YP.rotationDegrees(handSideSign * sinSqrtSwingProg1 * 70.0f))
        stack.mulPose(ZP.rotationDegrees(handSideSign * sinSwingProg2 * -20.0f))
        stack.translate(-handSideSign, 3.6f, 3.5f)
        stack.mulPose(ZP.rotationDegrees(handSideSign * 120.0f))
        stack.mulPose(XP.rotationDegrees(200.0f))
        stack.mulPose(YP.rotationDegrees(handSideSign * -135.0f))
        stack.translate(handSideSign * 5.6f, 0.0f, 0.0f)

        val playerRenderer = mc.entityRenderDispatcher.getRenderer(player) as PlayerRenderer
        RenderSystem.setShaderTexture(0, player.skin.texture())

        if (handSideSign >= 0.0f) {
            playerRenderer.renderRightHand(stack, buffer, combinedLight, player)
        } else {
            playerRenderer.renderLeftHand(stack, buffer, combinedLight, player)
        }
    }

    fun renderDropped(
        isItem: ItemStack,
        multiBufferSource: MultiBufferSource,
        stack: PoseStack,
        packedLight: Int
    ) {
        val data = isItem.get(DataComponents.CUSTOM_DATA)
        val isLit = data != null && data.contains("PadID")

        stack.pushPose()
        stack.translate(-ModelSmartphone.BODY_W * 0.5f, -ModelSmartphone.BODY_H * 0.5f, 0.0f)
        model.render(multiBufferSource, stack, packedLight, isLit)

        if (multiBufferSource is MultiBufferSource.BufferSource) {
            multiBufferSource.endBatch()
        }

        if (isLit && MCEF.isInitialized() && data != null) {
            val padId = extractPadId(data)
            val pd = if (padId == null) null else clientProxy.getPadByID(padId)
            val view = pd?.view

            if (view is MCEFBrowser) {
                val texId = view.renderer.textureID
                if (texId != 0) {
                    RenderSystem.enableDepthTest()
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
                    RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
                    RenderSystem.setShaderTexture(0, texId)
                    val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
                    val mat = stack.last().pose()
                    buffer.addVertex(mat, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y1, 0.002f).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y1, 0.002f).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, ModelSmartphone.SCREEN_X2, ModelSmartphone.SCREEN_Y2, 0.002f).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255)
                    buffer.addVertex(mat, ModelSmartphone.SCREEN_X1, ModelSmartphone.SCREEN_Y2, 0.002f).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255)
                    BufferUploader.drawWithShader(buffer.buildOrThrow())
                }
            }
        }

        stack.popPose()
        RenderSystem.enableCull()
    }
}
