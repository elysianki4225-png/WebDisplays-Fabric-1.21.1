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

class ClickControl : ScreenControl {
    enum class ControlType {
        CLICK, MOVE, DOWN, UP
    }

    internal var type: ControlType
    internal var coord: Vector2i?
    internal var button: Int = 1

    constructor(type: ControlType, coord: Vector2i?) : this(type, coord, 0)

    constructor(type: ControlType, coord: Vector2i?, button: Int) : super(id) {
        this.type = type
        this.coord = coord
        this.button = button
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        type = ControlType.entries[buf.readByte().toInt()]
        coord = if (type != ControlType.UP) Vector2i(buf) else null
        button = buf.readInt()
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeByte(type.ordinal)
        if (type != ControlType.UP) {
            coord!!.writeTo(buf)
        }
        buf.writeInt(button)
    }

    @Throws(MissingPermissionException::class)
    override fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    ) {
        throw RuntimeException("Cannot call click control on server")
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        if (coord != null) {
            tes.handleMouseEvent(side!!, ControlType.MOVE, coord, -1)
        }
        tes.handleMouseEvent(side!!, type, coord, button)
    }

    companion object {
        @JvmField
        val id: Identifier = Identifier.parse("webdisplays:click")
    }
}
