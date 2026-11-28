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
import java.util.function.Function

class KeyTypedControl : ScreenControl {
    internal var text: String
    internal var soundPos: BlockPos

    constructor(text: String, soundPos: BlockPos) : super(id) {
        this.text = text
        this.soundPos = soundPos
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        text = buf.readUtf()
        soundPos = buf.readBlockPos()
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(text)
        buf.writeBlockPos(soundPos)
    }

    @Throws(MissingPermissionException::class)
    override fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    ) {
        checkPerms(ScreenRights.INTERACT, permissionChecker, ctx.sender)
        tes.type(side!!, text, soundPos, ctx.sender)
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        tes.type(side!!, text, soundPos)
    }

    companion object {
        @JvmField
        val id: ResourceLocation = ResourceLocation.parse("webdisplays:type")
    }
}
