/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.client_bound

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.controls.ScreenControl
import net.montoyo.wd.controls.ScreenControlRegistry
import net.montoyo.wd.controls.builtin.AutoVolumeControl
import net.montoyo.wd.controls.builtin.ClickControl
import net.montoyo.wd.controls.builtin.KeyTypedControl
import net.montoyo.wd.controls.builtin.ManageRightsAndUpdgradesControl
import net.montoyo.wd.controls.builtin.OwnerControl
import net.montoyo.wd.controls.builtin.ScreenModifyControl
import net.montoyo.wd.controls.builtin.SetURLControl
import net.montoyo.wd.controls.builtin.TurnOffControl
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.Rotation
import net.montoyo.wd.utilities.Vector2i
import net.montoyo.wd.utilities.Vector3i

// TODO: use registry based approach
class S2CMessageScreenUpdate : Packet {
    @JvmField
    internal var control: ScreenControl? = null

    @JvmField
    internal var pos: BlockPos

    @JvmField
    internal var side: BlockSide?

    constructor(blockPos: BlockPos, side: BlockSide?) {
        this.pos = blockPos
        this.side = side
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        pos = buf.readBlockPos()
        side = BufferUtils.readEnum(buf, { i -> BlockSide.values()[i] }, 1.toByte()) as BlockSide?
        this.control = ScreenControlRegistry.parse(buf)
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeBlockPos(pos)
        BufferUtils.writeEnum(buf, side, 1.toByte())

        val ctrl = control!!
        buf.writeUtf(ctrl.getId().toString())
        ctrl.write(buf)
    }

    override fun handle(ctx: WDContext) {
        if (checkClient(ctx)) {
            ctx.enqueueWork {
                val level = WebDisplays.PROXY.getWorld(ctx) as Level
                val be = level.getBlockEntity(pos)
                if (be is TileEntityScreen) {
                    control!!.handleClient(pos, side, be, ctx)
                }
            }
            ctx.setPacketHandled(true)
        }
    }

    companion object {
        @JvmStatic
        fun setURL(screen: TileEntityScreen, side: BlockSide?, weburl: String): S2CMessageScreenUpdate {
            val screenUpdate = S2CMessageScreenUpdate(screen.blockPos, side)
            screenUpdate.control = SetURLControl(weburl, Vector3i(screenUpdate.pos))
            return screenUpdate
        }

        @JvmStatic
        fun setResolution(screen: TileEntityScreen, side: BlockSide?, res: Vector2i): S2CMessageScreenUpdate {
            val screenUpdate = S2CMessageScreenUpdate(screen.blockPos, side)
            screenUpdate.control = ScreenModifyControl(res)
            return screenUpdate
        }

        @JvmStatic
        fun rotation(screen: TileEntityScreen, side: BlockSide?, rot: Rotation): S2CMessageScreenUpdate {
            val screenUpdate = S2CMessageScreenUpdate(screen.blockPos, side)
            screenUpdate.control = ScreenModifyControl(rot)
            return screenUpdate
        }

        @JvmStatic
        fun upgrade(
            screen: TileEntityScreen,
            side: BlockSide?,
            adding: Boolean,
            stack: ItemStack
        ): S2CMessageScreenUpdate {
            val screenUpdate = S2CMessageScreenUpdate(screen.blockPos, side)
            screenUpdate.control = ManageRightsAndUpdgradesControl(adding, stack)
            return screenUpdate
        }

        @JvmStatic
        @JvmOverloads
        fun click(
            screen: TileEntityScreen,
            side: BlockSide?,
            mouseMove: ClickControl.ControlType,
            pos: Vector2i?,
            button: Int = 1
        ): S2CMessageScreenUpdate {
            val screenUpdate = S2CMessageScreenUpdate(screen.blockPos, side)
            screenUpdate.control = ClickControl(mouseMove, pos, button)
            return screenUpdate
        }

        @JvmStatic
        fun type(screen: TileEntityScreen, side: BlockSide?, text: String): S2CMessageScreenUpdate {
            val screenUpdate = S2CMessageScreenUpdate(screen.blockPos, side)
            screenUpdate.control = KeyTypedControl(text, screenUpdate.pos)
            return screenUpdate
        }

        @JvmStatic
        fun autoVolume(screen: TileEntityScreen, side: BlockSide?, av: Boolean): S2CMessageScreenUpdate {
            val screenUpdate = S2CMessageScreenUpdate(screen.blockPos, side)
            screenUpdate.control = AutoVolumeControl(av)
            return screenUpdate
        }

        @JvmStatic
        fun owner(screen: TileEntityScreen, side: BlockSide?, owner: NameUUIDPair): S2CMessageScreenUpdate {
            val screenUpdate = S2CMessageScreenUpdate(screen.blockPos, side)
            screenUpdate.control = OwnerControl(owner)
            return screenUpdate
        }

        @JvmStatic
        fun turnOff(blockPos: BlockPos, side: BlockSide?): S2CMessageScreenUpdate {
            val screenUpdate = S2CMessageScreenUpdate(blockPos, side)
            screenUpdate.control = TurnOffControl.INSTANCE
            return screenUpdate
        }
    }
}
