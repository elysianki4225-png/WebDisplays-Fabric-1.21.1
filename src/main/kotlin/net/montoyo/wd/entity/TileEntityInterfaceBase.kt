/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.core.IComputerArgs
import net.montoyo.wd.core.IUpgrade
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.Rotation
import net.montoyo.wd.utilities.TypeData
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector2i
import java.io.IOException

abstract class TileEntityInterfaceBase(
    arg: BlockEntityType<*>,
    arg2: BlockPos,
    arg3: BlockState
) : TileEntityPeripheralBase(arg, arg2, arg3) {

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class ComputerFunc

    private var owner: NameUUIDPair? = null

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        owner = Util.readOwnerFromNBT(tag)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        Util.writeOwnerToNBT(tag, owner)
    }

    fun setOwner(ep: Player) {
        owner = NameUUIDPair(ep.gameProfile)
        setChanged()
    }

    @ComputerFunc
    fun isLinked(args: IComputerArgs): Array<Any?> = arrayOf(isLinked())

    @ComputerFunc
    fun isScreenChunkLoaded(args: IComputerArgs): Array<Any?> = arrayOf(isScreenChunkLoaded())

    @ComputerFunc
    fun getScreenPos(args: IComputerArgs): Array<Any?>? {
        val pos = screenPos
        return if (isLinked() && pos != null) arrayOf(pos.x, pos.y, pos.z) else null
    }

    @ComputerFunc
    fun getScreenSide(args: IComputerArgs): Array<Any?>? {
        val side = screenSide
        return if (isLinked() && side != null) arrayOf(side.toString().lowercase()) else null
    }

    @ComputerFunc
    fun getOwner(args: IComputerArgs): Array<Any?>? {
        val o = owner ?: return null
        return arrayOf(o.name, o.uuid.toString())
    }

    @ComputerFunc
    fun can(args: IComputerArgs): Array<Any?>? {
        val what = args.checkString(0).lowercase()
        val right = when (what) {
            "click", "type" -> ScreenRights.INTERACT
            "seturl", "js", "javascript", "runjs" -> ScreenRights.CHANGE_URL
            "setresolution", "setrotation" -> ScreenRights.MODIFY_SCREEN
            else -> throw IllegalArgumentException("invalid right name")
        }

        val o = owner
        val tes = getConnectedScreenEx()
        val side = screenSide
        if (o == null || tes == null || side == null) {
            return null
        }
        val scr = tes.getScreen(side) ?: return null
        return if ((scr.rightsFor(o.uuid) and right) == 0) FALSE else TRUE
    }

    @ComputerFunc
    fun hasUpgrade(args: IComputerArgs): Array<Any?>? {
        val name = args.checkString(0)

        val o = owner
        val tes = getConnectedScreenEx()
        val side = screenSide
        if (o == null || tes == null || side == null) {
            return null
        }
        val scr = tes.getScreen(side) ?: return null
        return if (scr.upgrades.any { stack -> (stack.item as IUpgrade).getJSName(stack).equals(name, ignoreCase = true) }) TRUE else FALSE
    }

    @ComputerFunc
    fun getSize(args: IComputerArgs): Array<Any?>? {
        val tes = getConnectedScreenEx()
        val side = screenSide
        if (owner == null || tes == null || side == null) {
            return null
        }
        val scr = tes.getScreen(side) ?: return null
        val sz = scr.size
        return arrayOf(sz.x, sz.y)
    }

    @ComputerFunc
    fun getResolution(args: IComputerArgs): Array<Any?>? {
        val tes = getConnectedScreenEx()
        val side = screenSide
        if (owner == null || tes == null || side == null) {
            return null
        }
        val scr = tes.getScreen(side) ?: return null
        val res = scr.resolution
        return arrayOf(res.x, res.y)
    }

    @ComputerFunc
    fun getRotation(args: IComputerArgs): Array<Any?>? {
        val tes = getConnectedScreenEx()
        val side = screenSide
        if (owner == null || tes == null || side == null) {
            return null
        }
        val scr = tes.getScreen(side) ?: return null
        return arrayOf(scr.rotation.angleAsInt)
    }

    @ComputerFunc
    fun getURL(args: IComputerArgs): Array<Any?>? {
        val tes = getConnectedScreenEx()
        val side = screenSide
        if (owner == null || tes == null || side == null) {
            return null
        }
        val scr = tes.getScreen(side) ?: return null
        return arrayOf(scr.url)
    }

    @ComputerFunc
    fun click(args: IComputerArgs): Array<Any?> {
        var x = args.checkInteger(0)
        var y = args.checkInteger(1)
        var action = "click"
        if (args.count() > 2) {
            action = args.checkString(2).lowercase()
        }

        val o = owner
        val scr = getConnectedScreenEx()
        val side = screenSide
        if (o == null || scr == null || side == null) {
            return err("notlinked")
        }

        val scrscr = scr.getScreen(side) ?: return err("notlinked")
        return if ((scrscr.rightsFor(o.uuid) and ScreenRights.INTERACT) == 0) {
            err("restrictions")
        } else {
            when (scrscr.rotation) {
                Rotation.ROT_90 -> y = scrscr.resolution.y - y
                Rotation.ROT_180 -> {
                    x = scrscr.resolution.x - x
                    y = scrscr.resolution.y - y
                }
                Rotation.ROT_270 -> x = scrscr.resolution.x - x
                else -> {}
            }
            TRUE
        }
    }

    private fun realType(what: String): Array<Any?> {
        val o = owner
        val scr = getConnectedScreenEx()
        val side = screenSide
        if (o == null || scr == null || side == null) {
            return err("notlinked")
        }
        val scrSide = scr.getScreen(side) ?: return err("notlinked")
        return if ((scrSide.rightsFor(o.uuid) and ScreenRights.INTERACT) == 0) {
            err("restrictions")
        } else {
            scr.type(side, what, null)
            TRUE
        }
    }

    @ComputerFunc
    fun type(args: IComputerArgs): Array<Any?> {
        val text = args.checkString(0)
        if (text.length > 64) {
            return err("toolong")
        }
        if (text.indexOf(1.toChar()) >= 0) {
            return err("badchar")
        }
        return realType("t$text")
    }

    @ComputerFunc
    fun typeAdvanced(args: IComputerArgs): Array<Any?> {
        val data = ArrayList<TypeData>()
        val map = args.checkTable(0)
        var maxEvents = 0

        for (o in map.values) {
            if (o !is Map<*, *>) {
                return err("badinput")
            }

            if (++maxEvents >= 16) {
                return err("toomany")
            }

            val action = o["action"]
            val chr = o["char"]
            var code = 0

            if (action !is String) {
                return err("badaction")
            }
            if (chr !is String) {
                return err("badchar")
            }

            val dataAction = when {
                action.equals("press", ignoreCase = true) -> TypeData.Action.PRESS
                action.equals("release", ignoreCase = true) -> TypeData.Action.RELEASE
                action.equals("type", ignoreCase = true) -> TypeData.Action.TYPE
                else -> return err("unknownaction")
            }

            if (chr.isEmpty()) {
                return err("emptychar")
            }

            if (dataAction != TypeData.Action.TYPE) {
                val oCode = o["code"]
                if (oCode !is Double) {
                    return err("badcode")
                }
                code = oCode.toInt()
            }

            data.add(TypeData(dataAction, code, 0, 0))
        }

        return realType(WebDisplays.GSON.toJson(data))
    }

    @ComputerFunc
    fun setURL(args: IComputerArgs): Array<Any?> {
        val url = args.checkString(0)
        val o = owner
        val scr = getConnectedScreenEx()
        val side = screenSide

        if (o == null || scr == null || side == null) {
            return err("notlinked")
        }
        val scrSide = scr.getScreen(side) ?: return err("notlinked")
        return if ((scrSide.rightsFor(o.uuid) and ScreenRights.CHANGE_URL) == 0) {
            err("restrictions")
        } else {
            try {
                scr.setScreenURL(side, url)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            TRUE
        }
    }

    @ComputerFunc
    fun setResolution(args: IComputerArgs): Array<Any?> {
        val rx = args.checkInteger(0)
        val ry = args.checkInteger(1)
        val o = owner
        val scr = getConnectedScreenEx()
        val side = screenSide

        if (o == null || scr == null || side == null) {
            return err("notlinked")
        }
        val scrSide = scr.getScreen(side) ?: return err("notlinked")
        return if ((scrSide.rightsFor(o.uuid) and ScreenRights.MODIFY_SCREEN) == 0) {
            err("restrictions")
        } else {
            scr.setResolution(side, Vector2i(rx, ry))
            TRUE
        }
    }

    @ComputerFunc
    fun setRotation(args: IComputerArgs): Array<Any?> {
        var rot = args.checkInteger(0)
        if (rot < 0) {
            val toAdd = (rot / -360) + 1
            rot += toAdd * 360
        }

        rot /= 90
        rot = rot and 3

        val o = owner
        val scr = getConnectedScreenEx()
        val side = screenSide

        if (o == null || scr == null || side == null) {
            return err("notlinked")
        }
        val scrSide = scr.getScreen(side) ?: return err("notlinked")
        return if ((scrSide.rightsFor(o.uuid) and ScreenRights.MODIFY_SCREEN) == 0) {
            err("restrictions")
        } else {
            scr.setRotation(side, Rotation.values()[rot])
            TRUE
        }
    }

    @ComputerFunc
    fun runJS(args: IComputerArgs): Array<Any?> {
        val code = args.checkString(0)
        val o = owner
        val scr = getConnectedScreenEx()
        val side = screenSide

        if (o == null || scr == null || side == null) {
            return err("notlinked")
        }
        val scrSide = scr.getScreen(side) ?: return err("notlinked")
        return if ((scrSide.rightsFor(o.uuid) and ScreenRights.CHANGE_URL) == 0) {
            err("restrictions")
        } else {
            // scr.evalJS(side, code)
            TRUE
        }
    }

    @ComputerFunc
    fun unlink(args: IComputerArgs): Array<Any?>? {
        if (isLinked()) {
            screenPos = null
            screenSide = null
            setChanged()
        }

        return null
    }

    companion object {
        private val TRUE: Array<Any?> = arrayOf(true)
        private val FALSE: Array<Any?> = arrayOf(false)

        private fun err(str: String): Array<Any?> = arrayOf(false, str)
    }
}
