/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
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
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.Vector3i
import org.lwjgl.glfw.GLFW
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
        loadFrom(ResourceLocation.fromNamespaceAndPath("webdisplays", "gui/seturl.json"))
        tfURL.setText(screenURL ?: "")
        tfURL.focus()
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
            minecraft!!.setScreen(GuiMinePad(returnPad))
        } else {
            minecraft!!.setScreen(null)
        }
    }

    protected fun getUUID(): UUID {
        val st = stack
        if (st != null && !st.isEmpty && st.item is ItemMinePad2) {
            val tag = st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            if (!tag.contains("PadID")) {
                val uuid = returnPad?.id ?: UUID.randomUUID()
                tag.putUUID("PadID", uuid)
                st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                return uuid
            }
            return tag.getUUID("PadID")
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
                    tag.remove("PadID")
                    st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                }
                (WebDisplays.PROXY as ClientProxy).closePad(uuid)
            }

            minecraft!!.setScreen(null)
        }
    }

    @GuiSubscribe
    fun onEnterPressed(ev: TextField.EnterPressedEvent) {
        validate(ev.getText())
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (quitOnEscape && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeToParent()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun validate(rawUrl: String?) {
        var url = rawUrl?.trim() ?: ""
        if (url.isNotEmpty()) {
            try {
                TileEntityScreen.url(url)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

            url = Util.addProtocol(url)

            if (isPad) {
                val uuid = getUUID()
                val mobile = isMobilePhone()
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageMinepadUrl(uuid, url))
                val st = stack
                if (st != null && !st.isEmpty) {
                    val tag = st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                    tag.putUUID("PadID", uuid)
                    tag.putString("PadURL", url)
                    if (mobile) tag.putBoolean("PadMobile", true)
                    st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                }

                val proxy = WebDisplays.PROXY as ClientProxy
                val pd = returnPad ?: proxy.getOrCreatePad(uuid, url, mobile)
                pd?.view?.loadURL(WebDisplays.applyBlacklist(url) ?: url)
                if ((returnPad != null || mobile) && pd?.view != null) {
                    minecraft!!.setScreen(GuiMinePad(pd))
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
