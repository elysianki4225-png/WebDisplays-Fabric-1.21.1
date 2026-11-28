/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.client_bound

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.Level
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.block.BlockScreen
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.BufferUtils
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.Rotation
import net.montoyo.wd.utilities.Vector2i
import net.montoyo.wd.utilities.Vector3i
import java.io.IOException
import java.util.ArrayList

class S2CMessageAddScreen : Packet {
    private var clear: Boolean
    private var pos: Vector3i
    private var screens: Array<TileEntityScreen.Screen>

    constructor(tes: TileEntityScreen) {
        clear = true
        pos = Vector3i(tes.blockPos)
        screens = Array(tes.screenCount()) { i -> tes.getScreen(i) }
    }

    constructor(tes: TileEntityScreen, vararg toSend: TileEntityScreen.Screen) {
        clear = false
        pos = Vector3i(tes.blockPos)
        @Suppress("UNCHECKED_CAST")
        screens = toSend as Array<TileEntityScreen.Screen>
    }

    constructor(clear: Boolean, pos: Vector3i, screens: Array<TileEntityScreen.Screen>) {
        this.clear = clear
        this.pos = pos
        this.screens = screens
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        clear = buf.readBoolean()
        pos = Vector3i(buf)

        val cnt = buf.readByte().toInt() and 7
        screens = Array(cnt) {
            val scr = TileEntityScreen.Screen()
            scr.side = BlockSide.values()[buf.readByte().toInt()]
            scr.size = Vector2i(buf)
            scr.url = buf.readUtf()
            scr.resolution = Vector2i(buf)
            scr.rotation = Rotation.values()[buf.readByte().toInt() and 3]
            scr.owner = NameUUIDPair(buf)
            scr.upgrades = ArrayList()

            val numUpgrades = buf.readByte().toInt()
            for (j in 0 until numUpgrades) {
                scr.upgrades.add(BufferUtils.readItem(buf))
            }
            scr
        }
    }

    override fun write(buf: FriendlyByteBuf) {
        buf.writeBoolean(clear)
        pos.writeTo(buf)
        buf.writeByte(screens.size)

        for (scr in screens) {
            buf.writeByte(scr.side.ordinal)
            scr.size.writeTo(buf)
            buf.writeUtf(scr.url)
            scr.resolution.writeTo(buf)
            buf.writeByte(scr.rotation.ordinal)
            scr.owner?.writeTo(buf) ?: NameUUIDPair().writeTo(buf)
            buf.writeByte(scr.upgrades.size)

            for (isItem in scr.upgrades) {
                BufferUtils.writeItem(buf, isItem)
            }
        }
    }

    override fun handle(ctx: WDContext) {
        if (checkClient(ctx)) {
            ctx.enqueueWork {
                val lvl = WebDisplays.PROXY.getWorld(ctx) as Level
                var te = lvl.getBlockEntity(pos.toBlock())
                if (te !is TileEntityScreen) {
                    lvl.setBlockAndUpdate(pos.toBlock(), lvl.getBlockState(pos.toBlock()).setValue(BlockScreen.hasTE, true))
                    te = lvl.getBlockEntity(pos.toBlock())

                    if (te !is TileEntityScreen) {
                        if (clear) {
                            Log.error("CMessageAddScreen: Can't add screen to invalid tile entity at %s", pos.toString())
                        }
                        return@enqueueWork
                    }
                }

                if (clear) te.clear()

                for (entry in screens) {
                    val scr = te.addScreen(entry.side, entry.size, entry.resolution, null, false)
                    scr.rotation = entry.rotation
                    val webUrl = try {
                        TileEntityScreen.url(entry.url)
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }

                    scr.url = webUrl
                    scr.owner = entry.owner
                    scr.upgrades = entry.upgrades

                    if (scr.browser != null) {
                        scr.browser.loadURL(webUrl)
                    }
                }
            }

            ctx.setPacketHandled(true)
        }
    }
}
