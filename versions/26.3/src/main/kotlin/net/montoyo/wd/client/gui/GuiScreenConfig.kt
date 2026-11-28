/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.resources.language.I18n
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.montoyo.wd.WebDisplays
import net.montoyo.wd.client.gui.controls.Button
import net.montoyo.wd.client.gui.controls.CheckBox
import net.montoyo.wd.client.gui.controls.Control
import net.montoyo.wd.client.gui.controls.ControlGroup
import net.montoyo.wd.client.gui.controls.Label
import net.montoyo.wd.client.gui.controls.List as WDList
import net.montoyo.wd.client.gui.controls.TextField
import net.montoyo.wd.client.gui.controls.UpgradeGroup
import net.montoyo.wd.client.gui.loading.FillControl
import net.montoyo.wd.config.CommonConfig
import net.montoyo.wd.core.ScreenResolution
import net.montoyo.wd.core.ScreenRights
import net.montoyo.wd.entity.TileEntityScreen
import net.montoyo.wd.item.WDItem
import net.montoyo.wd.net.WDNetworkRegistry
import net.montoyo.wd.net.server_bound.C2SMessageScreenCtrl
import net.montoyo.wd.utilities.BlockSide
import net.montoyo.wd.utilities.Log
import net.montoyo.wd.utilities.NameUUIDPair
import net.montoyo.wd.utilities.Rotation
import net.montoyo.wd.utilities.Vector2i
import java.util.Arrays
import java.util.UUID
import kotlin.math.max

class GuiScreenConfig(
    component: Component,
    private val tes: TileEntityScreen,
    private val side: BlockSide,
    private var friends: Array<NameUUIDPair>,
    private var friendRights: Int,
    private var otherRights: Int
) : WDScreen(component) {
    private var owner: NameUUIDPair? = null
    private var rotation = Rotation.ROT_0
    private var aspectRatio = 0f

    private var waitingAC = false
    private var acFailTicks = -1
    private val acResults = ArrayList<NameUUIDPair>()
    private var adding = false

    @FillControl
    private lateinit var lblOwner: Label

    @FillControl
    private lateinit var lstFriends: WDList

    @FillControl
    private lateinit var btnAdd: Button

    @FillControl
    private lateinit var tfFriend: TextField

    @FillControl
    private lateinit var tfResX: TextField

    @FillControl
    private lateinit var tfResY: TextField

    @FillControl
    private lateinit var grpFriends: ControlGroup

    @FillControl
    private lateinit var grpOthers: ControlGroup

    @FillControl
    private lateinit var boxFSetUrl: CheckBox

    @FillControl
    private lateinit var boxFClick: CheckBox

    @FillControl
    private lateinit var boxFFriends: CheckBox

    @FillControl
    private lateinit var boxFOthers: CheckBox

    @FillControl
    private lateinit var boxFUpgrades: CheckBox

    @FillControl
    private lateinit var boxFResolution: CheckBox

    @FillControl
    private lateinit var boxOSetUrl: CheckBox

    @FillControl
    private lateinit var boxOClick: CheckBox

    @FillControl
    private lateinit var boxOUpgrades: CheckBox

    @FillControl
    private lateinit var boxOResolution: CheckBox

    @FillControl
    private lateinit var btnSetRes: Button

    @FillControl
    private lateinit var btnAutoRes: Button

    @FillControl
    private lateinit var btnPresetHD: Button

    @FillControl
    private lateinit var btnPresetFHD: Button

    @FillControl
    private lateinit var ugUpgrades: UpgradeGroup

    @FillControl
    private lateinit var btnChangeRot: Button

    @FillControl
    private lateinit var cbLockRatio: CheckBox

    @FillControl
    private lateinit var cbAutoVolume: CheckBox

    private lateinit var friendBoxes: Array<CheckBox>
    private lateinit var otherBoxes: Array<CheckBox>
    private var panelBounds: IntArray? = null

    override fun init() {
        super.init()
        loadFrom(Identifier.fromNamespaceAndPath("webdisplays", "gui/screencfg.json"))
        panelBounds = centerInPanel(12, 30, 8)

        friendBoxes = arrayOf(boxFResolution, boxFUpgrades, boxFOthers, boxFFriends, boxFClick, boxFSetUrl)
        boxFResolution.setUserdata(ScreenRights.MODIFY_SCREEN)
        boxFUpgrades.setUserdata(ScreenRights.MANAGE_UPGRADES)
        boxFOthers.setUserdata(ScreenRights.MANAGE_OTHER_RIGHTS)
        boxFFriends.setUserdata(ScreenRights.MANAGE_FRIEND_LIST)
        boxFClick.setUserdata(ScreenRights.INTERACT)
        boxFSetUrl.setUserdata(ScreenRights.CHANGE_URL)

        otherBoxes = arrayOf(boxOResolution, boxOUpgrades, boxOClick, boxOSetUrl)
        boxOResolution.setUserdata(ScreenRights.MODIFY_SCREEN)
        boxOUpgrades.setUserdata(ScreenRights.MANAGE_UPGRADES)
        boxOClick.setUserdata(ScreenRights.INTERACT)
        boxOSetUrl.setUserdata(ScreenRights.CHANGE_URL)

        val scr = tes.getScreen(side)
        if (scr != null) {
            owner = scr.owner
            rotation = scr.rotation

            tfResX.setText("${scr.resolution.x}")
            tfResY.setText("${scr.resolution.y}")
            aspectRatio = scr.resolution.x.toFloat() / scr.resolution.y.toFloat()

            ugUpgrades.setUpgrades(scr.upgrades)
            cbAutoVolume.setChecked(scr.autoVolume)
        }

        if (owner == null) owner = NameUUIDPair("???", UUID.randomUUID())

        lblOwner.setLabel("${lblOwner.getLabel()} ${owner!!.name}")
        for (f in friends) {
            lstFriends.addElementRaw(f.name, f)
        }

        lstFriends.updateContent()
        updateRights(friendRights, friendRights, friendBoxes, true)
        updateRights(otherRights, otherRights, otherBoxes, true)
        updateMyRights()
        updateRotationStr()

        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(WebDisplays.INSTANCE.soundScreenCfg!!, 1.0f, 1.0f)
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, ptt: Float) {
        val pb = panelBounds
        if (pb != null) {
            val scr = tes.getScreen(side)
            val badge = if (scr != null) "${scr.size.x}×${scr.size.y} · ${scr.resolution.x}×${scr.resolution.y}" else ""
            WDTheme.drawGlassPanelWithHeader(
                graphics,
                font,
                pb[0], pb[1], pb[2], pb[3],
                I18n.get("webdisplays.gui.screencfg.title"),
                badge
            )
        }
        super.extractRenderState(graphics, mouseX, mouseY, ptt)
    }

    private fun updateRotationStr() {
        btnChangeRot.setLabel(I18n.get("webdisplays.gui.screencfg.rot${rotation.getAngleAsInt()}"))
    }

    private fun applyAdaptivePreset(preset: Vector2i) {
        val scr = tes.getScreen(side) ?: return

        tfResX.setText("${preset.x}")
        tfResY.setText("${preset.y}")
        aspectRatio = preset.x.toFloat() / max(1, preset.y).toFloat()
        if (preset.x != scr.resolution.x || preset.y != scr.resolution.y) {
            WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.resolution(tes, side, preset))
        }
        btnSetRes.setDisabled(true)
    }

    private fun addFriend(name: String) {
        if (name.isNotEmpty()) {
            requestAutocomplete(name, true)
            tfFriend.setDisabled(true)
            adding = true
            waitingAC = true
        }
    }

    private fun clickSetRes() {
        val scr = tes.getScreen(side) ?: return

        try {
            val x = tfResX.getText().toInt()
            val y = tfResY.getText().toInt()
            if (x < 1 || y < 1) throw NumberFormatException()

            if (x != scr.resolution.x || y != scr.resolution.y) {
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.resolution(tes, side, Vector2i(x, y)))
            }
        } catch (_: NumberFormatException) {
            tfResX.setText("${scr.resolution.x}")
            tfResY.setText("${scr.resolution.y}")
        }

        btnSetRes.setDisabled(true)
    }

    @GuiSubscribe
    fun onClick(ev: Button.ClickEvent) {
        if (ev.getSource() === btnAdd && !waitingAC) {
            addFriend(tfFriend.getText().trim())
        } else if (ev.getSource() === btnSetRes) {
            clickSetRes()
        } else if (ev.getSource() === btnAutoRes) {
            val scr = tes.getScreen(side)
            if (scr != null) {
                applyAdaptivePreset(
                    ScreenResolution.computeAdaptiveResolution(
                        scr.size,
                        CommonConfig.Screen.defaultPixelsPerBlock,
                        CommonConfig.Screen.minAdaptiveEdge,
                        CommonConfig.Screen.maxResolutionX,
                        CommonConfig.Screen.maxResolutionY
                    )
                )
            }
        } else if (ev.getSource() === btnPresetHD) {
            val scr = tes.getScreen(side)
            if (scr != null) {
                applyAdaptivePreset(
                    ScreenResolution.computePresetResolution(
                        scr.size, 720, CommonConfig.Screen.maxResolutionX, CommonConfig.Screen.maxResolutionY
                    )
                )
            }
        } else if (ev.getSource() === btnPresetFHD) {
            val scr = tes.getScreen(side)
            if (scr != null) {
                applyAdaptivePreset(
                    ScreenResolution.computePresetResolution(
                        scr.size, 1080, CommonConfig.Screen.maxResolutionX, CommonConfig.Screen.maxResolutionY
                    )
                )
            }
        } else if (ev.getSource() === btnChangeRot) {
            val rots = Rotation.entries.toTypedArray()
            WDNetworkRegistry.INSTANCE.sendToServer(
                C2SMessageScreenCtrl(tes, side, rots[(rotation.ordinal + 1) % rots.size])
            )
        }
    }

    @GuiSubscribe
    fun onEnterPressed(ev: TextField.EnterPressedEvent) {
        if (ev.getSource() === tfFriend && !waitingAC) {
            addFriend(ev.getText().trim())
        } else if ((ev.getSource() === tfResX || ev.getSource() === tfResY) && !btnSetRes.isDisabled()) {
            clickSetRes()
        }
    }

    @GuiSubscribe
    fun onAutocomplete(ev: TextField.TabPressedEvent) {
        if (ev.getSource() === tfFriend && !waitingAC && ev.getBeginning().isNotEmpty()) {
            if (acResults.isEmpty()) {
                waitingAC = true
                requestAutocomplete(ev.getBeginning(), false)
            } else {
                val pair = acResults.removeAt(0)
                tfFriend.setText(pair.name)
            }
        } else if (ev.getSource() === tfResX) {
            tfResX.setFocused(false)
            tfResY.focus()
            tfResY.getMcField().setCursorPosition(0)
            tfResY.getMcField().setHighlightPos(tfResY.getText().length)
        }
    }

    @GuiSubscribe
    fun onTextChanged(ev: TextField.TextChangedEvent) {
        if (ev.getSource() === tfResX || ev.getSource() === tfResY) {
            val newContent = ev.getNewContent()
            for (i in newContent.indices) {
                if (!Character.isDigit(newContent[i])) {
                    ev.getSource().setText(ev.getOldContent())
                    return
                }
            }

            if (cbLockRatio.isChecked()) {
                if (ev.getSource() === tfResX) {
                    try {
                        var value = newContent.toInt().toFloat()
                        value /= aspectRatio
                        tfResY.setText("${value.toInt()}")
                    } catch (_: NumberFormatException) {
                    }
                } else {
                    try {
                        var value = newContent.toInt().toFloat()
                        value *= aspectRatio
                        tfResX.setText("${value.toInt()}")
                    } catch (_: NumberFormatException) {
                    }
                }
            }

            btnSetRes.setDisabled(false)
        }
    }

    @GuiSubscribe
    fun onRemovePlayer(ev: WDList.EntryClick) {
        if (ev.getSource() === lstFriends) {
            WDNetworkRegistry.INSTANCE.sendToServer(
                C2SMessageScreenCtrl(tes, side, ev.getUserdata() as NameUUIDPair, true)
            )
        }
    }

    @GuiSubscribe
    fun onCheckboxChanged(ev: CheckBox.CheckedEvent) {
        if (isFriendCheckbox(ev.getSource())) {
            val flag = ev.getSource().getUserdata() as Int
            if (ev.isChecked()) friendRights = friendRights or flag
            else friendRights = friendRights and flag.inv()

            requestSync()
        } else if (isOtherCheckbox(ev.getSource())) {
            val flag = ev.getSource().getUserdata() as Int
            if (ev.isChecked()) otherRights = otherRights or flag
            else otherRights = otherRights and flag.inv()

            requestSync()
        } else if (ev.getSource() === cbLockRatio && ev.isChecked()) {
            try {
                val x = tfResX.getText().toInt()
                val y = tfResY.getText().toInt()
                aspectRatio = x.toFloat() / y.toFloat()
            } catch (_: NumberFormatException) {
                cbLockRatio.setChecked(false)
            }
        } else if (ev.getSource() === cbAutoVolume) {
            WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.autoVol(tes, side, ev.isChecked()))
        }
    }

    @GuiSubscribe
    fun onRemoveUpgrade(ev: UpgradeGroup.ClickEvent) {
        WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl(tes, side, ev.getMouseOverStack()!!))
    }

    fun isFriendCheckbox(cb: CheckBox): Boolean = friendBoxes.any { it === cb }

    fun isOtherCheckbox(cb: CheckBox): Boolean = otherBoxes.any { it === cb }

    fun hasFriend(f: NameUUIDPair): Boolean = friends.any { f == it }

    override fun onAutocompleteResult(pairs: Array<NameUUIDPair>) {
        waitingAC = false

        if (adding) {
            if (!hasFriend(pairs[0])) {
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl(tes, side, pairs[0], false))
            }

            tfFriend.setDisabled(false)
            tfFriend.clear()
            tfFriend.focus()
            adding = false
        } else {
            acResults.clear()
            acResults.addAll(Arrays.asList(*pairs))

            val pair = acResults.removeAt(0)
            tfFriend.setText(pair.name)
        }
    }

    override fun onAutocompleteFailure() {
        waitingAC = false
        acResults.clear()
        acFailTicks = 0
        tfFriend.setTextColor(Control.COLOR_RED)

        if (adding) {
            tfFriend.setDisabled(false)
            adding = false
        }
    }

    override fun tick() {
        super.tick()

        if (acFailTicks >= 0) {
            if (++acFailTicks >= 10) {
                acFailTicks = -1
                tfFriend.setTextColor(TextField.DEFAULT_TEXT_COLOR)
            }
        }
    }

    fun updateFriends(friends: Array<NameUUIDPair>) {
        var diff = false
        if (friends.size != this.friends.size) {
            diff = true
        } else {
            for (pair in friends) {
                if (!hasFriend(pair)) {
                    diff = true
                    break
                }
            }
        }

        if (diff) {
            this.friends = friends
            lstFriends.clearRaw()
            for (pair in friends) {
                lstFriends.addElementRaw(pair.name, pair)
            }
            lstFriends.updateContent()
        }
    }

    private fun updateRights(current: Int, newVal: Int, boxes: Array<CheckBox>, force: Boolean): Int {
        if (force || current != newVal) {
            for (box in boxes) {
                val flag = box.getUserdata() as Int
                box.setChecked((newVal and flag) != 0)
            }

            if (!force) {
                Log.info("Screen check boxes were updated")
                abortSync()
            }
        }
        return newVal
    }

    fun updateFriendRights(rights: Int) {
        friendRights = updateRights(friendRights, rights, friendBoxes, false)
    }

    fun updateOtherRights(rights: Int) {
        otherRights = updateRights(otherRights, rights, otherBoxes, false)
    }

    override fun sync() {
        WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl(tes, side, friendRights, otherRights))
        Log.info("Sent sync packet")
    }

    fun updateMyRights() {
        val me = NameUUIDPair(minecraft.player!!.gameProfile)
        var myRights: Int
        var clientIsOwner = false

        if (me == owner) {
            myRights = ScreenRights.ALL
            clientIsOwner = true
        } else if (hasFriend(me)) {
            myRights = friendRights
        } else {
            myRights = otherRights
        }

        grpFriends.setDisabled(!clientIsOwner)

        var flag = (myRights and ScreenRights.MANAGE_FRIEND_LIST) == 0
        lstFriends.setDisabled(flag)
        tfFriend.setDisabled(flag)
        btnAdd.setDisabled(flag)

        flag = (myRights and ScreenRights.MANAGE_OTHER_RIGHTS) == 0
        grpOthers.setDisabled(flag)

        flag = (myRights and ScreenRights.MODIFY_SCREEN) == 0
        tfResX.setDisabled(flag)
        tfResY.setDisabled(flag)
        btnAutoRes.setDisabled(flag)
        btnPresetHD.setDisabled(flag)
        btnPresetFHD.setDisabled(flag)
        btnChangeRot.setDisabled(flag)

        if (flag) btnSetRes.setDisabled(true)

        flag = (myRights and ScreenRights.MANAGE_UPGRADES) == 0
        ugUpgrades.setDisabled(flag)
        cbAutoVolume.setDisabled(flag)
    }

    fun updateResolution(res: Vector2i) {
        aspectRatio = res.x.toFloat() / res.y.toFloat()
        tfResX.setText("${res.x}")
        tfResY.setText("${res.y}")
        btnSetRes.setDisabled(true)
    }

    fun updateRotation(rot: Rotation) {
        rotation = rot
        updateRotationStr()
    }

    fun updateAutoVolume(av: Boolean) {
        cbAutoVolume.setChecked(av)
    }

    override fun isForBlock(bp: BlockPos, side: BlockSide): Boolean {
        return bp == tes.blockPos && side == this.side
    }

    override fun getWikiPageName(): String? {
        val itemStack = ugUpgrades.getMouseOverUpgrade()
        if (itemStack != null) {
            val item = itemStack.item
            return if (item is WDItem) item.getWikiName(itemStack) else null
        }
        return "Screen_Configurator"
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            Minecraft.getInstance().gui.setScreen(null)
            return true
        }
        return super.keyPressed(event)
    }
}
