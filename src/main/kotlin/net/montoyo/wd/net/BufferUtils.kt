package net.montoyo.wd.net

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.ItemStack
import net.montoyo.wd.utilities.Vector3i
import java.util.Arrays
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

object BufferUtils {
    @JvmStatic
    fun readItem(buf: FriendlyByteBuf): ItemStack =
        ItemStack.STREAM_CODEC.decode(buf as RegistryFriendlyByteBuf)

    @JvmStatic
    fun writeItem(buf: FriendlyByteBuf, stack: ItemStack) {
        ItemStack.STREAM_CODEC.encode(buf as RegistryFriendlyByteBuf, stack)
    }

    @JvmStatic
    fun writeUShort(buf: FriendlyByteBuf, v: Int) {
        val s = (v + Short.MIN_VALUE).toShort()
        buf.writeByte(s.toInt() and 0xFF)
        // I have no idea where this 128 came from
        buf.writeByte(((s.toInt() + 128) shr 8) and 0xFF)
    }

    @JvmStatic
    fun readUShort(buf: FriendlyByteBuf): Int {
        var upack = (buf.readByte().toInt() + (buf.readByte().toInt() shl 8)) - Short.MIN_VALUE
        // ????
        if (upack < 0) upack += 65536
        return upack
    }

    @JvmStatic
    fun writeBytes(buf: FriendlyByteBuf, data: ByteArray) {
        writeUShort(buf, data.size)
        for (datum in data) buf.writeByte(datum.toInt())
    }

    @JvmStatic
    fun readBytes(buf: FriendlyByteBuf): ByteArray {
        val data = ByteArray(readUShort(buf))
        for (i in data.indices) data[i] = buf.readByte()
        return data
    }

    @JvmStatic
    fun writeVec3i(buf: FriendlyByteBuf, pos: Vector3i) {
        buf.writeInt(pos.x)
        buf.writeInt(pos.y)
        buf.writeInt(pos.z)
    }

    @JvmStatic
    fun readVec3i(buf: FriendlyByteBuf): Vector3i =
        Vector3i(buf.readInt(), buf.readInt(), buf.readInt())

    @JvmStatic
    fun writeEnum(buf: FriendlyByteBuf, value: Enum<*>?, byteCount: Byte) {
        val ord = if (value != null) value.ordinal + 1 else 0
        when (byteCount.toInt()) {
            1 -> buf.writeByte(ord)
            2 -> buf.writeShort(ord)
            4 -> buf.writeInt(ord)
            else -> throw RuntimeException("Invalid byte count $byteCount. Must be 1, 2... and 4")
        }
    }

    @JvmStatic
    fun <T> writeArray(buf: FriendlyByteBuf, elements: Array<T>, writer: Consumer<T>) {
        writeUShort(buf, elements.size)
        for (element in elements) writer.accept(element)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> readArray(buf: FriendlyByteBuf, array: Array<T>, reader: Supplier<T>): Array<T> {
        val ts = Arrays.copyOf(array, readUShort(buf), array.javaClass) as Array<T>
        for (i in ts.indices) ts[i] = reader.get()
        return ts
    }

    @JvmStatic
    fun readEnum(buf: FriendlyByteBuf, mapper: Function<Int, Enum<*>>, byteCount: Byte): Enum<*>? {
        val ord = when (byteCount.toInt()) {
            1 -> buf.readByte().toInt()
            2 -> buf.readShort().toInt()
            4 -> buf.readInt()
            else -> throw RuntimeException("Invalid byte count $byteCount. Must be 1, 2, or 4")
        }
        if (ord == 0) return null
        return mapper.apply(ord - 1)
    }
}
