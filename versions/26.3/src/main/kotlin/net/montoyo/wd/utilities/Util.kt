/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.lang.reflect.Modifier
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.StringJoiner
import java.util.UUID

object Util {
    @JvmField
    val SIZES = arrayOf("bytes", "KiB", "MiB", "GiB", "TiB")

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
    private val SPECIAL_SCHEME_REGEX = Regex("^(?:about|data|javascript|blob|chrome|devtools):.*", RegexOption.IGNORE_CASE)
    private val IPV4_REGEX = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val IPV6_AUTH_REGEX = Regex("^\\[[0-9a-fA-F:]+\\](?::(\\d{1,5}))?$")
    private val DOMAIN_LABEL_REGEX = Regex("^[\\p{L}\\p{N}-]+$")

    @Deprecated("For removal", level = DeprecationLevel.WARNING)
    @JvmStatic
    fun serialize(bb: FriendlyByteBuf, f: Any) {
        val cls: Class<*> = f.javaClass

        when {
            cls == Integer::class.java || cls == Integer.TYPE -> bb.writeInt(f as Int)
            cls == java.lang.Float::class.java || cls == java.lang.Float.TYPE -> bb.writeFloat(f as Float)
            cls == java.lang.Double::class.java || cls == java.lang.Double.TYPE -> bb.writeDouble(f as Double)
            cls == java.lang.Boolean::class.java || cls == java.lang.Boolean.TYPE -> bb.writeBoolean(f as Boolean)
            cls == String::class.java -> bb.writeUtf(f as String)
            cls == NameUUIDPair::class.java -> (f as NameUUIDPair).writeTo(bb)
            cls.isEnum -> bb.writeByte((f as Enum<*>).ordinal)
            cls.isArray -> {
                @Suppress("UNCHECKED_CAST")
                val ray = f as Array<Any>
                bb.writeInt(ray.size)
                for (o in ray) serialize(bb, o)
            }
            cls == Identifier::class.java -> bb.writeUtf(f.toString())
            !cls.isPrimitive -> {
                for (ff in cls.fields) {
                    try {
                        if (ff.getAnnotation(DontSerialize::class.java) == null && !Modifier.isStatic(ff.modifiers)) {
                            serialize(bb, ff.get(f))
                        }
                    } catch (e: IllegalAccessException) {
                        e.printStackTrace()
                        throw RuntimeException("Caught IllegalAccessException for ${cls.name}.${ff.name}")
                    }
                }
            }
            else -> throw RuntimeException("Cannot transmit class ${cls.name} over network!")
        }
    }

    @Deprecated("For removal", level = DeprecationLevel.WARNING)
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun unserialize(bb: FriendlyByteBuf, cls: Class<*>): Any {
        return when {
            cls == Integer::class.java || cls == Integer.TYPE -> bb.readInt()
            cls == java.lang.Float::class.java || cls == java.lang.Float.TYPE -> bb.readFloat()
            cls == java.lang.Double::class.java || cls == java.lang.Double.TYPE -> bb.readDouble()
            cls == java.lang.Boolean::class.java || cls == java.lang.Boolean.TYPE -> bb.readBoolean()
            cls == String::class.java -> bb.readUtf()
            cls == NameUUIDPair::class.java -> NameUUIDPair(bb)
            cls.isEnum -> cls.enumConstants[bb.readByte().toInt()]!!
            cls.isArray -> {
                val len = bb.readInt()
                val ray = arrayOfNulls<Any>(len)
                val compType = cls.componentType
                for (i in 0 until len) {
                    ray[i] = unserialize(bb, compType)
                }
                Arrays.copyOf(ray, ray.size, cls as Class<out Array<Any?>>)
            }
            cls == Identifier::class.java -> Identifier.parse(bb.readUtf())
            !cls.isPrimitive -> {
                val ret = try {
                    cls.getDeclaredConstructor().newInstance()
                } catch (e: InstantiationException) {
                    e.printStackTrace()
                    throw RuntimeException("Caught InstantiationException for class ${cls.name}")
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                    throw RuntimeException("Caught IllegalAccessException for class ${cls.name}")
                }

                for (ff in cls.fields) {
                    try {
                        if (ff.getAnnotation(DontSerialize::class.java) == null && !Modifier.isStatic(ff.modifiers)) {
                            ff.set(ret, unserialize(bb, ff.type))
                        }
                    } catch (e: IllegalAccessException) {
                        throw RuntimeException("Caught IllegalAccessException for ${cls.name}.${ff.name}")
                    }
                }
                ret
            }
            else -> throw RuntimeException("Cannot unserialize class ${cls.name}!")
        }
    }

    @JvmStatic
    fun addSlashes(str: String): String {
        val sb = StringBuilder(str.length)
        for (c in str) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '\"' -> sb.append("\\\"")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun scrambleKey(idx: Int): Int {
        val scrambled = idx * 0x9E3779B9.toInt()
        return scrambled xor (scrambled shr 16)
    }

    @JvmStatic
    fun toast(player: Player, key: String, vararg data: Any?) {
        toast(player, ChatFormatting.RED, key, *data)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun toast(player: Player, color: ChatFormatting, key: String, vararg data: Any?) {
        val root = Component.translatable("[WebDisplays] ")
            .setStyle(Style.EMPTY.withColor(color))
            .append(Component.translatable("webdisplays.message.$key", *(data as Array<out Any>)))
        player.sendSystemMessage(root)
    }

    @JvmStatic
    fun silentClose(obj: Any) {
        try {
            obj.javaClass.getMethod("close").invoke(obj)
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun addProtocol(str: String): String =
        if (str.isEmpty() || str.contains("://")) str else "http://$str"

    @JvmStatic
    fun resolveUrlOrSearch(str: String): String {
        val trimmed = str.trim()
        if (trimmed.isEmpty()) return ""
        if (SCHEME_REGEX.matches(trimmed) || SPECIAL_SCHEME_REGEX.matches(trimmed)) {
            return trimmed
        }
        if (isLikelyHostOrUrl(trimmed)) {
            return "http://$trimmed"
        }
        return "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8)
    }

    private fun isLikelyHostOrUrl(trimmed: String): Boolean {
        if (trimmed.any { it.isWhitespace() }) return false

        val sepIdx = trimmed.indexOfAny(charArrayOf('/', '?', '#'))
        val authority = if (sepIdx >= 0) trimmed.substring(0, sepIdx) else trimmed
        if (authority.isEmpty()) return false

        if (authority.startsWith("[")) {
            val m = IPV6_AUTH_REGEX.matchEntire(authority) ?: return false
            val portStr = m.groupValues[1]
            if (portStr.isNotEmpty()) {
                val port = portStr.toIntOrNull() ?: return false
                if (port !in 1..65535) return false
            }
            return true
        }

        val colonCount = authority.count { it == ':' }
        if (colonCount > 1) return false

        val host: String
        val port: Int?
        if (colonCount == 1) {
            val colonIdx = authority.indexOf(':')
            host = authority.substring(0, colonIdx)
            val portStr = authority.substring(colonIdx + 1)
            port = portStr.toIntOrNull() ?: return false
            if (port !in 1..65535) return false
        } else {
            host = authority
            port = null
        }

        if (host.isEmpty()) return false
        if (host.equals("localhost", ignoreCase = true)) return true

        val ipv4Match = IPV4_REGEX.matchEntire(host)
        if (ipv4Match != null) {
            return (1..4).all { i ->
                val oct = ipv4Match.groupValues[i].toIntOrNull()
                oct != null && oct in 0..255
            }
        }

        if (port != null && !host.contains('.') && DOMAIN_LABEL_REGEX.matches(host) &&
            !host.startsWith('-') && !host.endsWith('-')
        ) {
            return true
        }

        if (!host.contains('.')) return false
        if (host.startsWith('.') || host.endsWith('.')) return false

        val labels = host.split('.')
        if (labels.size < 2) return false
        for (label in labels) {
            if (label.isEmpty() || label.startsWith('-') || label.endsWith('-')) return false
            if (!DOMAIN_LABEL_REGEX.matches(label)) return false
        }

        val tld = labels.last()
        return tld.length >= 2 && tld.any { it.isLetter() }
    }

    @JvmStatic
    fun isFileNameInvalid(fname: String): Boolean =
        fname.isEmpty() || fname.length > 64 || fname[0] == '.' || fname.indexOf('/') >= 0 || fname.indexOf('\\') >= 0

    @JvmStatic
    fun sizeString(l: Long): String {
        var bytes = l
        var d = bytes.toDouble()
        var size = 0

        while (bytes >= 1024L && size + 1 < SIZES.size) {
            d /= 1024.0
            bytes /= 1024L
            size++
        }

        return String.format("%.2f %s", d, SIZES[size])
    }

    @JvmStatic
    fun join(array: Array<String>, sep: String): String {
        val j = StringJoiner(sep)
        array.forEach(j::add)
        return j.toString()
    }

    @JvmStatic
    fun writeOwnerToNBT(tag: CompoundTag, owner: NameUUIDPair?): CompoundTag {
        if (owner != null) {
            tag.putLong("OwnerMSB", owner.uuid.mostSignificantBits)
            tag.putLong("OwnerLSB", owner.uuid.leastSignificantBits)
            tag.putString("OwnerName", owner.name)
        }
        return tag
    }

    @JvmStatic
    fun readOwnerFromNBT(tag: CompoundTag): NameUUIDPair {
        val msb = tag.getLongOr("OwnerMSB", 0L)
        val lsb = tag.getLongOr("OwnerLSB", 0L)
        val str = tag.getStringOr("OwnerName", "")
        return NameUUIDPair(str, UUID(msb, lsb))
    }

    @JvmStatic
    fun writeOwnerToValueOutput(output: ValueOutput, owner: NameUUIDPair?) {
        if (owner != null) {
            output.putLong("OwnerMSB", owner.uuid.mostSignificantBits)
            output.putLong("OwnerLSB", owner.uuid.leastSignificantBits)
            output.putString("OwnerName", owner.name)
        }
    }

    @JvmStatic
    fun readOwnerFromValueInput(input: ValueInput): NameUUIDPair {
        val msb = input.getLongOr("OwnerMSB", 0L)
        val lsb = input.getLongOr("OwnerLSB", 0L)
        val str = input.getStringOr("OwnerName", "")
        return NameUUIDPair(str, UUID(msb, lsb))
    }
}
