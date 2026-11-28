package net.montoyo.wd.registry

import java.util.Optional
import java.util.function.Supplier

/**
 * Holds a value that has been registered into a vanilla registry.
 */
class RegistryObject<T : Any>(private val value: T) : Supplier<T> {
    override fun get(): T = value

    fun isPresent(): Boolean = true

    fun orElseGet(other: T): T = value

    fun toOptional(): Optional<T> = Optional.of(value)
}
