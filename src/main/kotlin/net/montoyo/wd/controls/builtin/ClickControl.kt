package net.montoyo.wd.controls.builtin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
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

    constructor(type: ControlType, coord: Vector2i?) : this(type, coord, -1)

    constructor(type: ControlType, coord: Vector2i?, button: Int) : super(id) {
        this.type = type
        this.coord = coord
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        type = ControlType.entries[buf.readByte().toInt()]
        coord = Vector2i(buf)
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeByte(type.ordinal)
        coord!!.writeTo(buf)
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
            tes.handleMouseEvent(side!!, ControlType.MOVE, coord!!, -1)
        }
        tes.handleMouseEvent(side!!, type, coord!!, 1)
    }

    companion object {
        @JvmField
        val id: ResourceLocation = ResourceLocation.parse("webdisplays:click")
    }
}
