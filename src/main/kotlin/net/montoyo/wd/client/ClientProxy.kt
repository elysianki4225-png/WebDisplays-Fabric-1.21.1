/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.advancements.AdvancementProgress
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.Options
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientAdvancements
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.montoyo.wd.SharedProxy
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.block.BlockScreen
import net.montoyo.wd.client.gui.*
import net.montoyo.wd.client.gui.loading.GuiLoader
import net.montoyo.wd.client.renderers.*
import net.montoyo.wd.config.CommonConfig
import net.montoyo.wd.core.HasAdvancement
import net.montoyo.wd.data.GuiData
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.init.BlockInit
import net.montoyo.wd.init.ItemInit
import net.montoyo.wd.init.TileInit
import net.montoyo.wd.item.ItemLaserPointer
import net.montoyo.wd.item.ItemMinePad2
import net.montoyo.wd.miniserv.client.Client
import net.montoyo.wd.net.WDContext
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl
import net.montoyo.wd.utilities.*
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandler
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.misc.CefCursorType
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.io.IOException
import java.lang.reflect.Field
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.*
import kotlin.math.max
import kotlin.math.min

@Environment(EnvType.CLIENT)
class ClientProxy : SharedProxy(), CefDisplayHandler {
    inner class PadData(url: String, @JvmField val id: UUID, @JvmField val mobile: Boolean) {
        @JvmField
        var view: CefBrowser? = null

        var isInHotbar = true
        var lastURLSent: Long = 0

        @JvmField
        val resX: Int = if (mobile) 412 else max(1, WebDisplays.INSTANCE.padResX.toInt())

        @JvmField
        val resY: Int = if (mobile) 732 else max(1, WebDisplays.INSTANCE.padResY.toInt())

        @JvmField
        var activeCursor = 0

        init {
            if (MCEF.isInitialized()) {
                val resolved = if (url.isBlank()) CommonConfig.Browser.homepage else Util.resolveUrlOrSearch(url)
                val webUrl = try {
                    TileEntityScreen.url(resolved)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
                ensureSchemeHandlers()
                view = MCEF.createBrowser(WebDisplays.applyBlacklist(webUrl) ?: webUrl, false, resX, resY, mobile)
                val v = view
                if (v is MCEFBrowser) {
                    v.setCursorChangeListener { cursor -> activeCursor = cursor }
                }
            }
        }
    }

    private lateinit var mc: Minecraft
    private var minePadRenderer: MinePadRenderer? = null
    private var smartphoneRenderer: SmartphoneRenderer? = null
    private var laserPointerRenderer: LaserPointerRenderer? = null
    private var nextScreen: Screen? = null

    private var miniservPort = 0
    private var msClientStarted = false

    private val advancementToProgressField: Field? = findAdvancementToProgressField()
    private var lastAdvMgr: ClientAdvancements? = null
    private var advancementToProgress: Map<*, *>? = null

    private val screenTracking = ArrayList<TileEntityScreen>()
    private var lastTracked = 0

    private val padMap = HashMap<UUID, PadData>()
    private val padList = ArrayList<PadData>()
    private var minePadTickCounter = 0
    private var outlineHiddenByMod = false
    private var cefClientHandlersRegistered = false

    init {
        INSTANCE = this
    }

    override fun preInit() {
        super.preInit()
        mc = Minecraft.getInstance()
    }

    override fun init() {
        super.init()
        if (minePadRenderer == null) minePadRenderer = MinePadRenderer()
        if (smartphoneRenderer == null) smartphoneRenderer = SmartphoneRenderer()
        if (laserPointerRenderer == null) laserPointerRenderer = LaserPointerRenderer()
    }

    override fun onCefInit() {
        if (minePadRenderer == null) minePadRenderer = MinePadRenderer()
        if (smartphoneRenderer == null) smartphoneRenderer = SmartphoneRenderer()
        if (laserPointerRenderer == null) laserPointerRenderer = LaserPointerRenderer()

        if (!MCEF.isInitialized()) return

        ensureSchemeHandlers()
        if (!cefClientHandlersRegistered) {
            cefClientHandlersRegistered = true
            val client = MCEF.getClient()
            client.addDisplayHandler(this)
            client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
                override fun onBeforePopup(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    targetUrl: String?,
                    targetFrameName: String?
                ): Boolean {
                    if (!targetUrl.isNullOrBlank() && !targetUrl.equals("about:blank", ignoreCase = true)) {
                        val safeUrl = WebDisplays.applyBlacklist(targetUrl) ?: targetUrl
                        mc.execute { browser?.loadURL(safeUrl) }
                    }
                    return true
                }
            })
            client.addRequestHandler(object : CefRequestHandlerAdapter() {
                override fun onOpenURLFromTab(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    targetUrl: String?,
                    userGesture: Boolean
                ): Boolean {
                    if (!targetUrl.isNullOrBlank() && !targetUrl.equals("about:blank", ignoreCase = true)) {
                        val safeUrl = WebDisplays.applyBlacklist(targetUrl) ?: targetUrl
                        mc.execute { browser?.loadURL(safeUrl) }
                    }
                    return true
                }
            })
        }
        findAdvancementToProgressField()
    }

    override fun postInit() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun getFabricId(): ResourceLocation {
                return ResourceLocation.fromNamespaceAndPath("webdisplays", "gui_loader")
            }

            override fun onResourceManagerReload(resourceManager: ResourceManager) {
                GuiLoader.clearCache()
            }
        })
    }

    override fun getWorld(dim: ResourceKey<Level>): Level {
        val ret = mc.level ?: throw RuntimeException("Level on client is null")
        if (ret.dimension() != dim) {
            throw RuntimeException("Can't get non-current dimension $dim from client.")
        }
        return ret
    }

    override fun enqueue(r: Runnable) {
        mc.submit(r)
    }

    override fun displayGui(data: GuiData) {
        val gui = data.createGui(mc.screen, mc.level!!)
        if (gui != null) mc.setScreen(gui)
    }

    override fun trackScreen(tes: TileEntityScreen, track: Boolean) {
        var idx = -1
        for (i in screenTracking.indices) {
            if (screenTracking[i] === tes) {
                idx = i
                break
            }
        }

        if (track) {
            if (idx < 0) screenTracking.add(tes)
        } else if (idx >= 0) {
            screenTracking.removeAt(idx)
        }
    }

    override fun onAutocompleteResult(pairs: Array<NameUUIDPair>) {
        val screen = mc.screen
        if (screen is WDScreen) {
            if (pairs.isEmpty()) screen.onAutocompleteFailure()
            else screen.onAutocompleteResult(pairs)
        }
    }

    override fun getOnlineGameProfiles(): Array<GameProfile> {
        return arrayOf(mc.player!!.gameProfile)
    }

    override fun screenUpdateResolutionInGui(pos: Vector3i, side: BlockSide, res: Vector2i) {
        val gsc = mc.screen
        if (gsc is GuiScreenConfig && gsc.isForBlock(pos.toBlock(), side)) {
            gsc.updateResolution(res)
        }
    }

    override fun screenUpdateRotationInGui(pos: Vector3i, side: BlockSide, rot: Rotation) {
        val gsc = mc.screen
        if (gsc is GuiScreenConfig && gsc.isForBlock(pos.toBlock(), side)) {
            gsc.updateRotation(rot)
        }
    }

    override fun screenUpdateAutoVolumeInGui(pos: Vector3i, side: BlockSide, av: Boolean) {
        val gsc = mc.screen
        if (gsc is GuiScreenConfig && gsc.isForBlock(pos.toBlock(), side)) {
            gsc.updateAutoVolume(av)
        }
    }

    override fun displaySetPadURLGui(isItem: ItemStack, padURL: String) {
        mc.setScreen(GuiSetURL2(isItem, padURL))
    }

    override fun openMinePadGui(padId: UUID) {
        var pd = padMap[padId]
        val player = mc.player
        if (pd == null && player != null) {
            for (hand in InteractionHand.entries) {
                val stack = player.getItemInHand(hand)
                val item = stack.item
                if (item is ItemMinePad2) {
                    val data = stack.get(DataComponents.CUSTOM_DATA)
                    if (data != null && data.contains("PadID")) {
                        val tag = data.copyTag()
                        if (padId == tag.getUUID("PadID")) {
                            val url = if (tag.contains("PadURL")) tag.getString("PadURL") else CommonConfig.Browser.homepage
                            pd = getOrCreatePad(padId, url, item.isMobileLayout())
                            break
                        }
                    }
                }
            }
            if (pd == null) {
                val inv = player.inventory
                for (i in 0 until inv.containerSize) {
                    val stack = inv.getItem(i)
                    val item = stack.item
                    if (item is ItemMinePad2) {
                        val data = stack.get(DataComponents.CUSTOM_DATA)
                        if (data != null && data.contains("PadID")) {
                            val tag = data.copyTag()
                            if (padId == tag.getUUID("PadID")) {
                                val url = if (tag.contains("PadURL")) tag.getString("PadURL") else CommonConfig.Browser.homepage
                                pd = getOrCreatePad(padId, url, item.isMobileLayout())
                                break
                            }
                        }
                    }
                }
            }
        }

        if (pd?.view != null) {
            mc.setScreen(GuiMinePad(pd))
        }
    }

    fun getOrCreatePad(id: UUID?, url: String, mobile: Boolean): PadData? {
        if (id == null) return null
        val existing = padMap[id]
        if (existing != null) {
            existing.isInHotbar = true
            return existing
        }
        val pd = PadData(url, id, mobile)
        padMap[id] = pd
        padList.add(pd)
        return pd
    }

    fun closePad(id: UUID?) {
        if (id == null) return
        val pd = padMap.remove(id)
        if (pd != null) {
            padList.remove(pd)
            pd.view?.close(true)
            pd.view = null
        }
    }

    override fun hasClientPlayerAdvancement(rl: ResourceLocation): HasAdvancement {
        val player = mc.player
        if (advancementToProgressField != null && player != null && player.connection != null) {
            val cam = player.connection.advancements
            val adv = cam.get(rl) ?: return HasAdvancement.DONT_KNOW

            if (lastAdvMgr !== cam) {
                lastAdvMgr = cam
                advancementToProgress = try {
                    advancementToProgressField.get(cam) as? Map<*, *>
                } catch (t: Throwable) {
                    Log.warningEx("Could not get ClientAdvancementManager.advancementToProgress field", t)
                    null
                }
                if (advancementToProgress == null) return HasAdvancement.DONT_KNOW
            }

            val map = advancementToProgress ?: return HasAdvancement.DONT_KNOW
            val progress = map[adv] ?: return HasAdvancement.NO
            if (progress !is AdvancementProgress) {
                Log.warning("The ClientAdvancementManager.advancementToProgress map does not contain AdvancementProgress instances")
                advancementToProgress = null
                return HasAdvancement.DONT_KNOW
            }
            return if (progress.isDone) HasAdvancement.YES else HasAdvancement.NO
        }

        return HasAdvancement.DONT_KNOW
    }

    override fun getServer(): MinecraftServer? = mc.singleplayerServer

    override fun setMiniservClientPort(port: Int) {
        miniservPort = port
    }

    override fun startMiniservClient() {
        if (miniservPort <= 0) {
            Log.warning("Can't start miniserv client: miniserv is disabled")
            return
        }

        val player = mc.player
        if (player == null) {
            Log.warning("Can't start miniserv client: player is null")
            return
        }

        var saddr: SocketAddress? = player.connection.connection.remoteAddress
        if (saddr !is InetSocketAddress) {
            Log.warning("Miniserv client: remote address is not inet, assuming local address")
            saddr = InetSocketAddress("127.0.0.1", 1234)
        }

        val msAddr = InetSocketAddress(saddr.address, miniservPort)
        Client.getInstance().start(msAddr)
        msClientStarted = true
    }

    override fun isMiniservDisabled(): Boolean = miniservPort <= 0

    override fun closeGui(bp: BlockPos, bs: BlockSide) {
        val scr = mc.screen
        if (scr is WDScreen && scr.isForBlock(bp, bs)) {
            mc.setScreen(null)
        }
    }

    override fun renderRecipes() {
        nextScreen = RenderRecipe()
    }

    override fun isShiftDown(): Boolean = Screen.hasShiftDown()

    override fun onSmartphoneUsed(player: Player, hand: InteractionHand) {
        if (player === mc.player) {
            lastSmartphoneUseMs = System.currentTimeMillis()
        }
    }

    override fun onAddressChange(browser: CefBrowser?, cefFrame: CefFrame?, url: String) {
        if (browser != null) {
            val t = System.currentTimeMillis()
            for (pd in padList) {
                if (pd.view === browser && t - pd.lastURLSent >= 1000) {
                    if (WebDisplays.isSiteBlacklisted(url)) {
                        pd.view?.loadURL(WebDisplays.BLACKLIST_URL)
                    } else {
                        pd.lastURLSent = t
                        WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageMinepadUrl(pd.id, url))
                    }
                    break
                }
            }

            for (tes in screenTracking) {
                tes.updateClientSideURL(browser, url)
            }
        }
    }

    override fun onTitleChange(cefBrowser: CefBrowser?, s: String?) {}

    override fun onTooltip(cefBrowser: CefBrowser?, s: String?): Boolean = false

    override fun onStatusMessage(cefBrowser: CefBrowser?, s: String?) {}

    override fun onConsoleMessage(
        cefBrowser: CefBrowser?,
        logSeverity: CefSettings.LogSeverity?,
        s: String?,
        s1: String?,
        i: Int
    ): Boolean = false

    override fun onCursorChange(cefBrowser: CefBrowser?, i: Int): Boolean = false

    fun onLevelTick(level: ClientLevel) {
        val player = mc.player
        if (player == null || screenTracking.isEmpty()) return

        val id = lastTracked % screenTracking.size
        val tes = screenTracking[id]
        if (tes.level != level) return

        lastTracked++
        if (tes.level !== player.level()) {
            if (!tes.isLoaded()) tes.load()
        } else {
            var dist = Double.POSITIVE_INFINITY
            val bp = tes.blockPos

            for (i in 0 until tes.screenCount()) {
                val scrn = tes.getScreen(i)
                val extentX = scrn.side.right.x.toDouble() * scrn.size.x + scrn.size.y.toDouble() * scrn.side.up.x
                val extentY = scrn.side.right.y.toDouble() * scrn.size.x + scrn.size.y.toDouble() * scrn.side.up.y
                val extentZ = scrn.side.right.z.toDouble() * scrn.size.x + scrn.size.y.toDouble() * scrn.side.up.z

                val centerX = bp.x + 0.5 + extentX / 2.0
                val centerY = bp.y + 0.5 + extentY / 2.0
                val centerZ = bp.z + 0.5 + extentZ / 2.0

                dist = min(dist, player.distanceToSqr(centerX, centerY, centerZ))
            }

            if (dist.isInfinite()) return

            if (tes.isLoaded()) {
                if (dist > WebDisplays.INSTANCE.unloadDistance2) tes.unload()
            } else if (dist <= WebDisplays.INSTANCE.loadDistance2) {
                tes.load()
            }
        }
    }

    fun onTick() {
        updateBlockOutline()
        if (nextScreen != null && mc.screen == null) {
            mc.setScreen(nextScreen)
            nextScreen = null
        }

        if (KEY_MOUSE.isDown) {
            if (!rDown) {
                rDown = true
                mouseOn = !mouseOn
            }
        } else {
            rDown = false
        }

        val curPlayer = Minecraft.getInstance().player
        if (curPlayer == null || curPlayer.getItemInHand(InteractionHand.MAIN_HAND).item !is ItemLaserPointer) {
            mouseOn = false
        }

        if (++minePadTickCounter >= 10) {
            minePadTickCounter = 0
            val ep = mc.player
            for (pd in padList) pd.isInHotbar = false

            if (ep != null) {
                updateInventory(ep.inventory.items, ep.getItemInHand(InteractionHand.MAIN_HAND), 9)
                updateInventory(ep.inventory.offhand, ep.getItemInHand(InteractionHand.OFF_HAND), 1)
                val lvl = mc.level
                if (lvl != null) {
                    val nearbyDrops = lvl.getEntitiesOfClass(ItemEntity::class.java, ep.boundingBox.inflate(48.0))
                    for (drop in nearbyDrops) {
                        val stack = drop.item
                        val padItem = stack.item
                        if (padItem is ItemMinePad2) {
                            val data = stack.get(DataComponents.CUSTOM_DATA)
                            if (data != null && data.contains("PadID")) {
                                val tag = data.copyTag()
                                if (!tag.contains("PadURL")) {
                                    tag.putString("PadURL", CommonConfig.Browser.homepage)
                                }
                                updatePad(tag.getUUID("PadID"), tag, true, padItem.isMobileLayout())
                            }
                        }
                    }
                }
            }

            for (i in padList.size - 1 downTo 0) {
                val pd = padList[i]
                if (!pd.isInHotbar) {
                    pd.view?.close(true)
                    pd.view = null
                    padList.removeAt(i)
                    padMap.remove(pd.id)
                }
            }
        }

        if (LaserPointerRenderer.isOn()) {
            ItemLaserPointer.tick(mc)
        } else {
            ItemLaserPointer.deselect(mc)
        }

        if (msClientStarted && mc.player == null) {
            msClientStarted = false
            Client.getInstance().stop()
        }
    }

    fun onWorldDisconnect() {
        Log.info("World unloaded; killing screens...")
        for (i in screenTracking.size - 1 downTo 0) {
            screenTracking.removeAt(i).unload()
        }
    }

    private fun updateInventory(inv: NonNullList<ItemStack>, heldStack: ItemStack, cnt: Int) {
        for (i in 0 until cnt) {
            val item = inv[i]
            val padItem = item.item
            if (padItem is ItemMinePad2) {
                val data = item.get(DataComponents.CUSTOM_DATA)
                if (data != null && data.contains("PadID")) {
                    val tag = data.copyTag()
                    updatePad(tag.getUUID("PadID"), tag, item === heldStack, padItem.isMobileLayout())
                }
            }
        }
    }

    private fun updatePad(id: UUID, tag: CompoundTag, isSelected: Boolean, mobile: Boolean) {
        val existing = padMap[id]
        if (existing != null) {
            existing.isInHotbar = true
        } else if (isSelected && tag.contains("PadURL")) {
            val pd = PadData(tag.getString("PadURL"), id, mobile)
            padMap[id] = pd
            padList.add(pd)
        }
    }

    fun getMinePadRenderer(): MinePadRenderer? = minePadRenderer

    fun getPadByID(id: UUID): PadData? = padMap[id]

    class ScreenSidePair {
        @JvmField
        var tes: TileEntityScreen? = null

        @JvmField
        var side: BlockSide? = null
    }

    fun findScreenFromBrowser(browser: CefBrowser, pair: ScreenSidePair): Boolean {
        for (tes in screenTracking) {
            for (i in 0 until tes.screenCount()) {
                val scr = tes.getScreen(i)
                if (scr.browser === browser) {
                    pair.tes = tes
                    pair.side = scr.side
                    return true
                }
            }
        }
        return false
    }

    override fun getWorld(context: WDContext): BlockGetter? {
        val senderLevel = super.getWorld(context)
        return senderLevel ?: Minecraft.getInstance().level
    }

    private fun updateBlockOutline() {
        var shouldHide = false
        val hit = mc.hitResult
        val level = mc.level
        if (hit is BlockHitResult && level != null) {
            val state = level.getBlockState(hit.blockPos)
            if (state.block is BlockScreen) {
                val vec = Vector3i(hit.blockPos.x, hit.blockPos.y, hit.blockPos.z)
                val side = BlockSide.fromInt(hit.direction.ordinal)
                if (side != null) {
                    Multiblock.findOrigin(level, vec, side, null)

                    val be = level.getBlockEntity(vec.toBlock())
                    if (be is TileEntityScreen && be.getScreen(side) != null) {
                        shouldHide = true
                    }
                }
            }
        }

        if (shouldHide != outlineHiddenByMod) {
            mc.gameRenderer.setRenderBlockOutline(!shouldHide)
            outlineHiddenByMod = shouldHide
        }
    }

    companion object {
        private var INSTANCE: ClientProxy? = null
        private var schemeHandlersRegistered = false
        private var cachedCrosshairTick = Long.MIN_VALUE
        private var cachedCrosshairX = 240
        private var cachedCrosshairY = 240
        private var lastSmartphoneUseMs = 0L

        @JvmStatic
        fun getSmartphoneTapProgress(): Float {
            val elapsed = System.currentTimeMillis() - lastSmartphoneUseMs
            if (elapsed <= 0L || elapsed >= 450L) return 0.0f
            return elapsed.toFloat() / 450.0f
        }

        @JvmStatic
        fun renderDroppedPad(
            entity: ItemEntity,
            partialTicks: Float,
            pose: PoseStack,
            buffers: MultiBufferSource,
            light: Int
        ): Boolean {
            val stack = entity.item
            val item = stack.item
            if (item !is ItemMinePad2) return false

            val proxy = INSTANCE ?: return false
            if (proxy.minePadRenderer == null) proxy.minePadRenderer = MinePadRenderer()
            if (proxy.smartphoneRenderer == null) proxy.smartphoneRenderer = SmartphoneRenderer()

            val yaw = ((entity.uuid.leastSignificantBits and 0xFFFFL).toFloat() / 65535.0f) * 360.0f
            val isMobile = item.isMobileLayout()
            val scale = if (isMobile) 0.50f else 0.62f

            pose.pushPose()
            pose.translate(0.0, 0.022, 0.0)
            pose.mulPose(Axis.YP.rotationDegrees(yaw))
            pose.mulPose(Axis.XP.rotationDegrees(-90.0f))
            pose.scale(scale, scale, scale)

            if (isMobile) {
                proxy.smartphoneRenderer!!.renderDropped(stack, buffers, pose, light)
            } else {
                proxy.minePadRenderer!!.renderDropped(stack, buffers, pose, light)
            }

            pose.popPose()
            return true
        }

        @JvmField
        val KEY_MOUSE = KeyMapping("webdisplays.key.toggle_mouse", GLFW.GLFW_KEY_R, "key.categories.misc")
        private var rDown = false

        @JvmField
        var mouseOn = false

        @JvmStatic
        fun renderCrosshair(
            options: Options,
            screenWidth: Int,
            screenHeight: Int,
            offset: Int,
            poseStack: GuiGraphics,
            ci: CallbackInfo
        ) {
            val mc = Minecraft.getInstance()
            val player = mc.player ?: return

            val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
            val stack1 = player.getItemInHand(InteractionHand.OFF_HAND)

            val mainItem = stack.item
            val offItem = stack1.item
            if (mainItem is ItemMinePad2 && !mainItem.isMobileLayout()) {
                val sign = if (player.mainArm == HumanoidArm.LEFT) -1f else 1f
                if (!MinePadRenderer.renderAtSide(sign)) {
                    ci.cancel()
                    return
                }
            } else if (offItem is ItemMinePad2 && !offItem.isMobileLayout()) {
                val sign = if (player.mainArm == HumanoidArm.LEFT) 1f else -1f
                if (!MinePadRenderer.renderAtSide(sign)) {
                    ci.cancel()
                    return
                }
            }

            if (mainItem !is ItemLaserPointer && offItem !is ItemLaserPointer) return

            if (!LaserPointerRenderer.isOn()) {
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
                )
                poseStack.blit(
                    ResourceLocation.parse("webdisplays:textures/gui/cursors.png"),
                    (screenWidth - 15) / 2,
                    (screenHeight - 15) / 2,
                    offset,
                    240f,
                    240f,
                    15,
                    15,
                    256,
                    256
                )
                ci.cancel()
                return
            }

            val level = mc.level ?: return
            val gameTime = level.gameTime
            if (gameTime != cachedCrosshairTick) {
                cachedCrosshairTick = gameTime
                cachedCrosshairX = 240
                cachedCrosshairY = 240

                val result = raycast(64.0)
                if (result.type == HitResult.Type.BLOCK && level.getBlockState(result.blockPos).block === BlockInit.blockScreen.get()) {
                    val pos = Vector3i(result.blockPos)
                    val side = BlockSide.entries[result.direction.ordinal]
                    Multiblock.findOrigin(level, pos, side, null)
                    val be = level.getBlockEntity(pos.toBlock())
                    if (be is TileEntityScreen) {
                        val sc = be.getScreen(side)
                        if (sc != null) {
                            var cx = sc.mouseType * 15
                            var cy = cx / 255
                            cx -= cy * 255
                            cy *= 15
                            if (sc.mouseType >= CefCursorType.NOT_ALLOWED.ordinal) cx -= 15
                            cachedCrosshairX = cx
                            cachedCrosshairY = cy
                        }
                    }
                }
            }

            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
            )
            poseStack.blit(
                ResourceLocation.parse("webdisplays:textures/gui/cursors.png"),
                (screenWidth - 15) / 2,
                (screenHeight - 15) / 2,
                offset,
                cachedCrosshairX.toFloat(),
                cachedCrosshairY.toFloat(),
                15,
                15,
                256,
                256
            )
            ci.cancel()
        }

        @JvmStatic
        fun onClientSetup() {
            BlockEntityRenderers.register(TileInit.SCREEN_BLOCK_ENTITY.get(), ScreenRenderer.ScreenRendererProvider())
            ScreenModelLoader.register()
        }

        private fun isMiniservHost(url: String): Boolean {
            val schemeEnd = url.indexOf("://")
            if (schemeEnd < 0) return false
            val slash = url.indexOf('/', schemeEnd + 3)
            val host = if (slash < 0) url.substring(schemeEnd + 3) else url.substring(schemeEnd + 3, slash)
            return try {
                UUID.fromString(host)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }

        @JvmStatic
        fun ensureSchemeHandlers() {
            if (!MCEF.isInitialized()) return
            MobileEmulation.register()
            if (schemeHandlersRegistered) return
            schemeHandlersRegistered = true

            val wd = MCEF.getApp().handle.registerSchemeHandlerFactory(
                "webdisplays", ""
            ) { _, _, _, request ->
                if (isMiniservHost(request.url)) WDScheme(request.url) else WDModScheme()
            }
            val mod = MCEF.getApp().handle.registerSchemeHandlerFactory(
                "mod", "webdisplays"
            ) { _, _, _, _ -> WDModScheme() }

            Log.info("Scheme handler registration: webdisplays=%b mod=%b", wd, mod)
        }

        @JvmStatic
        fun renderFirstPersonItem(
            stack: ItemStack,
            leftHand: Boolean,
            swingProgress: Float,
            equipProgress: Float,
            pose: PoseStack,
            buffers: MultiBufferSource,
            light: Int
        ): Boolean {
            val proxy = INSTANCE
            if (proxy == null || proxy.mc.player == null) return false

            if (proxy.minePadRenderer == null) proxy.minePadRenderer = MinePadRenderer()
            if (proxy.smartphoneRenderer == null) proxy.smartphoneRenderer = SmartphoneRenderer()
            if (proxy.laserPointerRenderer == null) proxy.laserPointerRenderer = LaserPointerRenderer()

            val item = stack.item
            val renderer: IItemRenderer = when {
                ItemInit.SMARTPHONE.isPresent() && item === ItemInit.SMARTPHONE.get() -> proxy.smartphoneRenderer!!
                ItemInit.MINEPAD.isPresent() && item === ItemInit.MINEPAD.get() -> proxy.minePadRenderer!!
                ItemInit.LASER_POINTER.isPresent() && item === ItemInit.LASER_POINTER.get() -> proxy.laserPointerRenderer!!
                else -> return false
            }

            val sign = if (leftHand) -1.0f else 1.0f
            return renderer.render(pose, stack, sign, swingProgress, equipProgress, buffers, light)
        }

        @JvmStatic
        fun raycast(dist: Double): BlockHitResult {
            val mc = Minecraft.getInstance()
            val player = mc.player!!
            val start = player.getEyePosition(1.0f)
            val lookVec = player.lookAngle
            val end = start.add(lookVec.x * dist, lookVec.y * dist, lookVec.z * dist)
            return mc.level!!.clip(ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player))
        }

        private fun findAdvancementToProgressField(): Field? {
            val fields = ClientAdvancements::class.java.declaredFields
            val result = Arrays.stream(fields).filter { it.type == Map::class.java }.findAny()
            if (result.isPresent) {
                try {
                    val ret = result.get()
                    ret.isAccessible = true
                    return ret
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            Log.warning("ClientAdvancementManager.advancementToProgress field could not be found")
            return null
        }

        @JvmStatic
        fun onClientChat(message: String) {
            if (message == "!WD render recipes") {
                WebDisplays.PROXY.renderRecipes()
            }
        }

        @JvmStatic
        fun registerKeybinds() {
            KeyBindingHelper.registerKeyBinding(KEY_MOUSE)
        }

        @JvmStatic
        fun registerClientEvents() {
            ClientTickEvents.END_CLIENT_TICK.register { _ ->
                INSTANCE?.onTick()
            }

            ClientTickEvents.END_WORLD_TICK.register { level ->
                INSTANCE?.onLevelTick(level)
            }

            ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
                INSTANCE?.onWorldDisconnect()
            }

            ClientSendMessageEvents.ALLOW_CHAT.register { message ->
                if (message == "!WD render recipes") {
                    onClientChat(message)
                    return@register false
                }
                if (message == "!WD config") {
                    val mc = Minecraft.getInstance()
                    mc.setScreen(WDConfigScreen.create(mc.screen))
                    return@register false
                }
                true
            }
        }
    }
}
