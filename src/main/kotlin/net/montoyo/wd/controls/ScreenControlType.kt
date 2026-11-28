package net.montoyo.wd.controls

import net.minecraft.network.FriendlyByteBuf
import java.util.function.Function

class ScreenControlType<T : ScreenControl>(
    @JvmField val clazz: Class<T>,
    @JvmField val deserializer: Function<FriendlyByteBuf, ScreenControl>
)
