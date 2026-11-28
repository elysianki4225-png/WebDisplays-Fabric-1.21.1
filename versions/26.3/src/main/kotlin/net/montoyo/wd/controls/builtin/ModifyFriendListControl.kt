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
import net.montoyo.wd.utilities.NameUUIDPair
import java.util.function.Function

class ModifyFriendListControl : ScreenControl {
    internal var adding: Boolean
    internal var friend: NameUUIDPair

    constructor(pair: NameUUIDPair, adding: Boolean) : super(id) {
        this.adding = adding
        this.friend = pair
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        adding = buf.readBoolean()
        friend = NameUUIDPair(buf)
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeBoolean(adding)
        friend.writeTo(buf)
    }

    @Throws(MissingPermissionException::class)
    override fun handleServer(
        pos: BlockPos,
        side: BlockSide?,
        tes: TileEntityScreen,
        ctx: WDContext,
        permissionChecker: Function<Int, Boolean>
    ) {
        val player = ctx.sender
        checkPerms(ScreenRights.MANAGE_FRIEND_LIST, permissionChecker, player)
        if (adding) tes.addFriend(player, side!!, friend)
        else tes.removeFriend(player, side!!, friend)
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        throw RuntimeException("TODO")
    }

    companion object {
        @JvmField
        val id: Identifier = Identifier.parse("webdisplays:mod_friend_list")
    }
}
