package net.montoyo.wd.net

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * One channel per direction carries every WebDisplays message; the body starts with the packet
 * index dispatched by WDNetworkRegistry.
 */
object WDPayload {
    class C2S(private val body: ByteArray) : CustomPacketPayload {
        fun body(): ByteArray = body

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        override fun equals(other: Any?): Boolean =
            this === other || (other is C2S && body.contentEquals(other.body))

        override fun hashCode(): Int = body.contentHashCode()

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<C2S> = CustomPacketPayload.Type(
                ResourceLocation.fromNamespaceAndPath("webdisplays", "packetsystem_c2s")
            )

            @JvmField
            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, C2S> = StreamCodec.of(
                { buf, packet -> buf.writeBytes(packet.body()) },
                { buf -> C2S(readBody(buf)) }
            )
        }
    }

    class S2C(private val body: ByteArray) : CustomPacketPayload {
        fun body(): ByteArray = body

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        override fun equals(other: Any?): Boolean =
            this === other || (other is S2C && body.contentEquals(other.body))

        override fun hashCode(): Int = body.contentHashCode()

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<S2C> = CustomPacketPayload.Type(
                ResourceLocation.fromNamespaceAndPath("webdisplays", "packetsystem_s2c")
            )

            @JvmField
            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, S2C> = StreamCodec.of(
                { buf, packet -> buf.writeBytes(packet.body()) },
                { buf -> S2C(readBody(buf)) }
            )
        }
    }

    private fun readBody(buf: RegistryFriendlyByteBuf): ByteArray {
        val out = ByteArray(buf.readableBytes())
        buf.readBytes(out)
        return out
    }
}
