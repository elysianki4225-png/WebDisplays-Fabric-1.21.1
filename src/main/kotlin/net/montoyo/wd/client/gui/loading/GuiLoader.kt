/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.loading

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.montoyo.wd.client.gui.controls.*
import net.montoyo.wd.utilities.Log
import java.io.IOException
import java.io.InputStreamReader
import java.lang.reflect.Modifier

object GuiLoader {
    private val CONTROLS = HashMap<String, Class<out Control>>()
    private val RESOURCES = HashMap<ResourceLocation, JsonObject>()

    @JvmStatic
    fun register(cls: Class<out Control>) {
        if (Modifier.isAbstract(cls.modifiers)) {
            throw RuntimeException("GG retard, you just registered an abstract class...")
        }

        val name = cls.simpleName
        if (CONTROLS.containsKey(name)) {
            throw RuntimeException("Control class already registered or name taken!")
        }

        CONTROLS[name] = cls
    }

    init {
        register(Button::class.java)
        register(CheckBox::class.java)
        register(ControlGroup::class.java)
        register(Label::class.java)
        register(net.montoyo.wd.client.gui.controls.List::class.java)
        register(TextField::class.java)
        register(Icon::class.java)
        register(UpgradeGroup::class.java)
        register(YTButton::class.java)
    }

    @JvmStatic
    fun create(json: JsonOWrapper): Control {
        val type = json.getString("type", null)
        val ret = try {
            CONTROLS[type]!!.getDeclaredConstructor().newInstance()
        } catch (e: ReflectiveOperationException) {
            Log.errorEx("Could not create control from JSON", e)
            throw RuntimeException(e)
        }

        ret.load(json)
        return ret
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getJson(resLoc: ResourceLocation): JsonObject {
        var ret = RESOURCES[resLoc]
        if (ret == null) {
            val resource = Minecraft.getInstance().resourceManager.getResource(resLoc).get()
            ret = JsonParser.parseReader(InputStreamReader(resource.open())).asJsonObject
            RESOURCES[resLoc] = ret
        }
        return ret
    }

    @JvmStatic
    fun clearCache() {
        RESOURCES.clear()
    }
}
