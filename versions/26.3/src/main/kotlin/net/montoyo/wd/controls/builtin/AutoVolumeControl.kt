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
import java.util.function.Function

class AutoVolumeControl : ScreenControl {
    internal var autoVol: Boolean

    constructor(autoVol: Boolean) : super(id) {
        this.autoVol = autoVol
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        autoVol = buf.readBoolean()
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeBoolean(autoVol)
    }

    @Throws(MissingPermissionException::class)
    override fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    ) {
        checkPerms(ScreenRights.MANAGE_UPGRADES, permissionChecker, ctx.sender)
        tes.setAutoVolume(side!!, autoVol)
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        tes.setAutoVolume(side!!, autoVol)
    }

    companion object {
        @JvmField
        val id: Identifier = Identifier.parse("webdisplays:auto_volume")
    }
}
