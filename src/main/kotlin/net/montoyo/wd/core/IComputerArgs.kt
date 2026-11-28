/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.core

interface IComputerArgs {
    fun checkString(i: Int): String
    fun checkInteger(i: Int): Int
    fun checkTable(i: Int): Map<*, *>
    fun count(): Int
}
