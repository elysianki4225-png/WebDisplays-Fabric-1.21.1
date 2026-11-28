/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.data

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.montoyo.wd.net.WDDistributor
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.client_bound.S2CMessageOpenGui
import java.util.function.Supplier

abstract class GuiData {
    protected class GuiType(
        @JvmField val clazz: Class<out GuiData>,
        @JvmField val constructor: Supplier<GuiData>
    ) {
        fun create(): GuiData = constructor.get()
    }

    @Environment(EnvType.CLIENT)
    abstract fun createGui(old: Screen?, world: Level): Screen?

    abstract fun getName(): String

    fun sendTo(player: ServerPlayer) {
        WDNetworkRegistry.INSTANCE.send(WDDistributor.PLAYER.with { player }, S2CMessageOpenGui(this))
    }

    abstract fun serialize(buf: FriendlyByteBuf)

    abstract fun deserialize(buf: FriendlyByteBuf)

    companion object {
        private val dataTable = HashMap<String, GuiType>().apply {
            put("SetURL", GuiType(SetURLData::class.java) { SetURLData() })
            put("ScreenConfig", GuiType(ScreenConfigData::class.java) { ScreenConfigData() })
            put("Keyboard", GuiType(KeyboardData::class.java) { KeyboardData() })
            put("RedstoneCtrl", GuiType(RedstoneCtrlData::class.java) { RedstoneCtrlData() })
            put("Server", GuiType(ServerData::class.java) { ServerData() })
        }

        @JvmStatic
        fun read(name: String, buf: FriendlyByteBuf): GuiData {
            val type = dataTable[name]!!
            val data = type.create()
            data.deserialize(buf)
            return data
        }

        @JvmStatic
        fun classOf(name: String): Class<out GuiData> = dataTable[name]!!.clazz
    }
}
