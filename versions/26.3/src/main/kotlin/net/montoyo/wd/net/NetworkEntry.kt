package net.montoyo.wd.net

import net.minecraft.network.FriendlyByteBuf
import java.util.function.Function

class NetworkEntry<T : Packet> private constructor(
    private val direction: Direction,
    private val fabricator: Function<FriendlyByteBuf, T>
) {
    enum class Direction {
        CLIENT_TO_SERVER,
        SERVER_TO_CLIENT
    }

    fun getDirection(): Direction = direction

    fun read(buf: FriendlyByteBuf): T = fabricator.apply(buf)

    companion object {
        @JvmStatic
        fun <T : Packet> clientToServer(fabricator: Function<FriendlyByteBuf, T>): NetworkEntry<T> =
            NetworkEntry(Direction.CLIENT_TO_SERVER, fabricator)

        @JvmStatic
        fun <T : Packet> serverToClient(fabricator: Function<FriendlyByteBuf, T>): NetworkEntry<T> =
            NetworkEntry(Direction.SERVER_TO_CLIENT, fabricator)
    }
}
