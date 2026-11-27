package net.montoyo.wd.utilities;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class NbtCompat {
    private NbtCompat() {
    }

    public static void putUUID(CompoundTag tag, String key, UUID uuid) {
        tag.putIntArray(key, UUIDUtil.uuidToIntArray(uuid));
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        if (tag.getIntArray(key).map(arr -> arr.length == 4).orElse(false))
            return true;
        return tag.contains(key + "Most") && tag.contains(key + "Least");
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        int[] arr = tag.getIntArray(key).orElse(null);
        if (arr != null && arr.length == 4)
            return UUIDUtil.uuidFromIntArray(arr);
        if (tag.contains(key + "Most") && tag.contains(key + "Least"))
            return new UUID(tag.getLongOr(key + "Most", 0L), tag.getLongOr(key + "Least", 0L));
        return new UUID(0L, 0L);
    }

    public static void removeUUID(CompoundTag tag, String key) {
        tag.remove(key);
        tag.remove(key + "Most");
        tag.remove(key + "Least");
    }
}
