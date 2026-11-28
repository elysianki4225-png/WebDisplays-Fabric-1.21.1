/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.montoyo.wd.client.gui.GuiScreenConfig
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.net.WDDistributor
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.client_bound.S2CMessageOpenGui
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.Vector3i

class ScreenConfigData : GuiData {
    @JvmField
    var onlyUpdate: Boolean = false

    @JvmField
    var pos: Vector3i = Vector3i()

    @JvmField
    var side: BlockSide = BlockSide.BOTTOM

    @JvmField
    var friends: Array<NameUUIDPair> = emptyArray()

    @JvmField
    var friendRights: Int = 0

    @JvmField
    var otherRights: Int = 0

    constructor()

    constructor(pos: Vector3i, side: BlockSide, scr: TileEntityScreen.Screen) {
        this.pos = pos
        this.side = side
        friends = scr.friends.toTypedArray()
        friendRights = scr.friendRights
        otherRights = scr.otherRights
        onlyUpdate = false
    }

    @Environment(EnvType.CLIENT)
    override fun createGui(old: Screen?, world: Level): Screen? {
        if (old is GuiScreenConfig) {
            if (old.isForBlock(pos.toBlock(), side)) {
                old.updateFriends(friends)
                old.updateFriendRights(friendRights)
                old.updateOtherRights(otherRights)
                old.updateMyRights()
                return null
            }
        }

        if (onlyUpdate) return null

        val te = world.getBlockEntity(pos.toBlock())
        if (te !is TileEntityScreen) {
            Log.error("TileEntity at %s is not a screen; can't open gui!", pos.toString())
            return null
        }

        return GuiScreenConfig(Component.nullToEmpty(""), te, side, friends, friendRights, otherRights)
    }

    override fun getName(): String = "ScreenConfig"

    fun updateOnly(): ScreenConfigData {
        onlyUpdate = true
        return this
    }

    fun sendTo(tp: WDDistributor.TargetPoint) {
        WDNetworkRegistry.INSTANCE.send(WDDistributor.NEAR.with { tp }, S2CMessageOpenGui(this))
    }

    override fun serialize(buf: FriendlyByteBuf) {
        buf.writeBoolean(onlyUpdate)
        BufferUtils.writeVec3i(buf, pos)
        BufferUtils.writeEnum(buf, side, 1.toByte())
        BufferUtils.writeArray(buf, friends) { nameUUIDPair -> nameUUIDPair.writeTo(buf) }
        buf.writeInt(friendRights)
        buf.writeInt(otherRights)
    }

    override fun deserialize(buf: FriendlyByteBuf) {
        onlyUpdate = buf.readBoolean()
        pos = BufferUtils.readVec3i(buf)
        side = BufferUtils.readEnum(buf, { v -> BlockSide.entries[v] }, 1.toByte()) as BlockSide
        friends = BufferUtils.readArray(buf, emptyArray()) { NameUUIDPair(buf) }
        friendRights = buf.readInt()
        otherRights = buf.readInt()
    }
}
