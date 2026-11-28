/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls

import net.minecraft.network.chat.Component
import net.montoyo.wd.client.gui.loading.JsonOWrapper
import net.montoyo.wd.utilities.Util
import net.montoyo.wd.utilities.VideoType
import java.net.MalformedURLException
import java.net.URL

class YTButton : Button(), TextField.TextChangeListener {
    private var urlField: TextField? = null

    init {
        btn.message = Component.nullToEmpty("YT")
        btn.active = false
        btn.setVariant(WDButton.Variant.SUBTLE)
        shiftColor = 0xFFFF6464.toInt()
    }

    override fun onClick(): Boolean {
        val uf = urlField
        if (uf != null) {
            val urlStr = Util.addProtocol(uf.getText())
            val url = try {
                URL(urlStr)
            } catch (_: MalformedURLException) {
                return true
            }

            val vt = VideoType.getTypeFromURL(url)
            if (vt == VideoType.YOUTUBE) {
                uf.setText(VideoType.YOUTUBE_EMBED.getURLFromID(vt.getVideoIDFromURL(url), shiftDown))
            }
        }
        return true
    }

    fun setURLField(tf: TextField?) {
        urlField?.removeTextChangeListener(this)
        urlField = tf
        urlField?.addTextChangeListener(this)
    }

    fun getURLField(): TextField? = urlField

    override fun load(json: JsonOWrapper) {
        super.load(json)
        val tfName = json.getString("urlField", null)
        if (tfName != null) {
            val ctrl = parent!!.getControlByName(tfName)
            if (ctrl is TextField) {
                urlField = ctrl
                ctrl.addTextChangeListener(this)
            }
        }
    }

    override fun onTextChange(tf: TextField, oldContent: String, newContent: String) {
        btn.active = VideoType.getTypeFromURL(Util.addProtocol(newContent)) == VideoType.YOUTUBE
    }
}
