/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.item

import net.minecraft.client.Minecraft
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.HitResult
import net.montoyo.wd.block.BlockScreen
import net.montoyo.wd.client.ClientProxy
import net.montoyo.wd.config.ClientConfig
import net.montoyo.wd.controls.builtin.ClickControl
import net.montoyo.wd.core.DefaultUpgrade
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.init.BlockInit
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Multiblock
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector2i
import net.montoyo.wd.utilities.Vector3i

class ItemLaserPointer(properties: Properties) : Item(properties.stacksTo(1)), WDItem {
    override fun getWikiName(isStack: ItemStack): String = isStack.hoverName.string

    companion object {
        // Laser pointer
        private var pointedScreen: TileEntityScreen? = null
        private var pointedScreenSide: BlockSide? = null
        private var lastPointPacket: Long = 0

        private var activePressScreen: TileEntityScreen? = null
        private var activePressSide: BlockSide? = null
        private var activePressBtn: Int = -1
        private var lastUpgradeToastMs: Long = 0

        private var mouseDown = false
        private var left = false
        private var middle = false
        private var right = false

        @JvmStatic
        fun tick(mc: Minecraft) {
            val result = ClientProxy.raycast(64.0) // TODO: Make that distance configurable
            val bpos = result.blockPos
            val level = mc.level ?: return

            if (result.type == HitResult.Type.BLOCK && level.getBlockState(bpos).block == BlockInit.blockScreen.get()) {
                val pos = Vector3i(result.blockPos)
                val side = BlockSide.values()[result.direction.ordinal]

                Multiblock.findOrigin(level, pos, side, null)
                val te = level.getBlockEntity(pos.toBlock()) as? TileEntityScreen

                if (te != null && te.hasUpgrade(side, DefaultUpgrade.LASERMOUSE)) {
                    val scr = te.getScreen(side)
                    if (scr != null && scr.browser != null) {
                        val hitX = result.location.x.toFloat() - pos.x.toFloat()
                        val hitY = result.location.y.toFloat() - pos.y.toFloat()
                        val hitZ = result.location.z.toFloat() - pos.z.toFloat()
                        val tmp = Vector2i()

                        if (BlockScreen.hit2pixels(side, bpos, Vector3i(result.blockPos), scr, hitX, hitY, hitZ, tmp)) {
                            laserClick(te, side, scr, tmp)
                        }
                    }
                }
            }
        }

        @JvmStatic
        fun deselect(mc: Minecraft) {
            deselectScreen()
        }

        private fun laserClick(tes: TileEntityScreen, side: BlockSide, scr: TileEntityScreen.Screen, hit: Vector2i) {
            tes.handleMouseEvent(side, ClickControl.ControlType.MOVE, hit, -1)
            if (pointedScreen === tes && pointedScreenSide == side) {
                val t = System.currentTimeMillis()
                if (t - lastPointPacket >= 100) {
                    lastPointPacket = t
                    WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserMove(tes, side, hit))
                }
            } else {
                deselectScreen()
                pointedScreen = tes
                pointedScreenSide = side
            }
        }

        private fun deselectScreen() {
            val pressScr = activePressScreen
            val pressSide = activePressSide
            val pressBtn = activePressBtn
            if (pressScr != null && pressSide != null && pressBtn in 0..1) {
                pressScr.handleMouseEvent(pressSide, ClickControl.ControlType.UP, null, pressBtn)
                if (Minecraft.getInstance().level != null) {
                    WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserUp(pressScr, pressSide, pressBtn))
                }
            }
            activePressScreen = null
            activePressSide = null
            activePressBtn = -1
            left = false
            middle = false
            right = false

            if (pointedScreen != null && pointedScreenSide != null) {
                pointedScreen = null
                pointedScreenSide = null
            }
        }

        @JvmStatic
        fun press(press: Boolean, button: Int) {
            var btn = button
            if (btn <= 1 && ClientConfig.switchButtons) btn = 1 - btn

            when (btn) {
                0 -> left = press
                1 -> right = press
                2 -> middle = press
            }

            val mc = Minecraft.getInstance()
            val level = mc.level ?: return

            var handledOnHit = false
            val result = ClientProxy.raycast(64.0) // TODO: Make that distance configurable
            if (result.type == HitResult.Type.BLOCK && level.getBlockState(result.blockPos).block == BlockInit.blockScreen.get()) {
                val pos = Vector3i(result.blockPos)
                val side = BlockSide.values()[result.direction.ordinal]
                Multiblock.findOrigin(level, pos, side, null)

                val hitX = result.location.x.toFloat() - pos.x.toFloat()
                val hitY = result.location.y.toFloat() - pos.y.toFloat()
                val hitZ = result.location.z.toFloat() - pos.z.toFloat()
                val tmp = Vector2i()

                val te = level.getBlockEntity(pos.toBlock()) as? TileEntityScreen
                if (te != null && te.hasUpgrade(side, DefaultUpgrade.LASERMOUSE)) {
                    val scr = te.getScreen(side)
                    if (scr != null && scr.browser != null) {
                        if (BlockScreen.hit2pixels(side, result.blockPos, Vector3i(result.blockPos), scr, hitX, hitY, hitZ, tmp)) {
                            te.handleMouseEvent(side, ClickControl.ControlType.MOVE, tmp, -1)
                            te.handleMouseEvent(side, if (press) ClickControl.ControlType.DOWN else ClickControl.ControlType.UP, tmp, btn)

                            if (press) {
                                if (btn <= 1) {
                                    activePressScreen = te
                                    activePressSide = side
                                    activePressBtn = btn
                                }
                                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserDown(te, side, tmp, btn))
                            } else {
                                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserUp(te, side, btn))
                                val prevScr = activePressScreen
                                val prevSide = activePressSide
                                if (prevScr != null && prevSide != null && (prevScr !== te || prevSide != side)) {
                                    prevScr.handleMouseEvent(prevSide, ClickControl.ControlType.UP, null, btn)
                                    WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserUp(prevScr, prevSide, btn))
                                }
                            }
                            handledOnHit = true
                        }
                    }
                } else if (press && te != null && te.getScreen(side) != null && !te.hasUpgrade(side, DefaultUpgrade.LASERMOUSE)) {
                    val now = System.currentTimeMillis()
                    if (now - lastUpgradeToastMs >= 2000L) {
                        lastUpgradeToastMs = now
                        mc.player?.let { Util.toast(it, "noLaserMouse") }
                    }
                }
            }

            if (!press) {
                if (!handledOnHit) {
                    val prevScr = activePressScreen
                    val prevSide = activePressSide
                    if (prevScr != null && prevSide != null) {
                        prevScr.handleMouseEvent(prevSide, ClickControl.ControlType.UP, null, btn)
                        WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserUp(prevScr, prevSide, btn))
                    }
                }
                if (activePressBtn == btn || btn <= 1) {
                    activePressScreen = null
                    activePressSide = null
                    activePressBtn = -1
                }
            }
        }

        @JvmStatic
        fun isOn(): Boolean = left || right || middle
    }
}
