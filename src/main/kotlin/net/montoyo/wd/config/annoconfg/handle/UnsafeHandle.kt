package net.montoyo.wd.config.annoconfg.handle

import net.montoyo.wd.config.annoconfg.util.EnumType
import sun.misc.Unsafe
import java.lang.reflect.Field
import java.util.function.Consumer
import java.util.function.Supplier

class UnsafeHandle(relative: Any?, f: Field) {
    private val offset: Long
    private val uploader: Consumer<Any?>?
    private val getter: Supplier<Any?>

    constructor(f: Field) : this(null, f)

    init {
        @Suppress("DEPRECATION")
        offset = theUnsafe.staticFieldOffset(f)
        @Suppress("DEPRECATION")
        val finalRelative: Any = relative ?: theUnsafe.staticFieldBase(f)

        if (f.type.isPrimitive) {
            when (EnumType.forClass(f.type)) {
                EnumType.BYTE -> {
                    uploader = Consumer { v -> theUnsafe.putByte(finalRelative, offset, v as Byte) }
                    getter = Supplier { theUnsafe.getByte(finalRelative, offset) }
                }
                EnumType.SHORT -> {
                    uploader = Consumer { v -> theUnsafe.putShort(finalRelative, offset, v as Short) }
                    getter = Supplier { theUnsafe.getShort(finalRelative, offset) }
                }
                EnumType.INT -> {
                    uploader = Consumer { v -> theUnsafe.putInt(finalRelative, offset, v as Int) }
                    getter = Supplier { theUnsafe.getInt(finalRelative, offset) }
                }
                EnumType.LONG -> {
                    uploader = Consumer { v -> theUnsafe.putLong(finalRelative, offset, v as Long) }
                    getter = Supplier { theUnsafe.getLong(finalRelative, offset) }
                }
                EnumType.FLOAT -> {
                    uploader = Consumer { v -> theUnsafe.putFloat(finalRelative, offset, v as Float) }
                    getter = Supplier { theUnsafe.getFloat(finalRelative, offset) }
                }
                EnumType.DOUBLE -> {
                    uploader = Consumer { v -> theUnsafe.putDouble(finalRelative, offset, v as Double) }
                    getter = Supplier { theUnsafe.getDouble(finalRelative, offset) }
                }
                EnumType.BOOLEAN -> {
                    uploader = Consumer { v -> theUnsafe.putBoolean(finalRelative, offset, v as Boolean) }
                    getter = Supplier { theUnsafe.getBoolean(finalRelative, offset) }
                }
                else -> {
                    uploader = null
                    getter = Supplier { null }
                }
            }
        } else {
            uploader = Consumer { v -> theUnsafe.putObject(finalRelative, offset, v) }
            getter = Supplier { theUnsafe.getObject(finalRelative, offset) }
        }
    }

    fun set(o: Any?) {
        uploader?.accept(o)
    }

    fun get(): Any? = getter.get()

    companion object {
        private val theUnsafe: Unsafe = try {
            val f = Unsafe::class.java.getDeclaredField("theUnsafe")
            f.isAccessible = true
            f.get(null) as Unsafe
        } catch (_: Throwable) {
            throw RuntimeException("AnnoConfg: Failed to acquire an instance of the unsafe.")
        }
    }
}
