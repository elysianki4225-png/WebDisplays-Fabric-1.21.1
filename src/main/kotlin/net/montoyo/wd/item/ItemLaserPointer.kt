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
import net.montoyo.wd.utilities.Vector2i
import net.montoyo.wd.utilities.Vector3i

class ItemLaserPointer(properties: Properties) : Item(properties.stacksTo(1)), WDItem {
    override fun getWikiName(isStack: ItemStack): String = isStack.item.getName(isStack).string

    companion object {
        // Laser pointer
        private var pointedScreen: TileEntityScreen? = null
        private var pointedScreenSide: BlockSide? = null
        private var lastPointPacket: Long = 0

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

            val result = ClientProxy.raycast(64.0) // TODO: Make that distance configurable
            val pos = Vector3i(result.blockPos)
            val side = BlockSide.values()[result.direction.ordinal]
            Multiblock.findOrigin(level, pos, side, null)

            val hitX = result.location.x.toFloat() - pos.x.toFloat()
            val hitY = result.location.y.toFloat() - pos.y.toFloat()
            val hitZ = result.location.z.toFloat() - pos.z.toFloat()
            val tmp = Vector2i()

            val te = level.getBlockEntity(pos.toBlock()) as? TileEntityScreen ?: return
            if (te.hasUpgrade(side, DefaultUpgrade.LASERMOUSE)) {
                val scr = te.getScreen(side)
                if (scr != null && scr.browser != null) {
                    if (BlockScreen.hit2pixels(side, result.blockPos, Vector3i(result.blockPos), scr, hitX, hitY, hitZ, tmp)) {
                        te.handleMouseEvent(side, ClickControl.ControlType.MOVE, tmp, -1)
                        te.handleMouseEvent(side, if (press) ClickControl.ControlType.DOWN else ClickControl.ControlType.UP, tmp, btn)

                        if (press) {
                            WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserDown(te, side, tmp, btn))
                        } else {
                            WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserUp(te, side, btn))
                        }
                    }
                }
            }
        }

        @JvmStatic
        fun isOn(): Boolean = left || right || middle
    }
}
