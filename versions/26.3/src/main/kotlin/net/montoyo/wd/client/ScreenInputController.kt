package net.montoyo.wd.client

import com.cinemamod.mcef.MCEFBrowser
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.montoyo.wd.block.BlockScreen
import net.montoyo.wd.config.ClientConfig
import net.montoyo.wd.controls.builtin.ClickControl
import net.montoyo.wd.core.DefaultUpgrade
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.init.BlockInit
import net.montoyo.wd.init.ItemInit
import net.montoyo.wd.item.ItemLaserPointer
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Multiblock
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector2i
import net.montoyo.wd.utilities.Vector3i
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Unified, low-latency client input controller for 26.3 world screens.
 * Handles laser pointer clicks/drags/scrolls (main hand or offhand, up to 64 blocks)
 * and instant client-predicted hand clicks with canonical button mapping:
 * 0 = Left, 1 = Right, 2 = Middle.
 */
@Environment(EnvType.CLIENT)
object ScreenInputController {
    const val BTN_LEFT = 0
    const val BTN_RIGHT = 1
    const val BTN_MIDDLE = 2
    const val LASER_REACH = 64.0

    private data class ActivePress(
        val tileEntity: TileEntityScreen,
        val side: BlockSide
    )

    data class ScreenTarget(
        val tileEntity: TileEntityScreen,
        val side: BlockSide,
        val screen: TileEntityScreen.Screen,
        val pixel: Vector2i,
        val hitPos: Vec3,
        val hasLaserUpgrade: Boolean,
        val canInteract: Boolean
    )

    private val pressedButtons = arrayOfNulls<ActivePress>(3)
    private var lastHoverTe: TileEntityScreen? = null
    private var lastHoverSide: BlockSide? = null
    private var lastLocalMoveX = Int.MIN_VALUE
    private var lastLocalMoveY = Int.MIN_VALUE
    private var lastNetMoveX = Int.MIN_VALUE
    private var lastNetMoveY = Int.MIN_VALUE
    private var lastMovePacketMs = 0L
    private var lastToastMs = 0L

    @JvmStatic
    fun isLaserHeld(player: Player?): Boolean {
        if (player == null) return false
        return player.getItemInHand(InteractionHand.MAIN_HAND).item is ItemLaserPointer ||
            player.getItemInHand(InteractionHand.OFF_HAND).item is ItemLaserPointer
    }

    @JvmStatic
    fun isLaserInMainHand(player: Player?): Boolean {
        if (player == null) return false
        return player.getItemInHand(InteractionHand.MAIN_HAND).item is ItemLaserPointer
    }

    @JvmStatic
    fun isAnyButtonPressed(): Boolean {
        return pressedButtons[0] != null || pressedButtons[1] != null || pressedButtons[2] != null
    }

    @JvmStatic
    fun resolveTarget(mc: Minecraft, maxDist: Double = LASER_REACH): ScreenTarget? {
        val level = mc.level ?: return null
        val player = mc.player ?: return null
        val result = ClientProxy.raycast(maxDist)
        if (result.type != HitResult.Type.BLOCK) return null

        val hitPos = result.blockPos
        if (level.getBlockState(hitPos).block !== BlockInit.blockScreen.get()) return null

        val sideIdx = result.direction.ordinal
        val side = BlockSide.entries.getOrNull(sideIdx) ?: return null
        val origin = Vector3i(hitPos)
        Multiblock.findOrigin(level, origin, side, null)

        val te = level.getBlockEntity(origin.toBlock()) as? TileEntityScreen ?: return null
        val scr = te.getScreen(side) ?: return null

        val hitX = (result.location.x - origin.x).toFloat()
        val hitY = (result.location.y - origin.y).toFloat()
        val hitZ = (result.location.z - origin.z).toFloat()
        val pixel = Vector2i()
        if (!BlockScreen.hit2pixels(side, hitPos, Vector3i(hitPos), scr, hitX, hitY, hitZ, pixel)) {
            return null
        }

        val hasLaser = te.hasUpgrade(side, DefaultUpgrade.LASERMOUSE)
        val canInteract = (scr.rightsFor(player) and ScreenRights.INTERACT) != 0
        return ScreenTarget(te, side, scr, pixel, result.location, hasLaser, canInteract)
    }

    @JvmStatic
    fun getBeamEnd(mc: Minecraft, maxDist: Double = LASER_REACH): Vec3? {
        mc.level ?: return null
        mc.player ?: return null
        val result = ClientProxy.raycast(maxDist)
        return result.location
    }

    @JvmStatic
    fun handleDirectHandClick(te: TileEntityScreen, side: BlockSide, pixel: Vector2i) {
        te.handleMouseEvent(side, ClickControl.ControlType.MOVE, pixel, -1)
        te.handleMouseEvent(side, ClickControl.ControlType.CLICK, pixel, BTN_LEFT)
    }

    @JvmStatic
    fun onMouseButton(
        mc: Minecraft,
        mouseHandler: MouseHandler,
        buttonInfo: MouseButtonInfo,
        action: Int
    ): Boolean {
        if (action != InputConstants.PRESS && action != InputConstants.RELEASE) {
            return false
        }
        val player = mc.player ?: return false
        if (mc.level == null || mc.gui.screen() != null) {
            return false
        }

        val mainHandLaser = isLaserInMainHand(player)
        val offHandLaser = !mainHandLaser && player.getItemInHand(InteractionHand.OFF_HAND).item is ItemLaserPointer
        if (!mainHandLaser && !offHandLaser) {
            return false
        }

        var btn = mapSdlButton(buttonInfo.button())
        if (btn < 0) return false
        if (btn <= 1 && ClientConfig.swapLaserButtons) {
            btn = 1 - btn
        }

        val press = action == InputConstants.PRESS
        if (!press) {
            val active = pressedButtons[btn]
            val target = resolveTarget(mc, LASER_REACH)
            var handled = active != null

            if (target != null && target.hasLaserUpgrade && target.canInteract && target.screen.browser != null) {
                target.tileEntity.handleMouseEvent(target.side, ClickControl.ControlType.MOVE, target.pixel, -1)
                target.tileEntity.handleMouseEvent(target.side, ClickControl.ControlType.UP, target.pixel, btn)
                WDNetworkRegistry.INSTANCE.sendToServer(
                    C2SMessageScreenCtrl.laserUp(target.tileEntity, target.side, btn)
                )
                handled = true
            }

            if (active != null && (target == null || active.tileEntity !== target.tileEntity || active.side != target.side)) {
                active.tileEntity.handleMouseEvent(active.side, ClickControl.ControlType.UP, null, btn)
                WDNetworkRegistry.INSTANCE.sendToServer(
                    C2SMessageScreenCtrl.laserUp(active.tileEntity, active.side, btn)
                )
            }

            pressedButtons[btn] = null
            return mainHandLaser || handled
        }

        if (!mouseHandler.isMouseGrabbed) {
            return false
        }

        val target = resolveTarget(mc, LASER_REACH)
        if (target == null) {
            return mainHandLaser
        }

        if (!target.hasLaserUpgrade) {
            showRateLimitedToast(player, "noLaserMouse")
            return true
        }
        if (!target.canInteract) {
            showRateLimitedToast(player, "restrictions")
            return true
        }
        if (target.screen.browser == null) {
            return true
        }

        target.tileEntity.handleMouseEvent(target.side, ClickControl.ControlType.MOVE, target.pixel, -1)
        target.tileEntity.handleMouseEvent(target.side, ClickControl.ControlType.DOWN, target.pixel, btn)
        pressedButtons[btn] = ActivePress(target.tileEntity, target.side)
        lastHoverTe = target.tileEntity
        lastHoverSide = target.side
        lastLocalMoveX = target.pixel.x
        lastLocalMoveY = target.pixel.y
        lastNetMoveX = target.pixel.x
        lastNetMoveY = target.pixel.y
        lastMovePacketMs = System.currentTimeMillis()

        WDNetworkRegistry.INSTANCE.sendToServer(
            C2SMessageScreenCtrl.laserDown(target.tileEntity, target.side, target.pixel, btn)
        )
        return true
    }

    @JvmStatic
    fun onMouseScroll(mc: Minecraft, xOffset: Double, yOffset: Double): Boolean {
        if (yOffset == 0.0) return false
        val player = mc.player ?: return false
        if (mc.level == null || mc.gui.screen() != null) return false
        if (!isLaserHeld(player)) return false

        val target = resolveTarget(mc, LASER_REACH) ?: return false
        if (!target.hasLaserUpgrade || !target.canInteract) return false

        val browser = target.screen.browser as? MCEFBrowser ?: return false
        val scr = target.screen
        val canonW = max(1, if (scr.rotation.isVertical) scr.resolution.y else scr.resolution.x)
        val canonH = max(1, if (scr.rotation.isVertical) scr.resolution.x else scr.resolution.y)
        val targetW = if (scr.renderWidth > 0) scr.renderWidth else canonW
        val targetH = if (scr.renderHeight > 0) scr.renderHeight else canonH
        val mx = max(0, min(targetW - 1, (target.pixel.x * targetW.toFloat() / canonW.toFloat()).roundToInt()))
        val my = max(0, min(targetH - 1, (target.pixel.y * targetH.toFloat() / canonH.toFloat()).roundToInt()))

        val mods = if (mc.hasControlDown() && !mc.hasAltDown() && !mc.hasShiftDown()) InputConstants.MOD_CONTROL else 0
        browser.sendMouseMove(mx, my)
        browser.sendMouseWheel(mx, my, yOffset, mods)
        return true
    }

    @JvmStatic
    fun tick(mc: Minecraft) {
        val player = mc.player
        val level = mc.level
        if (player == null || level == null || mc.gui.screen() != null || !isLaserHeld(player)) {
            releaseAll(mc)
            return
        }

        if (!ClientProxy.mouseOn && !isAnyButtonPressed()) {
            return
        }

        val target = resolveTarget(mc, LASER_REACH)
        if (target == null || !target.hasLaserUpgrade || !target.canInteract || target.screen.browser == null) {
            lastHoverTe = null
            lastHoverSide = null
            return
        }

        val changedScreen = lastHoverTe !== target.tileEntity || lastHoverSide != target.side
        val movedLocally = changedScreen || target.pixel.x != lastLocalMoveX || target.pixel.y != lastLocalMoveY

        if (movedLocally) {
            target.tileEntity.handleMouseEvent(target.side, ClickControl.ControlType.MOVE, target.pixel, -1)
            lastHoverTe = target.tileEntity
            lastHoverSide = target.side
            lastLocalMoveX = target.pixel.x
            lastLocalMoveY = target.pixel.y
        }

        val now = System.currentTimeMillis()
        val movedEnough = changedScreen ||
            abs(target.pixel.x - lastNetMoveX) >= 2 ||
            abs(target.pixel.y - lastNetMoveY) >= 2
        if (movedEnough && (changedScreen || now - lastMovePacketMs >= 50L)) {
            lastMovePacketMs = now
            lastNetMoveX = target.pixel.x
            lastNetMoveY = target.pixel.y
            WDNetworkRegistry.INSTANCE.sendToServer(
                C2SMessageScreenCtrl.laserMove(target.tileEntity, target.side, target.pixel)
            )
        }
    }

    @JvmStatic
    fun releaseAll(mc: Minecraft) {
        val hasLevel = mc.level != null
        for (btn in 0..2) {
            val active = pressedButtons[btn] ?: continue
            pressedButtons[btn] = null
            active.tileEntity.handleMouseEvent(active.side, ClickControl.ControlType.UP, null, btn)
            if (hasLevel) {
                WDNetworkRegistry.INSTANCE.sendToServer(
                    C2SMessageScreenCtrl.laserUp(active.tileEntity, active.side, btn)
                )
            }
        }
        lastHoverTe = null
        lastHoverSide = null
        lastLocalMoveX = Int.MIN_VALUE
        lastLocalMoveY = Int.MIN_VALUE
    }

    private fun mapSdlButton(mcButton: Int): Int {
        return when (mcButton) {
            InputConstants.MOUSE_BUTTON_LEFT -> BTN_LEFT
            InputConstants.MOUSE_BUTTON_RIGHT -> BTN_RIGHT
            InputConstants.MOUSE_BUTTON_MIDDLE -> BTN_MIDDLE
            else -> -1
        }
    }

    private fun showRateLimitedToast(player: Player, key: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastMs >= 2000L) {
            lastToastMs = now
            Util.toast(player, key)
        }
    }
}
