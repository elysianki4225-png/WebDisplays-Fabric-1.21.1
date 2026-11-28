/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

import net.minecraft.server.level.ServerPlayer

class MissingPermissionException(
    private val permission: Int,
    private val player: ServerPlayer
) : Exception("Player ${player.name} is missing permission $permission") {
    fun getPermission(): Int = permission

    fun getPlayer(): ServerPlayer = player
}
