package net.montoyo.wd.controls.builtin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.controls.ScreenControl
import net.montoyo.wd.core.MissingPermissionException
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import java.util.function.Function

class TurnOffControl : ScreenControl(id) {
    override fun write(buf: FriendlyByteBuf) {
    }

    @Throws(MissingPermissionException::class)
    override fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    ) {
        throw RuntimeException("Cannot handle deactivation packet from server")
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        if (side != null) {
            WebDisplays.PROXY.closeGui(pos, side)
            tes.disableScreen(side)
        } else {
            for (value in BlockSide.entries) {
                WebDisplays.PROXY.closeGui(pos, value)
                tes.disableScreen(value)
            }
        }
    }

    companion object {
        @JvmField
        val id: ResourceLocation = ResourceLocation.parse("webdisplays:deactivate")

        @JvmField
        val INSTANCE = TurnOffControl()
    }
}
