package net.montoyo.wd.controls.builtin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.Identifier
import net.montoyo.wd.controls.ScreenControl
import net.montoyo.wd.core.MissingPermissionException
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector2i
import java.util.function.Function

class LaserControl : ScreenControl {
    enum class ControlType {
        MOVE, DOWN, UP
    }

    internal var type: ControlType
    internal var coord: Vector2i? = null
    internal var button: Int = 0

    constructor(type: ControlType, coord: Vector2i?) : this(type, coord, -1)

    constructor(type: ControlType, coord: Vector2i?, button: Int) : super(id) {
        this.type = type
        this.coord = coord
        this.button = button
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        type = ControlType.entries[buf.readByte().toInt()]
        if (type != ControlType.UP) {
            coord = Vector2i(buf)
        }
        if (type != ControlType.MOVE) {
            button = buf.readInt()
        }
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeByte(type.ordinal)
        coord?.writeTo(buf)
        if (type != ControlType.MOVE) {
            buf.writeInt(button)
        }
    }

    @Throws(MissingPermissionException::class)
    override fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    ) {
        val sender = ctx.sender!!
        when (type) {
            ControlType.UP -> tes.laserUp(side!!, sender, button)
            ControlType.DOWN -> tes.laserDownMove(side!!, sender, coord!!, true, button)
            ControlType.MOVE -> tes.laserDownMove(side!!, sender, coord!!, false, button)
        }
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        coord?.let { tes.handleMouseEvent(side!!, ClickControl.ControlType.MOVE, it, -1) }
        when (type) {
            ControlType.UP -> tes.handleMouseEvent(side!!, ClickControl.ControlType.UP, coord, button)
            ControlType.DOWN -> tes.handleMouseEvent(side!!, ClickControl.ControlType.DOWN, coord, button)
            ControlType.MOVE -> Unit
        }
    }

    companion object {
        @JvmField
        val id: Identifier = Identifier.parse("webdisplays:laser")
    }
}
