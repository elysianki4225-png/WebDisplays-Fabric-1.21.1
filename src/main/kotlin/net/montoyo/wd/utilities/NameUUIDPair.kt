/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.utilities

import com.mojang.authlib.GameProfile
import net.minecraft.network.FriendlyByteBuf
import java.util.UUID

class NameUUIDPair {
    @JvmField
    val name: String

    @JvmField
    val uuid: UUID

    constructor() {
        name = ""
        uuid = UUID(0L, 0L)
    }

    constructor(name: String, uuid: UUID) {
        this.name = name
        this.uuid = uuid
    }

    constructor(profile: GameProfile) {
        name = profile.name
        uuid = profile.id
    }

    constructor(bb: FriendlyByteBuf) {
        name = bb.readUtf()
        val msb = bb.readLong()
        val lsb = bb.readLong()
        uuid = UUID(msb, lsb)
    }

    fun getName(): String = name

    fun getUUID(): UUID = uuid

    override fun equals(other: Any?): Boolean {
        if (other !is NameUUIDPair) return false
        return other.uuid == uuid
    }

    override fun hashCode(): Int = uuid.hashCode()

    fun isBlank(): Boolean =
        name.isEmpty() && uuid.mostSignificantBits == 0L && uuid.leastSignificantBits == 0L

    fun writeTo(bb: FriendlyByteBuf) {
        bb.writeUtf(name)
        bb.writeLong(uuid.mostSignificantBits)
        bb.writeLong(uuid.leastSignificantBits)
    }
}
