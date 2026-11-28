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
import net.montoyo.wd.utilities.NameUUIDPair
import java.util.function.Function

class OwnerControl : ScreenControl {
    internal var owner: NameUUIDPair

    constructor(pair: NameUUIDPair) : super(id) {
        this.owner = pair
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        owner = NameUUIDPair(buf)
    }

    override fun write(buf: FriendlyByteBuf) {
        owner.writeTo(buf)
    }

    @Throws(MissingPermissionException::class)
    override fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    ) {
        throw RuntimeException("Cannot handle ownership theft packet from server")
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        tes.getScreen(side!!).owner = owner
    }

    companion object {
        @JvmField
        val id: ResourceLocation = ResourceLocation.parse("webdisplays:set_owner")
    }
}
