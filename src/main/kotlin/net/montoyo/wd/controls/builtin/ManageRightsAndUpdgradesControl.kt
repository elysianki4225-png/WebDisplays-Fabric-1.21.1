package net.montoyo.wd.controls.builtin

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.controls.ScreenControl
import net.montoyo.wd.core.MissingPermissionException
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import java.util.function.Function

/**
 * TODO: I'm considering merging this with [ModifyFriendListControl] to make ManageScreenControl
 */
@Deprecated("Legacy control")
class ManageRightsAndUpdgradesControl : ScreenControl {
    enum class ControlType {
        RIGHTS, UPGRADES
    }

    internal var type: ControlType
    internal var adding: Boolean = false
    internal var toRemove: ItemStack = ItemStack.EMPTY
    private var friendRights: Int = 0
    private var otherRights: Int = 0

    constructor(adding: Boolean, toRemove: ItemStack) : super(id) {
        this.adding = adding
        type = ControlType.UPGRADES
        this.toRemove = toRemove
    }

    constructor(friendRights: Int, otherRights: Int) : super(id) {
        type = ControlType.RIGHTS
        this.friendRights = friendRights
        this.otherRights = otherRights
    }

    constructor(buf: FriendlyByteBuf) : super(id) {
        type = ControlType.entries[buf.readByte().toInt()]
        when (type) {
            ControlType.UPGRADES -> {
                adding = buf.readBoolean()
                toRemove = BufferUtils.readItem(buf)
            }
            ControlType.RIGHTS -> {
                friendRights = buf.readInt()
                otherRights = buf.readInt()
            }
        }
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeByte(type.ordinal)
        when (type) {
            ControlType.UPGRADES -> {
                buf.writeBoolean(adding)
                BufferUtils.writeItem(buf, toRemove)
            }
            ControlType.RIGHTS -> {
                buf.writeInt(friendRights)
                buf.writeInt(otherRights)
            }
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
        val player = ctx.sender
        when (type) {
            ControlType.UPGRADES -> {
                checkPerms(ScreenRights.MANAGE_UPGRADES, permissionChecker, player)
                if (adding) {
                    throw RuntimeException("Cannot add an upgrade from the client")
                } else {
                    tes.removeUpgrade(side!!, toRemove, player)
                }
            }
            ControlType.RIGHTS -> {
                val scr = tes.getScreen(side!!)
                val nonNullPlayer = player!!
                val fr = if (scr.owner.uuid == nonNullPlayer.gameProfile.id) friendRights else scr.friendRights
                val or = if ((scr.rightsFor(nonNullPlayer) and ScreenRights.MANAGE_OTHER_RIGHTS) == 0) scr.otherRights else otherRights
                if (scr.friendRights != fr || scr.otherRights != or) {
                    tes.setRights(nonNullPlayer, side, fr, or)
                }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    override fun handleClient(pos: BlockPos, side: BlockSide?, tes: TileEntityScreen, ctx: WDContext) {
        val player = ctx.sender
        when (type) {
            ControlType.UPGRADES -> {
                if (adding) {
                    tes.addUpgrade(side!!, toRemove, player, true)
                } else {
                    tes.removeUpgrade(side!!, toRemove, player)
                }
            }
            ControlType.RIGHTS -> {
                val scr = tes.getScreen(side!!)
                val fr = friendRights
                val or = otherRights
                if (scr.friendRights != fr || scr.otherRights != or) {
                    tes.setRights(player, side, fr, or)
                }
            }
        }
    }

    companion object {
        @JvmField
        val id: ResourceLocation = ResourceLocation.parse("webdisplays:mod_rights_upgrades")
    }
}
