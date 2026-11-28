package net.montoyo.wd.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import java.util.function.Supplier

/**
 * Registers into the vanilla registries right away, which is when Fabric calls entrypoints.
 */
class DeferredRegister<T : Any> private constructor(
    private val key: ResourceKey<out Registry<T>>,
    private val namespace: String
) {
    @Suppress("UNCHECKED_CAST")
    private fun registry(): Registry<T> {
        val id = key.identifier()

        return when (id) {
            Registries.BLOCK.identifier() -> BuiltInRegistries.BLOCK as Registry<T>
            Registries.ITEM.identifier() -> BuiltInRegistries.ITEM as Registry<T>
            Registries.BLOCK_ENTITY_TYPE.identifier() -> BuiltInRegistries.BLOCK_ENTITY_TYPE as Registry<T>
            Registries.SOUND_EVENT.identifier() -> BuiltInRegistries.SOUND_EVENT as Registry<T>
            Registries.CREATIVE_MODE_TAB.identifier() -> BuiltInRegistries.CREATIVE_MODE_TAB as Registry<T>
            else -> (BuiltInRegistries.REGISTRY.getValue(id) as? Registry<T>)
                ?: throw RuntimeException("No built-in registry for $id")
        }
    }

    fun <U : T> register(name: String, supplier: Supplier<out U>): RegistryObject<U> {
        val value = supplier.get()
        return RegistryObject(Registry.register(registry(), Identifier.fromNamespaceAndPath(namespace, name), value))
    }

    inline fun <U : T> register(name: String, crossinline factory: () -> U): RegistryObject<U> =
        register(name, Supplier { factory() })

    fun register() {
        // Values are registered eagerly by register(String, Supplier).
    }

    companion object {
        @JvmStatic
        fun <T : Any> create(key: ResourceKey<out Registry<T>>, namespace: String): DeferredRegister<T> =
            DeferredRegister(key, namespace)
    }
}
