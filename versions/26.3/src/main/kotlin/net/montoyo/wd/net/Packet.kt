package net.montoyo.wd.net

import net.minecraft.network.FriendlyByteBuf

open class Packet {
    constructor()

    constructor(buf: FriendlyByteBuf)

    open fun write(buf: FriendlyByteBuf) {
    }

    open fun handle(ctx: WDContext) {
    }

    open fun checkClient(ctx: WDContext): Boolean = ctx.isClientSide

    open fun checkServer(ctx: WDContext): Boolean = ctx.isServerSide

    open fun respond(ctx: WDContext, packet: Packet) {
        ctx.enqueueWork { WDNetworkRegistry.INSTANCE.reply(packet, ctx) }
    }
}
