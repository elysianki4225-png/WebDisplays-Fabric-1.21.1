package net.montoyo.wd.controls

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.api.FabricLoader
import net.montoyo.wd.controls.builtin.AutoVolumeControl
import net.montoyo.wd.controls.builtin.ClickControl
import net.montoyo.wd.controls.builtin.JSRequestControl
import net.montoyo.wd.controls.builtin.KeyTypedControl
import net.montoyo.wd.controls.builtin.LaserControl
import net.montoyo.wd.controls.builtin.ManageRightsAndUpdgradesControl
import net.montoyo.wd.controls.builtin.ModifyFriendListControl
import net.montoyo.wd.controls.builtin.OwnerControl
import net.montoyo.wd.controls.builtin.ScreenModifyControl
import net.montoyo.wd.controls.builtin.SetURLControl
import net.montoyo.wd.controls.builtin.TurnOffControl
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.Identifier

// TODO: enable deferred registry of these
object ScreenControlRegistry {
    private val CONTROL_TYPES = HashMap<Identifier, ScreenControlType<*>>()

    @JvmStatic
    fun register(name: Identifier, type: ScreenControlType<*>) {
        if (CONTROL_TYPES.containsKey(name)) {
            Log.warning("ScreenControlRegistry#CONTROL_TYPES already contains an entry with name $name")
            throw IllegalArgumentException("Cannot have two entries with the same name.")
        }
        CONTROL_TYPES[name] = type

        // lil thing for sanity
        // avoids the pain the access transformer would cause, hopefully
        if (!FabricLoader.getInstance().isDevelopmentEnvironment) return

        if (FabricLoader.getInstance().environmentType == EnvType.CLIENT) {
            var shouldThrow = false
            try {
                val m = type.clazz.getMethod(
                    "handleClient",
                    BlockPos::class.java,
                    BlockSide::class.java,
                    TileEntityScreen::class.java,
                    WDContext::class.java
                )
                val environment = m.getAnnotation(Environment::class.java)
                if (environment == null || environment.value != EnvType.CLIENT) {
                    shouldThrow = true
                }
            } catch (_: Throwable) {
            }
            if (shouldThrow) {
                Log.warning("handleClient on ScreenControl classes MUST be marked with `@Environment(EnvType.CLIENT)`, but it is not on ${type.clazz}")
                throw IllegalStateException(
                    "handleClient on ScreenControl classes MUST be marked with `@Environment(EnvType.CLIENT)`, but it is not on ${type.clazz}"
                )
            }
        }
    }

    init {
        register(SetURLControl.id, ScreenControlType(SetURLControl::class.java, ::SetURLControl))
        register(KeyTypedControl.id, ScreenControlType(KeyTypedControl::class.java, ::KeyTypedControl))
        register(AutoVolumeControl.id, ScreenControlType(AutoVolumeControl::class.java, ::AutoVolumeControl))
        register(JSRequestControl.id, ScreenControlType(JSRequestControl::class.java, ::JSRequestControl))
        register(LaserControl.id, ScreenControlType(LaserControl::class.java, ::LaserControl))
        register(ScreenModifyControl.id, ScreenControlType(ScreenModifyControl::class.java, ::ScreenModifyControl))
        register(ModifyFriendListControl.id, ScreenControlType(ModifyFriendListControl::class.java, ::ModifyFriendListControl))
        register(ManageRightsAndUpdgradesControl.id, ScreenControlType(ManageRightsAndUpdgradesControl::class.java, ::ManageRightsAndUpdgradesControl))
        register(ClickControl.id, ScreenControlType(ClickControl::class.java, ::ClickControl))
        register(OwnerControl.id, ScreenControlType(OwnerControl::class.java, ::OwnerControl))
        register(TurnOffControl.id, ScreenControlType(TurnOffControl::class.java) { TurnOffControl.INSTANCE })
    }

    @JvmStatic
    fun parse(buf: FriendlyByteBuf): ScreenControl =
        CONTROL_TYPES[Identifier.parse(buf.readUtf())]!!.deserializer.apply(buf)

    @JvmStatic
    fun init() {
        // NO-OP: allows static init to run during mod init in dev env
    }
}
