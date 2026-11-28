package net.montoyo.wd.utilities

import net.minecraft.core.UUIDUtil
import net.minecraft.nbt.CompoundTag
import java.util.UUID

object NbtCompat {
    @JvmStatic
    fun putUUID(tag: CompoundTag, key: String, uuid: UUID) {
        tag.putIntArray(key, UUIDUtil.uuidToIntArray(uuid))
    }

    @JvmStatic
    fun hasUUID(tag: CompoundTag, key: String): Boolean {
        if (tag.getIntArray(key).map { it.size == 4 }.orElse(false)) {
            return true
        }
        return tag.contains(key + "Most") && tag.contains(key + "Least")
    }

    @JvmStatic
    fun getUUID(tag: CompoundTag, key: String): UUID? {
        val arr = tag.getIntArray(key).orElse(null)
        if (arr != null && arr.size == 4) {
            return UUIDUtil.uuidFromIntArray(arr)
        }
        if (tag.contains(key + "Most") && tag.contains(key + "Least")) {
            return UUID(tag.getLongOr(key + "Most", 0L), tag.getLongOr(key + "Least", 0L))
        }
        return null
    }

    @JvmStatic
    fun removeUUID(tag: CompoundTag, key: String) {
        tag.remove(key)
        tag.remove(key + "Most")
        tag.remove(key + "Least")
    }
}
