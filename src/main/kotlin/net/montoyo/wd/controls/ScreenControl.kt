package net.montoyo.wd.controls

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.montoyo.wd.core.MissingPermissionException
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import java.util.Objects
import java.util.function.Function

abstract class ScreenControl(private val id: ResourceLocation) {
    abstract fun write(buf: FriendlyByteBuf)

    @Throws(MissingPermissionException::class)
    abstract fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    )

    @Environment(EnvType.CLIENT)
    abstract fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext)

    @Throws(MissingPermissionException::class)
    fun checkPerms(perms: Int, checker: Function<Int, Boolean>, player: ServerPlayer?) {
        if (!checker.apply(perms)) {
            throw MissingPermissionException(perms, Objects.requireNonNull(player)!!)
        }
    }

    fun getId(): ResourceLocation = id
}
