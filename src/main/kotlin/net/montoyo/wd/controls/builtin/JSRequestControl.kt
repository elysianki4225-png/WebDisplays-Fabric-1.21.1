package net.montoyo.wd.controls.builtin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.montoyo.wd.controls.ScreenControl
import net.montoyo.wd.core.JSServerRequest
import net.montoyo.wd.core.MissingPermissionException
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import java.util.function.Function

class JSRequestControl : ScreenControl {
    internal var reqId: Int
    internal var reqType: JSServerRequest?
    internal var data: Array<Any>? = null

    constructor(reqId: Int, reqType: JSServerRequest?, data: Array<Any>?) : super(id) {
        this.reqId = reqId
        this.reqType = reqType
        this.data = data
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        reqId = buf.readInt()
        reqType = JSServerRequest.fromID(buf.readByte().toInt())
        data = reqType?.deserialize(buf)
    }

    override fun write(buf: FriendlyByteBuf) {
        val type = reqType!!
        buf.writeInt(reqId)
        buf.writeByte(type.ordinal)
        if (!type.serialize(buf, data!!)) {
            throw RuntimeException("Could not serialize CTRL_JS_REQUEST $type")
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
        // Legacy no-op preserved
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        throw RuntimeException("TODO")
    }

    companion object {
        @JvmField
        val id: ResourceLocation = ResourceLocation.parse("webdisplays:js_req")
    }
}
