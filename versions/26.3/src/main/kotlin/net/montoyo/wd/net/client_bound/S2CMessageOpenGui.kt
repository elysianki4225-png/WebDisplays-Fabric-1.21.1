/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.net.client_bound

import net.minecraft.network.FriendlyByteBuf
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.data.GuiData
import net.montoyo.wd.net.Packet
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.Log

class S2CMessageOpenGui : Packet {
    private var data: GuiData?

    constructor(data: GuiData) {
        this.data = data
    }

    constructor(buf: FriendlyByteBuf) : super(buf) {
        val name = buf.readUtf()
        data = GuiData.read(name, buf)
        val cls = GuiData.classOf(name)

        if (cls == null) {
            Log.error("Could not create GuiData of type %s because it doesn't exist!", name)
        }
    }

    override fun write(buf: FriendlyByteBuf) {
        val d = data!!
        buf.writeUtf(d.getName())
        d.serialize(buf)
    }

    override fun handle(context: WDContext) {
        if (checkClient(context)) {
            context.enqueueWork { WebDisplays.PROXY.displayGui(data!!) }
            context.setPacketHandled(true)
        }
    }
}
