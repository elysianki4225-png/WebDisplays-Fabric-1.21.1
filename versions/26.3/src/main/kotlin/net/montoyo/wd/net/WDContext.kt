package net.montoyo.wd.net

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.Executor

/**
 * Replaces Forge's NetworkEvent.Context: the receiving side of a WebDisplays packet.
 */
class WDContext(
    val sender: ServerPlayer?,
    val server: MinecraftServer?,
    private val response: PacketSender,
    private val mainThread: Executor,
    val isClientSide: Boolean
) {
    val isServerSide: Boolean
        get() = !isClientSide

    /**
     * Packets are decoded on the network thread, so handlers hop to the logical side with this.
     */
    fun enqueueWork(work: Runnable) {
        mainThread.execute(work)
    }

    fun setPacketHandled(handled: Boolean) {
    }

    fun reply(packet: Packet) {
        WDNetworkRegistry.INSTANCE.sendResponse(response, packet, isClientSide)
    }
}
