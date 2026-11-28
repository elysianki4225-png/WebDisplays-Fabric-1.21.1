package net.montoyo.wd.net

import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.net.client_bound.S2CMessageACResult
import net.montoyo.wd.net.client_bound.S2CMessageAddScreen
import net.montoyo.wd.net.client_bound.S2CMessageCloseGui
import net.montoyo.wd.net.client_bound.S2CMessageJSResponse
import net.montoyo.wd.net.client_bound.S2CMessageMiniservKey
import net.montoyo.wd.net.client_bound.S2CMessageOpenGui
import net.montoyo.wd.net.client_bound.S2CMessageScreenUpdate
import net.montoyo.wd.net.client_bound.S2CMessageServerInfo
import net.montoyo.wd.net.server_bound.C2SMessageACQuery
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl
import net.montoyo.wd.net.server_bound.C2SMessageMiniservConnect
import net.montoyo.wd.net.server_bound.C2SMessageRedstoneCtrl
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl
import java.util.concurrent.Executor

class WDNetworkRegistry {
    fun sendToServer(packet: Packet) {
        val level = Minecraft.getInstance().level ?: return
        ClientPlayNetworking.send(WDPayload.C2S(toBytes(packet, level.registryAccess())))
    }

    fun send(target: WDDistributor.Target, packet: Packet) {
        val server = WebDisplays.PROXY.getServer() ?: return
        val body = toBytes(packet, server.registryAccess())
        target.forEachPlayer(server) { player ->
            ServerPlayNetworking.send(player, WDPayload.S2C(body))
        }
    }

    fun reply(packet: Packet, ctx: WDContext) {
        ctx.reply(packet)
    }

    internal fun sendResponse(sender: PacketSender, packet: Packet, clientSide: Boolean) {
        if (clientSide) {
            sender.sendPacket(WDPayload.C2S(toBytes(packet, Minecraft.getInstance().level!!.registryAccess())))
        } else {
            sender.sendPacket(WDPayload.S2C(toBytes(packet, WebDisplays.PROXY.getServer()!!.registryAccess())))
        }
    }

    companion object {
        const val networkingVersion = "2"

        @JvmField
        val CHANNEL: Identifier = WDPayload.C2S.TYPE.id()

        @JvmField
        val INSTANCE = WDNetworkRegistry()

        private val entries = ArrayList<NetworkEntry<*>>()
        private val indexByClass = HashMap<Class<out Packet>, Int>()
        private var payloadsRegistered = false

        private fun <T : Packet> add(entry: NetworkEntry<T>, clazz: Class<T>) {
            indexByClass[clazz] = entries.size
            entries.add(entry)
        }

        init {
            // login handshake
            add(NetworkEntry.serverToClient(::S2CMessageServerInfo), S2CMessageServerInfo::class.java)
            add(NetworkEntry.clientToServer(::C2SMessageMiniservConnect), C2SMessageMiniservConnect::class.java)
            add(NetworkEntry.serverToClient(::S2CMessageMiniservKey), S2CMessageMiniservKey::class.java)

            // guis
            add(NetworkEntry.serverToClient(::S2CMessageCloseGui), S2CMessageCloseGui::class.java)
            add(NetworkEntry.serverToClient(::S2CMessageOpenGui), S2CMessageOpenGui::class.java)

            // screen creation
            add(NetworkEntry.serverToClient(::S2CMessageAddScreen), S2CMessageAddScreen::class.java)

            // screen modifications
            add(NetworkEntry.clientToServer(::C2SMessageScreenCtrl), C2SMessageScreenCtrl::class.java)
            add(NetworkEntry.serverToClient(::S2CMessageScreenUpdate), S2CMessageScreenUpdate::class.java)

            // redstone control
            add(NetworkEntry.clientToServer(::C2SMessageRedstoneCtrl), C2SMessageRedstoneCtrl::class.java)

            // autocomplete
            add(NetworkEntry.clientToServer(::C2SMessageACQuery), C2SMessageACQuery::class.java)
            add(NetworkEntry.serverToClient(::S2CMessageACResult), S2CMessageACResult::class.java)

            // jsquery
            add(NetworkEntry.serverToClient(::S2CMessageJSResponse), S2CMessageJSResponse::class.java)

            // minepad
            add(NetworkEntry.clientToServer(::C2SMessageMinepadUrl), C2SMessageMinepadUrl::class.java)
        }

        private fun registerPayloadTypes() {
            if (payloadsRegistered) return
            payloadsRegistered = true
            PayloadTypeRegistry.serverboundPlay().register(WDPayload.C2S.TYPE, WDPayload.C2S.STREAM_CODEC)
            PayloadTypeRegistry.clientboundPlay().register(WDPayload.S2C.TYPE, WDPayload.S2C.STREAM_CODEC)
        }

        @JvmStatic
        fun init() {
            registerPayloadTypes()
            ServerPlayNetworking.registerGlobalReceiver(WDPayload.C2S.TYPE) { payload, context ->
                dispatch(payload.body(), context.responseSender(), context.player(), context.server(), false)
            }
        }

        @JvmStatic
        fun initClient() {
            registerPayloadTypes()
            ClientPlayNetworking.registerGlobalReceiver(WDPayload.S2C.TYPE) { payload, context ->
                dispatch(payload.body(), context.responseSender(), null, null, true)
            }
        }

        private fun dispatch(
            body: ByteArray,
            response: PacketSender,
            sender: ServerPlayer?,
            server: MinecraftServer?,
            clientSide: Boolean
        ) {
            val registries: RegistryAccess? = if (clientSide) {
                Minecraft.getInstance().level?.registryAccess()
            } else {
                server?.registryAccess()
            }
            if (registries == null) return

            val buf = RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(body), registries)
            try {
                val index = buf.readVarInt()
                if (index < 0 || index >= entries.size) {
                    throw IllegalArgumentException("Unknown WebDisplays packet index $index")
                }

                val entry = entries[index]
                val expectedFromClient = entry.getDirection() == NetworkEntry.Direction.CLIENT_TO_SERVER
                if (expectedFromClient == clientSide) {
                    throw IllegalArgumentException("WebDisplays packet $index arrived on the wrong side")
                }

                val packet = entry.read(buf)
                val executor: Executor = if (clientSide) {
                    Executor { Minecraft.getInstance().execute(it) }
                } else {
                    Executor { server!!.execute(it) }
                }
                val ctx = WDContext(sender, server, response, executor, clientSide)
                packet.handle(ctx)
            } finally {
                buf.release()
            }
        }

        private fun toBytes(packet: Packet, registries: RegistryAccess): ByteArray {
            val index = indexByClass[packet.javaClass]
                ?: throw IllegalArgumentException("Packet ${packet.javaClass.name} was not registered")

            val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), registries)
            try {
                buf.writeVarInt(index)
                packet.write(buf)

                val out = ByteArray(buf.readableBytes())
                buf.readBytes(out)
                return out
            } finally {
                buf.release()
            }
        }
    }
}
