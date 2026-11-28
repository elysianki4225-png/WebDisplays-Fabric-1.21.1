/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.server_bound

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.controls.ScreenControl
import net.montoyo.wd.controls.ScreenControlRegistry
import net.montoyo.wd.controls.builtin.AutoVolumeControl
import net.montoyo.wd.controls.builtin.JSRequestControl
import net.montoyo.wd.controls.builtin.KeyTypedControl
import net.montoyo.wd.controls.builtin.LaserControl
import net.montoyo.wd.controls.builtin.ManageRightsAndUpdgradesControl
import net.montoyo.wd.controls.builtin.ModifyFriendListControl
import net.montoyo.wd.controls.builtin.ScreenModifyControl
import net.montoyo.wd.controls.builtin.SetURLControl
import net.montoyo.wd.core.JSServerRequest
import net.montoyo.wd.core.MissingPermissionException
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.Rotation
import net.montoyo.wd.utilities.Vector2i
import net.montoyo.wd.utilities.Vector3i
import java.util.function.Function

// TODO: this is a mess; a registry based approach would likely be more readable
class C2SMessageScreenCtrl : Packet {
    @JvmField
    internal var control: ScreenControl? = null

    @JvmField
    internal var pos: BlockPos? = null

    @JvmField
    internal var side: BlockSide? = null

    constructor()

    constructor(screen: TileEntityScreen, side: BlockSide?, control: ScreenControl) {
        this.pos = screen.blockPos
        this.side = side
        this.control = control
    }

    @Deprecated("Legacy control", replaceWith = ReplaceWith(""))
    constructor(tes: TileEntityScreen, side: BlockSide?, friend: NameUUIDPair, del: Boolean) :
        this(tes, side, ModifyFriendListControl(friend, !del))

    @Deprecated("Legacy control", replaceWith = ReplaceWith(""))
    constructor(tes: TileEntityScreen, side: BlockSide?, fr: Int, or: Int) :
        this(tes, side, ManageRightsAndUpdgradesControl(fr, or))

    @Deprecated("Legacy control", replaceWith = ReplaceWith(""))
    constructor(tes: TileEntityScreen, side: BlockSide?, toRem: ItemStack) :
        this(tes, side, ManageRightsAndUpdgradesControl(false, toRem))

    @Deprecated("Legacy control", replaceWith = ReplaceWith(""))
    constructor(tes: TileEntityScreen, side: BlockSide?, rot: Rotation) :
        this(tes, side, ScreenModifyControl(rot))

    constructor(buf: FriendlyByteBuf) : super(buf) {
        pos = buf.readBlockPos()
        side = BufferUtils.readEnum(buf, { i -> BlockSide.values()[i] }, 1.toByte()) as BlockSide?
        this.control = ScreenControlRegistry.parse(buf)
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeBlockPos(pos!!)
        BufferUtils.writeEnum(buf, side, 1.toByte())

        val ctrl = control!!
        buf.writeUtf(ctrl.getId().toString())
        ctrl.write(buf)
    }

    @Throws(MissingPermissionException::class)
    fun checkPermission(sender: ServerPlayer, scr: TileEntityScreen, right: Int) {
        val screenSide = scr.getScreen(side!!) ?: return
        val prights = screenSide.rightsFor(sender)
        if ((prights and right) == 0) {
            throw MissingPermissionException(right, sender)
        }
    }

    override fun handle(ctx: WDContext) {
        if (checkServer(ctx)) {
            ctx.enqueueWork {
                try {
                    val level = WebDisplays.PROXY.getWorld(ctx) as Level
                    val p = pos!!
                    val be = level.getBlockEntity(p)
                    if (be is TileEntityScreen) {
                        control!!.handleServer(p, side, be, ctx, Function { perm ->
                            try {
                                checkPermission(ctx.sender!!, be, perm)
                                true
                            } catch (_: Throwable) {
                                false
                            }
                        })
                    }
                } catch (e: MissingPermissionException) {
                    e.printStackTrace()
                } catch (_: Throwable) {
                }
            }
            ctx.setPacketHandled(true)
        }
    }

    companion object {
        @Deprecated("Legacy constant", level = DeprecationLevel.WARNING)
        @JvmField
        val CTRL_LASER_MOVE = 0

        @Deprecated("Legacy constant", level = DeprecationLevel.WARNING)
        @JvmField
        val CTRL_LASER_UP = 0

        @Deprecated("Legacy constant", level = DeprecationLevel.WARNING)
        @JvmField
        val CTRL_LASER_DOWN = 0

        @Deprecated("Legacy constant", level = DeprecationLevel.WARNING)
        @JvmField
        val CTRL_SET_RESOLUTION = 0

        @JvmStatic
        protected fun base(screen: TileEntityScreen, side: BlockSide?): C2SMessageScreenCtrl {
            val packet = C2SMessageScreenCtrl()
            packet.pos = screen.blockPos
            packet.side = side
            return packet
        }

        @Deprecated("Legacy control", level = DeprecationLevel.WARNING)
        @JvmStatic
        fun setURL(
            tes: TileEntityScreen,
            side: BlockSide?,
            url: String,
            remoteLocation: Vector3i?
        ): C2SMessageScreenCtrl {
            val ret = base(tes, side)
            ret.control = SetURLControl(url, remoteLocation)
            return ret
        }

        @Deprecated("Legacy control", level = DeprecationLevel.WARNING)
        @JvmStatic
        fun vec2(tes: TileEntityScreen, side: BlockSide?, ctrl: Int, vec: Vector2i): C2SMessageScreenCtrl {
            throw RuntimeException("Moved: look into ScreenControlRegistry")
        }

        @Deprecated("Legacy control", level = DeprecationLevel.WARNING)
        @JvmStatic
        fun resolution(tes: TileEntityScreen, side: BlockSide?, vec: Vector2i): C2SMessageScreenCtrl {
            val ret = base(tes, side)
            ret.control = ScreenModifyControl(vec)
            return ret
        }

        @Deprecated("Legacy control", level = DeprecationLevel.WARNING)
        @JvmStatic
        fun type(
            tes: TileEntityScreen,
            side: BlockSide?,
            text: String,
            soundPos: BlockPos
        ): C2SMessageScreenCtrl {
            val ret = base(tes, side)
            ret.control = KeyTypedControl(text, soundPos)
            return ret
        }

        @JvmStatic
        fun laserMove(tes: TileEntityScreen, side: BlockSide?, vec: Vector2i): C2SMessageScreenCtrl {
            val ret = base(tes, side)
            ret.control = LaserControl(LaserControl.ControlType.MOVE, vec)
            return ret
        }

        @JvmStatic
        fun laserDown(
            tes: TileEntityScreen,
            side: BlockSide?,
            vec: Vector2i,
            button: Int
        ): C2SMessageScreenCtrl {
            val ret = base(tes, side)
            ret.control = LaserControl(LaserControl.ControlType.DOWN, vec, button)
            return ret
        }

        @Deprecated("Legacy control", level = DeprecationLevel.WARNING)
        @JvmStatic
        fun laserUp(tes: TileEntityScreen, side: BlockSide?, button: Int): C2SMessageScreenCtrl {
            val ret = base(tes, side)
            ret.control = LaserControl(LaserControl.ControlType.UP, null, button)
            return ret
        }

        @Deprecated("Legacy control", level = DeprecationLevel.WARNING)
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun jsRequest(
            tes: TileEntityScreen,
            side: BlockSide?,
            reqId: Int,
            reqType: JSServerRequest,
            vararg data: Any?
        ): C2SMessageScreenCtrl {
            val ret = base(tes, side)
            ret.control = JSRequestControl(reqId, reqType, data as Array<Any>)
            return ret
        }

        @Deprecated("Legacy control", level = DeprecationLevel.WARNING)
        @JvmStatic
        fun autoVol(tes: TileEntityScreen, side: BlockSide?, av: Boolean): C2SMessageScreenCtrl {
            val ret = base(tes, side)
            ret.control = AutoVolumeControl(av)
            return ret
        }
    }
}
