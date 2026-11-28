/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.ClientProxy
import net.montoyo.wd.client.gui.controls.Button
import net.montoyo.wd.client.gui.controls.TextField
import net.montoyo.wd.client.gui.loading.FillControl
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.item.ItemMinePad2
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageMinepadUrl
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.NbtCompat
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector3i
import java.io.IOException
import java.util.UUID

class GuiSetURL2 : WDScreen {
    private var tileEntity: TileEntityScreen? = null
    private var screenSide: BlockSide? = null
    private var remoteLocation: Vector3i? = null

    private var stack: ItemStack? = null
    private val isPad: Boolean
    private val returnPad: ClientProxy.PadData?

    private val screenURL: String?
    private var panelBounds: IntArray? = null

    @FillControl
    private lateinit var tfURL: TextField

    @FillControl
    private lateinit var btnShutDown: Button

    @FillControl
    private lateinit var btnCancel: Button

    @FillControl
    private lateinit var btnOk: Button

    constructor(tes: TileEntityScreen?, side: BlockSide?, url: String?, rl: Vector3i?) : super(Component.nullToEmpty(null)) {
        tileEntity = tes
        screenSide = side
        remoteLocation = rl
        isPad = false
        stack = null
        returnPad = null
        screenURL = url
    }

    @JvmOverloads
    constructor(isItem: ItemStack?, url: String?, returnPad: ClientProxy.PadData? = null) : super(Component.nullToEmpty(null)) {
        isPad = true
        stack = isItem
        this.returnPad = returnPad
        screenURL = url
    }

    override fun init() {
        super.init()
        loadFrom(Identifier.fromNamespaceAndPath("webdisplays", "gui/seturl.json"))
        panelBounds = centerInPanel(12, 32, 10)
        tfURL.setText(screenURL ?: "")
        tfURL.focus()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        val pb = panelBounds
        if (pb != null) {
            val isPhone = isMobilePhone()
            val titleKey = if (isPad) {
                if (isPhone) "webdisplays.gui.seturl.title_phone" else "webdisplays.gui.seturl.title_pad"
            } else {
                "webdisplays.gui.seturl.title"
            }
            val title = I18n.get(titleKey)
            WDTheme.drawGlassPanelWithHeader(
                graphics,
                font,
                pb[0], pb[1], pb[2], pb[3],
                title,
                if (isPad) (if (isPhone) "phone" else "minePad") else null
            )
        }
        super.extractRenderState(graphics, mouseX, mouseY, ptt)
    }

    override fun addLoadCustomVariables(vars: MutableMap<String, Double>) {
        vars["isPad"] = if (isPad) 1.0 else 0.0
    }

    private fun isMobilePhone(): Boolean {
        val st = stack
        if (st != null && !st.isEmpty) {
            val item = st.item
            if (item is ItemMinePad2 && item.isMobileLayout()) return true
        }
        return returnPad != null && returnPad.mobile
    }

    private fun closeToParent() {
        if (returnPad != null && returnPad.view != null) {
            minecraft.gui.setScreen(GuiMinePad(returnPad))
        } else {
            minecraft.gui.setScreen(null)
        }
    }

    protected fun getUUID(): UUID {
        val st = stack
        if (st != null && !st.isEmpty && st.item is ItemMinePad2) {
            val tag = st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            val existing = NbtCompat.getUUID(tag, "PadID")
            if (existing == null) {
                val uuid = returnPad?.id ?: UUID.randomUUID()
                NbtCompat.putUUID(tag, "PadID", uuid)
                st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                return uuid
            }
            return existing
        }
        if (returnPad?.id != null) return returnPad.id
        throw RuntimeException("Get UUID is being called for a non-minepad UI")
    }

    @GuiSubscribe
    fun onButtonClicked(ev: Button.ClickEvent) {
        if (ev.getSource() === btnCancel) {
            closeToParent()
        } else if (ev.getSource() === btnOk) {
            validate(tfURL.getText())
        } else if (ev.getSource() === btnShutDown) {
            if (isPad) {
                val uuid = getUUID()
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageMinepadUrl(uuid, ""))
                val st = stack
                if (st != null && !st.isEmpty) {
                    val tag = st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                    NbtCompat.removeUUID(tag, "PadID")
                    st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                }
                (WebDisplays.PROXY as ClientProxy).closePad(uuid)
            }

            minecraft.gui.setScreen(null)
        }
    }

    @GuiSubscribe
    fun onEnterPressed(ev: TextField.EnterPressedEvent) {
        validate(ev.getText())
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (quitOnEscape && event.key() == InputConstants.KEY_ESCAPE) {
            closeToParent()
            return true
        }
        return super.keyPressed(event)
    }

    private fun validate(rawUrl: String?) {
        var url = rawUrl?.trim() ?: ""
        if (url.isNotEmpty()) {
            try {
                TileEntityScreen.url(url)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

            url = Util.resolveUrlOrSearch(url)

            if (isPad) {
                val uuid = getUUID()
                val mobile = isMobilePhone()
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageMinepadUrl(uuid, url))
                val st = stack
                if (st != null && !st.isEmpty) {
                    val tag = st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                    NbtCompat.putUUID(tag, "PadID", uuid)
                    tag.putString("PadURL", url)
                    if (mobile) tag.putBoolean("PadMobile", true)
                    st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                }

                val proxy = WebDisplays.PROXY as ClientProxy
                val pd = returnPad ?: proxy.getOrCreatePad(uuid, url, mobile)
                pd?.view?.loadURL(WebDisplays.applyBlacklist(url) ?: url)
                if ((returnPad != null || mobile) && pd?.view != null) {
                    minecraft.gui.setScreen(GuiMinePad(pd))
                    return
                }
            } else {
                WDNetworkRegistry.INSTANCE.sendToServer(
                    C2SMessageScreenCtrl.setURL(tileEntity!!, screenSide!!, url, remoteLocation)
                )
            }
        }

        closeToParent()
    }

    override fun isForBlock(bp: BlockPos, side: BlockSide): Boolean {
        val rl = remoteLocation
        val te = tileEntity
        return (rl != null && rl.equalsBlockPos(bp)) || (te != null && bp == te.blockPos && side == screenSide)
    }
}
