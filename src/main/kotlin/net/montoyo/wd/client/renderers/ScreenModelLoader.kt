package net.montoyo.wd.client.renderers

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.resources.model.Material
import net.minecraft.resources.ResourceLocation

object ScreenModelLoader {
    @JvmField
    val SCREEN_SIDE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("webdisplays", "block/screen")

    private val SIDES = arrayOfNulls<ResourceLocation>(16)

    @JvmField
    val MATERIALS_SIDES = arrayOfNulls<Material>(16)

    init {
        for (i in SIDES.indices) {
            SIDES[i] = ResourceLocation.fromNamespaceAndPath(SCREEN_SIDE.namespace, SCREEN_SIDE.path + i)
            MATERIALS_SIDES[i] = Material(TextureAtlas.LOCATION_BLOCKS, SIDES[i]!!)
        }
    }

    @JvmStatic
    fun register() {
        ModelLoadingPlugin.register { pluginContext ->
            pluginContext.modifyModelAfterBake().register { original, context ->
                if (SCREEN_SIDE == context.resourceId()) ScreenBaker(context.textureGetter()) else original
            }
        }
    }
}
