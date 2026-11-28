/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.entity

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.cinemamod.mcef.listeners.MCEFCursorChangeListener
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.block.BlockPeripheral.Companion.point
import net.montoyo.wd.block.BlockScreen
import net.montoyo.wd.client.ClientProxy
import net.montoyo.wd.config.CommonConfig
import net.montoyo.wd.controls.builtin.ClickControl
import net.montoyo.wd.core.DefaultUpgrade
import net.montoyo.wd.core.IUpgrade
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.data.ScreenConfigData
import net.montoyo.wd.init.BlockInit
import net.montoyo.wd.init.ItemInit
import net.montoyo.wd.init.TileInit
import net.montoyo.wd.miniserv.SyncPlugin
import net.montoyo.wd.net.WDDistributor
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.client_bound.S2CMessageAddScreen
import net.montoyo.wd.net.client_bound.S2CMessageScreenUpdate
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.MutableAABB
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.NibbleArray
import net.montoyo.wd.utilities.Rotation
import net.montoyo.wd.utilities.ScreenIterator
import net.montoyo.wd.utilities.TypeData
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector2i
import net.montoyo.wd.utilities.Vector3f
import net.montoyo.wd.utilities.Vector3i
import net.montoyo.wd.utilities.VideoType
import org.cef.browser.CefBrowser
import java.io.IOException
import java.util.ArrayList
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.floor
import kotlin.math.max

class TileEntityScreen(arg2: BlockPos, arg3: BlockState) :
    BlockEntity(TileInit.SCREEN_BLOCK_ENTITY.get(), arg2, arg3) {

    class Screen {
        @JvmField
        var side: BlockSide = BlockSide.BOTTOM

        @JvmField
        var size: Vector2i = Vector2i()

        @JvmField
        var resolution: Vector2i = Vector2i()

        @JvmField
        var rotation: Rotation = Rotation.ROT_0

        @JvmField
        var url: String = ""

        internal var videoType: VideoType? = null

        @JvmField
        var owner: NameUUIDPair? = null

        @JvmField
        var friends: ArrayList<NameUUIDPair> = ArrayList()

        @JvmField
        var friendRights: Int = 0

        @JvmField
        var otherRights: Int = 0

        @JvmField
        var browser: CefBrowser? = null

        @JvmField
        var upgrades: ArrayList<ItemStack> = ArrayList()

        @JvmField
        var doTurnOnAnim: Boolean = false

        @JvmField
        var turnOnTime: Long = 0

        @JvmField
        var lastVisibleMs: Long = System.currentTimeMillis()

        @JvmField
        var laserUser: Player? = null

        @JvmField
        val lastMousePos: Vector2i = Vector2i()

        @JvmField
        var redstoneStatus: NibbleArray? = null //null on client

        @JvmField
        var autoVolume: Boolean = true

        @JvmField
        var mouseType: Int = 0

        fun serialize(registryAccess: HolderLookup.Provider): CompoundTag {
            val tag = CompoundTag()
            tag.putByte("Side", side.ordinal.toByte())
            tag.putInt("Width", size.x)
            tag.putInt("Height", size.y)
            tag.putInt("ResolutionX", resolution.x)
            tag.putInt("ResolutionY", resolution.y)
            tag.putByte("Rotation", rotation.ordinal.toByte())
            tag.putString("URL", url)

            val o = owner
            if (o == null) {
                Log.warning("Found TES with NO OWNER!!")
            } else {
                tag.putString("OwnerName", o.name)
                tag.putUUID("OwnerUUID", o.uuid)
            }

            var list = ListTag()
            for (f in friends) {
                val nf = CompoundTag()
                nf.putString("Name", f.name)
                nf.putUUID("UUID", f.uuid)
                list.add(nf)
            }

            tag.put("Friends", list)
            tag.putByte("FriendRights", friendRights.toByte())
            tag.putByte("OtherRights", otherRights.toByte())

            list = ListTag()
            for (isStack in upgrades) {
                list.add(isStack.save(registryAccess, CompoundTag()))
            }

            tag.put("Upgrades", list)
            tag.putBoolean("AutoVolume", autoVolume)
            return tag
        }

        fun rightsFor(ply: Player): Int = rightsFor(ply.gameProfile.id)

        fun rightsFor(uuid: UUID?): Int {
            val o = owner
            if (o != null && o.uuid == uuid) {
                return ScreenRights.ALL
            }

            if (friends.any { f -> f.uuid == uuid }) {
                return friendRights
            }

            return otherRights
        }

        fun setupRedstoneStatus(world: Level, start: BlockPos) {
            if (world.isClientSide()) {
                Log.warning("Called Screen.setupRedstoneStatus() on client.")
                return
            }

            if (redstoneStatus != null) {
                Log.warning("Called Screen.setupRedstoneStatus() on server, but redstone status is non-null")
                return
            }

            val values = Direction.values()
            val status = NibbleArray(size.x * size.y)
            redstoneStatus = status
            val facing = values[side.reverse().ordinal]
            val it = ScreenIterator(start, side, size)

            while (it.hasNext()) {
                val idx = it.getIndex()
                status.set(idx, world.getSignal(it.next(), facing))
            }
        }

        fun clampResolution() {
            if (resolution.x > CommonConfig.Screen.maxResolutionX) {
                val newY = resolution.y.toFloat() * CommonConfig.Screen.maxResolutionX.toFloat() / resolution.x.toFloat()
                resolution.x = CommonConfig.Screen.maxResolutionX
                resolution.y = newY.toInt()
            }

            if (resolution.y > CommonConfig.Screen.maxResolutionY) {
                val newX = resolution.x.toFloat() * CommonConfig.Screen.maxResolutionY.toFloat() / resolution.y.toFloat()
                resolution.x = newX.toInt()
                resolution.y = CommonConfig.Screen.maxResolutionY
            }
        }

        fun createBrowser(doAnim: Boolean) {
            if (WebDisplays.PROXY is ClientProxy && MCEF.isInitialized()) {
                ClientProxy.ensureSchemeHandlers()
                val targetW = if (rotation.isVertical) resolution.y else resolution.x
                val targetH = if (rotation.isVertical) resolution.x else resolution.y
                val created = MCEF.createBrowser(
                    WebDisplays.applyBlacklist(url),
                    false,
                    max(1, targetW),
                    max(1, targetH)
                )
                browser = created

                if (created is MCEFBrowser) {
                    created.setCursorChangeListener(MCEFCursorChangeListener { type -> mouseType = type })
                }

                lastVisibleMs = System.currentTimeMillis()
                doTurnOnAnim = doAnim
                turnOnTime = lastVisibleMs
            }
        }

        companion object {
            @JvmStatic
            fun deserialize(tag: CompoundTag, registryAccess: HolderLookup.Provider): Screen {
                val ret = Screen()
                ret.side = BlockSide.values()[tag.getByte("Side").toInt()]
                ret.size = Vector2i(tag.getInt("Width"), tag.getInt("Height"))
                ret.resolution = Vector2i(tag.getInt("ResolutionX"), tag.getInt("ResolutionY"))
                ret.rotation = Rotation.values()[tag.getByte("Rotation").toInt()]
                ret.url = tag.getString("URL")
                ret.videoType = VideoType.getTypeFromURL(ret.url)

                if (ret.resolution.x <= 0 || ret.resolution.y <= 0) {
                    var psx = ret.size.x.toFloat() * 16.0f - 4.0f
                    var psy = ret.size.y.toFloat() * 16.0f - 4.0f
                    psx *= 8.0f //TODO: Use ratio in config file
                    psy *= 8.0f

                    ret.resolution.x = psx.toInt()
                    ret.resolution.y = psy.toInt()
                }

                if (tag.contains("OwnerName")) {
                    val name = tag.getString("OwnerName")
                    val uuid = tag.getUUID("OwnerUUID")
                    ret.owner = NameUUIDPair(name, uuid)
                }

                val friends = tag.getList("Friends", 10)
                ret.friends = ArrayList(friends.size)

                for (i in 0 until friends.size) {
                    val nf = friends.getCompound(i)
                    val pair = NameUUIDPair(nf.getString("Name"), nf.getUUID("UUID"))
                    ret.friends.add(pair)
                }

                ret.friendRights = tag.getByte("FriendRights").toInt()
                ret.otherRights = tag.getByte("OtherRights").toInt()

                val upgrades = tag.getList("Upgrades", 10)
                ret.upgrades = ArrayList()

                for (i in 0 until upgrades.size) {
                    ret.upgrades.add(ItemStack.parseOptional(registryAccess, upgrades.getCompound(i)))
                }

                if (tag.contains("AutoVolume")) {
                    ret.autoVolume = tag.getBoolean("AutoVolume")
                }

                return ret
            }
        }
    }

    private val screens = ArrayList<Screen>()
    private var renderBB: AABB = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
    private var loaded: Boolean = true

    @JvmField
    var ytVolume: Float = Float.POSITIVE_INFINITY

    fun forEachScreenBlocks(side: BlockSide, func: Consumer<BlockPos>) {
        val scr = getScreen(side)

        if (scr != null) {
            val it = ScreenIterator(blockPos, side, scr.size)
            while (it.hasNext()) {
                func.accept(it.next())
            }
        }
    }

    fun isLoaded(): Boolean = loaded

    fun load() {
        loaded = true
    }

    fun unload() {
        for (scr in screens) {
            scr.browser?.let {
                it.close(true)
                scr.browser = null
            }
        }

        loaded = false
    }

    override fun loadAdditional(tag: CompoundTag, registryAccess: HolderLookup.Provider) {
        super.loadAdditional(tag, registryAccess)

        val list = tag.getList("WDScreens", Tag.TAG_COMPOUND.toInt())
        if (list.isEmpty()) {
            return
        }

        screens.clear()
        for (i in 0 until list.size) {
            screens.add(Screen.deserialize(list.getCompound(i), registryAccess))
        }
    }

    override fun getUpdateTag(registryAccess: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        saveAdditional(tag, registryAccess)
        return tag
    }

    override fun saveAdditional(tag: CompoundTag, registryAccess: HolderLookup.Provider) {
        super.saveAdditional(tag, registryAccess)

        val list = ListTag()
        for (scr in screens) {
            list.add(scr.serialize(registryAccess))
        }

        tag.put("WDScreens", list)
    }

    fun addScreen(
        side: BlockSide,
        size: Vector2i,
        resolution: Vector2i?,
        owner: Player?,
        sendUpdate: Boolean
    ): Screen {
        for (scr in screens) {
            if (scr.side == side) {
                return scr
            }
        }

        val ret = Screen()
        ret.side = side
        ret.size = size
        ret.url = CommonConfig.Browser.homepage
        ret.friends = ArrayList()
        ret.friendRights = ScreenRights.DEFAULTS
        ret.otherRights = ScreenRights.DEFAULTS
        ret.upgrades = ArrayList()

        if (owner != null) {
            ret.owner = NameUUIDPair(owner.gameProfile)

            if (side == BlockSide.TOP || side == BlockSide.BOTTOM) {
                var rot = floor((owner.yRot * 4.0f / 360.0f).toDouble() + 2.5).toInt() and 3

                if (side == BlockSide.TOP) {
                    if (rot == 1) {
                        rot = 3
                    } else if (rot == 3) {
                        rot = 1
                    }
                }

                ret.rotation = Rotation.values()[rot]
            }
        }

        if (resolution == null || resolution.x < 1 || resolution.y < 1) {
            var psx = size.x.toFloat() * 16.0f - 4.0f
            var psy = size.y.toFloat() * 16.0f - 4.0f
            psx *= 8.0f //TODO: Use ratio in config file
            psy *= 8.0f

            ret.resolution = Vector2i(psx.toInt(), psy.toInt())
        } else {
            ret.resolution = resolution
        }

        ret.clampResolution()

        val lvl = level!!
        if (!lvl.isClientSide) {
            ret.setupRedstoneStatus(lvl, blockPos)

            if (sendUpdate) {
                WDNetworkRegistry.INSTANCE.send(
                    WDDistributor.NEAR.with { point(lvl, blockPos) },
                    S2CMessageAddScreen(this, ret)
                )
            }
        }

        screens.add(ret)

        if (lvl.isClientSide) {
            updateAABB()
        } else {
            setChanged()
        }

        return ret
    }

    fun getScreen(side: BlockSide): Screen? {
        for (scr in screens) {
            if (scr.side == side) {
                return scr
            }
        }

        return null
    }

    fun screenCount(): Int = screens.size

    fun getScreen(idx: Int): Screen = screens[idx]

    fun clear() {
        screens.clear()

        val lvl = level
        if (lvl != null && !lvl.isClientSide) {
            setChanged()
        }
    }

    fun requestData(ep: ServerPlayer) {
        val lvl = level ?: return
        if (!lvl.isClientSide) {
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.PLAYER.with { ep },
                S2CMessageAddScreen(this)
            )
        }
    }

    @Throws(IOException::class)
    fun setScreenURL(side: BlockSide, url: String) {
        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Attempt to change URL of non-existing screen on side %s", side.toString())
            return
        }

        var weburl = url(Util.resolveUrlOrSearch(url))
        weburl = WebDisplays.applyBlacklist(weburl) ?: weburl
        scr.url = weburl
        scr.videoType = VideoType.getTypeFromURL(weburl)

        val lvl = level ?: return
        if (lvl.isClientSide) {
            scr.browser?.loadURL(weburl)
        } else {
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { point(lvl, blockPos) },
                S2CMessageScreenUpdate.setURL(this, side, weburl)
            )
            setChanged()
        }
    }

    fun removeScreen(side: BlockSide) {
        var idx = -1
        for (i in 0 until screens.size) {
            if (screens[i].side == side) {
                idx = i
                break
            }
        }

        if (idx < 0) {
            Log.error("Tried to delete non-existing screen on side %s", side.toString())
            return
        }

        val lvl = level ?: return
        if (lvl.isClientSide) {
            screens[idx].browser?.let {
                it.close(true)
                screens[idx].browser = null
            }
        } else {
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { point(lvl, blockPos) },
                S2CMessageScreenUpdate(this.blockPos, side)
            ) //Delete the screen
        }

        screens.removeAt(idx)

        if (!lvl.isClientSide) {
            if (screens.isEmpty()) { //No more screens: remove tile entity
                lvl.setBlockAndUpdate(
                    blockPos,
                    BlockInit.blockScreen.get().defaultBlockState().setValue(BlockScreen.hasTE, false)
                )
            } else {
                setChanged()
            }
        }
    }

    fun setResolution(side: BlockSide, res: Vector2i) {
        if (res.x < 1 || res.y < 1) {
            Log.warning(
                "Call to TileEntityScreen.setResolution(%s) with suspicious values X=%d and Y=%d",
                side.toString(),
                res.x,
                res.y
            )
            return
        }

        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Tried to change resolution of non-existing screen on side %s", side.toString())
            return
        }

        scr.resolution = res
        scr.clampResolution()

        val lvl = level ?: return
        if (lvl.isClientSide) {
            WebDisplays.PROXY.screenUpdateResolutionInGui(Vector3i(blockPos), side, res)

            scr.browser?.let {
                it.close(true)
                scr.browser = null //Will be re-created by renderer
            }
        } else {
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { point(lvl, blockPos) },
                S2CMessageScreenUpdate.setResolution(this, side, res)
            )
            setChanged()
        }
    }

    fun clearLaserUser(side: BlockSide) {
        getScreen(side)?.laserUser = null
    }

    fun click(side: BlockSide, vec: Vector2i) {
        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Attempt click non-existing screen of side %s", side.toString())
            return
        }

        val lvl = level ?: return
        if (lvl.isClientSide) {
            Log.warning("TileEntityScreen.click() from client side is useless...")
        } else if (getLaserUser(scr) == null) {
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { point(lvl, blockPos) },
                S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.CLICK, vec)
            )
        }
    }

    internal fun clickUnsafe(side: BlockSide, action: ClickControl.ControlType, x: Int, y: Int) {
        val lvl = level ?: return
        if (lvl.isClientSide) {
            val vec = if (action == ClickControl.ControlType.UP) null else Vector2i(x, y)
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { point(lvl, blockPos) },
                S2CMessageScreenUpdate.click(this, side, action, vec)
            )
        }
    }

    fun handleMouseEvent(side: BlockSide, event: ClickControl.ControlType, vec: Vector2i?, buttonArg: Int) {
        if (buttonArg > 1) return // buttons above 1 crash the game

        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Attempt inject mouse events on non-existing screen of side %s", side.toString())
            return
        }

        val browser = scr.browser
        if (browser is MCEFBrowser) {
            var button = buttonArg
            if (button == 1) button = 0 else if (button == 0) button = 1

            when (event) {
                ClickControl.ControlType.CLICK -> {
                    val v = vec!!
                    browser.sendMouseMove(v.x, v.y) //Move to target
                    browser.sendMousePress(v.x, v.y, button) //Press
                    browser.sendMouseRelease(v.x, v.y, button) //Release
                }
                ClickControl.ControlType.DOWN -> {
                    val v = vec!!
                    browser.sendMouseMove(v.x, v.y) //Move to target
                    browser.sendMousePress(v.x, v.y, button) //Press
                }
                ClickControl.ControlType.MOVE -> {
                    val v = vec!!
                    browser.sendMouseMove(v.x, v.y) //Move
                }
                ClickControl.ControlType.UP -> {
                    browser.sendMouseRelease(scr.lastMousePos.x, scr.lastMousePos.y, button) //Release
                }
            }

            browser.setFocus(true)

            if (vec != null) {
                scr.lastMousePos.x = vec.x
                scr.lastMousePos.y = vec.y
            }
        }
    }

    fun onClientLoad() {
        val lvl = level
        if (lvl == null || !lvl.isClientSide) {
            return
        }

        WebDisplays.PROXY.trackScreen(this, true)

        for (screen in screens) {
            val b = screen.browser
            if (b != null) {
                b.loadURL(WebDisplays.applyBlacklist(screen.url))
            }
        }

        updateAABB()
    }

    fun onClientUnload() {
        val lvl = level
        if (lvl != null && lvl.isClientSide) {
            WebDisplays.PROXY.trackScreen(this, false)

            for (scr in screens) {
                scr.browser?.let {
                    it.close(true)
                    scr.browser = null
                }
            }
        }
    }

    private fun updateAABB() {
        val origin = Vector3i(blockPos)
        var box: MutableAABB? = null

        for (scr in screens) {
            val f = scr.side.forward
            val fx = max(f.x, 0)
            val fy = max(f.y, 0)
            val fz = max(f.z, 0)
            var ox = 0
            if (scr.side == BlockSide.NORTH) ox = 1
            var oz = 0
            if (scr.side == BlockSide.EAST || scr.side == BlockSide.TOP || scr.side == BlockSide.BOTTOM) {
                oz = 1
            }

            if (box == null) {
                box = MutableAABB(
                    (origin.x + fx + ox).toDouble(),
                    (origin.y + fy).toDouble(),
                    (origin.z + fz + oz).toDouble(),
                    (origin.x + ox + scr.side.right.x * scr.size.x + fx + scr.side.up.x * scr.size.y).toDouble(),
                    (origin.y + scr.side.right.y * scr.size.x + fy + scr.side.up.y * scr.size.y).toDouble(),
                    (origin.z + oz + scr.side.right.z * scr.size.x + fz + scr.side.up.z * scr.size.y).toDouble()
                )
            } else {
                box.expand(
                    (origin.x + fx + ox).toDouble(),
                    (origin.y + fy).toDouble(),
                    (origin.z + fz + oz).toDouble(),
                    (origin.x + ox + scr.side.right.x * scr.size.x + fx + scr.side.up.x * scr.size.y).toDouble(),
                    (origin.y + scr.side.right.y * scr.size.x + fy + scr.side.up.y * scr.size.y).toDouble(),
                    (origin.z + oz + scr.side.right.z * scr.size.x + fz + scr.side.up.z * scr.size.y).toDouble()
                )
            }
        }

        renderBB = box?.toMc() ?: AABB(worldPosition)
    }

    fun updateClientSideURL(target: CefBrowser, url: String) {
        for (scr in screens) {
            if (scr.browser == target) {
                try {
                    url(url)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
                val blacklisted = WebDisplays.isSiteBlacklisted(url)
                scr.url = if (blacklisted) WebDisplays.BLACKLIST_URL else url //FIXME: This is an invalid fix for something that CANNOT be fixed
                scr.videoType = VideoType.getTypeFromURL(scr.url)
                ytVolume = Float.POSITIVE_INFINITY //Force volume update

                if (blacklisted) {
                    scr.browser?.loadURL(WebDisplays.BLACKLIST_URL)
                }

                break
            }
        }
    }

    override fun setRemoved() {
        super.setRemoved()

        val lvl = level
        if (lvl != null && lvl.isClientSide) {
            onClientUnload()
        }
    }

    fun addFriend(ply: ServerPlayer?, side: BlockSide, pair: NameUUIDPair) {
        val lvl = level ?: return
        if (!lvl.isClientSide) {
            val scr = getScreen(side)
            if (scr == null) {
                Log.error("Tried to add friend to invalid screen side %s", side.toString())
                return
            }

            if (!scr.friends.contains(pair)) {
                scr.friends.add(pair)
                ScreenConfigData(Vector3i(blockPos), side, scr).updateOnly().sendTo(point(lvl, blockPos))
                setChanged()
            }
        }
    }

    fun removeFriend(ply: ServerPlayer?, side: BlockSide, pair: NameUUIDPair) {
        val lvl = level ?: return
        if (!lvl.isClientSide) {
            val scr = getScreen(side)
            if (scr == null) {
                Log.error("Tried to remove friend from invalid screen side %s", side.toString())
                return
            }

            if (scr.friends.remove(pair)) {
                checkLaserUserRights(scr)
                ScreenConfigData(Vector3i(blockPos), side, scr).updateOnly().sendTo(point(lvl, blockPos))
                setChanged()
            }
        }
    }

    fun setRights(ply: ServerPlayer?, side: BlockSide, fr: Int, or: Int) {
        val lvl = level ?: return
        if (!lvl.isClientSide) {
            val scr = getScreen(side)
            if (scr == null) {
                Log.error("Tried to change rights of invalid screen on side %s", side.toString())
                return
            }

            scr.friendRights = fr
            scr.otherRights = or

            checkLaserUserRights(scr)
            ScreenConfigData(Vector3i(blockPos), side, scr).updateOnly().sendTo(point(lvl, blockPos))
            setChanged()
        }
    }

    fun type(side: BlockSide, text: String, soundPos: BlockPos?) {
        type(side, text, soundPos, null)
    }

    fun type(side: BlockSide, text: String, soundPos: BlockPos?, sender: ServerPlayer?) {
        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Tried to type on invalid screen on side %s", side.toString())
            return
        }

        val lvl = level ?: return
        if (lvl.isClientSide) {
            val browser = scr.browser
            if (browser is MCEFBrowser) {
                try {
                    browser.setFocus(true)
                    if (text.startsWith("t")) {
                        for (i in 1 until text.length) {
                            val chr = text[i]
                            if (chr.code == 1) {
                                break
                            }

                            browser.sendKeyTyped(chr, 0)
                        }
                    } else {
                        val data = WebDisplays.GSON.fromJson(text, Array<TypeData>::class.java)

                        for (ev in data) {
                            val actualEv = if (ev.getKeyCode() == 257 || ev.getKeyCode() == 335) {
                                TypeData(ev.getAction(), 10, ev.getModifier(), ev.getScanCode())
                            } else {
                                ev
                            }

                            when (actualEv.getAction()) {
                                TypeData.Action.PRESS -> {
                                    browser.sendKeyPress(actualEv.getKeyCode(), actualEv.getScanCode().toLong(), actualEv.getModifier())
                                    if (actualEv.getKeyCode() == 10) {
                                        browser.sendKeyTyped('\r', actualEv.getModifier())
                                    }
                                }
                                TypeData.Action.RELEASE -> {
                                    browser.sendKeyRelease(actualEv.getKeyCode(), actualEv.getScanCode().toLong(), actualEv.getModifier())
                                }
                                TypeData.Action.TYPE -> {
                                    browser.sendKeyTyped(actualEv.getKeyCode().toChar(), actualEv.getModifier())
                                }
                                else -> throw RuntimeException("Invalid type action '${actualEv.getAction()}'")
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.warningEx("Suspicious keyboard type packet received...", t)
                }
            }
        } else {
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with(
                    if (sender != null) {
                        { point(sender, lvl, blockPos) }
                    } else {
                        { point(lvl, blockPos) }
                    }
                ),
                S2CMessageScreenUpdate.type(this, side, text)
            )

            if (soundPos != null) {
                playSoundAt(WebDisplays.INSTANCE.soundTyping, soundPos, 0.25f, 1.0f)
            }
        }
    }

    private fun playSoundAt(snd: SoundEvent?, at: BlockPos, vol: Float, pitch: Float) {
        val lvl = level ?: return
        val s = snd ?: return
        val x = at.x.toDouble()
        val y = at.y.toDouble()
        val z = at.z.toDouble()

        lvl.playSound(null, x + 0.5, y + 0.5, z + 0.5, s, SoundSource.BLOCKS, vol, pitch)
    }

    //If equal is null, no duplicate check is preformed
    fun addUpgrade(side: BlockSide, isStack: ItemStack, player: Player?, abortIfExisting: Boolean): Boolean {
        val lvl = level ?: return false
        if (lvl.isClientSide) {
            val itemAsUpgrade = isStack.item as IUpgrade
            val scr = getScreen(side)!!
            val isCopy = isStack.copy() //FIXME: Duct tape fix, because the original stack will be shrinked
            scr.upgrades.add(isCopy)
            itemAsUpgrade.onInstall(this, side, player, isCopy)
            return false
        }

        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Tried to add an upgrade on invalid screen on side %s", side.toString())
            return false
        }

        val item = isStack.item
        if (item !is IUpgrade) {
            Log.error(
                "Tried to add a non-upgrade item %s to screen (%s does not implement IUpgrade)",
                safeName(isStack),
                item.javaClass.canonicalName
            )
            return false
        }

        if (scr.upgrades.size >= 16) {
            Log.error(
                "Can't insert upgrade %s in screen %s at %s: too many upgrades already!",
                safeName(isStack),
                side.toString(),
                blockPos.toString()
            )
            return false
        }

        if (abortIfExisting && scr.upgrades.any { otherStack -> item.isSameUpgrade(isStack, otherStack) }) {
            return false //Upgrade already exists
        }

        val isCopy = isStack.copy() //FIXME: Duct tape fix, because the original stack will be shrinked
        isCopy.count = 1

        scr.upgrades.add(isCopy)
        if (player != null && !player.level().isClientSide) {
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { point(lvl, blockPos) },
                S2CMessageScreenUpdate.upgrade(this, side, true, isStack)
            )
            item.onInstall(this, side, player, isCopy)
            playSoundAt(WebDisplays.INSTANCE.soundUpgradeAdd, blockPos, 1.0f, 1.0f)
        }
        setChanged()
        return true
    }

    fun hasUpgrade(side: BlockSide, isStack: ItemStack): Boolean {
        val scr = getScreen(side) ?: return false
        val item = isStack.item as? IUpgrade ?: return false
        return scr.upgrades.any { otherStack -> item.isSameUpgrade(isStack, otherStack) }
    }

    fun hasUpgrade(side: BlockSide, du: DefaultUpgrade): Boolean {
        val scr = getScreen(side) ?: return false
        return when (du) {
            DefaultUpgrade.LASERMOUSE -> scr.upgrades.any { du.matchesLaserMouse(it) }
            DefaultUpgrade.REDINPUT -> scr.upgrades.any { du.matchesRedInput(it) }
            DefaultUpgrade.GPS -> scr.upgrades.any { du.matchesGps(it) }
            DefaultUpgrade.REDOUTPUT -> scr.upgrades.any { du.matchesRedOutput(it) }
        }
    }

    fun removeUpgrade(side: BlockSide, isStack: ItemStack, player: Player?) {
        val lvl = level ?: return
        if (lvl.isClientSide) {
            return
        }

        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Tried to remove an upgrade on invalid screen on side %s", side.toString())
            return
        }

        val item = isStack.item
        if (item !is IUpgrade) {
            Log.error(
                "Tried to remove a non-upgrade item %s to screen (%s does not implement IUpgrade)",
                safeName(isStack),
                item.javaClass.canonicalName
            )
            return
        }

        var idxToRemove = -1
        for (i in 0 until scr.upgrades.size) {
            if (item.isSameUpgrade(isStack, scr.upgrades[i])) {
                idxToRemove = i
                break
            }
        }

        if (idxToRemove >= 0) {
            dropUpgrade(scr.upgrades[idxToRemove], side, player)
            scr.upgrades.removeAt(idxToRemove)
            if (player != null && !player.level().isClientSide) {
                WDNetworkRegistry.INSTANCE.send(
                    WDDistributor.NEAR.with { point(lvl, blockPos) },
                    S2CMessageScreenUpdate.upgrade(this, side, false, isStack)
                )
                playSoundAt(WebDisplays.INSTANCE.soundUpgradeDel, blockPos, 1.0f, 1.0f)
            }
            setChanged()
        } else {
            Log.warning(
                "Tried to remove non-existing upgrade %s to screen %s at %s",
                safeName(isStack),
                side.toString(),
                blockPos.toString()
            )
        }
    }

    private fun dropUpgrade(isStack: ItemStack, side: BlockSide, ply: Player?) {
        if (!(isStack.item as IUpgrade).onRemove(this, side, ply, isStack)) { //Drop upgrade item
            var spawnDrop = true

            if (ply != null) {
                if (ply.isCreative || ply.addItem(isStack)) {
                    spawnDrop = false //If in creative or if the item was added to the player's inventory, don't spawn drop entity
                }
            }

            if (spawnDrop) {
                val pos = Vector3f(blockPos.x.toFloat(), blockPos.y.toFloat(), blockPos.z.toFloat())
                pos.addMul(side.backward.toFloat(), 1.5f)

                level?.addFreshEntity(ItemEntity(level!!, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), isStack))
            }
        }
    }

    private fun getScreenForLaserOp(side: BlockSide, ply: Player): Screen? {
        val lvl = level ?: return null
        if (lvl.isClientSide) {
            return null
        }

        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Called laser operation on invalid screen on side %s", side.toString())
            return null
        }

        if ((scr.rightsFor(ply) and ScreenRights.INTERACT) == 0) {
            return null //Don't output an error, it can 'legally' happen
        }

        if (scr.upgrades.none { DefaultUpgrade.LASERMOUSE.matchesLaserMouse(it) }) {
            Log.error("Called laser operation on side %s, but it's missing the laser sensor upgrade", side.toString())
            return null
        }

        return scr //Okay, go for it...
    }

    fun laserDownMove(side: BlockSide, ply: Player, pos: Vector2i, down: Boolean, button: Int) {
        val lvl = level ?: return
        val scr = getScreenForLaserOp(side, ply)

        if (scr != null) {
            val currentLaserUser = getLaserUser(scr)
            if (currentLaserUser != null && currentLaserUser !== ply) {
                return
            }

            when {
                button == -1 -> WDNetworkRegistry.INSTANCE.send(
                    WDDistributor.NEAR.with { point(ply, lvl, blockPos) },
                    S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.MOVE, pos, -1)
                )
                down -> {
                    scr.laserUser = ply
                    WDNetworkRegistry.INSTANCE.send(
                        WDDistributor.NEAR.with { point(ply, lvl, blockPos) },
                        S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.DOWN, pos, button)
                    )
                }
                else -> {
                    scr.laserUser = null
                    WDNetworkRegistry.INSTANCE.send(
                        WDDistributor.NEAR.with { point(ply, lvl, blockPos) },
                        S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.UP, pos, button)
                    )
                }
            }
        }
    }

    fun laserUp(side: BlockSide, ply: Player, button: Int) {
        val lvl = level ?: return
        val scr = getScreenForLaserOp(side, ply)

        if (scr != null && getLaserUser(scr) === ply) {
            scr.laserUser = null
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { point(ply, lvl, blockPos) },
                S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.UP, null, button)
            )
        }
    }

    fun onDestroy(ply: Player?) {
        for (scr in screens) {
            scr.upgrades.forEach { isStack -> dropUpgrade(isStack, scr.side, ply) }
            scr.upgrades.clear()
        }

        val lvl = level ?: return
        WDNetworkRegistry.INSTANCE.send(
            WDDistributor.NEAR.with { point(lvl, blockPos) },
            S2CMessageScreenUpdate.turnOff(blockPos, null)
        )
    }

    fun disableScreen(side: BlockSide) {
        var remove: Screen? = null
        for (screen in screens) {
            if (screen.side == side) {
                remove = screen
                break
            }
        }

        if (remove == null) return

        val lvl = level
        if (lvl != null && !lvl.isClientSide) {
            val scrn = remove
            remove.upgrades.forEach { isStack -> dropUpgrade(isStack, scrn.side, null) }
        }

        remove.upgrades.clear()
        remove.browser?.close(true)
        screens.remove(remove)
    }

    fun setOwner(side: BlockSide, newOwner: Player?) {
        val lvl = level ?: return
        if (lvl.isClientSide) {
            Log.error("Called TileEntityScreen.setOwner() on client...")
            return
        }

        if (newOwner == null) {
            Log.error("Called TileEntityScreen.setOwner() with null owner")
            return
        }

        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Called TileEntityScreen.setOwner() on invalid screen on side %s", side.toString())
            return
        }

        scr.owner = NameUUIDPair(newOwner.gameProfile)
        WDNetworkRegistry.INSTANCE.send(
            WDDistributor.NEAR.with { point(lvl, blockPos) },
            S2CMessageScreenUpdate.owner(this, side, scr.owner!!)
        )
        checkLaserUserRights(scr)
        setChanged()
    }

    fun setRotation(side: BlockSide, rot: Rotation) {
        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Trying to change rotation of invalid screen on side %s", side.toString())
            return
        }

        val lvl = level ?: return
        if (lvl.isClientSide) {
            val oldWasVertical = scr.rotation.isVertical
            scr.rotation = rot

            WebDisplays.PROXY.screenUpdateRotationInGui(Vector3i(blockPos), side, rot)

            if (scr.browser != null && oldWasVertical != rot.isVertical) {
                scr.browser?.close(true)
                scr.browser = null //Will be re-created by renderer
            }
        } else {
            scr.rotation = rot
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { point(lvl, blockPos) },
                S2CMessageScreenUpdate.rotation(this, side, rot)
            )
            setChanged()
        }
    }

    fun setAutoVolume(side: BlockSide, av: Boolean) {
        val scr = getScreen(side)
        if (scr == null) {
            Log.error("Trying to toggle auto-volume on invalid screen (side %s)", side.toString())
            return
        }

        scr.autoVolume = av

        val lvl = level ?: return
        if (lvl.isClientSide) {
            WebDisplays.PROXY.screenUpdateAutoVolumeInGui(Vector3i(blockPos), side, av)
        } else {
            WDNetworkRegistry.INSTANCE.send(
                WDDistributor.NEAR.with { point(lvl, blockPos) },
                S2CMessageScreenUpdate.autoVolume(this, side, av)
            )
            setChanged()
        }
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun url(url: String): String {
            if (WebDisplays.PROXY !is ClientProxy) {
                val server = WebDisplays.PROXY.getServer()
                if (server != null) {
                    val serverPlayers = server.playerList.players
                    SyncPlugin.syncPlayers(serverPlayers)
                    for (serverPlayer in serverPlayers) {
                        SyncPlugin.setPlayerString(serverPlayer, url)
                    }
                }
            }
            return url
        }

        private fun getLaserUser(scr: Screen): Player? {
            val lu = scr.laserUser
            if (lu != null) {
                if (lu.isRemoved || lu.getItemInHand(InteractionHand.MAIN_HAND).item != ItemInit.LASER_POINTER.get()) {
                    scr.laserUser = null
                }
            }

            return scr.laserUser
        }

        private fun checkLaserUserRights(scr: Screen) {
            val lu = scr.laserUser
            if (lu != null && (scr.rightsFor(lu) and ScreenRights.INTERACT) == 0) {
                scr.laserUser = null
            }
        }

        private fun safeName(isStack: ItemStack): String = isStack.item.getName(isStack).string
    }
}
