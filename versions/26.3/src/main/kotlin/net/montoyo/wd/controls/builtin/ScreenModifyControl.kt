package net.montoyo.wd.controls.builtin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.Identifier
import net.montoyo.wd.controls.ScreenControl
import net.montoyo.wd.core.MissingPermissionException
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Rotation
import net.montoyo.wd.utilities.Vector2i
import java.util.function.Function

class ScreenModifyControl : ScreenControl {
    enum class ControlType {
        RESOLUTION, ROTATION
    }

    internal var type: ControlType
    internal var res: Vector2i? = null
    internal var rotation: Rotation? = null

    constructor(res: Vector2i) : super(id) {
        this.type = ControlType.RESOLUTION
        this.res = res
    }

    constructor(rotation: Rotation) : super(id) {
        this.type = ControlType.ROTATION
        this.rotation = rotation
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        type = ControlType.entries[buf.readByte().toInt()]
        if (type == ControlType.RESOLUTION) {
            res = Vector2i(buf)
        } else {
            rotation = Rotation.entries[buf.readByte().toInt()]
        }
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeByte(type.ordinal)
        res?.writeTo(buf) ?: rotation?.let { buf.writeByte(it.ordinal) }
    }

    @Throws(MissingPermissionException::class)
    override fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    ) {
        checkPerms(ScreenRights.MODIFY_SCREEN, permissionChecker, ctx.sender)
        when (type) {
            ControlType.RESOLUTION -> tes.setResolution(side!!, res!!)
            ControlType.ROTATION -> tes.setRotation(side!!, rotation!!)
        }
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        when (type) {
            ControlType.RESOLUTION -> tes.setResolution(side!!, res!!)
            ControlType.ROTATION -> tes.setRotation(side!!, rotation!!)
        }
    }

    companion object {
        @JvmField
        val id: Identifier = Identifier.parse("webdisplays:mod_screen")
    }
}
