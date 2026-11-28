package net.montoyo.wd.registry

import java.util.Optional
import java.util.function.Supplier

/**
 * Holds a value that has been registered into a vanilla registry.
 */
class RegistryObject<T>(private val value: T) : Supplier<T> {
    override fun get(): T = value

    fun isPresent(): Boolean = value != null

    fun orElseGet(other: T): T = value ?: other

    fun toOptional(): Optional<T> = Optional.ofNullable(value)
}
