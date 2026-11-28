package net.montoyo.wd.controls.builtin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.montoyo.wd.controls.ScreenControl
import net.montoyo.wd.core.MissingPermissionException
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Vector3i
import java.util.function.Function

class SetURLControl : ScreenControl {
    internal var url: String
    internal var remoteLocation: Vector3i? = null

    constructor(url: String, remoteLocation: Vector3i?) : super(id) {
        this.url = url
        this.remoteLocation = remoteLocation
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        url = buf.readUtf()
        if (buf.readBoolean()) {
            remoteLocation = Vector3i(buf)
        }
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(url)
        buf.writeBoolean(remoteLocation != null)
        remoteLocation?.writeTo(buf)
    }

    @Throws(MissingPermissionException::class)
    override fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    ) {
        checkPerms(ScreenRights.CHANGE_URL, permissionChecker, ctx.sender)
        try {
            tes.setScreenURL(side!!, url)
        } catch (err: Throwable) {
            err.printStackTrace()
        }
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        try {
            tes.setScreenURL(side!!, url)
        } catch (err: Throwable) {
            err.printStackTrace()
        }
    }

    companion object {
        @JvmField
        val id: ResourceLocation = ResourceLocation.parse("webdisplays:set_url")
    }
}
